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

package it.mscuttari.kaoldb.examples.films.models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;

import it.mscuttari.kaoldb.annotations.Column;
import it.mscuttari.kaoldb.annotations.Entity;
import it.mscuttari.kaoldb.annotations.Id;
import it.mscuttari.kaoldb.annotations.JoinColumn;
import it.mscuttari.kaoldb.annotations.ManyToMany;
import it.mscuttari.kaoldb.annotations.ManyToOne;
import it.mscuttari.kaoldb.annotations.OneToMany;
import it.mscuttari.kaoldb.annotations.Table;

@Entity
@Table(name = "people")
public class Person {

    @Id
    @Column(name = "first_name")
    public String firstName;

    @Id
    @Column(name = "last_name")
    public String lastName;

    @Column(name = "birth_date")
    public Calendar birthDate;

    @ManyToOne
    @JoinColumn(name = "country", referencedColumnName = "name")
    public Country country;

    @OneToMany(mappedBy = "director")
    public Collection<Film> directing = new ArrayList<>();

    @ManyToMany(mappedBy = "actors")
    public Collection<Film> acting = new ArrayList<>();


    /**
     * Constructor
     *
     * @param   firstName       first name
     * @param   lastName        last name
     * @param   birthDate       birth date
     * @param   country         country
     */
    public Person(String firstName, String lastName, Calendar birthDate, Country country) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.birthDate = birthDate;
        this.country = country;
    }


    @Override
    public int hashCode() {
        Object[] x = {firstName, lastName};
        return Arrays.hashCode(x);
    }


    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (obj == this) return true;

        if (!(obj instanceof Person)) return false;
        Person o = (Person) obj;

        if (firstName != null && !firstName.equals(o.firstName)) return false;
        if (firstName == null && o.firstName != null) return false;

        if (lastName != null && !lastName.equals(o.lastName)) return false;
        if (lastName == null && o.lastName != null) return false;

        if (birthDate != null && birthDate.compareTo(o.birthDate) != 0) return false;
        if (birthDate == null && o.birthDate != null) return false;

        if (country != null && !country.equals(o.country)) return false;
        if (country == null && o.country != null) return false;

        return true;
    }


    @Override
    public String toString() {
        return "[first name: " + firstName + ", last name: " + lastName + ", birth date: " + birthDate.getTimeInMillis() + "]";
    }

}
