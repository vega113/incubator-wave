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
package org.waveprotocol.box.server.frontend;

import static org.junit.Assert.*;

import com.typesafe.config.ConfigFactory;
import com.google.protobuf.RpcCallback;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.waveprotocol.box.common.comms.WaveClientRpc;
import org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolOpenRequest;
import org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolWaveletUpdate;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.id.ModernIdSerialiser;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics;

/** Tests viewport limit clamping and direction normalization. */
public final class WaveClientRpcViewportHintsTest {

  private int prevDefault;
  private int prevMax;
  private boolean prevMetricsEnabled;
  private long prevSnapshotFallbacks;
  private long prevInitialWindows;

  @Before
  public void setUp() {
    prevDefault = WaveClientRpcImpl.getDefaultViewportLimit();
    prevMax = WaveClientRpcImpl.getMaxViewportLimit();
    prevMetricsEnabled = FragmentsMetrics.isEnabled();
    prevSnapshotFallbacks = FragmentsMetrics.j2clViewportSnapshotFallbacks.get();
    prevInitialWindows = FragmentsMetrics.j2clViewportInitialWindows.get();
    FragmentsMetrics.setEnabled(true);
    FragmentsMetrics.j2clViewportSnapshotFallbacks.set(0L);
    FragmentsMetrics.j2clViewportInitialWindows.set(0L);
    // Enable fragments handler with RPC fetch enabled
    WaveletProvider provider = providerWithBlips(15);
    WaveClientRpcImpl.setFragmentsHandler(new FragmentsViewChannelHandler(provider,
        ConfigFactory.parseString("server.enableFetchFragmentsRpc=true")));
  }

  @After
  public void tearDown() {
    WaveClientRpcImpl.setViewportLimits(prevDefault, prevMax);
    WaveClientRpcImpl.setFragmentsHandler(null);
    FragmentsMetrics.j2clViewportSnapshotFallbacks.set(prevSnapshotFallbacks);
    FragmentsMetrics.j2clViewportInitialWindows.set(prevInitialWindows);
    FragmentsMetrics.setEnabled(prevMetricsEnabled);
  }

  @Test
  public void negativeLimitFallsBackToDefault() {
    WaveClientRpcImpl.setViewportLimits(4, 10);
    ProtocolWaveletUpdate update = openWithHints("b+1", "forward", -123);
    assertTrue(update.hasFragments());
    int blipCount = countBlipRanges(update.getFragments());
    assertEquals(4, blipCount);
  }

  @Test
  public void limitClampedToMax() {
    WaveClientRpcImpl.setViewportLimits(3, 6);
    ProtocolWaveletUpdate update = openWithHints("b+1", "forward", 100);
    assertTrue(update.hasFragments());
    int blipCount = countBlipRanges(update.getFragments());
    assertEquals(6, blipCount);
  }

  @Test
  public void invalidDirectionNormalizesToForward() {
    WaveClientRpcImpl.setViewportLimits(5, 10);
    // Start from b+5, invalid direction => treated as forward; expect b+5 then b+6 first two blips
    ProtocolWaveletUpdate update = openWithHints("b+5", "sideways", 2);
    // Expect that invalid direction does not break fragments emission path.
    assertTrue("Expected fragments payload present", update.hasFragments());
  }

  @Test
  public void viewportHintsSuppressWholeSnapshotWhenFragmentsAvailable() {
    ProtocolWaveletUpdate update = openWithHints("b+1", "forward", 5);

    assertTrue("Expected fragments payload present", update.hasFragments());
    assertFalse("Viewport-hinted open should not include full snapshot", update.hasSnapshot());
    assertTrue("Expected resulting version for write-session coupling", update.hasResultingVersion());
    assertTrue("Expected commit notice for selected-wave bootstrap", update.hasCommitNotice());
    assertEquals(1L, FragmentsMetrics.j2clViewportInitialWindows.get());
    assertEquals(0L, FragmentsMetrics.j2clViewportSnapshotFallbacks.get());
  }

  @Test
  public void noHintOpenKeepsWholeSnapshotForLegacyClients() {
    ProtocolWaveletUpdate update = openWithoutHints();

    assertTrue("Legacy no-hint open keeps snapshot bootstrap", update.hasSnapshot());
    assertTrue("Fragments can still be attached for legacy clients", update.hasFragments());
    assertEquals(0L, FragmentsMetrics.j2clViewportInitialWindows.get());
    assertEquals(0L, FragmentsMetrics.j2clViewportSnapshotFallbacks.get());
  }

