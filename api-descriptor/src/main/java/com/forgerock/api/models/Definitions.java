/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */

package com.forgerock.api.models;

import static com.forgerock.api.util.ValidationUtil.containsWhitespace;
import static com.forgerock.api.util.ValidationUtil.isEmpty;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import com.forgerock.api.ApiValidationException;
import org.forgerock.util.Reject;

/**
 * Class that represents API descriptor {@link Schema} definitions.
 */
public final class Definitions {

    private final Map<String, Schema> definitions;

    private Definitions(Builder builder) {
        this.definitions = builder.definitions;

        if (definitions.isEmpty()) {
            throw new ApiValidationException("Must have at least one schema definition");
        }
    }

    /**
     * Gets a {@code Map} of schema-names to {@link Schema}s. This method is currently only used for JSON serialization.
     *
     * @return {@code Map} of schema-names to {@link Schema}s.
     */
    @JsonValue
    protected Map<String, Schema> getDefinitions() {
        return definitions;
    }

    /**
     * Gets the {@link Schema} for a given Schema-name.
     *
     * @param name Schema name
     * @return {@link Schema} or {@code null} if does-not-exist.
     */
    @JsonIgnore
    public Schema get(String name) {
        return definitions.get(name);
    }

    /**
     * Returns all {@link Schema} names.
     *
     * @return All {@link Schema} names.
     */
    @JsonIgnore
    public Set<String> getNames() {
        return definitions.keySet();
    }

    /**
     * Create a new Builder for Definitions.
     *
     * @return Builder
     */
    public static Builder definitions() {
        return new Builder();
    }

    /**
     * Builder to help construct the Definitions.
     */
    public static final class Builder {

        private final Map<String, Schema> definitions = new HashMap<>();

        /**
         * Private default constructor.
         */
        private Builder() {
        }

        /**
         * Adds a {@link Schema}.
         *
         * @param name Schema name
         * @param schema {@link Schema}
         * @return Builder
         */
        public Builder put(String name, Schema schema) {
            if (isEmpty(name) || containsWhitespace(name)) {
                throw new IllegalArgumentException("name required and may not contain whitespace");
            }
            if (definitions.containsKey(name)) {
                throw new IllegalStateException("name not unique");
            }

            definitions.put(name, Reject.checkNotNull(schema));
            return this;
        }

        /**
         * Builds the Definitions instance.
         *
         * @return Definitions instance
         */
        public Definitions build() {
            return new Definitions(this);
        }
    }

}