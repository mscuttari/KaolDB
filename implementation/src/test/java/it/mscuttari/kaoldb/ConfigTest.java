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

package it.mscuttari.kaoldb;

import org.junit.Before;
import org.junit.Test;

import it.mscuttari.kaoldb.AbstractTest;
import it.mscuttari.kaoldb.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfigTest extends AbstractTest {

    private Config config;

    @Before
    public void setUp() {
        config = new Config();
    }

    @Test
    public void debugEnabled() {
        config.setDebugMode(true);
        assertTrue(config.isDebugEnabled());
    }

    @Test
    public void debugNotEnabled() {
        config.setDebugMode(false);
        assertFalse(config.isDebugEnabled());
    }

}
