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
import javax.jcr.query.Row;

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
import static org.junit.Assert.assertTrue;

/**
 * Tests Lucene index support, enabled automatically as oak-lucene is in the test class path.
 * Async indexes are expected to be updated synchronously within each commit.
 */
@RunWith(Parameterized.class)
public class LuceneIndexTest {

    private static final String QUERY_FULLTEXT =
            "SELECT * FROM [nt:unstructured] AS n WHERE ISDESCENDANTNODE(n, '/content') AND CONTAINS(n.*, 'quick')";
    private static final String QUERY_PROPERTY =
            "SELECT * FROM [nt:unstructured] AS n WHERE ISDESCENDANTNODE(n, '/content') AND n.[category] = 'news'";

    @Parameters(name = "async={0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            {"none", null},
            {"async", new String[] {"async"}},
            {"async,nrt", new String[] {"async", "nrt"}},
            {"async,sync", new String[] {"async", "sync"}},
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

        Node properties = addIndex("luceneTest");
        Node text = properties.addNode("text", JcrConstants.NT_UNSTRUCTURED);
        text.setProperty("name", "text");
        text.setProperty("analyzed", true);
        text.setProperty("nodeScopeIndex", true);
        Node category = properties.addNode("category", JcrConstants.NT_UNSTRUCTURED);
        category.setProperty("name", "category");
        category.setProperty("propertyIndex", true);
        session.save();

        Node content = session.getRootNode().addNode("content", JcrConstants.NT_UNSTRUCTURED);
        addNode(content, "page1", "The quick brown fox", "news");
        addNode(content, "page2", "jumps over the lazy dog", "news");
        addNode(content, "page3", "A quick reply", "blog");
        session.save();
    }

    /**
     * Adds a Lucene index definition with the "async" property of the current test parameter.
     * @return Properties node of the index rule for nt:unstructured
     */
    private Node addIndex(String name) throws RepositoryException {
        Node index = session.getNode("/oak:index").addNode(name, "oak:QueryIndexDefinition");
        index.setProperty("type", "lucene");
        index.setProperty("compatVersion", 2L);
        index.setProperty("evaluatePathRestrictions", true);
        index.setProperty("includedPaths", new String[] {"/content"});
        if (async != null) {
            index.setProperty("async", async);
        }
        return index.addNode("indexRules", JcrConstants.NT_UNSTRUCTURED)
                .addNode(JcrConstants.NT_UNSTRUCTURED, JcrConstants.NT_UNSTRUCTURED)
                .addNode("properties", JcrConstants.NT_UNSTRUCTURED);
    }

    private static void addNode(Node parent, String name, String text, String category) throws RepositoryException {
        Node node = parent.addNode(name, JcrConstants.NT_UNSTRUCTURED);
        node.setProperty("text", text);
        node.setProperty("category", category);
    }

    @Test
    public void testFulltextQuery() throws RepositoryException {
        assertUsesLuceneIndex(QUERY_FULLTEXT);
        assertEquals(Set.of("/content/page1", "/content/page3"), queryPaths(QUERY_FULLTEXT));
    }

    @Test
    public void testPropertyQuery() throws RepositoryException {
        assertUsesLuceneIndex(QUERY_PROPERTY);
        assertEquals(Set.of("/content/page1", "/content/page2"), queryPaths(QUERY_PROPERTY));
    }

    @Test
    public void testContentChangesAreIndexedSynchronously() throws RepositoryException {
        addNode(session.getNode("/content"), "page4", "quick update", "news");
        session.save();

        assertEquals(Set.of("/content/page1", "/content/page3", "/content/page4"), queryPaths(QUERY_FULLTEXT));
        assertEquals(Set.of("/content/page1", "/content/page2", "/content/page4"), queryPaths(QUERY_PROPERTY));
    }

    @Test
    public void testContentRemovalIsIndexedSynchronously() throws RepositoryException {
        session.getNode("/content/page1").remove();
        session.getNode("/content/page2").setProperty("category", "blog");
        session.save();

        assertEquals(Set.of("/content/page3"), queryPaths(QUERY_FULLTEXT));
        assertEquals(Set.of(), queryPaths(QUERY_PROPERTY));
    }

    @Test
    public void testIndexAddedForExistingContent() throws RepositoryException {
        Node tag = addIndex("luceneLate").addNode("tag", JcrConstants.NT_UNSTRUCTURED);
        tag.setProperty("name", "tag");
        tag.setProperty("propertyIndex", true);
        session.getNode("/content/page2").setProperty("tag", "animal");
        session.save();
        session.getNode("/content/page3").setProperty("tag", "animal");
        session.save();

        String query =
                "SELECT * FROM [nt:unstructured] AS n WHERE ISDESCENDANTNODE(n, '/content') AND n.[tag] = 'animal'";
        assertUsesLuceneIndex(query, "luceneLate");
        assertEquals(Set.of("/content/page2", "/content/page3"), queryPaths(query));
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

    private void assertUsesLuceneIndex(String statement) throws RepositoryException {
        assertUsesLuceneIndex(statement, "luceneTest");
    }

    private void assertUsesLuceneIndex(String statement, String indexName) throws RepositoryException {
        QueryManager queryManager = session.getWorkspace().getQueryManager();
        Row row = queryManager
                .createQuery("EXPLAIN " + statement, Query.JCR_SQL2)
                .execute()
                .getRows()
                .nextRow();
        String plan = row.getValue("plan").getString();
        assertTrue("Unexpected query plan: " + plan, plan.contains("lucene:" + indexName));
    }
}
