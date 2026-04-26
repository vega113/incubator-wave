/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.waveprotocol.box.server.rpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.waveprotocol.box.common.ExceptionalIterator;
import org.waveprotocol.box.common.Receiver;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.FeatureFlagService;
import org.waveprotocol.box.server.persistence.FeatureFlagStore;
import org.waveprotocol.box.server.robots.operations.TestingWaveletData;
import org.waveprotocol.box.server.rpc.render.J2clSelectedWaveSnapshotRenderer;
import org.waveprotocol.box.server.rpc.render.WavePreRenderer;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.federation.Proto.ProtocolWaveletDelta;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;

/**
 * F-2.S4 acceptance fixture (#1048). Sibling to
 * {@link J2clStageOneReadSurfaceParityTest}; the two stay in lockstep
 * and asserts the slice's six floating + overlay mount points are
 * present on {@code ?view=j2cl-root} and absent from
 * {@code ?view=gwt}, covering inventory affordances:
 *
 * <ul>
 *   <li><b>J.2</b> &lt;wavy-floating-scroll-to-new&gt; pill,</li>
 *   <li><b>J.3</b> &lt;wavy-wave-controls-toggle&gt;,</li>
 *   <li><b>J.4</b> &lt;wavy-nav-drawer-toggle&gt; with aria-controls,</li>
 *   <li><b>J.5</b> &lt;wavy-back-to-inbox&gt;,</li>
 *   <li><b>K.1–K.6</b> &lt;wavy-version-history&gt; overlay,</li>
 *   <li><b>L.1 + L.5</b> &lt;wavy-profile-overlay&gt; scaffolding.</li>
 * </ul>
 *
 * <p>Companion lit-side coverage lives in:
 * {@code j2cl/lit/test/wavy-floating-scroll-to-new.test.js},
 * {@code wavy-wave-controls-toggle.test.js},
 * {@code wavy-nav-drawer-toggle.test.js},
 * {@code wavy-back-to-inbox.test.js},
 * {@code wavy-version-history.test.js},
 * {@code wavy-profile-overlay.test.js}; those cover the live Lit
 * element behavior (aria, events, keyboard) the server-rendered HTML
 * upgrades into.
 */
public final class J2clStageOneFloatingOverlaysParityTest {
  private static final WaveId WAVE_ID = WaveId.of("example.com", "w+f2-s4");
  private static final WaveletId CONV_ROOT = WaveletId.of("example.com", "conv+root");
  private static final ParticipantId AUTHOR = ParticipantId.ofUnsafe("alice@example.com");
  private static final ParticipantId VIEWER = ParticipantId.ofUnsafe("alice@example.com");

  /**
   * J.2 — the J2CL root shell mounts &lt;wavy-floating-scroll-to-new&gt;
   * with the {@code hidden} attribute (initial state — no unread). The
   * client element flips visibility via removeAttribute("hidden") when
   * the unread count goes positive.
   */
  @Test
  public void j2clRootShellMountsScrollToNewFloatingPill() throws Exception {
    String html = renderJ2clRootShell();
    assertTrue(
        "Floating scroll-to-new pill must be mounted in the J2CL root shell, got: " + html,
        html.contains("<wavy-floating-scroll-to-new"));
    assertTrue(
        "Pill must be initially hidden so it does not steal tab focus when unread==0",
        html.contains("<wavy-floating-scroll-to-new")
            && html.contains("data-j2cl-floating-mount=\"true\"")
            && htmlContainsTagWithAttr(html, "wavy-floating-scroll-to-new", "hidden"));
  }

  /** J.3 — wave controls toggle present in the J2CL root shell. */
  @Test
  public void j2clRootShellMountsWaveControlsToggle() throws Exception {
    String html = renderJ2clRootShell();
    assertTrue(
        "Hide/Show wave controls toggle must be mounted, got: " + html,
        html.contains("<wavy-wave-controls-toggle"));
  }

  /**
   * J.4 — nav-drawer toggle present, with aria-controls pointing at the
   * shell drawer id so AT can announce the drawer relationship.
   */
  @Test
  public void j2clRootShellMountsNavDrawerToggle() throws Exception {
    String html = renderJ2clRootShell();
    assertTrue(
        "Nav drawer toggle must be mounted, got: " + html,
        html.contains("<wavy-nav-drawer-toggle"));
    assertTrue(
        "Nav drawer toggle must declare aria-controls so AT exposes the drawer relationship",
        html.contains("<wavy-nav-drawer-toggle")
            && html.contains("aria-controls=\"shell-nav-drawer\""));
  }

  /** J.5 — back-to-inbox affordance present (mobile-only via CSS). */
  @Test
  public void j2clRootShellMountsBackToInbox() throws Exception {
    String html = renderJ2clRootShell();
    assertTrue(
        "Back-to-inbox affordance must be mounted, got: " + html,
        html.contains("<wavy-back-to-inbox"));
  }

