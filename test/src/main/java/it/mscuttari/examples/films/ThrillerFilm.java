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

package it.mscuttari.examples.films;

import it.mscuttari.kaoldb.annotations.DiscriminatorValue;
import it.mscuttari.kaoldb.annotations.Entity;
import it.mscuttari.kaoldb.annotations.Table;

@Entity
@Table(name = "thriller_films")
@DiscriminatorValue(value = "Thriller")
public final class ThrillerFilm extends Film {

    /**
     * Default constructor
     */
    public ThrillerFilm() {
        this(null, null, null, null, FilmRestriction.MIN14);
    }


    /**
     * Constructor
     *
     * @param   title           title
     * @param   year            year
     * @param   director        director
     * @param   length          length
     * @param   restriction     restriction
     */
    public ThrillerFilm(String title, Integer year, Person director, Integer length, FilmRestriction restriction) {
        super(title, year, new Genre("Thriller"), director, length, restriction);
    }

}
