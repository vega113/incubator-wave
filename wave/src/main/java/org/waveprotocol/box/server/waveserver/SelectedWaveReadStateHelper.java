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

package org.waveprotocol.box.server.waveserver;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.wave.api.SearchResult.Digest;

import org.waveprotocol.box.server.CoreSettingsNames;
import org.waveprotocol.box.server.util.WaveletDataUtil;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.ParticipantIdUtil;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveViewData;
import org.waveprotocol.wave.util.logging.Log;

/**
 * Computes per-user unread/read state for a single wave on demand.
 *
 * <p>Reuses the existing search-side data path ({@link WaveMap},
 * {@link AbstractSearchProviderImpl#buildWaveViewData}, {@link WaveDigester#build})
 * so there is no second code path for supplement construction. This helper is
 * the server seam used by the J2CL sidecar's selected-wave read-state endpoint
 * (issue #931) and is intentionally narrow: it exposes a single
 * {@code computeReadState} method and leaks no internal supplement types.
 */
public class SelectedWaveReadStateHelper {

  private static final Log LOG = Log.get(SelectedWaveReadStateHelper.class);


  /**
   * Outcome of a read-state computation. Non-existence and access-denied
   * collapse into the same {@link #notFound()} sentinel so the servlet can
   * return a single 404 for both cases and existence cannot be probed.
   */
  public static final class Result {
    private final boolean exists;
    private final boolean accessAllowed;
    private final int unreadCount;
    private final boolean read;

    private Result(boolean exists, boolean accessAllowed, int unreadCount, boolean read) {
      this.exists = exists;
      this.accessAllowed = accessAllowed;
      this.unreadCount = unreadCount;
      this.read = read;
    }

    public boolean exists() { return exists; }
    public boolean accessAllowed() { return accessAllowed; }
    public int getUnreadCount() { return unreadCount; }
    public boolean isRead() { return read; }

    public static Result notFound() {
      return new Result(false, false, 0, true);
    }

    public static Result found(int unreadCount) {
      return new Result(true, true, unreadCount, unreadCount <= 0);
    }
  }

  private final WaveMap waveMap;
  private final WaveDigester digester;
  private final ParticipantId sharedDomainParticipantId;

  @Inject
  public SelectedWaveReadStateHelper(
      @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String waveDomain,
      WaveMap waveMap,
      WaveDigester digester) {
    this.waveMap = waveMap;
    this.digester = digester;
    this.sharedDomainParticipantId =
        ParticipantIdUtil.makeUnsafeSharedDomainParticipantId(waveDomain);
  }

  /**
   * Computes unread/read state for the given wave and user.
   *
   * <p>Returns {@link Result#notFound()} when the wave does not exist, when no
   * conversational wavelet is present, or when the user lacks read access on
   * any conversational wavelet. The distinction is intentional: collapsing
   * these into one response prevents existence probing by non-participants.
   */
  public Result computeReadState(ParticipantId user, WaveId waveId) {
    if (user == null || waveId == null) {
      return Result.notFound();
    }

    ImmutableSet<WaveletId> waveletIds;
    try {
      waveletIds = waveMap.lookupWavelets(waveId);
    } catch (WaveletStateException e) {
      LOG.warning("read-state: failed to look up wavelets for " + waveId, e);
      throw new RuntimeException("Failed to load read state for " + waveId, e);
    }
    if (waveletIds == null || waveletIds.isEmpty()) {
      return Result.notFound();
    }

    Function<ReadableWaveletData, Boolean> accessFilter =
        new Function<ReadableWaveletData, Boolean>() {
          @Override
          public Boolean apply(ReadableWaveletData wavelet) {
            if (IdUtil.isUserDataWavelet(user.getAddress(), wavelet.getWaveletId())) {
              return Boolean.TRUE;
            }
            return WaveletDataUtil.checkAccessPermission(wavelet, user, sharedDomainParticipantId);
          }
        };
    WaveViewData view =
        AbstractSearchProviderImpl.buildWaveViewData(waveId, waveletIds, accessFilter, waveMap);
    if (view == null || !hasAccessibleConversationalWavelet(view, user)) {
      return Result.notFound();
    }

    try {
      Digest digest = digester.build(user, view);
      return Result.found(Math.max(0, digest.getUnreadCount()));
    } catch (RuntimeException e) {
      LOG.warning("read-state: digest build failed for " + waveId, e);
      throw e;
    }
  }

  private boolean hasAccessibleConversationalWavelet(WaveViewData view, ParticipantId user) {
    for (ReadableWaveletData wavelet : view.getWavelets()) {
      if (!IdUtil.isConversationalId(wavelet.getWaveletId())) {
        continue;
      }
      if (WaveletDataUtil.checkAccessPermission(wavelet, user, sharedDomainParticipantId)) {
        return true;
      }
    }
    return false;
  }

}
