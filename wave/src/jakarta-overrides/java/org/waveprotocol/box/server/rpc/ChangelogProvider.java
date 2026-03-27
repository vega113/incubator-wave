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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.json.JSONArray;
import org.json.JSONObject;
import org.waveprotocol.wave.util.logging.Log;

@Singleton
public final class ChangelogProvider {
  private static final Log LOG = Log.get(ChangelogProvider.class);

  private final JSONArray entries;
  private final String latestVersion;
  private final String latestTitle;
  private final String latestSummary;

  @Inject
  public ChangelogProvider() {
    this(loadEntries(Paths.get("config", "changelog.json")));
  }

  ChangelogProvider(Path changelogPath) {
    this(loadEntries(changelogPath));
  }

  ChangelogProvider(JSONArray entries) {
    this.entries = new JSONArray(entries.toString());
    JSONObject latestEntry = this.entries.length() > 0 ? this.entries.optJSONObject(0) : null;
    this.latestVersion = latestEntry != null ? latestEntry.optString("version", null) : null;
    this.latestTitle = latestEntry != null ? latestEntry.optString("title", null) : null;
    this.latestSummary = latestEntry != null ? latestEntry.optString("summary", null) : null;
  }

  public JSONArray getEntries() {
    return new JSONArray(entries.toString());
  }

  public JSONObject getLatestEntry() {
    JSONObject latestEntry = entries.length() > 0 ? entries.optJSONObject(0) : null;
    return latestEntry != null ? new JSONObject(latestEntry.toString()) : null;
  }

  public String getLatestVersion() {
    return latestVersion;
  }

  public String getLatestTitle() {
    return latestTitle;
  }

  public String getLatestSummary() {
    return latestSummary;
  }

  private static JSONArray loadEntries(Path changelogPath) {
    JSONArray loadedEntries = new JSONArray();
    if (Files.exists(changelogPath)) {
      try {
        String json = Files.readString(changelogPath, StandardCharsets.UTF_8);
        loadedEntries = new JSONArray(json);
      } catch (IOException | RuntimeException e) {
        LOG.warning("Failed to load changelog from " + changelogPath.toAbsolutePath(), e);
      }
    } else {
      LOG.warning("Changelog file not found at " + changelogPath.toAbsolutePath());
    }
    return loadedEntries;
  }
}
