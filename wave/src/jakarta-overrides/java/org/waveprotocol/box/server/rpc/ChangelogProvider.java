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
import com.typesafe.config.Config;
import java.io.IOException;
import java.io.InputStream;
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
  public ChangelogProvider(Config config) {
    this(loadDefaultEntries(config));
  }

  ChangelogProvider(Path changelogPath) {
    this(loadEntries(changelogPath));
  }

  public ChangelogProvider() {
    this(loadDefaultEntries(null));
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

  private static JSONArray loadDefaultEntries(Config config) {
    JSONArray loadedEntries = new JSONArray();
    if (config != null && config.hasPath("core.changelog_path")) {
      loadedEntries = loadEntries(resolveConfiguredPath(config.getString("core.changelog_path")));
    }
    if (loadedEntries.length() == 0) {
      loadedEntries = loadEntriesFromClasspath("config/changelog.json");
    }
    if (loadedEntries.length() == 0) {
      loadedEntries = loadEntries(Paths.get("config", "changelog.json"));
    }
    return loadedEntries;
  }

  private static Path resolveConfiguredPath(String changelogPath) {
    Path configuredPath = Paths.get(changelogPath);
    if (configuredPath.isAbsolute()) {
      return configuredPath;
    }
    String serverConfigPath = System.getProperty("wave.server.config");
    if (serverConfigPath != null && !serverConfigPath.isBlank()) {
      Path configDirectory = Paths.get(serverConfigPath).toAbsolutePath().getParent();
      if (configDirectory != null) {
        return configDirectory.resolve(configuredPath).normalize();
      }
    }
    return configuredPath.toAbsolutePath();
  }

  private static JSONArray loadEntriesFromClasspath(String resourceName) {
    JSONArray loadedEntries = new JSONArray();
    try (InputStream inputStream =
        ChangelogProvider.class.getClassLoader().getResourceAsStream(resourceName)) {
      if (inputStream != null) {
        String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        loadedEntries = new JSONArray(json);
      } else {
        LOG.warning("Changelog resource not found at " + resourceName);
      }
    } catch (IOException | RuntimeException e) {
      LOG.warning("Failed to load changelog resource " + resourceName, e);
    }
    return loadedEntries;
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
