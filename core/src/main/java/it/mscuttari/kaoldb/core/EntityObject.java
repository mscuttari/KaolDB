/*
 * Copyright 2018 Scuttari Michele
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.mscuttari.kaoldb.core;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArraySet;

import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;
import org.objenesis.instantiator.ObjectInstantiator;

import it.mscuttari.kaoldb.annotations.Column;
import it.mscuttari.kaoldb.annotations.DiscriminatorColumn;
import it.mscuttari.kaoldb.annotations.DiscriminatorValue;
import it.mscuttari.kaoldb.annotations.Entity;
import it.mscuttari.kaoldb.annotations.JoinColumn;
import it.mscuttari.kaoldb.annotations.JoinColumns;
import it.mscuttari.kaoldb.annotations.JoinTable;
import it.mscuttari.kaoldb.annotations.ManyToMany;
import it.mscuttari.kaoldb.annotations.ManyToOne;
import it.mscuttari.kaoldb.annotations.OneToMany;
import it.mscuttari.kaoldb.annotations.OneToOne;
import it.mscuttari.kaoldb.annotations.Table;
import it.mscuttari.kaoldb.annotations.UniqueConstraint;
import it.mscuttari.kaoldb.exceptions.InvalidConfigException;
import it.mscuttari.kaoldb.exceptions.MappingException;
import it.mscuttari.kaoldb.exceptions.PojoException;
import it.mscuttari.kaoldb.exceptions.QueryException;
import it.mscuttari.kaoldb.interfaces.EntityManager;
import it.mscuttari.kaoldb.interfaces.Expression;
import it.mscuttari.kaoldb.interfaces.QueryBuilder;
import it.mscuttari.kaoldb.interfaces.Root;

import static com.google.common.base.Preconditions.checkNotNull;
import static it.mscuttari.kaoldb.core.ConcurrentSession.doAndNotifyAll;
import static it.mscuttari.kaoldb.core.ConcurrentSession.waitWhile;
import static it.mscuttari.kaoldb.core.Propagation.Action.*;
import static it.mscuttari.kaoldb.core.StringUtils.escape;
import static it.mscuttari.kaoldb.core.StringUtils.implode;

/**
 * Each {@link EntityObject} maps a class annotated with the {@link Entity} annotation.
 */
class EntityObject<T> {

    /** Database the entity belongs to */
    public final DatabaseObject db;

    /** Entity class */
    public final Class<T> clazz;

    /** Entity class instantiator */
    private final ObjectInstantiator<T> instantiator;

    /**
     * Parent entity.
     * <p><code>Null</code> if the entity has no parent.</p>
     *
     * @see #loadParent()
     */
    @Nullable
    private EntityObject<? super T> parent;

    /** Whether the parent has been determined or not during the mapping process */
    private final AtomicBoolean parentLoaded = new AtomicBoolean(false);

    /**
     * Discriminator value.
     * <p><code>Null</code> if the entity has no parent.</p>
     *
     * @see #loadDiscriminatorValue()
     */
    @Nullable
    public Object discriminatorValue;

    /**
     * Children entities.
     * <p>The children entities are determined during the parent determination process. Every time
     * an entity find its parent entity, it is also added to the children list of the latter.</p>
     *
     * @see #loadParent()
     */
    @NonNull
    public Collection<EntityObject<? extends T>> children = new ArraySet<>();

    /**
     * Table name.
     * <p><code>Null</code> if the entity doesn't require a real table.</p>
     *
     * @see #loadTableName()
     */
    @Nullable
    public String tableName;

    /**
     * Columns of the table.
     *
     * <p>Populated during the 2nd phase of the mapping process.<br>
     * At the end of the mapping process, it contains all and only the columns that build
     * the real table in the database. Therefore, it contains also the inherited primary key
     * columns in case of a <code>JOINED</code> inheritance strategy or the children columns
     * in case of a <code>SINGLE_TABLE</code> inheritance strategy.</p>
     *
     * @see #loadColumns()
     * @see DatabaseObject#mapEntities()
     */
    public Columns columns = new Columns(this);

    /** Whether, during the mapping process, the parent columns have already been added or not */
    private final AtomicBoolean parentColumnsInherited = new AtomicBoolean(false);

    /**
     * Discriminator column.
     * <p>Determined during the 2nd phase of the mapping process.</p>
     *
     * @see #loadColumns()
     * @see DatabaseObject#mapEntities()
     */
    @Nullable
    public BaseColumnObject discriminatorColumn;

    /**
     * Relationships.
     *
     * <p>The collection contains the fields declared in {@link #clazz} (superclasses are
     * excluded) that are annotated with {@link OneToOne}, {@link OneToMany}, {@link ManyToOne}
     * or {@link ManyToMany}.</p>
     *
     * @see #loadRelationships()
     */
    public Relationships relationships = new Relationships();


