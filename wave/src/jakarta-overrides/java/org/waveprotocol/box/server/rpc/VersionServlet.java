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
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.json.JSONObject;

/**
 * Exposes the server version as a JSON endpoint so that long-lived browser
 * clients can detect when the server has been upgraded and prompt the user
 * to reload.
 *
 * <p>Response format: {@code {"version":"<sha>","buildTime":<epoch>}}
 *
 * <p>The version string is read from {@code core.server_version} in the
 * Typesafe Config (overridable via the {@code WAVE_SERVER_VERSION} environment
 * variable). {@code buildTime} is captured once at servlet construction time
 * (effectively the JVM startup timestamp), providing a deploy-unique signal
 * even when the version string stays the same across redeploys.
 */
@Singleton
public final class VersionServlet extends HttpServlet {
  private final String version;
  private final long buildTime;
  private final JSONObject changelog;

  @Inject
  public VersionServlet(Config config, ChangelogProvider changelogProvider) {
    this(
        config.hasPath("core.server_version") ? config.getString("core.server_version") : "dev",
        System.currentTimeMillis(),
        changelogProvider.getLatestEntry());
  }

  public long getBuildTime() { return buildTime; }

  /** Visible-for-testing constructor. */
  public VersionServlet(String version, long buildTime) {
    this(version, buildTime, null);
  }

  /** Visible-for-testing constructor. */
  public VersionServlet(String version, long buildTime, JSONObject changelog) {
    this.version = version;
    this.buildTime = buildTime;
    this.changelog = changelog != null ? new JSONObject(changelog.toString()) : null;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    JSONObject responseJson = new JSONObject();
    responseJson.put("version", version);
    responseJson.put("buildTime", buildTime);
    if (changelog != null && !changelog.isEmpty()) {
      responseJson.put("changelog", new JSONObject(changelog.toString()));
    }
    resp.setContentType("application/json; charset=UTF-8");
    resp.setHeader("Cache-Control", "no-cache, no-store");
    resp.getWriter().write(responseJson.toString());
  }
}
