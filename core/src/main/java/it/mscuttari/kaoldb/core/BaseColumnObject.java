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

import java.lang.reflect.Field;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;
import org.objenesis.instantiator.ObjectInstantiator;

import it.mscuttari.kaoldb.annotations.JoinColumn;
import it.mscuttari.kaoldb.annotations.JoinColumns;
import it.mscuttari.kaoldb.annotations.JoinTable;
import it.mscuttari.kaoldb.exceptions.MappingException;
import it.mscuttari.kaoldb.exceptions.PojoException;

import static it.mscuttari.kaoldb.core.StringUtils.escape;

/**
 * Representation of the basic properties of a column.
 *
 * @see SimpleColumnObject
 * @see JoinColumnObject
 */
abstract class BaseColumnObject implements ColumnsContainer {

    /** Database */
    @NonNull
    protected final DatabaseObject db;

    /** Entity the column belongs to */
    @NonNull
    protected final EntityObject<?> entity;

    /** Field the column is generated from */
    @NonNull
    public final Field field;

    /** Field class instantiator */
    private final ObjectInstantiator<?> instantiator;

    /** Mapping session */
    private final ConcurrentSession mappingSession = new ConcurrentSession();

    /** Column name */
    public String name;

    /** Custom column definition */
    @Nullable
    public String customColumnDefinition;

    /** Column type */
    public Class<?> type;

    /** Nullable column property */
    public Boolean nullable;

    /** Primary key column property */
    public Boolean primaryKey;

    /** Unique column property */
    public Boolean unique;

    /** Default value */
    @Nullable
    public String defaultValue;


    /**
     * Constructor.
     *
     * @param db                database
     * @param entity            entity the column belongs to
     * @param field             field the column is generated from
     */
    public BaseColumnObject(@NonNull DatabaseObject db,
                            @NonNull EntityObject<?> entity,
                            @NonNull Field field) {

        this.db = db;
        this.entity = entity;
        this.field = field;

        Objenesis objenesis = new ObjenesisStd();
        Class<?> fieldClass = field.getType();

        if (fieldClass == boolean.class) {
            fieldClass = Boolean.class;
        } else if (fieldClass == byte.class) {
            fieldClass = Byte.class;
        } else if (fieldClass == char.class) {
            fieldClass = Character.class;
        } else if (fieldClass == short.class) {
            fieldClass = Short.class;
        } else if (fieldClass == int.class) {
            fieldClass = Integer.class;
        } else if (fieldClass == long.class) {
            fieldClass = Long.class;
        } else if (fieldClass == float.class) {
            fieldClass = Float.class;
        } else if (fieldClass == double.class) {
            fieldClass = Double.class;
        }

        this.instantiator = objenesis.getInstantiatorOf(fieldClass);
    }


    @NonNull
    @Override
    public final String toString() {
        return name;
    }


    @Override
    public final boolean equals(Object obj) {
        if (!(obj instanceof BaseColumnObject))
            return false;

        BaseColumnObject columnObject = (BaseColumnObject) obj;
        return name.equals(columnObject.name);
    }


    @Override
    public final int hashCode() {
        return name.hashCode();
    }


    @NonNull
    @Override
    public Iterator<BaseColumnObject> iterator() {
        return new SingleColumnIterator(this);
    }


    @Override
    public final void map() {
        mappingSession.submit(this::mapAsync);
    }


    /**
     * Actions that are executed asynchronously in order to load the column properties.
     */
    protected abstract void mapAsync();


    @Override
    public final void waitUntilMapped() {
        try {
            mappingSession.waitForAll();

        } catch (ExecutionException | InterruptedException e) {
            throw new MappingException(e);
        }
    }


    /**
     * Get the default name for a column.
     *
     * <p>
     * Uppercase characters are replaced with underscore followed by the same character converted
     * to lowercase. Only the first character, if uppercase, is converted to lowercase avoiding
     * the underscore.<br>
     * Example: <code>columnFieldName</code> => <code>column_field_name</code>
     * </p>
     *
     * @param field     field the column is generated from
     * @return column name
     */
    protected static String getDefaultName(@NonNull Field field) {
        String fieldName = field.getName();
        char[] c = fieldName.toCharArray();
        c[0] = Character.toLowerCase(c[0]);
        fieldName = new String(c);
        return fieldName.replaceAll("([A-Z])", "_$1").toLowerCase();
    }


