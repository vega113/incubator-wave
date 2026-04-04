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

package com.google.wave.api;

import junit.framework.TestCase;

import net.oauth.http.HttpMessage;
import net.oauth.http.HttpResponseMessage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tests for JWT-backed WaveService authentication.
 */
public class WaveServiceJwtTest extends TestCase {

  private static final String RPC_SERVER_URL = "http://example.com/robot/rpc";

  public void testSetupJwtAddsAuthorizationHeader() throws Exception {
    RecordingFetcher fetcher = new RecordingFetcher();
    WaveService service = new WaveService(fetcher, "test-capabilities");
    service.setupJwt("jwt-token", RPC_SERVER_URL);

    service.submit(service.newWave("example.com", participants("alice@example.com")),
        RPC_SERVER_URL);

    assertHeader(fetcher.lastRequest, "Authorization", "Bearer jwt-token");
    assertHeader(fetcher.lastRequest, HttpMessage.CONTENT_TYPE,
        "application/json; charset=utf-8");
    assertMissingHeader(fetcher.lastRequest, "oauth_version");
    assertEquals(RPC_SERVER_URL, fetcher.lastRequest.url.toString());
  }

  public void testSetupJwtReplacesOAuthForSameRpcUrl() throws Exception {
    RecordingFetcher fetcher = new RecordingFetcher();
    WaveService service = new WaveService(fetcher, "test-capabilities");
    service.setupOAuth("consumer-key", "consumer-secret", RPC_SERVER_URL);
    service.setupJwt("jwt-token", RPC_SERVER_URL);

    service.submit(service.newWave("example.com", participants("alice@example.com")),
        RPC_SERVER_URL);

    assertHeader(fetcher.lastRequest, "Authorization", "Bearer jwt-token");
    assertMissingHeader(fetcher.lastRequest, "oauth_version");
    assertEquals(RPC_SERVER_URL, fetcher.lastRequest.url.toString());
  }

  public void testSetupOAuthReplacesJwtForSameRpcUrl() throws Exception {
    RecordingFetcher fetcher = new RecordingFetcher();
    WaveService service = new WaveService(fetcher, "test-capabilities");
    service.setupJwt("jwt-token", RPC_SERVER_URL);
    service.setupOAuth("consumer-key", "consumer-secret", RPC_SERVER_URL);

    service.submit(service.newWave("example.com", participants("alice@example.com")),
        RPC_SERVER_URL);

    assertHeader(fetcher.lastRequest, "oauth_version", "1.0");
    assertMissingHeader(fetcher.lastRequest, "Authorization");
  }

  public void testSubmitRestoresPendingOperationsWhenJwtRequestFails() {
    WaveService service = new WaveService(new FailingFetcher(), "test-capabilities");
    service.setupJwt("jwt-token", RPC_SERVER_URL);
    Wavelet wavelet = service.newWave("example.com", participants("alice@example.com"));

    try {
      service.submit(wavelet, RPC_SERVER_URL);
      fail("Expected submit to throw IOException");
    } catch (IOException expected) {
      assertFalse(wavelet.getOperationQueue().getPendingOperations().isEmpty());
    }
  }

  private static Set<String> participants(String participant) {
    Set<String> participants = new LinkedHashSet<String>();
    participants.add(participant);
    return participants;
  }

  private static void assertHeader(HttpMessage request, String name, String expectedValue) {
    String value = null;
    for (Map.Entry<String, String> header : request.headers) {
      if (name.equalsIgnoreCase(header.getKey())) {
        value = header.getValue();
      }
    }
    assertEquals(expectedValue, value);
  }

  private static void assertMissingHeader(HttpMessage request, String name) {
    for (Map.Entry<String, String> header : request.headers) {
      if (name.equalsIgnoreCase(header.getKey())) {
        fail("Did not expect header " + name + " but found value " + header.getValue());
      }
    }
  }

  private static final class RecordingFetcher extends WaveService.HttpFetcher {

    private HttpMessage lastRequest;

    @Override
    public HttpResponseMessage execute(HttpMessage request, Map<String, Object> stringObjectMap)
        throws IOException {
      lastRequest = request;
      return new WaveService.HttpResponse(request.method, new URL(request.url.toString()), 200,
          new ByteArrayInputStream(
              "[{\"id\":\"notify\",\"data\":{}},{\"id\":\"op\",\"data\":{}}]"
                  .getBytes(StandardCharsets.UTF_8)));
    }
  }

  private static final class FailingFetcher extends WaveService.HttpFetcher {

    @Override
    public HttpResponseMessage execute(HttpMessage request, Map<String, Object> stringObjectMap)
        throws IOException {
      throw new IOException("boom");
    }
  }
}
