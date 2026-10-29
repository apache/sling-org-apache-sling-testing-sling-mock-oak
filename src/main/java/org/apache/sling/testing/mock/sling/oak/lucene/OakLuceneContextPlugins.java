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

import org.apache.sling.testing.mock.osgi.context.AbstractContextPlugin;
import org.apache.sling.testing.mock.osgi.context.ContextPlugin;
import org.apache.sling.testing.mock.osgi.context.OsgiContextImpl;
import org.apache.sling.testing.mock.sling.oak.lucene.impl.LuceneIndexEnabled;
import org.jetbrains.annotations.NotNull;

/**
 * Context plugins for the {@code JCR_OAK} resource resolver type.
 */
public final class OakLuceneContextPlugins {

    private OakLuceneContextPlugins() {
        // constants only
    }

    /**
     * Enables Oak Lucene index support for the repository of this context.
     * <p>
     * Requires a test dependency to <code>org.apache.jackrabbit:oak-lucene</code> matching the Oak version.
     * Async index definitions are updated synchronously within each commit.
     * </p>
     * <p>
     * Usage: <code>new SlingContextBuilder(ResourceResolverType.JCR_OAK).plugin(OakLuceneContextPlugins.OAK_LUCENE).build()</code>
     * </p>
     */
    public static final @NotNull ContextPlugin<OsgiContextImpl> OAK_LUCENE =
            new AbstractContextPlugin<OsgiContextImpl>() {
                @Override
                public void beforeSetUp(@NotNull OsgiContextImpl context) {
                    // registered before the repository is created and activated
                    context.registerService(LuceneIndexEnabled.class, new LuceneIndexEnabled());
                }
            };
}
