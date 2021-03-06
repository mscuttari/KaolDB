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
import java.util.Calendar;
import java.util.Date;

import it.mscuttari.kaoldb.annotations.Column;
import it.mscuttari.kaoldb.annotations.Id;
import it.mscuttari.kaoldb.exceptions.MappingException;

import static it.mscuttari.kaoldb.ConcurrentSession.doAndNotifyAll;

/**
 * This class allows to map a column acting as a basic entity attribute.
 *
 * @see Column
 */
final class SimpleColumnObject extends FieldColumnObject {

    /**
     * Constructor.
     *
     * @param db        database
     * @param entity    entity the column belongs to
     * @param field     field the column is generated from
     */
    public SimpleColumnObject(@NonNull DatabaseObject db,
                              @NonNull EntityObject<?> entity,
                              @NonNull Field field) {

        super(db, entity, field);

        Column annotation = field.getAnnotation(Column.class);
        this.name = annotation.name().isEmpty() ? getDefaultName(field) : annotation.name();
    }

    @Override
    public void mapAsync() {

    }

    @Override
    public <T> T accept(Visitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    protected void loadCustomColumnDefinition() {
        Column annotation = field.getAnnotation(Column.class);
        String result = annotation.columnDefinition().isEmpty() ? null : annotation.columnDefinition();
        doAndNotifyAll(this, () -> customColumnDefinition = result);
    }

    @Override
    protected void loadType() {
        Class<?> result = field.getType();
        doAndNotifyAll(this, () -> type = result);
    }

    @Override
    protected void loadNullableProperty() {
        Column annotation = field.getAnnotation(Column.class);
        boolean result = annotation.nullable();
        doAndNotifyAll(this, () -> nullable = result);
    }

    @Override
    protected void loadPrimaryKeyProperty() {
        boolean result = field.isAnnotationPresent(Id.class);
        doAndNotifyAll(this, () -> primaryKey = result);
    }

    @Override
    protected void loadUniqueProperty() {
        Column annotation = field.getAnnotation(Column.class);
        boolean result = annotation.unique();
        doAndNotifyAll(this, () -> unique = result);
    }

    @Override
    protected void loadDefaultValue() {
        Column annotation = field.getAnnotation(Column.class);
        String def = annotation.defaultValue().isEmpty() ? null : annotation.defaultValue();

        // Check data compatibility
        if (def != null) {
            try {
                if (type.isAssignableFrom(Integer.class) || type.isAssignableFrom(int.class)) {
                    def = String.valueOf(Integer.parseInt(def));

                } else if (type.isAssignableFrom(Long.class) || type.isAssignableFrom(long.class)) {
                    def = String.valueOf(Long.parseLong(def));

                } else if (type.isAssignableFrom(Float.class) || type.isAssignableFrom(float.class)) {
                    def = String.valueOf(Float.parseFloat(def));

                } else if (type.isAssignableFrom(Double.class) || type.isAssignableFrom(double.class)) {
                    def = String.valueOf(Double.parseDouble(def));

                } else if (type.isAssignableFrom(Boolean.class) || type.isAssignableFrom(boolean.class)) {
                    boolean value = Boolean.parseBoolean(def);
                    def = value ? "1" : "0";

                } else if (type.isAssignableFrom(Date.class)) {
                    // Need to be a timestamp
                    def = String.valueOf(Long.parseLong(def));

                } else if (type.isAssignableFrom(Calendar.class)) {
                    // Need to be a timestamp
                    def = String.valueOf(Long.parseLong(def));
                }

            } catch (Exception e) {
                throw new MappingException("[Column \"" + name + "\"] default value is incompatible with type " + type.getSimpleName());
            }
        }

        String result = def;
        doAndNotifyAll(this, () -> defaultValue = result);
    }

    @Override
    public boolean hasRelationship() {
        return false;
    }

    @Override
    public void addToContentValues(@NonNull ContentValues cv, Object obj) {
        Object value = getValue(obj);
        insertIntoContentValues(cv, name, value);
    }

    @Override
    public <T> T accept(ColumnsContainer.Visitor<T> visitor) {
        return visitor.visit(this);
    }

}
