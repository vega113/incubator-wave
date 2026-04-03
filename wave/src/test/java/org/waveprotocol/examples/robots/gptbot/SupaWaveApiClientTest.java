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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.wave.api.Blip;
import com.google.wave.api.JsonRpcResponse;
import com.google.wave.api.SearchResult;
import com.google.wave.api.WaveService;
import com.google.wave.api.Wavelet;

import junit.framework.TestCase;

import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

public class SupaWaveApiClientTest extends TestCase {

  public void testSearchRestoresInterruptStatusWhenRequestInterrupted() {
    GptBotConfig config = GptBotConfig.forTest();
    SupaWaveApiClient client = new SupaWaveApiClient(config, new InterruptingHttpClient());

    try {
      Optional<String> summary = client.search("wave");
      assertFalse(summary.isPresent());
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }

  public void testAppendReplyUsesWaveServiceWithRobotToken() {
    GptBotConfig config = GptBotConfig.forTest().withReplyMode(GptBotConfig.ReplyMode.ACTIVE);
    RecordingWaveService waveService = new RecordingWaveService();
    RecordingTokenHttpClient httpClient = new RecordingTokenHttpClient("robot-token");
    SupaWaveApiClient client = new SupaWaveApiClient(config, httpClient, version -> waveService);

    Wavelet wavelet = mock(Wavelet.class);
    Blip parentBlip = mock(Blip.class);
    Blip childBlip = mock(Blip.class);
    JsonRpcResponse response = mock(JsonRpcResponse.class);

    when(wavelet.getBlip("b+root")).thenReturn(parentBlip);
    when(parentBlip.reply()).thenReturn(childBlip);
    when(childBlip.append("Reply from SDK")).thenReturn(null);
    when(response.isError()).thenReturn(false);

    waveService.fetchedWavelet = wavelet;
    waveService.submitResponses = List.of(response);

    assertTrue(client.appendReply("example.com!w+abc123", "example.com!conv+root", "b+root",
        "Reply from SDK"));

    assertEquals("robot-token", waveService.lastSetupToken);
    assertEquals(config.getBaseUrl() + "/robot/rpc", waveService.lastSetupRpcUrl);
    assertEquals(config.getBaseUrl() + "/robot/rpc", waveService.lastFetchRpcUrl);
    assertEquals(config.getBaseUrl() + "/robot/rpc", waveService.lastSubmitRpcUrl);
    verify(parentBlip).reply();
    verify(childBlip).append("Reply from SDK");
  }

  public void testAppendReplyReturnsFalseWhenParentBlipIsMissing() {
    GptBotConfig config = GptBotConfig.forTest().withReplyMode(GptBotConfig.ReplyMode.ACTIVE);
    RecordingWaveService waveService = new RecordingWaveService();
    RecordingTokenHttpClient httpClient = new RecordingTokenHttpClient("robot-token");
    SupaWaveApiClient client = new SupaWaveApiClient(config, httpClient, version -> waveService);

    Wavelet wavelet = mock(Wavelet.class);
    when(wavelet.getBlip("missing-blip")).thenReturn(null);
    waveService.fetchedWavelet = wavelet;

    assertFalse(client.appendReply("example.com!w+abc123", "example.com!conv+root",
        "missing-blip", "Reply from SDK"));
    assertEquals(config.getBaseUrl() + "/robot/rpc", waveService.lastSetupRpcUrl);
    assertEquals(config.getBaseUrl() + "/robot/rpc", waveService.lastFetchRpcUrl);
    assertNull(waveService.lastSubmitRpcUrl);
  }

  private static final class RecordingWaveService extends WaveService {

    private String lastSetupToken;
    private String lastSetupRpcUrl;
    private String lastFetchRpcUrl;
    private String lastSubmitRpcUrl;
    private Wavelet fetchedWavelet;
    private List<JsonRpcResponse> submitResponses = List.of();

    private RecordingWaveService() {
      super("test-version");
    }

    @Override
    public void setupJwt(String token, String rpcServerUrl) {
      lastSetupToken = token;
      lastSetupRpcUrl = rpcServerUrl;
    }

    @Override
    public Wavelet fetchWavelet(WaveId waveId, WaveletId waveletId, String rpcServerUrl)
        throws IOException {
      lastFetchRpcUrl = rpcServerUrl;
      return fetchedWavelet;
    }

    @Override
    public SearchResult search(String query, Integer index, Integer numResults, String rpcServerUrl)
        throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<JsonRpcResponse> submit(Wavelet wavelet, String rpcServerUrl) throws IOException {
      lastSubmitRpcUrl = rpcServerUrl;
      return submitResponses;
    }
  }

  private static final class RecordingTokenHttpClient extends HttpClient {

    private final String token;

    private RecordingTokenHttpClient(String token) {
      this.token = token;
    }

    @Override
    public Optional<CookieHandler> cookieHandler() {
      return Optional.empty();
    }

    @Override
    public Optional<Duration> connectTimeout() {
      return Optional.empty();
    }

    @Override
    public Redirect followRedirects() {
      return Redirect.NEVER;
    }

    @Override
    public Optional<ProxySelector> proxy() {
      return Optional.empty();
    }

    @Override
    public SSLContext sslContext() {
      return null;
    }

    @Override
    public SSLParameters sslParameters() {
      return null;
    }

    @Override
    public Optional<Authenticator> authenticator() {
      return Optional.empty();
    }

    @Override
    public HttpClient.Version version() {
      return HttpClient.Version.HTTP_1_1;
    }

    @Override
    public Optional<Executor> executor() {
      return Optional.empty();
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
        throws IOException, InterruptedException {
      @SuppressWarnings("unchecked")
      HttpResponse<T> response = (HttpResponse<T>) new StringHttpResponse(request,
          "{\"access_token\":\"" + token + "\",\"expires_in\":3600}");
      return response;
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
        HttpResponse.BodyHandler<T> responseBodyHandler) {
      return CompletableFuture.failedFuture(new UnsupportedOperationException());
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
        HttpResponse.BodyHandler<T> responseBodyHandler,
        HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
      return CompletableFuture.failedFuture(new UnsupportedOperationException());
    }
  }

  private static final class StringHttpResponse implements HttpResponse<String> {

    private final HttpRequest request;
    private final String body;

    private StringHttpResponse(HttpRequest request, String body) {
      this.request = request;
      this.body = body;
    }

    @Override
    public int statusCode() {
      return 200;
    }

    @Override
    public HttpRequest request() {
      return request;
    }

    @Override
    public Optional<HttpResponse<String>> previousResponse() {
      return Optional.empty();
    }

    @Override
    public HttpHeaders headers() {
      return HttpHeaders.of(Map.of(), (name, value) -> true);
    }

    @Override
    public String body() {
      return body;
    }

    @Override
    public Optional<SSLSession> sslSession() {
      return Optional.empty();
    }

    @Override
    public URI uri() {
      return request.uri();
    }

    @Override
    public HttpClient.Version version() {
      return HttpClient.Version.HTTP_1_1;
    }
  }

  private static final class InterruptingHttpClient extends HttpClient {

    @Override
    public Optional<CookieHandler> cookieHandler() {
      return Optional.empty();
    }

    @Override
    public Optional<Duration> connectTimeout() {
      return Optional.empty();
    }

    @Override
    public Redirect followRedirects() {
      return Redirect.NEVER;
    }

    @Override
    public Optional<ProxySelector> proxy() {
      return Optional.empty();
    }

    @Override
    public SSLContext sslContext() {
      return null;
    }

    @Override
    public SSLParameters sslParameters() {
      return null;
    }

    @Override
    public Optional<Authenticator> authenticator() {
      return Optional.empty();
    }

    @Override
    public Version version() {
      return Version.HTTP_1_1;
    }

    @Override
    public Optional<Executor> executor() {
      return Optional.empty();
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
        throws IOException, InterruptedException {
      throw new InterruptedException("interrupted");
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
        HttpResponse.BodyHandler<T> responseBodyHandler) {
      return CompletableFuture.failedFuture(new UnsupportedOperationException());
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
        HttpResponse.BodyHandler<T> responseBodyHandler,
        HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
      return CompletableFuture.failedFuture(new UnsupportedOperationException());
    }
  }
}