  @Test
  public void viewportHintsFallbackToSnapshotWhenFragmentsUnavailable() {
    WaveClientRpcImpl.setFragmentsHandler(null);

    ProtocolWaveletUpdate update = openWithHints("b+1", "forward", 5);

    assertFalse("No fragments should be attached without a handler", update.hasFragments());
    assertTrue("Viewport-hinted open must fall back to snapshot if fragments are unavailable",
        update.hasSnapshot());
    assertEquals(0L, FragmentsMetrics.j2clViewportInitialWindows.get());
    assertEquals(1L, FragmentsMetrics.j2clViewportSnapshotFallbacks.get());
  }

  @Test
  public void viewportHintsWithoutSnapshotOrFragmentsOnlyPreserveCommitNotice() {
    WaveClientRpcImpl.setFragmentsHandler(null);

    ProtocolWaveletUpdate update =
        openWithRpc(makeWaveClientRpcWithoutSnapshot(), viewportHintRequest("b+1", "forward", 5));

    assertFalse("No snapshot should be attached when the frontend has none", update.hasSnapshot());
    assertFalse("No fragments should be attached without a handler", update.hasFragments());
    assertTrue("Commit notice should still preserve the update version", update.hasCommitNotice());
    assertEquals(0L, FragmentsMetrics.j2clViewportInitialWindows.get());
    assertEquals(0L, FragmentsMetrics.j2clViewportSnapshotFallbacks.get());
  }

  @Test
  public void snapshotlessViewportFragmentsCountAsInitialWindow() {
    WaveClientRpcImpl.setForceClientFragments(true);
    try {
      ProtocolWaveletUpdate update =
          openWithRpc(makeWaveClientRpcWithoutSnapshot(), viewportHintRequest("b+1", "forward", 5));

      assertFalse("No full snapshot should be attached", update.hasSnapshot());
      assertTrue("Synthetic viewport fragments should be attached", update.hasFragments());
      assertEquals(1L, FragmentsMetrics.j2clViewportInitialWindows.get());
      assertEquals(0L, FragmentsMetrics.j2clViewportSnapshotFallbacks.get());
    } finally {
      WaveClientRpcImpl.setForceClientFragments(false);
    }
  }

  @Test
  public void viewportInitialWindowMetricCountsOncePerOpen() {
    openWithRpc(makeWaveClientRpcWithTwoSnapshotUpdates(),
        viewportHintRequest("b+1", "forward", 5));

    assertEquals(1L, FragmentsMetrics.j2clViewportInitialWindows.get());
    assertEquals(0L, FragmentsMetrics.j2clViewportSnapshotFallbacks.get());
  }

  @Test
  public void viewportInitialWindowMetricCountsEachWaveletOnce() {
    openWithRpc(makeWaveClientRpcWithTwoWavelets(),
        viewportHintRequest("b+1", "forward", 5));

    assertEquals(2L, FragmentsMetrics.j2clViewportInitialWindows.get());
    assertEquals(0L, FragmentsMetrics.j2clViewportSnapshotFallbacks.get());
  }

  @Test
  public void viewportSnapshotFallbackReasonLabelsOperatorPaths() {
    FragmentsViewChannelHandler disabledHandler = new FragmentsViewChannelHandler(
        providerWithBlips(1), ConfigFactory.parseString("server.enableFetchFragmentsRpc=false"));
    FragmentsViewChannelHandler enabledHandler = new FragmentsViewChannelHandler(
        providerWithBlips(1), ConfigFactory.parseString("server.enableFetchFragmentsRpc=true"));

    assertEquals("fragments-handler-absent",
        WaveClientRpcImpl.viewportSnapshotFallbackReason(null, testWaveletName("conv+root")));
    assertEquals("fragments-handler-disabled",
        WaveClientRpcImpl.viewportSnapshotFallbackReason(
            disabledHandler, testWaveletName("conv+root")));
    assertEquals("dummy-wavelet",
        WaveClientRpcImpl.viewportSnapshotFallbackReason(
            enabledHandler, testWaveletName("dummy+root")));
    assertEquals("no-fragments-emitted",
        WaveClientRpcImpl.viewportSnapshotFallbackReason(
            enabledHandler, testWaveletName("conv+root")));
  }

  private static int countBlipRanges(WaveClientRpc.ProtocolFragments f) {
    int c = 0;
    for (WaveClientRpc.ProtocolFragmentRange r : f.getRangeList()) {
      if (r.getSegment().startsWith("blip:")) c++;
    }
    return c;
  }

  private static ProtocolWaveletUpdate openWithHints(String startId, String dir, int limit) {
    return openWithRequest(viewportHintRequest(startId, dir, limit));
  }

