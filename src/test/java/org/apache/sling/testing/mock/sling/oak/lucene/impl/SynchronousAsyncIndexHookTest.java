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

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.plugins.memory.MemoryNodeStore;
import org.apache.jackrabbit.oak.spi.commit.CommitInfo;
import org.apache.jackrabbit.oak.spi.commit.EmptyHook;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.junit.Test;

import static org.apache.jackrabbit.oak.plugins.memory.EmptyNodeState.EMPTY_NODE;
import static org.junit.Assert.assertEquals;

public class SynchronousAsyncIndexHookTest {

    @Test
    public void testAsyncLanesOfChangedPaths() throws CommitFailedException {
        MemoryNodeStore store = new MemoryNodeStore();
        NodeBuilder builder = store.getRoot().builder();
        addIndex(builder, "root", "async", "nrt");
        addIndex(builder.child("content").child("site"), "nested", "fulltext-async", "sync");
        addIndex(builder.child("content").child("unchanged"), "unchanged", "unchanged-lane");
        // index definitions in hidden nodes are not collected
        addIndex(builder.child(":hidden"), "hidden", "hidden-lane");

        // initial content: all index definitions outside hidden nodes
        assertEquals(
                Set.of("async", "fulltext-async", "unchanged-lane"),
                SynchronousAsyncIndexHook.getAsyncLanes(EMPTY_NODE, builder.getNodeState()));

        // merged into the node store like in a repository, so the diff is reported only for changed nodes
        NodeState before = store.merge(builder, EmptyHook.INSTANCE, CommitInfo.EMPTY);

        // content changed below /content/site: index definitions of the root and /content/site
        NodeBuilder changed = before.builder();
        changed.child("content").child("site").child("page").setProperty("text", "value");
        assertEquals(
                Set.of("async", "fulltext-async"),
                SynchronousAsyncIndexHook.getAsyncLanes(before, changed.getNodeState()));
    }

    private static void addIndex(NodeBuilder parent, String name, String... async) {
        parent.child("oak:index").child(name).setProperty("async", Set.of(async), Type.STRINGS);
    }
}
