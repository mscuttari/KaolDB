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

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;

import java.util.Collection;

import it.mscuttari.kaoldb.exceptions.DumpException;

public interface DatabaseDump {

    /**
     * Get database version.
     *
     * @return database version at the moment of the dump
     */
    int getVersion();

    /**
     * Get all table dumps.
     *
     * @return table dumps
     */
    @CheckResult
    @NonNull
    Collection<TableDump> getTables();

    /**
     * Get specific table dump.
     *
     * @param tableName     table name
     * @return table dump
     * @throws IllegalArgumentException if the table doesn't exist
     */
    @CheckResult
    @NonNull
    TableDump getTable(String tableName);

}
