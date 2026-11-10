/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.testing.mock.sling.oak.lucene.impl;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.jackrabbit.oak.commons.PathUtils;
import org.apache.jackrabbit.oak.plugins.index.IndexConstants;
import org.apache.jackrabbit.oak.spi.commit.CommitInfo;
import org.apache.jackrabbit.oak.spi.commit.DefaultEditor;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.commit.EditorProvider;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs a warning if a Lucene index definition is added to or changed in an oak:index node (at any path) while
 * Lucene index support is not enabled - queries would not use this index, and full-text queries would return no results.
 * The warning is logged only once per index path.
 * <p>
 * Uses only Oak core classes, so it can be used without oak-lucene in the class path.
 * </p>
 */
public final class LuceneIndexMissingWarning implements EditorProvider {

    private static final String TYPE_LUCENE = "lucene";

    // log only once per index path, many tests typically load the same index definitions
    private static final Set<String> WARNED_INDEX_PATHS = ConcurrentHashMap.newKeySet();

    private static final Logger log = LoggerFactory.getLogger(LuceneIndexMissingWarning.class);

    private final String unavailableReason;

    /**
     * @param unavailableReason Reason why Lucene index support is not available, including a hint how to enable it
     */
    public LuceneIndexMissingWarning(@NotNull String unavailableReason) {
        this.unavailableReason = unavailableReason;
    }

    @Override
    public @Nullable Editor getRootEditor(
            @NotNull NodeState before,
            @NotNull NodeState after,
            @NotNull NodeBuilder builder,
            @NotNull CommitInfo info) {
        return new ContentEditor(PathUtils.ROOT_PATH);
    }

    /**
     * Visits all changed content nodes to find index definitions in <code>oak:index</code> nodes at any path.
     */
    private final class ContentEditor extends DefaultEditor {

        private final String path;

        ContentEditor(@NotNull String path) {
            this.path = path;
        }

        @Override
        public @Nullable Editor childNodeAdded(@NotNull String name, @NotNull NodeState after) {
            return getChildEditor(name);
        }

        @Override
        public @Nullable Editor childNodeChanged(
                @NotNull String name, @NotNull NodeState before, @NotNull NodeState after) {
            return getChildEditor(name);
        }

        private @Nullable Editor getChildEditor(@NotNull String name) {
            if (IndexConstants.INDEX_DEFINITIONS_NAME.equals(name)) {
                return new IndexDefinitionsEditor(PathUtils.concat(path, name));
            }
            if (name.startsWith(":")) {
                // hidden nodes (e.g. index data) do not contain index definitions
                return null;
            }
            return new ContentEditor(PathUtils.concat(path, name));
        }
    }

    /**
     * Checks the index definitions in an <code>oak:index</code> node.
     */
    private final class IndexDefinitionsEditor extends DefaultEditor {

        private final String path;

        IndexDefinitionsEditor(@NotNull String path) {
            this.path = path;
        }

        @Override
        public @Nullable Editor childNodeAdded(@NotNull String name, @NotNull NodeState after) {
            checkIndexDefinition(PathUtils.concat(path, name), after);
            return null;
        }

        @Override
        public @Nullable Editor childNodeChanged(
                @NotNull String name, @NotNull NodeState before, @NotNull NodeState after) {
            checkIndexDefinition(PathUtils.concat(path, name), after);
            return null;
        }
    }

    private void checkIndexDefinition(@NotNull String indexPath, @NotNull NodeState definition) {
        if (TYPE_LUCENE.equals(definition.getString(IndexConstants.TYPE_PROPERTY_NAME))
                && WARNED_INDEX_PATHS.add(indexPath)) {
            log.warn(
                    "Lucene index definition {} found, but Lucene index support is not enabled: queries will not "
                            + "use this index, and full-text queries return no results. {}",
                    indexPath,
                    unavailableReason);
        }
    }
}
