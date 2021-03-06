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

package it.mscuttari.kaoldb.mapping;

import android.content.ContentValues;

import androidx.annotation.NonNull;

import java.lang.reflect.Field;

import it.mscuttari.kaoldb.annotations.Id;
import it.mscuttari.kaoldb.annotations.JoinColumn;
import it.mscuttari.kaoldb.annotations.JoinColumns;
import it.mscuttari.kaoldb.annotations.JoinTable;
import it.mscuttari.kaoldb.annotations.ManyToOne;
import it.mscuttari.kaoldb.annotations.OneToOne;
import it.mscuttari.kaoldb.exceptions.InvalidConfigException;

import static it.mscuttari.kaoldb.ConcurrentSession.doAndNotifyAll;
import static it.mscuttari.kaoldb.ConcurrentSession.waitWhile;
import static it.mscuttari.kaoldb.mapping.Propagation.Action.CASCADE;
import static it.mscuttari.kaoldb.mapping.Propagation.Action.RESTRICT;
import static it.mscuttari.kaoldb.mapping.Propagation.Action.SET_NULL;

/**
 * This class allows to map a column acting as a foreign key.
 *
 * @see JoinColumn
 */
final class JoinColumnObject extends FieldColumnObject {

    /** The {@link JoinColumn} annotation that is responsible of this column properties */
    @NonNull
    private final JoinColumn annotation;

    /** Foreign key constraints */
    public Propagation propagation;

    /** Linked column */
    public BaseColumnObject linkedColumn;

    /**
     * Constructor.
     *
     * @param db            database
     * @param entity        entity the column belongs to
     * @param field         field the column is generated from
     * @param annotation    {@link JoinColumn} annotation the column is generated from
     */
    public JoinColumnObject(@NonNull DatabaseObject db,
                            @NonNull EntityObject<?> entity,
                            @NonNull Field field,
                            @NonNull JoinColumn annotation) {

        super(db, entity, field);

        this.annotation = annotation;
        this.name = annotation.name().isEmpty() ? getDefaultName(field) : annotation.name();
    }

    @Override
    protected void mapAsync() {
        loadPropagationProperty();
        loadLinkedColumn();
    }

