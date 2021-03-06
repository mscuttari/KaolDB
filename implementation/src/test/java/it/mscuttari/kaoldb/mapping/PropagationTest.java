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

import org.junit.Test;

import it.mscuttari.kaoldb.AbstractTest;
import it.mscuttari.kaoldb.mapping.Propagation;

import static it.mscuttari.kaoldb.mapping.Propagation.Action.CASCADE;
import static it.mscuttari.kaoldb.mapping.Propagation.Action.NO_ACTION;
import static it.mscuttari.kaoldb.mapping.Propagation.Action.RESTRICT;
import static it.mscuttari.kaoldb.mapping.Propagation.Action.SET_DEFAULT;
import static it.mscuttari.kaoldb.mapping.Propagation.Action.SET_NULL;
import static org.junit.Assert.assertEquals;

public class PropagationTest extends AbstractTest {

    @Test
    public void noAction_noAction() {
        Propagation propagation = new Propagation(NO_ACTION, NO_ACTION);
        assertEquals("ON UPDATE NO ACTION ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED", propagation.toString());
    }

    @Test
    public void noAction_restrict() {
        Propagation propagation = new Propagation(NO_ACTION, RESTRICT);
        assertEquals("ON UPDATE NO ACTION ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED", propagation.toString());
    }

    @Test
    public void noAction_setNull() {
        Propagation propagation = new Propagation(NO_ACTION, SET_NULL);
        assertEquals("ON UPDATE NO ACTION ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED", propagation.toString());
    }

    @Test
    public void noAction_setDefault() {
        Propagation propagation = new Propagation(NO_ACTION, SET_DEFAULT);
        assertEquals("ON UPDATE NO ACTION ON DELETE SET DEFAULT DEFERRABLE INITIALLY DEFERRED", propagation.toString());
    }

    @Test
    public void noAction_cascade() {
        Propagation propagation = new Propagation(NO_ACTION, CASCADE);
        assertEquals("ON UPDATE NO ACTION ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED", propagation.toString());
    }

    @Test
    public void restrict_noAction() {
        Propagation propagation = new Propagation(RESTRICT, NO_ACTION);
        assertEquals("ON UPDATE RESTRICT ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED", propagation.toString());
    }

    @Test
    public void restrict_restrict() {
        Propagation propagation = new Propagation(RESTRICT, RESTRICT);
        assertEquals("ON UPDATE RESTRICT ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED", propagation.toString());
    }

    @Test
    public void restrict_setNull() {
        Propagation propagation = new Propagation(RESTRICT, SET_NULL);
        assertEquals("ON UPDATE RESTRICT ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED", propagation.toString());
    }

    @Test
    public void restrict_setDefault() {
        Propagation propagation = new Propagation(RESTRICT, SET_DEFAULT);
        assertEquals("ON UPDATE RESTRICT ON DELETE SET DEFAULT DEFERRABLE INITIALLY DEFERRED", propagation.toString());
    }

    @Test
    public void restrict_cascade() {
        Propagation propagation = new Propagation(RESTRICT, CASCADE);
        assertEquals("ON UPDATE RESTRICT ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED", propagation.toString());
    }

    @Test
    public void setNull_noAction() {
        Propagation propagation = new Propagation(SET_NULL, NO_ACTION);
        assertEquals("ON UPDATE SET NULL ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED", propagation.toString());
    }

    @Test
    public void setNull_restrict() {
        Propagation propagation = new Propagation(SET_NULL, RESTRICT);
        assertEquals("ON UPDATE SET NULL ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED", propagation.toString());
    }

    @Test
    public void setNull_setNull() {
        Propagation propagation = new Propagation(SET_NULL, SET_NULL);
        assertEquals("ON UPDATE SET NULL ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED", propagation.toString());
    }

    @Test
    public void setNull_setDefault() {
        Propagation propagation = new Propagation(SET_NULL, SET_DEFAULT);
        assertEquals("ON UPDATE SET NULL ON DELETE SET DEFAULT DEFERRABLE INITIALLY DEFERRED", propagation.toString());
    }

    @Test
    public void setNull_cascade() {
        Propagation propagation = new Propagation(SET_NULL, CASCADE);
        assertEquals("ON UPDATE SET NULL ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED", propagation.toString());
    }

    @Test
    public void setDefault_noAction() {
        Propagation propagation = new Propagation(SET_DEFAULT, NO_ACTION);
        assertEquals("ON UPDATE SET DEFAULT ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED", propagation.toString());
    }

    @Test
    public void setDefault_restrict() {
        Propagation propagation = new Propagation(SET_DEFAULT, RESTRICT);
        assertEquals("ON UPDATE SET DEFAULT ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED", propagation.toString());
    }

    @Test
    public void setDefault_setNull() {
        Propagation propagation = new Propagation(SET_DEFAULT, SET_NULL);
        assertEquals("ON UPDATE SET DEFAULT ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED", propagation.toString());
    }

    @Test
    public void setDefault_setDefault() {
        Propagation propagation = new Propagation(SET_DEFAULT, SET_DEFAULT);
        assertEquals("ON UPDATE SET DEFAULT ON DELETE SET DEFAULT DEFERRABLE INITIALLY DEFERRED", propagation.toString());
    }

    @Test
    public void setDefault_cascade() {
        Propagation propagation = new Propagation(SET_DEFAULT, CASCADE);
        assertEquals("ON UPDATE SET DEFAULT ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED", propagation.toString());
    }

    @Test
    public void cascade_noAction() {
        Propagation propagation = new Propagation(CASCADE, NO_ACTION);
        assertEquals("ON UPDATE CASCADE ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED", propagation.toString());
    }

    @Test
    public void cascade_restrict() {
        Propagation propagation = new Propagation(CASCADE, RESTRICT);
        assertEquals("ON UPDATE CASCADE ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED", propagation.toString());
    }

    @Test
    public void cascade_setNull() {
        Propagation propagation = new Propagation(CASCADE, SET_NULL);
        assertEquals("ON UPDATE CASCADE ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED", propagation.toString());
    }

    @Test
    public void cascade_setDefault() {
        Propagation propagation = new Propagation(CASCADE, SET_DEFAULT);
        assertEquals("ON UPDATE CASCADE ON DELETE SET DEFAULT DEFERRABLE INITIALLY DEFERRED", propagation.toString());
    }

    @Test
    public void cascade_cascade() {
        Propagation propagation = new Propagation(CASCADE, CASCADE);
        assertEquals("ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED", propagation.toString());
    }

}
