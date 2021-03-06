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

package it.mscuttari.kaoldb.dump;

import android.database.Cursor;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.collection.ArrayMap;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import it.mscuttari.kaoldb.exceptions.DumpException;
import it.mscuttari.kaoldb.interfaces.RowDump;

public class RowDumpImpl implements RowDump {

    /** Map between column names and column values */
    private final ArrayMap<String, Object> values;

    /**
     * Dump a row of a table.
     *
     * @param c     cursor positioned to the row to be dumped
     */
    public RowDumpImpl(Cursor c) {
        List<String> columns = Arrays.asList(c.getColumnNames());
        values = new ArrayMap<>(columns.size());

        for (String column : columns) {
            int columnIndex = c.getColumnIndex(column);
            int dataType = c.getType(columnIndex);

            if (dataType == Cursor.FIELD_TYPE_NULL) {
                values.put(column, null);

            } else if (dataType == Cursor.FIELD_TYPE_INTEGER) {
                values.put(column, c.getLong(columnIndex));

            } else if (dataType == Cursor.FIELD_TYPE_FLOAT) {
                values.put(column, c.getDouble(columnIndex));

            } else if (dataType == Cursor.FIELD_TYPE_STRING) {
                values.put(column, c.getString(columnIndex));

            } else if (dataType == Cursor.FIELD_TYPE_BLOB) {
                values.put(column, c.getBlob(columnIndex));

            } else {
                throw new DumpException("Unexpected data type: " + dataType);
            }
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "{" +
                values.keySet()
                        .stream()
                        .map(column -> column + ": " + values.get(column))
                        .collect(Collectors.joining(", ")) +
                "}";
    }

    @NonNull
    @Override
    public Collection<String> getColumns() {
        return Collections.unmodifiableCollection(values.keySet());
    }

    @SuppressWarnings("unchecked")
    @CheckResult
    @Override
    public <T> T getColumnValue(String columnName) {
        if (!values.containsKey(columnName))
            throw new IllegalArgumentException("Column \"" + columnName + "\" not found");

        try {
            return (T) values.get(columnName);
        } catch (ClassCastException e) {
            throw new DumpException(e);
        }
    }

}
