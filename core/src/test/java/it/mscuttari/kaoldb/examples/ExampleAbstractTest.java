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

package it.mscuttari.kaoldb.examples;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;

import java.util.Calendar;

import it.mscuttari.kaoldb.AbstractTest;
import it.mscuttari.kaoldb.KaolDB;
import it.mscuttari.kaoldb.R;
import it.mscuttari.kaoldb.interfaces.EntityManager;

import static org.junit.Assert.assertTrue;

public abstract class ExampleAbstractTest extends AbstractTest {

    private final String databaseName;
    protected EntityManager em;

    /**
     * Constructor.
     *
     * @param databaseName  database name
     */
    public ExampleAbstractTest(String databaseName) {
        this.databaseName = databaseName;
    }

    /**
     * Create the database, load the configuration and get an entity manager instance.
     */
    @Before
    public void setUp() {
        // KaolDB instance
        KaolDB kdb = KaolDB.getInstance();
        kdb.setDebugMode(true);
        kdb.setConfig(ApplicationProvider.getApplicationContext(), R.xml.persistence);

        // Entity manager
        em = kdb.getEntityManager(ApplicationProvider.getApplicationContext(), databaseName);
    }

    /**
     * Delete the database.
     */
    @After
    public void tearDown() {
        assertTrue(em.deleteDatabase());
    }

    /**
     * Get a calendar with an already set date.
     *
     * @param year      year
     * @param month     month number
     * @param day       day of month
     *
     * @return calendar
     */
    protected final Calendar getCalendar(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();

        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month);
        calendar.set(Calendar.DAY_OF_MONTH, day);

        return calendar;
    }

}
