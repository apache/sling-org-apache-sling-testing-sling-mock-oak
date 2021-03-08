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
package org.apache.sling.testing.mock.sling.oak.contentimport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.loader.AbstractContentLoaderJsonTest;
import org.junit.Test;

@SuppressWarnings("null")
public class ContentLoaderJsonTest extends AbstractContentLoaderJsonTest {

    @Override
    protected ResourceResolverType getResourceResolverType() {
        return ResourceResolverType.JCR_OAK;
    }

    @Test
    public void testJcrUuid() {
        Resource resource = context.resourceResolver().getResource(path + "/sample/en/jcr:content/par/image/file/jcr:content");
        ValueMap props = ResourceUtil.getValueMap(resource);

        // jcr:uuid is not imported from json with OAK repository - just check for not null
        assertNotNull(props.get(JcrConstants.JCR_UUID));
    }

    @Test
    public void testMixinNodeType() throws Exception {
        Resource resource = context.resourceResolver().getResource(path + "/sample/en/jcr:content/par/image/ntLinkedFileTargetWithMixin/" + JcrConstants.JCR_CONTENT);

        assertMixinNodeType(resource, "app:TestMixin");
    }

    @Test
    public void testReferenceable() throws Exception {
        Resource resource = context.resourceResolver().getResource(path + "/sample/en/jcr:content/par/image/ntLinkedFileTargetWithMixin/" + JcrConstants.JCR_CONTENT);
        ValueMap props = ResourceUtil.getValueMap(resource);

        assertMixinNodeType(resource, JcrConstants.MIX_REFERENCEABLE);
        assertNotNull(props.get(JcrConstants.JCR_UUID));
    }

    @Test
    public void testLinkedFile() throws Exception {
        Resource resource = context.resourceResolver().getResource(path + "/sample/en/jcr:content/par/image/ntLinkedFile");
        Resource targetResource = context.resourceResolver().getResource(path + "/sample/en/jcr:content/par/image/ntLinkedFileTargetWithMixin/" + JcrConstants.JCR_CONTENT);
        Node node = resource.adaptTo(Node.class);
        Node target = node.getProperty(JcrConstants.JCR_CONTENT).getNode();

        assertEquals(targetResource.getPath(), target.getPath());
        assertEquals(targetResource.getValueMap().get(JcrConstants.JCR_UUID), target.getProperty(JcrConstants.JCR_UUID).getString());
    }

    private void assertMixinNodeType(final Resource resource, final String mixinNodeType) throws RepositoryException {
        Node node = resource.adaptTo(Node.class);
        if (node != null) {
            assertTrue(node.isNodeType(mixinNodeType));
        } else {
            fail();
        }
    }
}
