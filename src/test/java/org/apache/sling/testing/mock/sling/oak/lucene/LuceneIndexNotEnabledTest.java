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
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertFalse;

/**
 * Tests that Lucene index support is not enabled without the context plugin, even if oak-lucene is present
 * in the class path.
 */
public class LuceneIndexNotEnabledTest {

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @Test
    public void testLuceneIndexNotUsed() throws RepositoryException {
        Session session = context.resourceResolver().adaptTo(Session.class);

        Node index = session.getNode("/oak:index").addNode("luceneTest", "oak:QueryIndexDefinition");
        index.setProperty("type", "lucene");
        index.setProperty("compatVersion", 2L);
        index.addNode("indexRules", JcrConstants.NT_UNSTRUCTURED)
                .addNode(JcrConstants.NT_UNSTRUCTURED, JcrConstants.NT_UNSTRUCTURED)
                .addNode("properties", JcrConstants.NT_UNSTRUCTURED)
                .addNode("category", JcrConstants.NT_UNSTRUCTURED)
                .setProperty("propertyIndex", true);
        session.getRootNode().addNode("content", JcrConstants.NT_UNSTRUCTURED).setProperty("category", "news");
        session.save();

        String plan = session.getWorkspace()
                .getQueryManager()
                .createQuery("EXPLAIN SELECT * FROM [nt:unstructured] AS n WHERE n.[category] = 'news'", Query.JCR_SQL2)
                .execute()
                .getRows()
                .nextRow()
                .getValue("plan")
                .getString();
        assertFalse("Unexpected query plan: " + plan, plan.contains("lucene:"));
    }
}
