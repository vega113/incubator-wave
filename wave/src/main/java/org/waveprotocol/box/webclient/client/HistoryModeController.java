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

package org.waveprotocol.box.webclient.client;

import com.google.gwt.dom.client.Element;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.Window;

import org.waveprotocol.wave.client.widget.dialog.ConfirmDialog;
import org.waveprotocol.wave.client.widget.toast.ToastNotification;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Simple controller for inline version history browsing. The user clicks
 * "History", a scrubber slider appears at the bottom, and moving the slider
 * replaces the wave panel content with a rendered snapshot of the wave at
 * that version. No diff highlighting -- just shows the wave as it was.
 *
 * <p>States:
 * <ul>
 *   <li>INACTIVE -- normal editing/viewing mode</li>
 *   <li>LOADING -- fetching version list from server</li>
 *   <li>BROWSING -- history mode active, scrubber visible</li>
 * </ul>
 */
public final class HistoryModeController {

  public enum State {
    INACTIVE,
    LOADING,
    BROWSING
  }

  /** Listener for history mode state changes. */
  public interface Listener {
    void onHistoryModeEntered();
    void onHistoryModeExited();
    void onLoadingStarted();
    void onLoadingFailed(String error);
  }

  private State state = State.INACTIVE;

  private final HistoryApiClient apiClient;
  private final VersionScrubber scrubber;

  /** Wave/wavelet coordinates for API calls. */
  private String waveDomain;
  private String waveId;
  private String waveletDomain;
  private String waveletId;

  /** The element that holds the wave panel content. */
  private Element wavePanelElement;

  /** Saved innerHTML of the wave panel before entering history mode. */
  private String savedWavePanelHtml;

  /** The loaded delta groups (used to map slider positions to versions). */
  private List<HistoryApiClient.DeltaGroup> groups =
      new ArrayList<HistoryApiClient.DeltaGroup>();

  /** The currently displayed group index. */
  private int currentGroupIndex = -1;

  /** Whether history mode is active. */
  private boolean historyModeActive = false;

  /** Registered listeners. */
  private final List<Listener> listeners = new ArrayList<Listener>();

  public HistoryModeController(HistoryApiClient apiClient, VersionScrubber scrubber) {
    this.apiClient = apiClient;
    this.scrubber = scrubber;
  }

  /** Sets the wave/wavelet coordinates for API calls. */
  public void setWaveletCoordinates(String waveDomain, String waveId,
      String waveletDomain, String waveletId) {
    this.waveDomain = waveDomain;
    this.waveId = waveId;
    this.waveletDomain = waveletDomain;
    this.waveletId = waveletId;
  }

  /** Sets the wave panel element whose content will be swapped. */
  public void setWavePanelElement(Element element) {
    this.wavePanelElement = element;
  }

  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  public State getState() {
    return state;
  }

  /**
   * Returns true if history mode is active (LOADING or BROWSING).
   * Components should check this to disable editing.
   */
  public boolean isHistoryModeActive() {
    return historyModeActive;
  }

  /**
   * Enters history mode. Fetches delta groups from the server, then shows
   * the scrubber positioned at the latest version.
   */
  public void enterHistoryMode() {
    if (state != State.INACTIVE) {
      return;
    }
    if (waveDomain == null || waveId == null) {
      return;
    }

    state = State.LOADING;
    historyModeActive = true;

    // Save the current wave panel content so we can restore it on exit.
    if (wavePanelElement != null) {
      savedWavePanelHtml = wavePanelElement.getInnerHTML();
      wavePanelElement.addClassName("history-mode");
    }

    for (int i = 0; i < listeners.size(); i++) {
      listeners.get(i).onLoadingStarted();
    }

    apiClient.fetchGroups(waveDomain, waveId, waveletDomain, waveletId, 0,
        new HistoryApiClient.Callback<List<HistoryApiClient.DeltaGroup>>() {
          public void onSuccess(List<HistoryApiClient.DeltaGroup> result) {
            if (state != State.LOADING) {
              return; // cancelled while loading
            }
            groups = result;
            if (groups.isEmpty()) {
              exitHistoryMode();
              for (int i = 0; i < listeners.size(); i++) {
                listeners.get(i).onLoadingFailed("No history available");
              }
              return;
            }

            state = State.BROWSING;
            scrubber.configure(groups);
            scrubber.show();

            // Position at the last group (current version)
            int lastIndex = groups.size() - 1;
            scrubber.setGroupIndex(lastIndex);
            onScrubberMove(lastIndex);

            for (int i = 0; i < listeners.size(); i++) {
              listeners.get(i).onHistoryModeEntered();
            }
          }

          public void onFailure(String error) {
            state = State.INACTIVE;
            historyModeActive = false;
            if (wavePanelElement != null) {
              wavePanelElement.removeClassName("history-mode");
            }
            for (int i = 0; i < listeners.size(); i++) {
              listeners.get(i).onLoadingFailed(error);
            }
          }
        });
  }

