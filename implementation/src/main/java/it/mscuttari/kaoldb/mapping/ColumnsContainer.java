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

import it.mscuttari.kaoldb.exceptions.PojoException;

/**
 * The classes implementing this interface can be considered as containers of columns.<br>
 * Also simple columns can be considered as containers of themselves.<br>
 * The columns containers do not directly appear during the iteration and therefore all the
 * columns seem to be part of a single collection.
 *
 * @see BaseColumnObject
 * @see DiscriminatorColumnObject
 * @see SimpleColumnObject
 * @see JoinColumnObject
 * @see JoinColumnsObject
 * @see JoinTableObject
 */
public interface ColumnsContainer extends Iterable<BaseColumnObject> {

    /**
     * Load the columns properties.
     */
    void map();

    /**
     * Block the calling thread until all the columns properties have been loaded.
     *
     * @see #map()
     */
    void waitUntilMapped();

    /**
     * Add the columns to a {@link ContentValues} data set.
     *
     * @param cv    data set to be populated
     * @param obj   data to be added / object containing the data to be extracted
     *
     * @throws PojoException if <code>obj</code> doesn't contain the columns
     * @throws PojoException if the column value can't be retrieved
     */
    void addToContentValues(@NonNull ContentValues cv, Object obj);

    /**
     * Accept a visitor.
     *
     * @param visitor visitor
     * @param <T>     result data type
     * @return data
     */
    <T> T accept(Visitor<T> visitor);

    interface Visitor<T> {

        T visit(Columns container);
        T visit(DiscriminatorColumnObject column);
        T visit(JoinColumnObject column);
        T visit(JoinColumnsObject container);
        T visit(SimpleColumnObject column);

    }

}
