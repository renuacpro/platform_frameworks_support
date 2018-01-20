/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package android.support.tools.jetifier.core.map

import android.support.tools.jetifier.core.rules.JavaField
import android.support.tools.jetifier.core.rules.JavaType
import android.support.tools.jetifier.core.rules.RewriteRule
import android.support.tools.jetifier.core.utils.Log

/**
 * Contains all the mappings needed to rewrite java types and fields.
 *
 * These mappings are generated by the preprocessor from existing support libraries and by applying
 * the given [RewriteRule]s.
 */
data class TypesMap(
        val types: Map<JavaType, JavaType>,
        val fields: Map<JavaField, JavaField>) {

    companion object {
        private const val TAG = "TypesMap"

        val EMPTY = TypesMap(emptyMap(), emptyMap())
    }

    /** Returns JSON data model of this class */
    fun toJson(): JsonData {
        return JsonData(
                types = types.map { it.key.fullName to it.value.fullName }
                        .toMap(),
                fields = mapFields())
    }

    private fun mapFields(): Map<String, Map<String, List<String>>> {
        val rawMap = mutableMapOf<String, MutableMap<String, MutableList<String>>>()

        fields.forEach {
            rawMap
                .getOrPut(it.key.owner.fullName, { mutableMapOf() })
                .getOrPut(it.value.owner.fullName, { mutableListOf() })
                .add(it.key.name)
        }
        return rawMap
    }

    /**
     * Creates reversed version of this map (values become keys). Throws exception if the map does
     * not satisfy that.
     */
    fun reverseMapOrDie(): TypesMap {
        val typesReversed = mutableMapOf<JavaType, JavaType>()
        for ((from, to) in types) {
            val conflictFrom = typesReversed[to]
            if (conflictFrom != null) {
                Log.e(TAG, "Conflict: %s -> (%s, %s)", to, from, conflictFrom)
                continue
            }
            typesReversed[to] = from
        }

        val fieldsReversed = mutableMapOf<JavaField, JavaField>()
        for ((from, to) in fields) {
            val conflictFrom = fieldsReversed[to]
            if (conflictFrom != null) {
                Log.e(TAG, "Conflict: %s -> (%s, %s)", to, from, conflictFrom)
                continue
            }
            fieldsReversed[to] = from
        }

        if (types.size != typesReversed.size || fields.size != fieldsReversed.size) {
            throw IllegalArgumentException("Types map is not reversible as conflicts were found! " +
                "See the log for more details.")
        }

        return TypesMap(types= typesReversed, fields= fieldsReversed)
    }

    /**
     * JSON data model for [TypesMap].
     */
    data class JsonData(
            val types: Map<String, String>,
            val fields: Map<String, Map<String, List<String>>>) {

        /** Creates instance of [TypesMap] */
        fun toMappings(): TypesMap {
            return TypesMap(
                types = types
                    .orEmpty()
                    .map { JavaType(it.key) to JavaType(it.value) }
                    .toMap(),
                fields = fields
                    .orEmpty().entries
                    .flatMap {
                        top ->
                        top.value.flatMap {
                            mid ->
                            mid.value.map {
                                JavaField(top.key, it) to JavaField(mid.key, it)
                            }
                        }
                    }
                    .toMap())
        }
    }
}

