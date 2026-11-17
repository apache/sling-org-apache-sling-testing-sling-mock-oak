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
package org.apache.sling.testing.mock.sling.oak.lucene;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests Lucene index definitions in an oak:index node at a non-root path, which only index content below that path.
 * The "fulltext-async" lane is used only by the nested index definition, so it is not referenced in /oak:index.
 */
@RunWith(Parameterized.class)
public class NestedLuceneIndexTest {

    private static final String QUERY_SITE =
            "SELECT * FROM [nt:unstructured] AS n WHERE ISDESCENDANTNODE(n, '/content/site') AND CONTAINS(n.*, 'quick')";
    private static final String QUERY_OTHER =
            "SELECT * FROM [nt:unstructured] AS n WHERE ISDESCENDANTNODE(n, '/content/other') AND CONTAINS(n.*, 'quick')";

    @Parameters(name = "async={0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            {"none", null},
            {"async", new String[] {"async"}},
            {"async,nrt", new String[] {"async", "nrt"}},
            {"fulltext-async", new String[] {"fulltext-async"}}
        });
    }

    @Parameter(0)
    public String asyncLabel;

    @Parameter(1)
    public String[] async;

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private Session session;

    @Before
    public void setUp() throws RepositoryException {
        session = context.resourceResolver().adaptTo(Session.class);
        Node content = session.getRootNode().addNode("content", JcrConstants.NT_UNSTRUCTURED);
        Node site = content.addNode("site", JcrConstants.NT_UNSTRUCTURED);
        Node other = content.addNode("other", JcrConstants.NT_UNSTRUCTURED);
        session.save();

        addIndex(site, "nested");
        session.save();

        addNode(site, "page1", "The quick brown fox");
        addNode(site, "page2", "jumps over the lazy dog");
        addNode(other, "page1", "A quick reply");
        session.save();
    }

    private void addIndex(Node parent, String name) throws RepositoryException {
        Node oakIndex = parent.hasNode("oak:index")
                ? parent.getNode("oak:index")
                : parent.addNode("oak:index", JcrConstants.NT_UNSTRUCTURED);
        Node index = oakIndex.addNode(name, "oak:QueryIndexDefinition");
        index.setProperty("type", "lucene");
        index.setProperty("compatVersion", 2L);
        if (async != null) {
            index.setProperty("async", async);
        }
        Node text = index.addNode("indexRules", JcrConstants.NT_UNSTRUCTURED)
                .addNode(JcrConstants.NT_UNSTRUCTURED, JcrConstants.NT_UNSTRUCTURED)
                .addNode("properties", JcrConstants.NT_UNSTRUCTURED)
                .addNode("text", JcrConstants.NT_UNSTRUCTURED);
        text.setProperty("name", "text");
        text.setProperty("analyzed", true);
        text.setProperty("nodeScopeIndex", true);
    }

    private static void addNode(Node parent, String name, String text) throws RepositoryException {
        parent.addNode(name, JcrConstants.NT_UNSTRUCTURED).setProperty("text", text);
    }

    @Test
    public void testFulltextQuery() throws RepositoryException {
        assertTrue(getPlan(QUERY_SITE), getPlan(QUERY_SITE).contains("lucene:nested"));
        assertEquals(Set.of("/content/site/page1"), queryPaths(QUERY_SITE));
    }

    @Test
    public void testContentOutsideIndexPathIsNotIndexed() throws RepositoryException {
        assertFalse(getPlan(QUERY_OTHER), getPlan(QUERY_OTHER).contains("lucene:"));
        assertEquals(Set.of(), queryPaths(QUERY_OTHER));
    }

    @Test
    public void testContentChangesAreIndexedSynchronously() throws RepositoryException {
        addNode(session.getNode("/content/site"), "page3", "quick update");
        session.getNode("/content/site/page1").remove();
        session.save();

        assertEquals(Set.of("/content/site/page3"), queryPaths(QUERY_SITE));
    }

    @Test
    public void testIndexAddedForExistingContent() throws RepositoryException {
        addIndex(session.getNode("/content/other"), "nestedLate");
        session.save();

        assertTrue(getPlan(QUERY_OTHER), getPlan(QUERY_OTHER).contains("lucene:nestedLate"));
        assertEquals(Set.of("/content/other/page1"), queryPaths(QUERY_OTHER));
    }

    private Set<String> queryPaths(String statement) throws RepositoryException {
        QueryManager queryManager = session.getWorkspace().getQueryManager();
        NodeIterator nodes =
                queryManager.createQuery(statement, Query.JCR_SQL2).execute().getNodes();
        Set<String> paths = new HashSet<>();
        while (nodes.hasNext()) {
            paths.add(nodes.nextNode().getPath());
        }
        return paths;
    }

    private String getPlan(String statement) throws RepositoryException {
        return session.getWorkspace()
                .getQueryManager()
                .createQuery("EXPLAIN " + statement, Query.JCR_SQL2)
                .execute()
                .getRows()
                .nextRow()
                .getValue("plan")
                .getString();
    }
}
