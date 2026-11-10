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
import java.util.TreeSet;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.plugins.index.IndexConstants;
import org.apache.jackrabbit.oak.plugins.index.IndexEditorProvider;
import org.apache.jackrabbit.oak.plugins.index.IndexUpdateProvider;
import org.apache.jackrabbit.oak.spi.commit.CommitHook;
import org.apache.jackrabbit.oak.spi.commit.CommitInfo;
import org.apache.jackrabbit.oak.spi.commit.EditorHook;
import org.apache.jackrabbit.oak.spi.state.ChildNodeEntry;
import org.apache.jackrabbit.oak.spi.state.DefaultNodeStateDiff;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.jetbrains.annotations.NotNull;

import static org.apache.jackrabbit.oak.plugins.memory.EmptyNodeState.EMPTY_NODE;

/**
 * Updates asynchronous indexes synchronously as part of each commit.
 * <p>
 * No async indexing is running in the mock repository. Instead, for each async indexing lane
 * (e.g. <code>async</code> or <code>fulltext-async</code>) referenced by an index definition that may be affected by
 * the commit, an index update for this lane is executed within the commit. This way index definitions
 * with <code>async</code> property (including hybrid indexes like <code>async=["async", "nrt"]</code>)
 * can be used unchanged, and content is searchable as soon as it is saved.
 * </p>
 * <p>
 * Index definitions are supported in <code>/oak:index</code> and in <code>oak:index</code> nodes at non-root paths
 * (only supported by Lucene indexes), which only index content below that path.
 * </p>
 */
public final class SynchronousAsyncIndexHook implements CommitHook {

    private final IndexEditorProvider indexEditorProvider;

    /**
     * @param indexEditorProvider Index editor provider (should include all index types)
     */
    public SynchronousAsyncIndexHook(@NotNull IndexEditorProvider indexEditorProvider) {
        this.indexEditorProvider = indexEditorProvider;
    }

    @Override
    public @NotNull NodeState processCommit(
            @NotNull NodeState before, @NotNull NodeState after, @NotNull CommitInfo info)
            throws CommitFailedException {
        NodeState result = after;
        for (String asyncLane : getAsyncLanes(before, after)) {
            result = new EditorHook(new IndexUpdateProvider(indexEditorProvider, asyncLane, false))
                    .processCommit(before, result, info);
        }
        return result;
    }

    /**
     * Collects the async lane names of all index definitions that may be affected by the changes: the index
     * definitions in the <code>oak:index</code> nodes of all changed nodes, including the root node. As changes
     * are reported for all ancestors of a changed node, this covers all index definitions containing changed
     * content, and new index definitions.
     */
    static @NotNull Set<String> getAsyncLanes(@NotNull NodeState before, @NotNull NodeState after) {
        Set<String> asyncLanes = new TreeSet<>();
        collectAsyncLanes(before, after, asyncLanes);
        return asyncLanes;
    }

    private static void collectAsyncLanes(
            @NotNull NodeState before, @NotNull NodeState after, @NotNull Set<String> asyncLanes) {
        for (ChildNodeEntry entry :
                after.getChildNode(IndexConstants.INDEX_DEFINITIONS_NAME).getChildNodeEntries()) {
            PropertyState async = entry.getNodeState().getProperty(IndexConstants.ASYNC_PROPERTY_NAME);
            if (async != null) {
                for (String value : async.getValue(Type.STRINGS)) {
                    if (!IndexConstants.INDEXING_MODE_NRT.equals(value)
                            && !IndexConstants.INDEXING_MODE_SYNC.equals(value)) {
                        asyncLanes.add(value);
                    }
                }
            }
        }
        after.compareAgainstBaseState(before, new DefaultNodeStateDiff() {
            @Override
            public boolean childNodeAdded(String name, NodeState after) {
                if (isContentNode(name)) {
                    collectAsyncLanes(EMPTY_NODE, after, asyncLanes);
                }
                return true;
            }

            @Override
            public boolean childNodeChanged(String name, NodeState before, NodeState after) {
                if (isContentNode(name)) {
                    collectAsyncLanes(before, after, asyncLanes);
                }
                return true;
            }
        });
    }

    /**
     * Index definitions and hidden nodes (e.g. index data) do not contain further index definitions.
     */
    static boolean isContentNode(@NotNull String name) {
        return !IndexConstants.INDEX_DEFINITIONS_NAME.equals(name) && !name.startsWith(":");
    }
}