    /**
     * Get field value.
     *
     * @param obj       object to get the value from
     * @return field value
     * @throws PojoException if the field can't be accessed
     */
    @Nullable
    public final Object getValue(Object obj) {
        if (obj == null)
            return null;

        field.setAccessible(true);

        try {
            return field.get(obj);
        } catch (IllegalAccessException e) {
            throw new PojoException(e);
        }
    }


    /**
     * Set field value.
     *
     * @param obj       object containing the field to be set
     * @param value     value to be set
     *
     * @throws PojoException if the field can't be accessed
     */
    public final void setValue(Object obj, Object value) {
        field.setAccessible(true);

        try {
            field.set(obj, value);
        } catch (IllegalAccessException e) {
            throw new PojoException(e);
        }
    }


    /**
     * Check if the field leads to a relationship with another entity.<br>
     * This is the same of checking if the field is annotated with {@link JoinColumn},
     * {@link JoinColumns} or {@link JoinTable}.
     *
     * @return <code>true</code> if the field leads to a relationship; <code>false</code> otherwise
     */
    public abstract boolean hasRelationship();


    /**
     * Create an instance of the field class.
     *
     * @return object having a class equal to the one of {@link #field}
     */
    public Object newInstance() {
        return instantiator.newInstance();
    }


    /**
     * Extract the column value from a {@link Cursor}.
     *
     * @param c             cursor
     * @param alias         alias of the table
     *
     * @return column value
     *
     * @throws PojoException if the data type is not compatible with the one found in the cursor
     */
    @SuppressWarnings("unchecked")
    public Object parseCursor(Cursor c, String alias) {
        int columnIndex = c.getColumnIndexOrThrow(alias + "." + name);
        int columnType = c.getType(columnIndex);

        Object value = null;

        if (columnType == Cursor.FIELD_TYPE_INTEGER) {
            if (type.equals(Integer.class) || type.equals(int.class)) {
                value = c.getInt(columnIndex);

            } else if (type.equals(Long.class) || type.equals(long.class)) {
                value = c.getLong(columnIndex);

            } else if (type.equals(Date.class)) {
                value = new Date(c.getLong(columnIndex));

            } else if (type.equals(Calendar.class)) {
                value = Calendar.getInstance();
                ((Calendar) value).setTimeInMillis(c.getLong(columnIndex));

            } else {
                throw new PojoException("Incompatible data type: expected " + type.getSimpleName() + ", found Integer");
            }

        } else if (columnType == Cursor.FIELD_TYPE_FLOAT) {
            if (type.equals(Float.class) || type.equals(float.class)) {
                value = c.getFloat(columnIndex);

            } else if (type.equals(Double.class) || type.equals(double.class)) {
                value = c.getDouble(columnIndex);

            } else {
                throw new PojoException("Incompatible data type: expected " + type.getSimpleName() + ", found Float");
            }

        } else if (columnType == Cursor.FIELD_TYPE_STRING) {
            if (type.isEnum()) {
                Enum enumClass = Enum.class.cast(type);

                if (enumClass == null)
                    throw new PojoException("Enum class \"" + type.getSimpleName() + "\" not found");

                value = Enum.valueOf(enumClass.getClass(), c.getString(columnIndex));

            } else if (String.class.isAssignableFrom(type)) {
                value = c.getString(columnIndex);
            }

            if (!(type.equals(String.class)))
                throw new PojoException("Incompatible data type: expected " + type.getSimpleName() + ", found String");

        }

        return value;
    }


