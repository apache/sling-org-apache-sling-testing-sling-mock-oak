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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.rules.ExternalResource;

/**
 * Captures System.err, which is where slf4j-simple writes log messages to in the tests.
 */
public final class StdErrCapture extends ExternalResource {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private PrintStream originalErr;

    @Override
    protected void before() {
        originalErr = System.err;
        System.setErr(new PrintStream(buffer, true, StandardCharsets.UTF_8));
    }

    @Override
    protected void after() {
        System.setErr(originalErr);
        originalErr.print(getOutput());
    }

    public String getOutput() {
        return buffer.toString(StandardCharsets.UTF_8);
    }

    public int count(String text) {
        String output = getOutput();
        int count = 0;
        int index = output.indexOf(text);
        while (index >= 0) {
            count++;
            index = output.indexOf(text, index + text.length());
        }
        return count;
    }
}
