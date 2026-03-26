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

package org.waveprotocol.box.webclient.contact;

import java.util.List;

/**
 * Interface for searching contacts on the server via the
 * {@code GET /contacts/search} endpoint.
 *
 * <p>This is the GWT client-side counterpart of
 * {@code ContactSearchServlet} (server-side).
 */
public interface ContactSearchService {

  /** Holds a single search result with address and score. */
  public static class ContactSearchResult {
    private final String address;
    private final double score;

    public ContactSearchResult(String address, double score) {
      this.address = address;
      this.score = score;
    }

    public String getAddress() {
      return address;
    }

    public double getScore() {
      return score;
    }
  }

  /** Callback for asynchronous contact search. */
  public interface Callback {
    void onFailure(String message);

    /**
     * Notifies this callback of a successful search.
     *
     * @param results the list of matching contacts with their scores
     */
    void onSuccess(List<ContactSearchResult> results);
  }

  /**
   * Searches contacts on the server by address prefix.
   *
   * @param query the prefix to match (case-insensitive); empty string returns all
   * @param limit the maximum number of results to return
   * @param callback the callback to receive results
   */
  void search(String query, int limit, Callback callback);
}