  /**
   * Exits history mode. Hides the scrubber, restores the live wave panel
   * content, and re-enables editing.
   */
  public void exitHistoryMode() {
    if (state == State.INACTIVE) {
      return;
    }

    state = State.INACTIVE;
    historyModeActive = false;
    currentGroupIndex = -1;
    groups = new ArrayList<HistoryApiClient.DeltaGroup>();

    scrubber.hide();

    // Restore the original wave panel content.
    if (wavePanelElement != null) {
      wavePanelElement.removeClassName("history-mode");
      if (savedWavePanelHtml != null) {
        wavePanelElement.setInnerHTML(savedWavePanelHtml);
        savedWavePanelHtml = null;
      }
    }

    for (int i = 0; i < listeners.size(); i++) {
      listeners.get(i).onHistoryModeExited();
    }
  }

  /** Toggles history mode on/off. */
  public void toggleHistoryMode() {
    if (state == State.INACTIVE) {
      enterHistoryMode();
    } else {
      exitHistoryMode();
    }
  }

  /**
   * Called when the scrubber position changes. Fetches the snapshot at the
   * selected version and replaces the wave panel content with rendered HTML.
   */
  public void onScrubberMove(final int groupIndex) {
    if (state != State.BROWSING || groupIndex < 0 || groupIndex >= groups.size()) {
      return;
    }
    if (groupIndex == currentGroupIndex) {
      return;
    }

    currentGroupIndex = groupIndex;
    final HistoryApiClient.DeltaGroup group = groups.get(groupIndex);

    // Update scrubber label immediately
    scrubber.updateLabel(group);

    // Show "Restore" button only if not at the latest version
    boolean isLatest = (groupIndex == groups.size() - 1);
    scrubber.setRestoreVisible(!isLatest);

    // Fetch the snapshot at this version and render it
    apiClient.fetchSnapshotDebounced(waveDomain, waveId, waveletDomain, waveletId,
        group.getEndVersion(),
        new HistoryApiClient.Callback<HistoryApiClient.SnapshotData>() {
          public void onSuccess(HistoryApiClient.SnapshotData snapshot) {
            if (currentGroupIndex != groupIndex) {
              return; // user has moved on
            }
            renderSnapshot(snapshot, group);
          }

          public void onFailure(String error) {
            if (currentGroupIndex != groupIndex) {
              return;
            }
            if (wavePanelElement != null) {
              wavePanelElement.setInnerHTML(
                  "<div class='history-error'>Failed to load version: "
                  + escapeHtml(error) + "</div>");
            }
          }
        });
  }

  /**
   * Restores the wave to the currently viewed historical version by sending
   * a POST to the server.
   */
  public void restoreCurrentVersion() {
    if (state != State.BROWSING || currentGroupIndex < 0
        || currentGroupIndex >= groups.size()) {
      return;
    }

    // Don't restore if already at latest
    if (currentGroupIndex == groups.size() - 1) {
      return;
    }

    final long targetVersion = groups.get(currentGroupIndex).getEndVersion();

    ConfirmDialog.show(
        "Restore version",
        "Restore wave to version " + targetVersion + "?\n\n"
            + "This will revert all changes made after this version.",
        "Restore", "Cancel",
        new ConfirmDialog.Listener() {
          @Override
          public void onConfirm() {
            doRestore(targetVersion);
          }

          @Override
          public void onCancel() {
            // User cancelled -- nothing to do.
          }
        });
  }

  /** Sends the POST request to restore a wave to the given version. */
  private void doRestore(long targetVersion) {
    String url = "/history/" + enc(waveDomain) + "/" + enc(waveId) + "/"
        + enc(waveletDomain) + "/" + enc(waveletId)
        + "/api/restore?version=" + targetVersion;

    RequestBuilder rb = new RequestBuilder(RequestBuilder.POST, url);
    rb.setCallback(new RequestCallback() {
      public void onResponseReceived(Request request, Response response) {
        if (response.getStatusCode() == Response.SC_OK) {
          // Exit history mode -- the wave will reload with the restored content
          exitHistoryMode();
          // Force a page reload to pick up the new wave state
          Window.Location.reload();
        } else {
          ToastNotification.showWarning("Failed to restore version: HTTP "
              + response.getStatusCode() + " " + response.getStatusText());
        }
      }

      public void onError(Request request, Throwable exception) {
        ToastNotification.showWarning(
            "Failed to restore version: " + exception.getMessage());
      }
    });

    try {
      rb.send();
    } catch (RequestException e) {
      ToastNotification.showWarning(
          "Failed to send restore request: " + e.getMessage());
    }
  }