  /**
   * K.1–K.6 — the version-history overlay element host is present
   * with the {@code hidden} attribute (initial state — closed).
   * The Lit element ships the slider, toggles, restore + exit
   * affordances under one custom element; the server only mounts the
   * outer host.
   */
  @Test
  public void j2clRootShellMountsVersionHistoryOverlay() throws Exception {
    String html = renderJ2clRootShell();
    assertTrue(
        "Version-history overlay host must be mounted, got: " + html,
        html.contains("<wavy-version-history"));
    assertTrue(
        "Version-history host must be initially hidden so the overlay does not flash",
        htmlContainsTagWithAttr(html, "wavy-version-history", "hidden"));
  }

  /**
   * L.1 + L.5 — the profile overlay element host is present with the
   * {@code hidden} attribute. The Lit element listens on document for
   * wave-blip-profile-requested (S1's CustomEvent on avatar click)
   * and opens itself; the server only mounts the outer host.
   */
  @Test
  public void j2clRootShellMountsProfileOverlay() throws Exception {
    String html = renderJ2clRootShell();
    assertTrue(
        "Profile overlay host must be mounted, got: " + html,
        html.contains("<wavy-profile-overlay"));
    assertTrue(
        "Profile overlay host must be initially hidden so it does not flash on first paint",
        htmlContainsTagWithAttr(html, "wavy-profile-overlay", "hidden"));
  }

  /**
   * Umbrella assertion — exactly six S4 floating mounts present on the
   * signed-in shell, counted via the {@code data-j2cl-floating-mount}
   * marker every S4 mount carries. Guards against accidental drops in
   * future refactors.
   */
  @Test
  public void j2clRootShellMountsAllSixFloatingControls() throws Exception {
    String html = renderJ2clRootShell();
    int markerCount = countOccurrences(html, "data-j2cl-floating-mount=\"true\"");
    assertEquals(
        "Exactly six S4 floating mount points must be present on the J2CL root shell, got "
            + markerCount + " in:\n" + html,
        6,
        markerCount);
  }

  /**
   * Reciprocal — the legacy GWT path must NOT load any of the six S4
   * floating mounts. Rollback to {@code ?view=gwt} stays safe.
   */
  @Test
  public void legacyGwtRouteOmitsAllSixFloatingControls() throws Exception {
    WaveletProvider provider = providerForWave(buildWaveletData(3));
    J2clSelectedWaveSnapshotRenderer renderer = new J2clSelectedWaveSnapshotRenderer(provider);
    WaveClientServlet servlet = createServlet(VIEWER, renderer);

    String html = invokeServlet(servlet, "gwt", WAVE_ID.serialise());

    assertFalse(
        "GWT path must not mount the J.2 floating pill",
        html.contains("wavy-floating-scroll-to-new"));
    assertFalse(
        "GWT path must not mount the J.3 wave-controls toggle",
        html.contains("wavy-wave-controls-toggle"));
    assertFalse(
        "GWT path must not mount the J.4 nav-drawer toggle",
        html.contains("wavy-nav-drawer-toggle"));
    assertFalse(
        "GWT path must not mount the J.5 back-to-inbox affordance",
        html.contains("wavy-back-to-inbox"));
    assertFalse(
        "GWT path must not mount the K version-history overlay host",
        html.contains("wavy-version-history"));
    assertFalse(
        "GWT path must not mount the L profile overlay host",
        html.contains("wavy-profile-overlay"));
    assertFalse(
        "GWT path must not carry the S4 floating-mount marker attribute",
        html.contains("data-j2cl-floating-mount"));
  }

  // --- helpers (mirror of the F-1 fixture so the two stay in lockstep) ----

  private static String renderJ2clRootShell() throws Exception {
    WaveletProvider provider = providerForWave(buildWaveletData(3));
    J2clSelectedWaveSnapshotRenderer renderer = new J2clSelectedWaveSnapshotRenderer(provider);
    WaveClientServlet servlet = createServlet(VIEWER, renderer);
    return invokeServlet(servlet, "j2cl-root", WAVE_ID.serialise());
  }

  private static boolean htmlContainsTagWithAttr(String html, String tag, String attr) {
    int idx = 0;
    String prefix = "<" + tag;
    while ((idx = html.indexOf(prefix, idx)) != -1) {
      int afterPrefix = idx + prefix.length();
      // Reject prefix collisions like <wavy-version-history-row when
      // querying for <wavy-version-history. The character following
      // the matched prefix must be a tag-name terminator (whitespace,
      // '/', or '>'); otherwise this is a different element.
      if (afterPrefix >= html.length()) {
        return false;
      }
      char next = html.charAt(afterPrefix);
      if (next != ' ' && next != '\t' && next != '\n' && next != '\r'
          && next != '/' && next != '>') {
        idx = afterPrefix;
        continue;
      }
      int end = html.indexOf('>', idx);
      if (end < 0) {
        return false;
      }
      String openTag = html.substring(idx, end);
      if (openTag.contains(" " + attr + "=") || openTag.endsWith(" " + attr)
          || openTag.contains(" " + attr + " ")) {
        return true;
      }
      idx = end + 1;
    }
    return false;
  }

