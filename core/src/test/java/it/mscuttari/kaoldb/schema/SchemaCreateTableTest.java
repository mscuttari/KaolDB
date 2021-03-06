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

package it.mscuttari.kaoldb.schema;

import android.database.sqlite.SQLiteDatabase;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;

import static it.mscuttari.kaoldb.dump.SQLiteUtils.getTables;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

public class SchemaCreateTableTest extends SchemaAbstractTest {

    @Override
    protected void createDb(SQLiteDatabase db) {
        // No need to create any table
    }

    @Test
    public void createTable() {
        assertThat(getTables(db), not(hasItem("table_new_1")));

        Collection<Column> columns = new ArrayList<>();
        columns.add(new Column("id", Integer.class, null, true, false, false));
        new SchemaCreateTable("table_1", columns, null).run(db);

        assertTrue(getTables(db).contains("table_1"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void createTable_emptyTable() {
        Collection<Column> columns = new ArrayList<>();
        columns.add(new Column("id", Integer.class, null, true, false, false));
        new SchemaCreateTable("", columns, null).run(db);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createTable_noPrimaryKey() {
        Collection<Column> columns = new ArrayList<>();
        columns.add(new Column("id", Integer.class, null, false, false, false));
        new SchemaCreateTable("table_1", columns, null).run(db);
    }

}
