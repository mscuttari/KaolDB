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

package it.mscuttari.kaoldb.interfaces;

import it.mscuttari.kaoldb.exceptions.DumpException;

public interface RowDump {

    /**
     * Get the value of a column at this row
     *
     * @param columnName    column name
     * @param <T>           data type
     *
     * @return column value
     * @throws DumpException if the expected data type is different than the one in the database
     */
    <T> T getColumnValue(String columnName);

}