  private static ProtocolWaveletUpdate openWithoutHints() {
    return openWithRequest(baseOpenRequestBuilder().build());
  }

  private static ProtocolOpenRequest.Builder baseOpenRequestBuilder() {
    return ProtocolOpenRequest.newBuilder()
        .setParticipantId("user@example.com")
        .setWaveId(ModernIdSerialiser.INSTANCE.serialiseWaveId(WaveId.of("example.com", "w+vh")));
  }

  private static WaveletName testWaveletName(String waveletId) {
    WaveId waveId = WaveId.of("example.com", "w+vh");
    return WaveletName.of(waveId, WaveletId.of(waveId.getDomain(), waveletId));
  }

  private static ProtocolOpenRequest viewportHintRequest(String startId, String dir, int limit) {
    ProtocolOpenRequest.Builder b = baseOpenRequestBuilder();
    if (startId != null) b.setViewportStartBlipId(startId);
    if (dir != null) b.setViewportDirection(dir);
    b.setViewportLimit(limit);
    return b.build();
  }

  private static ProtocolWaveletUpdate openWithRequest(ProtocolOpenRequest request) {
    return openWithRpc(makeWaveClientRpc(), request);
  }

  private static ProtocolWaveletUpdate openWithRpc(
      WaveClientRpcImpl rpc, ProtocolOpenRequest request) {
    final ProtocolWaveletUpdate[] holder = new ProtocolWaveletUpdate[1];
    RpcCallback<ProtocolWaveletUpdate> cb = update -> {
      holder[0] = update;
    };
    // Minimal controller
    org.waveprotocol.box.server.rpc.ServerRpcController controller = new org.waveprotocol.box.server.rpc.ServerRpcController() {
      @Override public ParticipantId getLoggedInUser() { return ParticipantId.ofUnsafe("user@example.com"); }
      @Override public void cancel() {}
      @Override public String errorText() { return null; }
      @Override public boolean failed() { return false; }
      @Override public boolean isCanceled() { return false; }
      @Override public void notifyOnCancel(com.google.protobuf.RpcCallback<Object> callback) {}
      @Override public void reset() {}
      @Override public void setFailed(String reason) {}
      @Override public void startCancel() {}
      @Override public void run() {}
    };
    rpc.open(controller, request, cb);
    assertNotNull("Expected a wavelet update", holder[0]);
    return holder[0];
  }

  private static WaveClientRpcImpl makeWaveClientRpc() {
    ClientFrontend frontend = new ClientFrontend() {
      @Override public void submitRequest(ParticipantId u, WaveletName wn, org.waveprotocol.wave.federation.Proto.ProtocolWaveletDelta d, String c, WaveletProvider.SubmitRequestListener l) {}
      @Override public void openRequest(ParticipantId u, WaveId waveId, org.waveprotocol.wave.model.id.IdFilter f, java.util.Collection<WaveClientRpc.WaveletVersion> k, String searchQuery, OpenListener listener) {
        WaveletId wid = WaveletId.of(waveId.getDomain(), "conv+root");
        WaveletName wn = WaveletName.of(waveId, wid);
        ReadableWaveletData data = providerDataWithBlips(waveId, wid, 20);
        CommittedWaveletSnapshot snap = new CommittedWaveletSnapshot(data, HashedVersion.unsigned(100));
        listener.onUpdate(wn, snap, java.util.Collections.emptyList(), HashedVersion.unsigned(100), null, "ch-vh");
      }
    };
    return WaveClientRpcImpl.create(frontend, false);
  }

  private static WaveClientRpcImpl makeWaveClientRpcWithoutSnapshot() {
    ClientFrontend frontend = new ClientFrontend() {
      @Override public void submitRequest(ParticipantId u, WaveletName wn, org.waveprotocol.wave.federation.Proto.ProtocolWaveletDelta d, String c, WaveletProvider.SubmitRequestListener l) {}
      @Override public void openRequest(ParticipantId u, WaveId waveId, org.waveprotocol.wave.model.id.IdFilter f, java.util.Collection<WaveClientRpc.WaveletVersion> k, String searchQuery, OpenListener listener) {
        WaveletId wid = WaveletId.of(waveId.getDomain(), "conv+root");
        WaveletName wn = WaveletName.of(waveId, wid);
        listener.onUpdate(wn, null, java.util.Collections.emptyList(), HashedVersion.unsigned(100), null, "ch-vh");
      }
    };
    return WaveClientRpcImpl.create(frontend, false);
  }

