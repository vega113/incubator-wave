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

package org.waveprotocol.examples.robots.gptbot;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.wave.api.Blip;
import com.google.wave.api.JsonRpcResponse;
import com.google.wave.api.SearchResult;
import com.google.wave.api.WaveService;
import com.google.wave.api.Wavelet;

import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Fetches extra context from SupaWave and can optionally post replies through the active API.
 */
public final class SupaWaveApiClient implements SupaWaveClient {

  private static final Log LOG = Log.get(SupaWaveApiClient.class);
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);

  private final GptBotConfig config;
  private final HttpClient httpClient;
  private final WaveServiceFactory waveServiceFactory;
  private final String robotCapabilitiesHash;
  private volatile AccessToken dataApiToken;
  private volatile AccessToken robotApiToken;

  public SupaWaveApiClient(GptBotConfig config) {
    this(config, HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(), WaveService::new);
  }

  SupaWaveApiClient(GptBotConfig config, HttpClient httpClient) {
    this(config, httpClient, WaveService::new);
  }

  SupaWaveApiClient(GptBotConfig config, HttpClient httpClient,
      WaveServiceFactory waveServiceFactory) {
    this.config = config;
    this.httpClient = httpClient;
    this.waveServiceFactory = waveServiceFactory;
    this.robotCapabilitiesHash = GptBotRobot.capabilitiesHashFor(config.getRobotName());
  }

  @Override
  public Optional<String> fetchWaveContext(String waveId, String waveletId) {
    Optional<String> context = Optional.empty();
    if (config.getContextMode() != GptBotConfig.ContextMode.NONE && config.hasApiCredentials()) {
      try {
        String endpoint = readRpcEndpoint();
        String token = readAccessToken();
        Wavelet wavelet = fetchWavelet(waveId, waveletId, endpoint, token);
        context = Optional.ofNullable(summarizeWavelet(wavelet)).filter(value -> !value.isBlank());
      } catch (IOException | InterruptedException | RuntimeException e) {
        logWarningAndRestoreInterrupt("Unable to fetch SupaWave context", e);
      }
    }
    return context;
  }

  @Override
  public Optional<String> search(String query) {
    Optional<String> summary = Optional.empty();
    if (!query.isBlank() && config.hasApiCredentials()) {
      try {
        String endpoint = readRpcEndpoint();
        String token = readAccessToken();
        SearchResult result = authenticatedService(endpoint, token).search(query, 0, 5, endpoint);
        summary = Optional.ofNullable(summarizeSearchResult(result)).filter(value -> !value.isBlank());
      } catch (IOException | InterruptedException | RuntimeException e) {
        logWarningAndRestoreInterrupt("Unable to search SupaWave", e);
      }
    }
    return summary;
  }

  @Override
  public boolean appendReply(String waveId, String waveletId, String blipId, String content) {
    boolean appended = false;
    String replyText = content == null ? "" : content.strip();
    if (!replyText.isBlank() && config.hasApiCredentials()) {
      try {
        String endpoint = activeRpcEndpoint();
        String token = getRobotAccessToken();
        WaveService waveService = authenticatedService(endpoint, token);
        Wavelet wavelet = waveService.fetchWavelet(
            WaveId.deserialise(waveId), WaveletId.deserialise(waveletId), endpoint);
        Blip parentBlip = wavelet.getBlip(blipId);
        if (parentBlip == null) {
          LOG.warning("Unable to find parent blip for active reply: " + blipId);
        } else {
          parentBlip.reply().append(replyText);
          List<JsonRpcResponse> responses = waveService.submit(wavelet, endpoint);
          appended = !containsError(responses);
        }
      } catch (IOException | InterruptedException | RuntimeException e) {
        logWarningAndRestoreInterrupt("Unable to append a reply through the active API", e);
      }
    }
    return appended;
  }

  private Wavelet fetchWavelet(String waveId, String waveletId, String endpoint, String token)
      throws IOException {
    WaveService waveService = authenticatedService(endpoint, token);
    return waveService.fetchWavelet(WaveId.deserialise(waveId), WaveletId.deserialise(waveletId),
        endpoint);
  }

  private WaveService authenticatedService(String endpoint, String token) {
    WaveService waveService = waveServiceFactory.create(robotCapabilitiesHash);
    waveService.setupJwt(token, endpoint);
    return waveService;
  }

  private boolean containsError(List<JsonRpcResponse> responses) {
    boolean containsError = false;
    if (responses != null) {
      for (JsonRpcResponse response : responses) {
        if (response != null && response.isError()) {
          containsError = true;
        }
      }
    }
    return containsError;
  }

  private String readAccessToken() throws IOException, InterruptedException {
    String token = getDataApiAccessToken();
    if (config.getContextMode() == GptBotConfig.ContextMode.ACTIVE) {
      token = getRobotAccessToken();
    }
    return token;
  }

  private synchronized String getDataApiAccessToken() throws IOException, InterruptedException {
    AccessToken current = dataApiToken;
    String token = null;
    if (current != null && current.isFresh()) {
      token = current.token;
    }
    if (token == null) {
      token = requestAccessToken(false);
    }
    return token;
  }

  private synchronized String getRobotAccessToken() throws IOException, InterruptedException {
    AccessToken current = robotApiToken;
    String token = null;
    if (current != null && current.isFresh()) {
      token = current.token;
    }
    if (token == null) {
      token = requestAccessToken(true);
    }
    return token;
  }

  private String requestAccessToken(boolean robotToken) throws IOException, InterruptedException {
    StringBuilder form = new StringBuilder();
    form.append("grant_type=client_credentials");
    form.append("&client_id=").append(encode(config.getApiRobotId()));
    form.append("&client_secret=").append(encode(config.getApiRobotSecret()));
    form.append("&expiry=3600");
    if (robotToken) {
      form.append("&token_type=robot");
    }

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(tokenEndpoint()))
        .timeout(REQUEST_TIMEOUT)
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
        .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IOException("Token endpoint returned HTTP " + response.statusCode());
    }

    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
    String token = json.get("access_token").getAsString();
    long expiresIn = json.has("expires_in") ? json.get("expires_in").getAsLong() : 3600L;
    AccessToken freshToken = new AccessToken(token,
        Instant.now().plusSeconds(Math.max(60L, expiresIn)));
    if (robotToken) {
      robotApiToken = freshToken;
    } else {
      dataApiToken = freshToken;
    }
    return token;
  }

  private String summarizeWavelet(Wavelet wavelet) {
    StringBuilder summary = new StringBuilder();
    if (wavelet != null) {
      appendField(summary, wavelet.getTitle(), "Title");
      appendField(summary, wavelet.getWaveId().serialise(), "Wave ID");
      appendField(summary, wavelet.getWaveletId().serialise(), "Wavelet ID");
      appendField(summary, wavelet.getRootBlipId(), "Root blip");

      List<String> blipIds = new ArrayList<String>(wavelet.getBlips().keySet());
      Collections.sort(blipIds);
      int count = 0;
      for (String currentBlipId : blipIds) {
        if (count >= 4) {
          break;
        }
        Blip blip = wavelet.getBlip(currentBlipId);
        if (blip != null) {
          String content = blip.getContent();
          if (content != null && !content.strip().isEmpty()) {
            summary.append("Blip ").append(currentBlipId).append(": ")
                .append(content.strip()).append('\n');
            count++;
          }
        }
      }
    }
    return clamp(summary.toString());
  }

  private String summarizeSearchResult(SearchResult searchResult) {
    StringBuilder summary = new StringBuilder();
    if (searchResult != null) {
      appendField(summary, searchResult.getQuery(), "Search query");
      int count = Math.min(3, searchResult.getDigests().size());
      for (int index = 0; index < count; index++) {
        SearchResult.Digest digest = searchResult.getDigests().get(index);
        appendField(summary, digest.getTitle(), "Result title");
        appendField(summary, digest.getWaveId(), "Result wave");
        appendField(summary, digest.getSnippet(), "Snippet");
      }
    }
    return clamp(summary.toString());
  }

  private static void appendField(StringBuilder summary, String value, String label) {
    if (value != null && !value.isBlank()) {
      summary.append(label).append(": ").append(value).append('\n');
    }
  }

  private String readRpcEndpoint() {
    String endpoint = dataRpcEndpoint();
    if (config.getContextMode() == GptBotConfig.ContextMode.ACTIVE) {
      endpoint = activeRpcEndpoint();
    }
    return endpoint;
  }

  private String dataRpcEndpoint() {
    return config.getBaseUrl() + "/robot/dataapi/rpc";
  }

  private String activeRpcEndpoint() {
    return config.getBaseUrl() + "/robot/rpc";
  }

  private String tokenEndpoint() {
    return config.getBaseUrl() + "/robot/dataapi/token";
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private void logWarningAndRestoreInterrupt(String message, Exception e) {
    if (e instanceof InterruptedException) {
      Thread.currentThread().interrupt();
    }
    LOG.warning(message, e);
  }

  private static String clamp(String value) {
    String clamped = value.trim();
    if (clamped.length() > 2000) {
      clamped = clamped.substring(0, 2000).trim() + "…";
    }
    return clamped;
  }

  interface WaveServiceFactory {
    WaveService create(String version);
  }

  private static final class AccessToken {

    private final String token;
    private final Instant expiresAt;

    private AccessToken(String token, Instant expiresAt) {
      this.token = token;
      this.expiresAt = expiresAt;
    }

    private boolean isFresh() {
      return Instant.now().isBefore(expiresAt.minusSeconds(30L));
    }
  }
}