    /**
     * Insert a value into {@link ContentValues}.
     *
     * <p>
     * The conversion of the <code>value</code> follows these rules:
     * <ul>
     *      <li>If the column name is already in use, its value is replaced with the newer one</li>
     *      <li>Passing a <code>null</code> value will result in a <code>NULL</code> table column</li>
     *      <li>An empty string (<code>""</code>) will not generate a <code>NULL</code> column but
     *      a string with a length of zero (empty string)</li>
     *      <li>Boolean values are stored either as <code>1</code> (<code>true</code>) or
     *      <code>0</code> (<code>false</code>)</li>
     *      <li>{@link Date} and {@link Calendar} objects are stored by saving their time in
     *      milliseconds, which is got by respectively calling {@link Date#getTime()} and
     *      {@link Calendar#getTimeInMillis()}</li>
     * </ul>
     * </p>
     *
     * @param cv        content values
     * @param column    column name
     * @param value     value
     */
    static void insertIntoContentValues(ContentValues cv, String column, Object value) {
        if (value == null) {
            cv.putNull(column);

        } else {
            if (value instanceof Enum) {
                cv.put(column, ((Enum) value).name());

            } else if (value instanceof Integer || value.getClass().equals(int.class)) {
                cv.put(column, (int) value);

            } else if (value instanceof Long || value.getClass().equals(long.class)) {
                cv.put(column, (long) value);

            } else if (value instanceof Float || value.getClass().equals(float.class)) {
                cv.put(column, (float) value);

            } else if (value instanceof Double || value.getClass().equals(double.class)) {
                cv.put(column, (double) value);

            } else if (value instanceof String) {
                cv.put(column, (String) value);

            } else if (value instanceof Boolean || value.getClass().equals(boolean.class)) {
                cv.put(column, ((boolean) value) ? 1 : 0);

            } else if (value instanceof Date) {
                cv.put(column, ((Date) value).getTime());

            } else if (value instanceof Calendar) {
                cv.put(column, ((Calendar) value).getTimeInMillis());
            }
        }
    }


    /**
     * Get the column SQL statement to be used in the create table query.
     *
     * <p>
     * The columns definition takes into consideration the following parameters:
     * <ul>
     *      <li><b>Name</b></li>
     *      <li><b>Column definition</b>: if specified, the following parameters are skipped</li>
     *      <li><b>Type</b>: the column type determination is based on the field type and with respect of
     *            the following associations:
     *            <ul>
     *                  <li>int, Integer   => INTEGER</li>
     *                  <li>long, Long     => INTEGER</li>
     *                  <li>float, Float   => REAL</li>
     *                  <li>double, Double => REAL</li>
     *                  <li>String         => TEXT</li>
     *                  <li>Date, Calendar => INTEGER (date is stored as milliseconds from epoch)</li>
     *                  <li>Enum           => TEXT (enum constant name)</li>
     *                  <li>Anything else  => BLOB</li>
     *           </ul>
     *      </li>
     *      <li><b>Nullability</b></li>
     *      <li><b>Uniqueness</b></li>
     *      <li><b>Default value</b></li>
     * </ul>
     * Example: <code>"column 1" REAL NOT NULL</code>
     * </p>
     *
     * @return SQL statement
     */
    public final String getSQL() {
        StringBuilder result = new StringBuilder();

        // Column name
        result.append(escape(name));

        // Custom column definition
        if (customColumnDefinition != null && !customColumnDefinition.isEmpty()) {
            result.append(" ").append(customColumnDefinition);
            return result.toString();
        }

        // Column type
        result.append(" ").append(classToDbType(type));

        // Nullable
        if (!nullable) {
            result.append(" NOT NULL");
        }

        // Unique
        if (unique) {
            result.append(" UNIQUE");
        }

        // Default value
        if (defaultValue != null && !defaultValue.isEmpty()) {
            result.append(" DEFAULT '").append(escape(defaultValue)).append("'");
        }

        return result.toString();
    }


    /**
     * Get the database column type corresponding to a given Java class
     *
     * @param clazz     Java class
     * @return column type
     */
    public static String classToDbType(Class<?> clazz) {
        if (clazz.equals(int.class) || clazz.equals(Integer.class)) {
            return "INTEGER";
        } else if (clazz.equals(long.class) || clazz.equals(Long.class)) {
            return "INTEGER";
        } else if (clazz.equals(float.class) || clazz.equals(Float.class)) {
            return "REAL";
        } else if (clazz.equals(double.class) || clazz.equals(Double.class)) {
            return "REAL";
        } else if (clazz.equals(String.class)) {
            return "TEXT";
        } else if (clazz.equals(Date.class) || clazz.equals(Calendar.class)) {
            return "INTEGER";
        } else if (clazz.isEnum()) {
            return "TEXT";
        } else {
            return "BLOB";
        }
    }


    /**
     * Fake iterator to be used to iterate on a single column.
     */
    private static class SingleColumnIterator implements Iterator<BaseColumnObject> {

        private BaseColumnObject column;

        /**
         * Constructor.
         *
         * @param column    column to be returned during iteration
         */
        public SingleColumnIterator(BaseColumnObject column) {
            this.column = column;
        }

        @Override
        public boolean hasNext() {
            return column != null;
        }

        @Override
        public BaseColumnObject next() {
            BaseColumnObject result = column;
            column = null;
            return result;
        }

    }

}
