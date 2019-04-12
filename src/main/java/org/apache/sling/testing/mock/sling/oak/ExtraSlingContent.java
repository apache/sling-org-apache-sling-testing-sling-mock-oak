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
package org.apache.sling.testing.mock.sling.oak;

import static java.util.Collections.singleton;
import static org.apache.jackrabbit.JcrConstants.JCR_SYSTEM;
import static org.apache.jackrabbit.oak.plugins.index.IndexConstants.INDEX_DEFINITIONS_NAME;
import static org.apache.jackrabbit.oak.plugins.index.IndexUtils.createIndexDefinition;
import static org.apache.jackrabbit.oak.spi.namespace.NamespaceConstants.REP_NAMESPACES;
import static org.apache.sling.jcr.resource.JcrResourceConstants.SLING_NAMESPACE_URI;

import org.apache.jackrabbit.oak.plugins.name.Namespaces;
import org.apache.jackrabbit.oak.spi.lifecycle.RepositoryInitializer;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * Adds some default indexes useful for by sling resource-jcr mapping.
 * This is only a small subset of what is defined by default in the org.apache.sling.jcr.oak.server bundle.
 */
final class ExtraSlingContent implements RepositoryInitializer {

    @Override
    public void initialize(@NotNull NodeBuilder root) {

        // register sling namespace
        String slingNs = "sling";                
        if (root.hasChildNode(JCR_SYSTEM)) {
            NodeBuilder jcrSystem = root.getChildNode(JCR_SYSTEM);
            if (jcrSystem.hasChildNode(REP_NAMESPACES)) {
                NodeBuilder namespaces = jcrSystem.getChildNode(REP_NAMESPACES);
                slingNs = Namespaces.addCustomMapping(namespaces, SLING_NAMESPACE_URI, slingNs);
                Namespaces.buildIndexNode(namespaces);
            }
        }

        // add useful index definitions
        if (root.hasChildNode(INDEX_DEFINITIONS_NAME)) {
            NodeBuilder index = root.child(INDEX_DEFINITIONS_NAME);

            // jcr:
            property(index, "jcrLanguage", "jcr:language");
            property(index, "jcrLockOwner", "jcr:lockOwner");

            // sling:
            property(index, "slingAlias", slingNs + ":alias");
            property(index, "slingResource", slingNs + ":resource");
            property(index, "slingResourceType", slingNs + ":resourceType");
            property(index, "slingVanityPath", slingNs + ":vanityPath");
        }
    }

    /**
     * A convenience method to create a non-unique property index.
     */
    private static void property(NodeBuilder index, String indexName, String propertyName) {
        if (!index.hasChildNode(indexName)) {
            createIndexDefinition(index, indexName, true, false, singleton(propertyName), null);
        }
    }

}
