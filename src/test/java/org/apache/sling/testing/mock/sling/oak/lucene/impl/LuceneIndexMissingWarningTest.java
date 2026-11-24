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

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.spi.commit.CommitInfo;
import org.apache.jackrabbit.oak.spi.commit.EditorHook;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.testing.mock.sling.oak.lucene.StdErrCapture;
import org.junit.Rule;
import org.junit.Test;

import static org.apache.jackrabbit.oak.plugins.memory.EmptyNodeState.EMPTY_NODE;
import static org.junit.Assert.assertEquals;

public class LuceneIndexMissingWarningTest {

    private static final String REASON = "Reason with hint.";
    // index names are unique per test, as warnings are logged only once per index path and JVM
    private static final String PREFIX = LuceneIndexMissingWarningTest.class.getSimpleName();

    @Rule
    public StdErrCapture stdErr = new StdErrCapture();

    private final EditorHook hook = new EditorHook(new LuceneIndexMissingWarning(REASON));

    @Test
    public void testLuceneIndexAdded() throws CommitFailedException {
        NodeBuilder after = EMPTY_NODE.builder();
        after.child("content");
        addIndex(after, PREFIX + "Added", "lucene");

        commit(EMPTY_NODE, after.getNodeState());

        assertWarnings(PREFIX + "Added", 1);
    }

    @Test
    public void testLuceneIndexChanged() throws CommitFailedException {
        NodeBuilder before = EMPTY_NODE.builder();
        before.child("content");
        addIndex(before, PREFIX + "Changed", "property");
        NodeState beforeState = before.getNodeState();

        // non-Lucene index definition changed, other root node changed
        NodeBuilder after1 = beforeState.builder();
        after1.child("content").setProperty("prop", "value1");
        after1.child("oak:index").child(PREFIX + "Changed").setProperty("prop", "value1");
        NodeState after1State = after1.getNodeState();
        commit(beforeState, after1State);
        assertWarnings(PREFIX + "Changed", 0);

        // changed to Lucene index definition
        NodeBuilder after2 = after1State.builder();
        after2.child("oak:index").child(PREFIX + "Changed").setProperty("type", "lucene");
        NodeState after2State = after2.getNodeState();
        commit(after1State, after2State);
        assertWarnings(PREFIX + "Changed", 1);

        // changed again - no additional warning
        NodeBuilder after3 = after2State.builder();
        after3.child("oak:index").child(PREFIX + "Changed").setProperty("prop", "value2");
        commit(after2State, after3.getNodeState());
        assertWarnings(PREFIX + "Changed", 1);
    }

    @Test
    public void testNonLuceneIndexAdded() throws CommitFailedException {
        NodeBuilder after = EMPTY_NODE.builder();
        addIndex(after, PREFIX + "Property", "property");
        addIndex(after, PREFIX + "NoType", null);

        commit(EMPTY_NODE, after.getNodeState());

        assertEquals(stdErr.getOutput(), 0, stdErr.count("Lucene index definition"));
    }

    @Test
    public void testNestedLuceneIndexAdded() throws CommitFailedException {
        NodeBuilder after = EMPTY_NODE.builder();
        NodeBuilder site = after.child("content").child("site");
        site.child("oak:index").child(PREFIX + "Nested").setProperty("type", "lucene");
        // hidden nodes are not inspected
        site.child(":hidden").child("oak:index").child(PREFIX + "Hidden").setProperty("type", "lucene");

        commit(EMPTY_NODE, after.getNodeState());

        assertWarnings("/content/site/oak:index/" + PREFIX + "Nested", 1);
        assertEquals(stdErr.getOutput(), 0, stdErr.count(PREFIX + "Hidden"));
    }

    private void commit(NodeState before, NodeState after) throws CommitFailedException {
        hook.processCommit(before, after, CommitInfo.EMPTY);
    }

    private static void addIndex(NodeBuilder root, String name, String type) {
        NodeBuilder index = root.child("oak:index").child(name);
        if (type != null) {
            index.setProperty("type", type);
        }
    }

    private void assertWarnings(String indexName, int expected) {
        assertEquals(
                stdErr.getOutput(),
                expected,
                stdErr.count("Lucene index definition " + (indexName.startsWith("/") ? "" : "/oak:index/") + indexName
                        + " found, but Lucene index support is not enabled: queries will not use this index, "
                        + "and full-text queries return no results. " + REASON));
    }
}