  /**
   * Renders a snapshot as simple HTML blip cards and replaces the wave panel
   * content with it. No diffs, no OT -- just the wave content as it was.
   */
  private void renderSnapshot(HistoryApiClient.SnapshotData snapshot,
      HistoryApiClient.DeltaGroup group) {
    if (wavePanelElement == null) {
      return;
    }

    StringBuilder html = new StringBuilder();

    // Version info header
    String author = group.getAuthor();
    int atIdx = author.indexOf('@');
    String displayName = (atIdx > 0) ? author.substring(0, atIdx) : author;
    String dateStr = formatTimestamp(group.getEndTimestamp());

    html.append("<div class='history-snapshot-header'>");
    html.append("<span class='history-snapshot-version'>Version ");
    html.append(group.getEndVersion());
    html.append("</span>");
    html.append(" <span class='history-snapshot-sep'>&mdash;</span> ");
    html.append("<span class='history-snapshot-author'>by ");
    html.append(escapeHtml(displayName));
    html.append("</span>");
    html.append(" <span class='history-snapshot-sep'>&mdash;</span> ");
    html.append("<span class='history-snapshot-date'>");
    html.append(escapeHtml(dateStr));
    html.append("</span>");
    html.append("</div>");

    // Render each blip document as a simple card
    List<HistoryApiClient.BlipData> docs = snapshot.getDocuments();
    boolean hasBlips = false;

    for (int i = 0; i < docs.size(); i++) {
      HistoryApiClient.BlipData blip = docs.get(i);
      // Only render actual blips (b+ prefix), skip metadata documents
      if (!blip.getId().startsWith("b+")) {
        continue;
      }

      String content = blip.getContent();
      if (content == null || content.trim().isEmpty()) {
        continue;
      }

      hasBlips = true;

      String blipAuthor = blip.getAuthor();
      int bAtIdx = blipAuthor.indexOf('@');
      String blipDisplayName = (bAtIdx > 0) ? blipAuthor.substring(0, bAtIdx) : blipAuthor;

      html.append("<div class='history-blip'>");
      html.append("<div class='history-blip-header'>");
      html.append("<strong>").append(escapeHtml(blipDisplayName)).append("</strong>");
      if (blip.getLastModified() > 0) {
        html.append(" <span class='history-blip-time'>");
        html.append(formatTimestamp(blip.getLastModified()));
        html.append("</span>");
      }
      html.append("</div>");
      html.append("<div class='history-blip-content'>");
      html.append(escapeHtml(content));
      html.append("</div>");
      html.append("</div>");
    }

    if (!hasBlips) {
      html.append("<div class='history-empty'>No content at this version.</div>");
    }

    wavePanelElement.setInnerHTML(html.toString());
  }

  /** Navigates to the previous group (left arrow). */
  public void movePrevious() {
    if (state == State.BROWSING && currentGroupIndex > 0) {
      int newIndex = currentGroupIndex - 1;
      scrubber.setGroupIndex(newIndex);
      onScrubberMove(newIndex);
    }
  }

  /** Navigates to the next group (right arrow). */
  public void moveNext() {
    if (state == State.BROWSING && currentGroupIndex < groups.size() - 1) {
      int newIndex = currentGroupIndex + 1;
      scrubber.setGroupIndex(newIndex);
      onScrubberMove(newIndex);
    }
  }

  /** Formats a Unix timestamp (ms) into a human-readable date string. */
  private static String formatTimestamp(long timestampMs) {
    Date date = new Date(timestampMs);
    String[] monthNames = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    String month = monthNames[date.getMonth()];
    int day = date.getDate();
    int hours = date.getHours();
    int mins = date.getMinutes();
    String ampm = hours >= 12 ? "PM" : "AM";
    int displayHours = hours % 12;
    if (displayHours == 0) displayHours = 12;
    String minStr = (mins < 10) ? "0" + mins : "" + mins;
    return month + " " + day + ", " + displayHours + ":" + minStr + " " + ampm;
  }

  /** Basic HTML escaping. */
  private static String escapeHtml(String text) {
    if (text == null) return "";
    return text.replace("&", "&amp;")
               .replace("<", "&lt;")
               .replace(">", "&gt;")
               .replace("\"", "&quot;");
  }

  /** URL-encodes a path component. */
  private static String enc(String s) {
    return URL.encodePathSegment(s);
  }
}
