/**
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
package org.waveprotocol.box.server.rpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONArray;
import org.junit.Test;

public final class ChangelogProviderTest {
  @Test
  public void loadsEntriesAndLatestMetadataFromJsonFile() throws Exception {
    Path tempDir = Files.createTempDirectory("changelog-provider");
    Path changelogFile = tempDir.resolve("changelog.json");
    Files.writeString(
        changelogFile,
        "[{\"version\":\"2026-03-27\",\"date\":\"2026-03-27\",\"title\":\"Changelog System\","
            + "\"summary\":\"You can now see what's new after each deploy.\","
            + "\"sections\":[{\"type\":\"feature\",\"items\":[\"New /changelog page\"]}]},"
            + "{\"version\":\"2026-03-20\",\"date\":\"2026-03-20\",\"title\":\"Public Waves\","
            + "\"summary\":\"Public wave sharing is now available.\","
            + "\"sections\":[{\"type\":\"feature\",\"items\":[\"Shared public waves\"]}]}]",
        StandardCharsets.UTF_8);

    ChangelogProvider provider = new ChangelogProvider(changelogFile);

    JSONArray entries = provider.getEntries();
    assertEquals(2, entries.length());
    assertEquals("2026-03-27", provider.getLatestVersion());
    assertEquals("Changelog System", provider.getLatestTitle());
    assertEquals("You can now see what's new after each deploy.", provider.getLatestSummary());
  }

  @Test
  public void returnsEmptyStateWhenJsonIsMalformed() throws Exception {
    Path tempDir = Files.createTempDirectory("changelog-provider-bad");
    Path changelogFile = tempDir.resolve("changelog.json");
    Files.writeString(changelogFile, "{not-json", StandardCharsets.UTF_8);

    ChangelogProvider provider = new ChangelogProvider(changelogFile);

    assertEquals(0, provider.getEntries().length());
    assertNull(provider.getLatestVersion());
    assertNull(provider.getLatestTitle());
    assertNull(provider.getLatestSummary());
  }
}