    /**
     * Constructor.
     *
     * @param db        database
     * @param clazz     entity class
     */
    private EntityObject(@NonNull DatabaseObject db, @NonNull Class<T> clazz) {
        this.db = db;
        this.clazz = checkNotNull(clazz);

        Objenesis objenesis = new ObjenesisStd();
        this.instantiator = objenesis.getInstantiatorOf(clazz);
    }


    /**
     * Create the {@link EntityObject} linked to an entity class and start the mapping process.
     *
     * @param db        database
     * @param clazz     entity class
     * @param <T>       entity class
     *
     * @return entity object
     */
    public static <T> EntityObject<T> map(@NonNull DatabaseObject db, @NonNull Class<T> clazz) {
        EntityObject<T> result = new EntityObject<>(db, clazz);

        ConcurrentSession.singleTask(() -> {
            LogUtils.d("[Database \"" + db.getName() + "\"] mapping class " + clazz.getSimpleName());

            result.loadTableName();
            result.loadParent();
            result.loadDiscriminatorValue();
            result.loadRelationships();
            result.loadColumns();

            // Tell the database that the entity has been completely mapped
            db.entityMapped();
        });

        return result;
    }


    @NonNull
    @Override
    public String toString() {
        return "Class: " + getName() + ", " +
                "Parent: " + (getParent() == null ? "null" : getParent().getName()) + ", " +
                "Children: {" + implode(children, EntityObject::getName, ", ") + "}, " +
                "Columns: " + columns + ", " +
                "Primary keys: " + columns.getPrimaryKeys();
    }


    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof EntityObject))
            return false;

        EntityObject<?> entityObject = (EntityObject<?>) obj;
        return clazz.equals(entityObject.clazz);
    }


    @Override
    public int hashCode() {
        return clazz.hashCode();
    }


    /**
     * Get the simple name of the class associated to this entity.
     *
     * @return class name
     */
    public String getName() {
        return clazz.getSimpleName();
    }


    /**
     * Determine the table name.
     *
     * <p>If the table is not specified, the following policy is applied:</p>
     * <p>Uppercase characters are replaces with underscore followed by the same character converted
     * to lowercase. Only the first class name character, if uppercase, is converted to lowercase
     * while avoiding the underscore.</p>
     * <p>Example: EntityClassName => entity_class_name</p>
     *
     * @throws InvalidConfigException if the class doesn't have the @Table annotation and doesn't
     *                                expect a real table (possible only in case of
     *                                <code>TABLE_PER_CLASS</code> inheritance strategy, which is
     *                                anyway not supported)
     */
    private void loadTableName() {
        String result;
        Table annotation = clazz.getAnnotation(Table.class);

        if (annotation == null) {
            throw new InvalidConfigException("Class " + clazz.getSimpleName() + " doesn't have the @Table annotation");

        } else if (!annotation.name().isEmpty()) {
            // Get specified table name
            result = annotation.name();

        } else {
            // Default table name
            LogUtils.w("[Class \"" + clazz.getSimpleName() + "\"] table name not specified, using the default one based on class name");

            String className = clazz.getSimpleName();
            char[] c = className.toCharArray();
            c[0] = Character.toLowerCase(c[0]);
            className = new String(c);
            result = className.replaceAll("([A-Z])", "_$1").toLowerCase();
        }

        doAndNotifyAll(this, () -> tableName = result);
    }


    /**
     * Determine the parent entity and eventually add this entity to its children.
     */
    private void loadParent() {
        EntityObject<? super T> parent = null;
        Class<? super T> superClass = clazz.getSuperclass();

        while (superClass != null && superClass != Object.class && parent == null) {
            // We need to check if the current class is one of the mapped classes,
            // because there could be non-entity classes between the child and the
            // parent entities.

            if (db.contains(superClass)) {
                parent = db.getEntity(superClass);
            }

            // Go up in the class hierarchy (which can be, as explained before,
            // different from entity hierarchy).
            superClass = superClass.getSuperclass();
        }

        if (parent != null) {
            parent.addChild(this);
        }

        EntityObject<? super T> result = parent;

        doAndNotifyAll(this, () -> {
            this.parent = result;
            parentLoaded.set(true);
        });
    }


    /**
     * Add a child to the children list.
     *
     * @param child     child entity
     */
    private void addChild(EntityObject<? extends T> child) {
        doAndNotifyAll(this, () -> {
            LogUtils.d("[Entity \"" + getName() + "\"] found child " + child.getName());
            children.add(child);
        });
    }


    /**
     * Block the calling thread until the parent has been determined.
     *
     * @see #loadParent()
     */
    private void waitUntilParentLoaded() {
        waitWhile(this, () -> !parentLoaded.get());
    }


    /**
     * Get the parent entity.
     * <p>The calling thread is blocked until the parent has been determined by the mapping process.</p>
     *
     * @return parent entity (<code>null</code> if the current entity has no parent)
     */
    @Nullable
    public EntityObject<? super T> getParent() {
        waitUntilParentLoaded();
        return parent;
    }


    /**
     * Determine the discriminator value.
     */
    private void loadDiscriminatorValue() {
        DiscriminatorValue annotation = clazz.getAnnotation(DiscriminatorValue.class);
        Object value = annotation == null ? null : annotation.value();
        doAndNotifyAll(this, () -> discriminatorValue = value);
    }


    /**
     * Get the child with a specific {@link #discriminatorValue}.
     *
     * @param discriminator     discriminator value of the child entity
     * @return child entity
     * @throws NoSuchElementException if there is no child with the specified discriminator value
     */
    public EntityObject<? extends T> getChild(Object discriminator) {
        if (discriminator != null) {
            for (EntityObject<? extends T> child : children) {
                // Children discriminator values should not be null by definition
                assert child.discriminatorValue != null : "Entity \"" + child.clazz.getSimpleName() + "\" should not have a null discriminator value";

                if (child.discriminatorValue.equals(discriminator)) {
                    return child;
                }
            }
        }

        throw new NoSuchElementException("Entity \"" + clazz.getSimpleName() + "\" has no child with discriminator value \"" + discriminator + "\"");
    }


    /**
     * Determine the relationships fields.
     *
     * A field is considered a relationship one if it is annotated with {@link OneToOne},
     * {@link OneToMany}, {@link ManyToMany} or {@link ManyToMany}.
     */
    private void loadRelationships() {
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);

            if (field.isAnnotationPresent(OneToOne.class) ||
                    field.isAnnotationPresent(OneToMany.class) ||
                    field.isAnnotationPresent(ManyToOne.class) ||
                    field.isAnnotationPresent(ManyToMany.class)) {

                Relationship relationship = new Relationship(db, field);
                doAndNotifyAll(this, () -> relationships.add(relationship));
            }
        }
    }


    /**
     * Get the unique columns sets (both the unique columns specified in the
     * {@link Table} annotation).
     *
     * @return list of unique columns sets
     * @throws InvalidConfigException if a column with the specified name doesn't exist
     * @see Table#uniqueConstraints()
     */
    private Collection<Collection<BaseColumnObject>> getMultipleUniqueColumns() {
        Collection<Collection<BaseColumnObject>> result = new ArrayList<>();
        Columns columns = new Columns(this, this.columns.getColumnsContainers());
        Table table = clazz.getAnnotation(Table.class);

        if (table != null) {
            UniqueConstraint[] uniqueConstraints = table.uniqueConstraints();

            for (UniqueConstraint uniqueConstraint : uniqueConstraints) {
                Collection<BaseColumnObject> multipleUniqueColumns = new ArrayList<>(uniqueConstraint.columnNames().length);

                for (String columnName : uniqueConstraint.columnNames()) {
                    BaseColumnObject column = columns.get(columnName);

                    if (column == null) {
                        throw new InvalidConfigException("Unique constraint: column \"" + columnName + "\" not found");
                    }

                    multipleUniqueColumns.add(column);
                }

                result.add(multipleUniqueColumns);
            }
        }

        return result;
    }


    /**
     * Load entity columns.
     *
     * <p>By normal columns are intended the one originated from a {@link Column} annotated field.</p>
     * <p>The join columns are derived from:
     * <ul>
     *      <li>Fields annotated with {@link OneToOne} that are the owning side</li>
     *      <li>Fields annotated with {@link ManyToOne}</li>
     * </ul></p>
     *
     * @throws InvalidConfigException in case of multiple column declaration
     */
    public void loadColumns() {
        // Normal and join columns

        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Column.class)) {
                columns.addAll(Columns.entityFieldToColumns(db, this, field));

            } else if (field.isAnnotationPresent(OneToOne.class)) {
                OneToOne annotation = field.getAnnotation(OneToOne.class);

                if (!annotation.mappedBy().isEmpty())
                    continue;

                columns.addAll(Columns.entityFieldToColumns(db, this, field));

            } else if (field.isAnnotationPresent(ManyToOne.class)) {
                columns.addAll(Columns.entityFieldToColumns(db, this, field));
            }

            // Fields annotated with @OneToMany and @ManyToMany are skipped because they don't lead to new columns.
            // In fact, those annotations should only map the existing table columns to the join table ones.
        }

        columns.map();
        columns.waitUntilMapped();

        LogUtils.i("[Entity \"" + getName() + "\"] own columns mapped");

        // Wait until all the hierarchies are determined
        Collection<EntityObject<?>> entities = db.getEntities();

        for (EntityObject<?> entity : entities) {
            entity.waitUntilParentLoaded();
        }

        // Parent inherited primary keys
        EntityObject<? super T> parent = getParent();

        if (parent != null) {
            waitWhile(this, () -> !parent.parentColumnsInherited.get());
            columns.addAll(parent.columns.getPrimaryKeys());
        }

        doAndNotifyAll(this, () -> parentColumnsInherited.set(true));
        LogUtils.i("[Entity \"" + getName() + "\"] inherited columns added");

        // Discriminator column

        if (children.size() != 0) {
            if (!clazz.isAnnotationPresent(DiscriminatorColumn.class))
                throw new InvalidConfigException("Class " + getName() + " has @Inheritance annotation but not @DiscriminatorColumn");

            DiscriminatorColumn discriminatorColumnAnnotation = clazz.getAnnotation(DiscriminatorColumn.class);

            if (discriminatorColumnAnnotation == null) {
                // Security check. Normally not reachable
                throw new MappingException("Class " + getName() + " doesn't have @DiscriminatorColumn annotation");
            }

            if (discriminatorColumnAnnotation.name().isEmpty())
                throw new InvalidConfigException("Class " + getName() + ": empty discriminator column");

            discriminatorColumn = columns.get(discriminatorColumnAnnotation.name());

            if (discriminatorColumn == null) {
                throw new InvalidConfigException("Class " + getName() + ": discriminator column \"" + discriminatorColumnAnnotation.name() + "\" not found");
            }

            doAndNotifyAll(discriminatorColumn, () -> discriminatorColumn.nullable = false);

            // Fix the discriminator value type
            for (EntityObject<? extends T> child : children) {
                doAndNotifyAll(child, () -> {
                    assert child.discriminatorValue != null;

                    switch (discriminatorColumnAnnotation.discriminatorType()) {
                        case CHAR:
                            child.discriminatorValue = ((String) child.discriminatorValue).charAt(0);
                            break;

                        case INTEGER:
                            child.discriminatorValue = Integer.valueOf((String) child.discriminatorValue);
                            break;
                    }
                });
            }
        }

        LogUtils.d("[Entity \"" + getName() + "\"] all columns loaded");
    }


    /**
     * Get field of a class given its name.
     * <p>The returned field is already set as accessible using {@link Field#setAccessible(boolean)}.</p>
     *
     * @param fieldName     field name
     * @return accessible field
     */
    public Field getField(String fieldName) {
        return getField(clazz, fieldName);
    }


    /**
     * Get the field of a class given the field name.
     *
     * <p>
     * The returned field is already set as accessible using {@link Field#setAccessible(boolean)}.
     * </p>
     *
     * @param clazz         class the field belongs to
     * @param fieldName     field name
     *
     * @return accessible field
     *
     * @throws IllegalArgumentException if there is no field in the class with the specified name
     */
    public static Field getField(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getField(fieldName);
            field.setAccessible(true);
            return field;

        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException("Field \"" + fieldName + "\" not found in class \"" + clazz.getSimpleName() + "\"", e);
        }
    }


    /**
     * Create an instance of the entity class.
     *
     * @return object having a class equal to {@link #clazz}
     */
    public T newInstance() {
        return instantiator.newInstance();
    }


    /**
     * Convert cursor to POJO.
     *
     * <p>The conversion automatically search for the child class according to the discriminator value.<br>
     * In fact, the method starts with a first part with the aim of going down through the hierarchy
     * tree in order to retrieve the leaf class representing the real object class.<br>
     * Then, the second part creates an instance of that class and populates its basic fields with
     * the data contained in the cursor.</p>
     *
     * @param c             cursor
     * @param alias         result class alias
     *
     * @return populated object
     *
     * @throws PojoException if the child class is not found (wrong discriminator column value) or
     *                       if it can not be instantiated
     */
    public T parseCursor(Cursor c, String alias) {
        // The result class may be abstract, so we need to determine the real class of the result,
        // which will be a subclass of the requested one.

        EntityObject<? extends T> resultEntity = this;

        // Go down to the child class.
        // Each iteration will go one step down through the hierarchy tree.

        while (resultEntity.children.size() != 0) {
            // Get the discriminator value
            assert resultEntity.discriminatorColumn != null;
            String discriminatorColumnName = alias + "." + resultEntity.discriminatorColumn.name;
            int columnIndex = c.getColumnIndexOrThrow(discriminatorColumnName);
            Object discriminatorValue = null;

            if (resultEntity.discriminatorColumn.type.equals(Integer.class)) {
                discriminatorValue = c.getInt(columnIndex);
            } else if (resultEntity.discriminatorColumn.type.equals(String.class)) {
                discriminatorValue = c.getString(columnIndex);
            }

            // Determine the child class according to the discriminator value specified
            resultEntity = resultEntity.getChild(discriminatorValue);
        }

        T result = resultEntity.newInstance();

        // We are now in the leaf entity. Go back up in the hierarchy tree and populate
        // the fields of each parent entity.

        EntityObject<?> entity = resultEntity;

        while (entity != null) {
            for (BaseColumnObject column : entity.columns) {
                if (column.hasRelationship()) {
                    // Relationships are loaded separately
                    continue;
                }

                // The parents and the children entities have their entity name appended
                // to their root aliases.

                Object fieldValue = column.parseCursor(c, entity.clazz.equals(clazz) ? alias: alias + entity.getName());
                column.setValue(result, fieldValue);
            }

            entity = entity.getParent();
        }

        return result;
    }


    /**
     * Prepare the data to be saved in a particular entity table.
     *
     * <p>{@link ContentValues#size()} must be checked before saving the data in the database.
     * If zero, no data needs to be saved and, if not skipped, the
     * {@link SQLiteDatabase#insert(String, String, ContentValues)} method would throw an exception.</p>
     *
     * @param entityManager     entity manager
     * @param childEntity       immediate child entity (can be <code>null</code> if this entity has no children)
     * @param obj               object to be converted
     *
     * @return data ready to be saved in the database
     *
     * @throws PojoException if the discriminator value has been manually set but is not
     *                       compatible with the child entity class
     * @throws PojoException if the field associated with the discriminator value can't be accessed
     */
    @NonNull
    public ContentValues toContentValues(Object obj, EntityObject childEntity, EntityManager entityManager) {
        ContentValues cv = new ContentValues();

        // Discriminator column
        if (childEntity != null) {
            assert discriminatorColumn != null;

            if (cv.containsKey(discriminatorColumn.name)) {
                // Discriminator value has been manually set.
                // Checking if it is in accordance with the child entity class.

                Object specifiedDiscriminatorValue = cv.get(discriminatorColumn.name);

                if (specifiedDiscriminatorValue == null || !specifiedDiscriminatorValue.equals(childEntity.discriminatorValue))
                    throw new PojoException("Wrong discriminator value: expected \"" + childEntity.discriminatorValue + "\", found \"" + specifiedDiscriminatorValue + "\"");

            } else {
                // The discriminator value has not been found. Adding it automatically

                if (discriminatorColumn.field.isAnnotationPresent(Column.class)) {
                    // The discriminator column is linked to a field annotated with @Column
                    discriminatorColumn.addToContentValues(cv, childEntity.discriminatorValue);

                } else if (discriminatorColumn.field.isAnnotationPresent(JoinColumn.class)) {
                    // The discriminator column is linked to a field annotated with @JoinColumn
                    JoinColumn joinColumnAnnotation = discriminatorColumn.field.getAnnotation(JoinColumn.class);
                    Class<?> discriminatorType = discriminatorColumn.field.getType();

                    // Create the discriminator object containing the discriminator column
                    Object discriminator = discriminatorColumn.newInstance();

                    try {
                        // Set the child discriminator value
                        Field discriminatorField = discriminatorType.getField(joinColumnAnnotation.referencedColumnName());
                        discriminatorField.setAccessible(true);
                        discriminatorField.set(discriminator, childEntity.discriminatorValue);

                        // Assign the discriminator value to the object to be persisted
                        discriminatorColumn.setValue(obj, discriminator);

                    } catch (IllegalAccessException e) {
                        throw new PojoException(e);

                    } catch (NoSuchFieldException e) {
                        throw new PojoException(e);
                    }

                    discriminatorColumn.addToContentValues(cv, obj);

                } else if (discriminatorColumn.field.isAnnotationPresent(JoinColumns.class)) {
                    // The discriminator column is linked to a field annotated with @JoinColumns
                    JoinColumns joinColumnsAnnotation = discriminatorColumn.field.getAnnotation(JoinColumns.class);
                    Class<?> discriminatorType = discriminatorColumn.field.getType();

                    // Create the discriminator object containing the discriminator column
                    Object discriminator = discriminatorColumn.newInstance();

                    try {
                        for (JoinColumn joinColumn : joinColumnsAnnotation.value()) {
                            if (joinColumn.name().equals(discriminatorColumn.name)) {
                                // Set the child discriminator value
                                Field discriminatorField = discriminatorType.getField(joinColumn.referencedColumnName());
                                discriminatorField.set(discriminator, childEntity.discriminatorValue);

                                // Assign the discriminator value to the object to be persisted
                                discriminatorColumn.setValue(obj, discriminator);

                                break;
                            }
                        }

                    } catch (IllegalAccessException e) {
                        throw new PojoException(e);

                    } catch (NoSuchFieldException e) {
                        throw new PojoException(e);
                    }

                    discriminatorColumn.addToContentValues(cv, obj);

                } else if (discriminatorColumn.field.isAnnotationPresent(JoinTable.class)) {
                    // The discriminator column is linked to a field annotated with @JoinTable
                    Class<?> discriminatorType = discriminatorColumn.field.getType();

                    // Create the discriminator object containing the discriminator column
                    Object discriminator = discriminatorColumn.newInstance();

                    try {
                        JoinTable discriminatorColumnAnnotation = discriminatorColumn.field.getAnnotation(JoinTable.class);

                        for (JoinColumn joinColumn : (discriminatorColumnAnnotation).joinColumns()) {
                            if (joinColumn.name().equals(discriminatorColumn.name)) {
                                // Set the child discriminator value
                                Field discriminatorField = discriminatorType.getField(joinColumn.referencedColumnName());
                                discriminatorField.set(discriminator, childEntity.discriminatorValue);

                                // Assign the discriminator value to the object to be persisted
                                discriminatorColumn.setValue(obj, discriminator);

                                break;
                            }
                        }

                    } catch (IllegalAccessException e) {
                        throw new PojoException(e);

                    } catch (NoSuchFieldException e) {
                        throw new PojoException(e);
                    }

                    discriminatorColumn.addToContentValues(cv, obj);
                }
            }
        }

        // Columns
        for (BaseColumnObject column : columns) {
            Object fieldValue = column.getValue(obj);

            if (!checkDataExistence(fieldValue, db, entityManager))
                throw new QueryException("Field \"" + column.field.getName() + "\" doesn't exist in the database. Persist it first!");

            column.addToContentValues(cv, obj);
        }

        return cv;
    }


    /**
     * Check if an object already exists in the database.
     *
     * @param obj               object to be searched. In case of basic object type (Integer,
     *                          String, etc.) the check will be successful; in case of complex type
     *                          (custom classes), a query searching for the object is run
     * @param db                {@link DatabaseObject} of the database the entity belongs to
     * @param entityManager     entity manager
     *
     * @return <code>true</code> if the data already exits in the database; <code>false</code> otherwise
     *
     * @throws InvalidConfigException  if the entity has a primary key not linked to a class field
     *                                 (not normally reachable situation)
     *
     * @see #isPrimitiveType(Class)
     */
    @SuppressWarnings("unchecked")
    private static boolean checkDataExistence(Object obj, DatabaseObject db, EntityManager entityManager) {
        // Null data is considered to be existing
        if (obj == null)
            return true;

        Class<?> clazz = obj.getClass();

        // Primitive data can't be stored alone in the database because they have no table associated.
        // Therefore, primitive data existence check should be skipped (this way it is always
        // considered successful)

        if (isPrimitiveType(clazz))
            return true;

        // Non-primitive data existence must be checked and so a select query is executed
        // The select query is based on the primary keys of the entity
        EntityObject entity = db.getEntity(clazz);

        QueryBuilder<?> qb = entityManager.getQueryBuilder(entity.clazz);
        Root root = qb.getRoot(entity.clazz);

        Expression where = null;

        for (BaseColumnObject primaryKey : entity.columns.getPrimaryKeys()) {
            // Create the property that the generated class would have because of the primary key field
            SingleProperty property = new SingleProperty<>(entity.clazz, primaryKey.field.getType(), primaryKey.field);

            Object value = primaryKey.getValue(obj);
            Expression expression = root.eq(property, value);

            // In case of multiple primary keys, concatenated the WHERE expressions
            where = where == null ? expression : where.and(expression);
        }

        // Run the query and check if its result is not an empty set
        Object queryResult = qb.from(root).where(where).build(root).getSingleResult();
        return queryResult != null;
    }


    /**
     * Check if the data class can be stored in the database as if it is a primitive type.
     *
     * @param clazz     data class
     * @return <code>true</code> if a "primitive-compatible class" is found;
     *         <code>false</code> otherwise
     */
    private static boolean isPrimitiveType(Class<?> clazz) {
        return Enum.class.isAssignableFrom(clazz) ||
                boolean.class.isAssignableFrom(clazz) ||
                Boolean.class.isAssignableFrom(clazz) ||
                byte.class.isAssignableFrom(clazz) ||
                Byte.class.isAssignableFrom(clazz) ||
                char.class.isAssignableFrom(clazz) ||
                Character.class.isAssignableFrom(clazz) ||
                short.class.isAssignableFrom(clazz) ||
                Short.class.isAssignableFrom(clazz) ||
                int.class.isAssignableFrom(clazz) ||
                Integer.class.isAssignableFrom(clazz) ||
                long.class.isAssignableFrom(clazz) ||
                Long.class.isAssignableFrom(clazz) ||
                float.class.isAssignableFrom(clazz) ||
                Float.class.isAssignableFrom(clazz) ||
                double.class.isAssignableFrom(clazz) ||
                Double.class.isAssignableFrom(clazz) ||
                String.class.isAssignableFrom(clazz) ||
                Date.class.isAssignableFrom(clazz) ||
                Calendar.class.isAssignableFrom(clazz);
    }


    /**
     * Get the SQL query to create the entity table.
     *
     * <p>The result can be used to create just the table directly linked to the provided entity.<br>
     * Optional join tables that are related to eventual internal fields must be managed
     * separately and in a second moment (after the creation of all the normal tables).</p>
     *
     * @return SQL query for table creation
     */
    @Nullable
    public String getSQL() {
        StringBuilder result = new StringBuilder();

        // Table name
        result.append("CREATE TABLE IF NOT EXISTS ")
                .append(escape(tableName))
                .append(" (");

        // Columns
        String columnSql = columns.getSQL();

        if (columnSql != null && !columnSql.isEmpty())
            result.append(columnSql);

        // Primary keys
        String primaryKeysSql = getTablePrimaryKeysSql(columns.getPrimaryKeys());

        if (primaryKeysSql != null && !primaryKeysSql.isEmpty()) {
            result.append(", ").append(primaryKeysSql);
        }

        // Unique keys (multiple columns)
        String uniqueKeysSql = getTableUniquesSql(getMultipleUniqueColumns());

        if (uniqueKeysSql != null && !uniqueKeysSql.isEmpty()) {
            result.append(", ").append(uniqueKeysSql);
        }

        // Foreign keys
        String foreignKeysSql = getTableForeignKeysSql();

        if (foreignKeysSql != null && !foreignKeysSql.isEmpty()) {
            result.append(", ").append(foreignKeysSql);
        }

        result.append(");");

        return result.toString();
    }


    /**
     * Get primary keys SQL statement to be inserted in the create table query.
     *
     * <p>Example: <code>PRIMARY KEY("column_1", "column_2", "column_3")</code></p>
     *
     * @param primaryKeys       collection of primary keys
     * @return SQL statement (<code>null</code> if the SQL statement is not needed in the main query)
     */
    @Nullable
    public static String getTablePrimaryKeysSql(Collection<BaseColumnObject> primaryKeys) {
        if (primaryKeys == null || primaryKeys.size() == 0) {
            return null;
        }

        return "PRIMARY KEY (" +
                implode(primaryKeys, column -> escape(column.name), ", ") +
                ")";
    }


    /**
     * Get unique columns SQL statement to be inserted in the create table query.
     *
     * <p>The parameter uniqueColumns is a collection of collections because each unique constraint
     * can be made of multiple columns. For example, a collection such as
     * <code>[[column_1, column_2], [column_2, column_3, column_4]]</code> would generate the
     * statement <code>UNIQUE("column_1", "column_2"), UNIQUE("column_2", "column_3", "column_4")</code></p>
     *
     * @param uniqueColumns     unique columns groups
     * @return SQL statement (<code>null</code> if the SQL statement is not needed in the main query)
     */
    @Nullable
    private static String getTableUniquesSql(Collection<Collection<BaseColumnObject>> uniqueColumns) {
        if (uniqueColumns == null || uniqueColumns.size() == 0) {
            return null;
        }

        Collection<String> uniqueSets = new ArrayList<>(uniqueColumns.size());

        for (Collection<BaseColumnObject> uniqueSet : uniqueColumns) {
            if (uniqueSet == null || uniqueSet.size() == 0)
                continue;

            uniqueSets.add(
                    "UNIQUE (" +
                    implode(uniqueSet, column -> escape(column.name), ", ") +
                    ")"
            );
        }

        if (uniqueSets.size() == 0) {
            return null;
        }

        return implode(uniqueSets, obj -> obj, ", ");
    }


    /**
     * Get the foreign keys SQL constraints to be inserted in the create table query.
     *
     * <p>Example:
     * <code>
     *     FOREIGN KEY ("column_1", "column_2") REFERENCES "referenced_table_1" ("referenced_column_1", "referenced_column_2") ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,
     *     FOREIGN KEY ("column_3", "column_4") REFERENCES "referenced_table_2" ("referenced_column_3", "referenced_column_4") ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,
     *     FOREIGN KEY ("column_5", "column_6") REFERENCES "referenced_table_3" ("referenced_column_5", "referenced_column_6") ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED
     * </code></p>
     *
     * @return SQL statement (<code>null</code> if the SQL statement is not needed in the main query)
     */
    @Nullable
    private String getTableForeignKeysSql() {
        Collection<String> constraints = new ArrayList<>();

        // Inheritance
        String inheritanceSql = getTableInheritanceConstraints();

        if (inheritanceSql != null && !inheritanceSql.isEmpty()) {
            constraints.add(inheritanceSql);
        }

        // Relationships
        String relationshipsSql = getTableRelationshipsConstraints();

        if (relationshipsSql != null && !relationshipsSql.isEmpty()) {
            constraints.add(relationshipsSql);
        }

        // Create SQL statement
        if (constraints.size() == 0) {
            return null;
        }

        return implode(constraints, obj -> obj, ", ");
    }


    /**
     * Get the inheritance SQL constraint to be inserted in the create table query.
     *
     * <p>Example:
     * <code>
     *     FOREIGN KEY ("primary_key_1", "primary_key_2")
     *     REFERENCES "parent_table" ("primary_key_1", "primary_key_2")
     *     ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED
     * </code></p>
     *
     * @return SQL statement (<code>null</code> if the SQL statement is not needed in the main query)
     */
    @Nullable
    private String getTableInheritanceConstraints() {
        EntityObject<? super T> parent = getParent();

        if (parent == null) {
            return null;
        }

        // Create associations
        Collection<BaseColumnObject> parentPrimaryKeys = parent.columns.getPrimaryKeys();
        String columns = implode(parentPrimaryKeys, primaryKey -> escape(primaryKey.name), ", ");
        Propagation propagation = new Propagation(CASCADE, CASCADE);

        return "FOREIGN KEY (" + columns + ") " +
                "REFERENCES " + escape(parent.tableName) + " (" + columns + ") " +
                propagation;
    }


    /**
     * Get the relationships SQL constraints to be inserted in the create table query.
     *
     * <p>Example:
     * <code>
     *     FOREIGN KEY ("column_1", "column_2")
     *     REFERENCES "referenced_table_1" ("referenced_column_1", "referenced_column_2")
     *     ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED
     * </code><br>
     * <code>
     *     FOREIGN KEY ("column_3", "column_4")
     *     REFERENCES "referenced_table_2" ("referenced_column_3", "referenced_column_4")
     *     ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED
     * </code></p>
     *
     * @return SQL statement (<code>null</code> if the SQL statement is not needed in the main query)
     */
    @Nullable
    private String getTableRelationshipsConstraints() {
        Collection<String> constraints = new ArrayList<>();

        for (ColumnsContainer container : columns.getColumnsContainers()) {
            if (container instanceof JoinColumnObject) {
                // @JoinColumn
                JoinColumnObject joinColumn = (JoinColumnObject) container;
                JoinColumn annotation = joinColumn.field.getAnnotation(JoinColumn.class);
                EntityObject<?> linkedEntity = db.getEntity(joinColumn.field.getType());

                constraints.add(
                        "FOREIGN KEY (" + escape(joinColumn.name) + ") " +
                        "REFERENCES " + escape(linkedEntity.tableName) + " (" + escape(annotation.referencedColumnName()) + ") " +
                        joinColumn.propagation
                );

            } else if (container instanceof JoinColumnsObject) {
                // @JoinColumns
                JoinColumnsObject joinColumns = (JoinColumnsObject) container;
                EntityObject<?> linkedEntity = db.getEntity(joinColumns.field.getType());

                List<String> local = new ArrayList<>();          // Local columns
                List<String> referenced = new ArrayList<>();     // Referenced columns

                for (BaseColumnObject column : joinColumns) {
                    JoinColumnObject joinColumn = (JoinColumnObject) column;
                    local.add(joinColumn.name);
                    referenced.add(joinColumn.linkedColumn.name);
                }

                constraints.add(
                        "FOREIGN KEY (" + implode(local, StringUtils::escape, ", ") + ") " +
                        "REFERENCES " + escape(linkedEntity.tableName) + " (" +
                        implode(referenced, StringUtils::escape, ", ") + ") " +
                        joinColumns.propagation
                );
            }
        }

        if (constraints.isEmpty()) {
            return null;
        }

        return implode(constraints, obj -> obj, ", ");
    }

}