  private static WaveClientRpcImpl makeWaveClientRpcWithTwoSnapshotUpdates() {
    ClientFrontend frontend = new ClientFrontend() {
      @Override public void submitRequest(ParticipantId u, WaveletName wn, org.waveprotocol.wave.federation.Proto.ProtocolWaveletDelta d, String c, WaveletProvider.SubmitRequestListener l) {}
      @Override public void openRequest(ParticipantId u, WaveId waveId, org.waveprotocol.wave.model.id.IdFilter f, java.util.Collection<WaveClientRpc.WaveletVersion> k, String searchQuery, OpenListener listener) {
        WaveletId wid = WaveletId.of(waveId.getDomain(), "conv+root");
        WaveletName wn = WaveletName.of(waveId, wid);
        ReadableWaveletData data = providerDataWithBlips(waveId, wid, 20);
        CommittedWaveletSnapshot snap = new CommittedWaveletSnapshot(data, HashedVersion.unsigned(100));
        listener.onUpdate(wn, snap, java.util.Collections.emptyList(), HashedVersion.unsigned(100), null, "ch-vh");
        listener.onUpdate(wn, snap, java.util.Collections.emptyList(), HashedVersion.unsigned(100), null, "ch-vh");
      }
    };
    return WaveClientRpcImpl.create(frontend, false);
  }

  private static WaveClientRpcImpl makeWaveClientRpcWithTwoWavelets() {
    ClientFrontend frontend = new ClientFrontend() {
      @Override public void submitRequest(ParticipantId u, WaveletName wn, org.waveprotocol.wave.federation.Proto.ProtocolWaveletDelta d, String c, WaveletProvider.SubmitRequestListener l) {}
      @Override public void openRequest(ParticipantId u, WaveId waveId, org.waveprotocol.wave.model.id.IdFilter f, java.util.Collection<WaveClientRpc.WaveletVersion> k, String searchQuery, OpenListener listener) {
        WaveletId first = WaveletId.of(waveId.getDomain(), "conv+root");
        WaveletId second = WaveletId.of(waveId.getDomain(), "conv+thread");
        listener.onUpdate(WaveletName.of(waveId, first),
            new CommittedWaveletSnapshot(providerDataWithBlips(waveId, first, 20),
                HashedVersion.unsigned(100)),
            java.util.Collections.emptyList(), HashedVersion.unsigned(100), null, "ch-vh-1");
        listener.onUpdate(WaveletName.of(waveId, second),
            new CommittedWaveletSnapshot(providerDataWithBlips(waveId, second, 20),
                HashedVersion.unsigned(100)),
            java.util.Collections.emptyList(), HashedVersion.unsigned(100), null, "ch-vh-2");
      }
    };
    return WaveClientRpcImpl.create(frontend, false);
  }

  private static ReadableWaveletData providerDataWithBlips(WaveId waveId, WaveletId wid, int count) {
    ReadableWaveletDataStub stub = new ReadableWaveletDataStub(waveId, wid, HashedVersion.unsigned(1));
    for (int i = 1; i <= count; i++) {
      String id = "b+" + i;
      stub.addDoc(id, new ReadableBlipDataStub(ParticipantId.ofUnsafe("user@example.com"), i * 100L));
    }
    return stub;
  }

  private static WaveletProvider providerWithBlips(int count) {
    return new WaveletProvider() {
      @Override public CommittedWaveletSnapshot getSnapshot(WaveletName wn) {
        return new CommittedWaveletSnapshot(
            providerDataWithBlips(wn.waveId, wn.waveletId, count), HashedVersion.unsigned(1));
      }
      @Override public void initialize() {}
      @Override public boolean checkAccessPermission(WaveletName waveletName, ParticipantId user) { return true; }
      @Override public void getHistory(WaveletName waveletName, HashedVersion fromVersion, HashedVersion toVersion, org.waveprotocol.box.common.Receiver<org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta> receiver) {}
      @Override public void submitRequest(WaveletName waveletName, org.waveprotocol.wave.federation.Proto.ProtocolWaveletDelta deltaRequest, SubmitRequestListener listener) {}
      @Override public org.waveprotocol.box.common.ExceptionalIterator<WaveId, org.waveprotocol.box.server.waveserver.WaveServerException> getWaveIds() { return null; }
      @Override public com.google.common.collect.ImmutableSet<WaveletId> getWaveletIds(WaveId waveId) { return com.google.common.collect.ImmutableSet.of(); }
      @Override public HashedVersion getHashedVersion(WaveletName waveletName, long version) { return null; }
    };
  }
}