  private static List<ObservableWaveletData> buildWaveletData(int blipCount) {
    TestingWaveletData data =
        new TestingWaveletData(WAVE_ID, CONV_ROOT, AUTHOR, true);
    for (int i = 0; i < blipCount; i++) {
      data.appendBlipWithText("Lorem ipsum dolor sit amet, blip " + i);
    }
    return data.copyWaveletData();
  }

  private static WaveletProvider providerForWave(List<ObservableWaveletData> wavelets) {
    Map<WaveletName, CommittedWaveletSnapshot> snapshots = new HashMap<>();
    ImmutableSet.Builder<WaveletId> waveletIds = ImmutableSet.builder();
    for (ObservableWaveletData waveletData : wavelets) {
      waveletIds.add(waveletData.getWaveletId());
      snapshots.put(
          WaveletName.of(waveletData.getWaveId(), waveletData.getWaveletId()),
          new CommittedWaveletSnapshot(waveletData, HashedVersion.unsigned(10)));
    }
    final ImmutableSet<WaveletId> finalWaveletIds = waveletIds.build();
    return new WaveletProvider() {
      @Override
      public void initialize() {
      }

      @Override
      public void submitRequest(
          WaveletName waveletName, ProtocolWaveletDelta delta, SubmitRequestListener listener) {
      }

      @Override
      public void getHistory(
          WaveletName waveletName,
          HashedVersion versionStart,
          HashedVersion versionEnd,
          Receiver<TransformedWaveletDelta> receiver) {
      }

      @Override
      public boolean checkAccessPermission(WaveletName waveletName, ParticipantId participantId) {
        return true;
      }

      @Override
      public ExceptionalIterator<WaveId, WaveServerException> getWaveIds() {
        return null;
      }

      @Override
      public ImmutableSet<WaveletId> getWaveletIds(WaveId waveId) {
        return WAVE_ID.equals(waveId) ? finalWaveletIds : ImmutableSet.of();
      }

      @Override
      public CommittedWaveletSnapshot getSnapshot(WaveletName waveletName) {
        return snapshots.get(waveletName);
      }

      @Override
      public HashedVersion getHashedVersion(WaveletName waveletName, long version) {
        return null;
      }
    };
  }

  private static WaveClientServlet createServlet(
      ParticipantId user, J2clSelectedWaveSnapshotRenderer snapshotRenderer)
      throws Exception {
    Config config = ConfigFactory.parseString(
        "core.http_frontend_addresses=[\"127.0.0.1:9898\"]\n"
            + "core.http_websocket_public_address=\"\"\n"
            + "core.http_websocket_presented_address=\"\"\n"
            + "core.search_type=\"memory\"\n"
            + "administration.analytics_account=\"\"\n");
    SessionManager sessionManager = mock(SessionManager.class);
    AccountStore accountStore = mock(AccountStore.class);
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(user);
    when(sessionManager.getLoggedInUser((WebSession) null)).thenReturn(user);
    if (user != null) {
      AccountData accountData = mock(AccountData.class);
      HumanAccountData humanAccountData = mock(HumanAccountData.class);
      when(accountData.isHuman()).thenReturn(true);
      when(accountData.asHuman()).thenReturn(humanAccountData);
      when(humanAccountData.getRole()).thenReturn(HumanAccountData.ROLE_USER);
      when(accountStore.getAccount(user)).thenReturn(accountData);
      when(sessionManager.getLoggedInAccount(any(WebSession.class))).thenReturn(accountData);
      when(sessionManager.getLoggedInAccount((WebSession) null)).thenReturn(accountData);
    }
    return new WaveClientServlet(
        "example.com",
        config,
        sessionManager,
        accountStore,
        new VersionServlet("test", 0L),
        mock(WavePreRenderer.class),
        snapshotRenderer,
        new FeatureFlagService(featureFlagStore()));
  }

  private static String invokeServlet(WaveClientServlet servlet, String view, String waveId)
      throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(request.getParameter("view")).thenReturn(view);
    when(request.getParameterValues("view")).thenReturn(new String[]{view});
    when(request.getParameter("wave")).thenReturn(waveId);
    when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
    when(request.getSession(false)).thenReturn(mock(HttpSession.class));
    when(response.getWriter()).thenReturn(new PrintWriter(body));
    servlet.doGet(request, response);
    return body.toString();
  }

  private static int countOccurrences(String haystack, String needle) {
    if (haystack == null || needle == null || needle.isEmpty()) {
      return 0;
    }
    int count = 0;
    int idx = 0;
    while ((idx = haystack.indexOf(needle, idx)) != -1) {
      count++;
      idx += needle.length();
    }
    return count;
  }

  private static FeatureFlagStore featureFlagStore() throws Exception {
    FeatureFlagStore store = mock(FeatureFlagStore.class);
    when(store.getAll()).thenReturn(Collections.emptyList());
    return store;
  }
}
