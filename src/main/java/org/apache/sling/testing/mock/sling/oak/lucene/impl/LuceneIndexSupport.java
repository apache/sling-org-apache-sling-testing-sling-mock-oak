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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.jackrabbit.oak.Oak;
import org.apache.jackrabbit.oak.jcr.Jcr;
import org.apache.jackrabbit.oak.plugins.index.CompositeIndexEditorProvider;
import org.apache.jackrabbit.oak.plugins.index.IndexConstants;
import org.apache.jackrabbit.oak.plugins.index.IndexEditorProvider;
import org.apache.jackrabbit.oak.plugins.index.lucene.LuceneIndexConstants;
import org.apache.jackrabbit.oak.plugins.index.lucene.LuceneIndexEditorProvider;
import org.apache.jackrabbit.oak.plugins.index.lucene.LuceneIndexProvider;
import org.apache.jackrabbit.oak.spi.commit.DefaultEditor;
import org.apache.jackrabbit.oak.spi.commit.Observer;
import org.apache.jackrabbit.oak.spi.query.QueryIndexProvider;
import org.apache.sling.testing.mock.sling.oak.impl.ComparableVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers Oak Lucene index support. Enabled automatically if a compatible oak-lucene artifact is present in the
 * class path (optional dependency).
 * <p>
 * Lucene classes are only referenced from this class, so callers must check {@link #getUnavailableReason(String)}
 * before calling {@link #register(Jcr)} to avoid a {@link NoClassDefFoundError}.
 * </p>
 * <p>
 * No async indexing is running in the mock repository: index definitions with <code>async</code> property are
 * updated synchronously within each commit instead, see {@link SynchronousAsyncIndexHook}.
 * </p>
 */
public final class LuceneIndexSupport {

    private static final String LUCENE_INDEX_PROVIDER_CLASS =
            "org.apache.jackrabbit.oak.plugins.index.lucene.LuceneIndexProvider";
    private static final String OAK_LUCENE_IMPLEMENTATION_TITLE = "Oak Lucene";

    private static final AtomicBoolean TOO_OLD_WARNING_LOGGED = new AtomicBoolean();

    private static final Logger log = LoggerFactory.getLogger(LuceneIndexSupport.class);

    private LuceneIndexSupport() {
        // static methods only
    }

    /**
     * Checks whether a compatible oak-lucene artifact is present in the class path.
     * <p>
     * Lucene classes may also be provided by other artifacts like the AEM API JARs (uber-jar, aem-sdk-api), which do
     * not contain a complete and compatible oak-lucene implementation. So the Lucene classes have to be loaded from
     * the oak-lucene artifact itself.
     * </p>
     * @param expectedOakVersion Oak version this artifact is built for
     * @return null if a compatible oak-lucene artifact is found, otherwise the reason why Lucene index support
     *         is not available
     */
    public static @Nullable String getUnavailableReason(@NotNull String expectedOakVersion) {
        return getUnavailableReason(expectedOakVersion, LUCENE_INDEX_PROVIDER_CLASS);
    }

    static @Nullable String getUnavailableReason(@NotNull String expectedOakVersion, @NotNull String className) {
        Class<?> luceneIndexProviderClass;
        try {
            luceneIndexProviderClass = Class.forName(className, false, LuceneIndexSupport.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError ex) {
            return "oak-lucene is not in the class path. To enable it, add a test dependency to "
                    + "org.apache.jackrabbit:oak-lucene:" + expectedOakVersion + ".";
        }
        // check that the class is provided by oak-lucene, and not by another artifact embedding it
        Package pkg = luceneIndexProviderClass.getPackage();
        return getUnavailableReason(
                expectedOakVersion, className, pkg.getImplementationTitle(), pkg.getImplementationVersion());
    }

    static @Nullable String getUnavailableReason(
            @NotNull String expectedOakVersion,
            @NotNull String className,
            @Nullable String implementationTitle,
            @Nullable String implementationVersion) {
        if (!OAK_LUCENE_IMPLEMENTATION_TITLE.equals(implementationTitle) || implementationVersion == null) {
            return className + " is loaded from another artifact (" + implementationTitle + " "
                    + implementationVersion + ") instead of oak-lucene. To enable it, add a test dependency to "
                    + "org.apache.jackrabbit:oak-lucene:" + expectedOakVersion
                    + " and place it before other dependencies containing Oak Lucene classes, "
                    + "e.g. the AEM API JARs.";
        }
        if (new ComparableVersion(expectedOakVersion).compareTo(new ComparableVersion(implementationVersion)) > 0) {
            String reason = "oak-lucene version " + implementationVersion + " is older than the expected version "
                    + expectedOakVersion + ".";
            // oak-lucene was added deliberately, so tell about the mismatch also without Lucene index definitions
            if (TOO_OLD_WARNING_LOGGED.compareAndSet(false, true)) {
                log.warn("Lucene index support is not enabled: {}", reason);
            }
            return reason;
        }
        return null;
    }

    /**
     * Registers Lucene query index provider and index editor provider, and updates async indexes synchronously.
     * @param jcr JCR repository builder
     * @return Action which closes the Lucene index provider, has to be called when the repository is shut down
     */
    public static @NotNull Runnable register(@NotNull Jcr jcr) {
        LuceneIndexProvider indexProvider = new LuceneIndexProvider();
        LuceneIndexEditorProvider indexEditorProvider = new LuceneIndexEditorProvider();
        jcr.with((QueryIndexProvider) indexProvider)
                .with((Observer) indexProvider)
                .with(syncOnly(indexEditorProvider));

        // async indexes are updated synchronously with the same index editors as the sync indexes
        List<IndexEditorProvider> asyncIndexEditorProviders = new ArrayList<>();
        new Oak.OakDefaultComponents().indexEditorProviders().forEach(asyncIndexEditorProviders::add);
        asyncIndexEditorProviders.add(indexEditorProvider);
        jcr.with(new SynchronousAsyncIndexHook(CompositeIndexEditorProvider.compose(asyncIndexEditorProviders)));

        return indexProvider::close;
    }

    /**
     * Wraps the Lucene index editor provider for the default (sync) index update, which also includes hybrid
     * indexes (e.g. <code>async=["async", "nrt"]</code>). As no NRT indexing is configured, Lucene would write
     * the persisted index for them, which is done already by {@link SynchronousAsyncIndexHook} for the async lane.
     * A no-op editor is returned for them to avoid indexing twice (returning null would log a warning).
     */
    private static @NotNull IndexEditorProvider syncOnly(@NotNull IndexEditorProvider luceneIndexEditorProvider) {
        return (type, definition, root, callback) -> {
            if (LuceneIndexConstants.TYPE_LUCENE.equals(type)
                    && definition.hasProperty(IndexConstants.ASYNC_PROPERTY_NAME)) {
                return new DefaultEditor();
            }
            return luceneIndexEditorProvider.getIndexEditor(type, definition, root, callback);
        };
    }
}