    @Override
    public <T> T accept(Visitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    protected void loadCustomColumnDefinition() {
        String result = annotation.columnDefinition().isEmpty() ? null : annotation.columnDefinition();
        doAndNotifyAll(this, () -> customColumnDefinition = result);
    }

    @Override
    protected void loadType() {
        Class<?> referencedClass = null;
        String referencedColumnName = null;

        if (field.isAnnotationPresent(JoinColumn.class) || field.isAnnotationPresent(JoinColumns.class)) {
            // The linked column directly belongs to the linked entity class, so to determine the
            // column type is sufficient to search for that foreign key and get its type

            referencedClass = field.getType();
            referencedColumnName = annotation.referencedColumnName();

        } else if (field.isAnnotationPresent(JoinTable.class)) {
            // The linked column belongs to a middle join table, so to determine the column type
            // we need to first determine that column class. Its class is indeed derived from the
            // foreign key of the second entity

            referencedColumnName = annotation.referencedColumnName();
            JoinTable joinTableAnnotation = field.getAnnotation(JoinTable.class);

            // We need to determine if the column is a direct or inverse join column.
            // If it is a direct join column, the column type is the same of the field enclosing class.
            // If it is an inverse join column, the column type is the field one.

            boolean direct = false;

            // Search in the direct join columns.
            // If not found, then the column must be an inverse join one.

            for (JoinColumn joinColumn : joinTableAnnotation.joinColumns()) {
                if (joinColumn.equals(annotation)) {
                    direct = true;
                    break;
                }
            }

            if (direct) {
                referencedClass = joinTableAnnotation.joinClass();
            } else {
                referencedClass = joinTableAnnotation.inverseJoinClass();
            }
        }

        if (referencedClass == null) {
            // Security check. Normally not reachable.
            throw new InvalidConfigException("Field \"" + field.getName() + "\": can't determine the referenced class");
        }

        EntityObject<?> referencedEntity = db.getEntity(referencedClass);
        BaseColumnObject referencedColumn = null;

        while (referencedEntity != null && referencedColumn == null) {
            EntityObject<?> refEntity = referencedEntity;
            String refColName = referencedColumnName;

            // Wait for the referenced column to be mapped
            waitWhile(referencedEntity.columns, () -> !refEntity.columns.contains(refColName));
            referencedColumn = refEntity.columns.get(referencedColumnName);

            // Go up in entity hierarchy
            referencedEntity = referencedEntity.getParent();
        }

        if (referencedColumn == null) {
            throw new InvalidConfigException("Field \"" + field.getName() + "\": referenced column \"" + referencedColumnName + "\" not found");
        }

        BaseColumnObject column = referencedColumn;

        // Wait for the referenced column type to be determined
        waitWhile(column, () -> column.type == null);

        // Save the current column type
        doAndNotifyAll(this, () -> type = column.type);
    }

    @Override
    protected void loadNullableProperty() {
        boolean result;

        if (annotation.nullable()) {
            // If the columns is nullable by itself, then the relationship optionality doesn't matter
            result = true;

        } else {
            // A non nullable column may be null if the relationship is optional

            if (field.isAnnotationPresent(OneToOne.class)) {
                result = field.getAnnotation(OneToOne.class).optional();

            } else if (field.isAnnotationPresent(ManyToOne.class)) {
                result = field.getAnnotation(ManyToOne.class).optional();

            } else {
                result = false;
            }
        }

        doAndNotifyAll(this, () -> nullable = result);
    }

    @Override
    protected void loadPrimaryKeyProperty() {
        boolean result = field.isAnnotationPresent(Id.class) || field.isAnnotationPresent(JoinTable.class);
        doAndNotifyAll(this, () -> primaryKey = result);
    }

    @Override
    protected void loadUniqueProperty() {
        boolean result = annotation.unique();
        doAndNotifyAll(this, () -> unique = result);
    }

    @Override
    protected void loadDefaultValue() {
        String result = annotation.defaultValue().isEmpty() ? null : annotation.defaultValue();
        doAndNotifyAll(this, () -> defaultValue = result);
    }

    /**
     * Determine the propagation property.
     */
    private void loadPropagationProperty() {
        waitWhile(this, () -> nullable == null);

        if (nullable) {
            propagation = new Propagation(CASCADE, SET_NULL);
        } else {
            propagation = new Propagation(CASCADE, RESTRICT);
        }
    }

    /**
     * Determine the linked column.
     */
    private void loadLinkedColumn() {
        EntityObject<?> linkedEntity;

        if (field.isAnnotationPresent(JoinTable.class)) {
            JoinTable joinTableAnnotation = field.getAnnotation(JoinTable.class);

            // Determine if the join column is a direct or inverse one
            boolean direct = false;

            for (JoinColumn joinColumn : joinTableAnnotation.joinColumns()) {
                if (joinColumn.equals(annotation)) {
                    direct = true;
                    break;
                }
            }

            Class<?> linkedEntityClass = direct ? joinTableAnnotation.joinClass() : joinTableAnnotation.inverseJoinClass();
            linkedEntity = db.getEntity(linkedEntityClass);

        } else {
            linkedEntity = db.getEntity(field.getType());
        }

        // Wait for the linked column to be mapped
        waitWhile(linkedEntity.columns, () -> !linkedEntity.columns.contains(annotation.referencedColumnName()));

        linkedColumn = linkedEntity.columns.get(annotation.referencedColumnName());
    }

    @Override
    public boolean hasRelationship() {
        return true;
    }

    @Override
    public void addToContentValues(@NonNull ContentValues cv, Object obj) {
        Object sourceObject = getValue(obj);

        if (sourceObject == null) {
            insertIntoContentValues(cv, name, null);
        } else {
            EntityObject<?> destinationEntity = db.getEntity(sourceObject.getClass());
            BaseColumnObject destinationColumn = destinationEntity.columns.get(annotation.referencedColumnName());
            assert destinationColumn != null : "Column \"" + annotation.referencedColumnName() + "\" not found in entity \"" + destinationEntity.getName() + "\"";
            Object value = destinationColumn.getValue(sourceObject);
            insertIntoContentValues(cv, name, value);
        }
    }

    @Override
    public <T> T accept(ColumnsContainer.Visitor<T> visitor) {
        return visitor.visit(this);
    }



}
