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

import org.apache.sling.testing.mock.sling.oak.lucene.StdErrCapture;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LuceneIndexSupportTest {

    private static final String LUCENE_INDEX_PROVIDER_CLASS =
            "org.apache.jackrabbit.oak.plugins.index.lucene.LuceneIndexProvider";

    @Rule
    public StdErrCapture stdErr = new StdErrCapture();

    @Test
    public void testAvailable() {
        assertNull(LuceneIndexSupport.getUnavailableReason("1.0"));
    }

    @Test
    public void testNotInClassPath() {
        String reason = LuceneIndexSupport.getUnavailableReason("1.86.0", "org.example.DoesNotExist");
        assertTrue(reason, reason.startsWith("oak-lucene is not in the class path."));
        assertTrue(reason, reason.contains("org.apache.jackrabbit:oak-lucene:1.86.0"));
    }

    @Test
    public void testOtherArtifact() {
        // class from another artifact with different implementation title
        String reason = LuceneIndexSupport.getUnavailableReason("1.86.0", Test.class.getName());
        assertTrue(reason, reason.contains("is loaded from another artifact"));
        assertTrue(reason, reason.contains("e.g. the AEM API JARs"));
    }

    @Test
    public void testOakLuceneTitleWithoutVersion() {
        String reason =
                LuceneIndexSupport.getUnavailableReason("1.86.0", LUCENE_INDEX_PROVIDER_CLASS, "Oak Lucene", null);
        assertTrue(reason, reason.contains("is loaded from another artifact (Oak Lucene null)"));
    }

    @Test
    public void testTooOldVersion() {
        String reason1 =
                LuceneIndexSupport.getUnavailableReason("1.86.0", LUCENE_INDEX_PROVIDER_CLASS, "Oak Lucene", "1.80.0");
        String reason2 =
                LuceneIndexSupport.getUnavailableReason("1.86.0", LUCENE_INDEX_PROVIDER_CLASS, "Oak Lucene", "1.80.0");
        assertEquals("oak-lucene version 1.80.0 is older than the expected version 1.86.0.", reason1);
        assertEquals(reason1, reason2);
        // warning is logged only once
        assertEquals(1, stdErr.count("Lucene index support is not enabled: " + reason1));
    }

    @Test
    public void testNewerVersion() {
        assertNull(
                LuceneIndexSupport.getUnavailableReason("1.86.0", LUCENE_INDEX_PROVIDER_CLASS, "Oak Lucene", "1.88.0"));
    }
}
