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
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;

import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests the behavior without oak-lucene in the class path.
 * Executed in a separate surefire execution which removes oak-lucene from the test class path.
 */
public class LuceneIndexNotAvailableTest {

    private final StdErrCapture stdErr = new StdErrCapture();
    private final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @Rule
    public RuleChain rules = RuleChain.outerRule(stdErr).around(context);

    @Before
    public void assumeOakLuceneMissing() {
        try {
            Class.forName("org.apache.jackrabbit.oak.plugins.index.lucene.LuceneIndexProvider");
            throw new AssertionError("oak-lucene is expected not to be in the class path for this test");
        } catch (ClassNotFoundException ex) {
            // expected
        }
    }

    @Test
    public void testLuceneIndexNotUsed() throws RepositoryException {
        Session session = context.resourceResolver().adaptTo(Session.class);

        Node index = session.getNode("/oak:index").addNode("luceneNotAvailable", "oak:QueryIndexDefinition");
        index.setProperty("type", "lucene");
        index.setProperty("compatVersion", 2L);
        Node property = index.addNode("indexRules", JcrConstants.NT_UNSTRUCTURED)
                .addNode(JcrConstants.NT_UNSTRUCTURED, JcrConstants.NT_UNSTRUCTURED)
                .addNode("properties", JcrConstants.NT_UNSTRUCTURED)
                .addNode("text", JcrConstants.NT_UNSTRUCTURED);
        property.setProperty("name", "text");
        property.setProperty("analyzed", true);
        property.setProperty("nodeScopeIndex", true);
        session.getRootNode()
                .addNode("content", JcrConstants.NT_UNSTRUCTURED)
                .setProperty("text", "The quick brown fox");
        session.save();

        assertTrue(
                stdErr.getOutput(),
                stdErr.getOutput()
                        .contains("Lucene index definition /oak:index/luceneNotAvailable found, but Lucene index "
                                + "support is not enabled: queries will not use this index, and full-text queries "
                                + "return no results. oak-lucene is not in the class path."));

        Query query = session.getWorkspace()
                .getQueryManager()
                .createQuery("SELECT * FROM [nt:unstructured] AS n WHERE CONTAINS(n.*, 'quick')", Query.JCR_SQL2);
        assertEquals(0, query.execute().getNodes().getSize());

        String plan = session.getWorkspace()
                .getQueryManager()
                .createQuery("EXPLAIN SELECT * FROM [nt:unstructured] AS n WHERE n.[text] = 'x'", Query.JCR_SQL2)
                .execute()
                .getRows()
                .nextRow()
                .getValue("plan")
                .getString();
        assertFalse("Unexpected query plan: " + plan, plan.contains("lucene:"));
    }
}
