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

package org.waveprotocol.wave.client.wavepanel.render;

import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.Response;
import com.google.gwt.http.client.URL;

/** Simple client requester that calls /fragments?top=..&bottom=.. (HTTP path). */
public final class ClientFragmentRequester implements FragmentRequester {
  private final String endpoint;

  public ClientFragmentRequester() { this("/fragments"); }
  public ClientFragmentRequester(String endpoint) { this.endpoint = endpoint; }

  @Override
  public void fetchRange(int viewportTop, int viewportBottom, final Callback cb) {
    String url = endpoint + "?top=" + URL.encodeQueryString(String.valueOf(viewportTop))
        + "&bottom=" + URL.encodeQueryString(String.valueOf(viewportBottom));
    RequestBuilder rb = new RequestBuilder(RequestBuilder.GET, url);
    try {
      rb.sendRequest("", new RequestCallback() {
        @Override public void onResponseReceived(Request request, Response response) {
          if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
            if (cb != null) cb.onSuccess();
          } else {
            if (cb != null) cb.onError(new RuntimeException("HTTP " + response.getStatusCode()));
          }
        }
        @Override public void onError(Request request, Throwable exception) {
          if (cb != null) cb.onError(exception);
        }
      });
    } catch (Exception ex) {
      if (cb != null) cb.onError(ex);
    }
  }
}
