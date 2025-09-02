package org.waveprotocol.box.server.frontend;

import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.model.wave.data.BlipData;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compat fetcher that lists blip ids + metadata using current snapshot. */
public final class FragmentsFetcherCompat {

  public static final class BlipMeta {
    public final ParticipantId author; public final long lastModifiedTime;
    public BlipMeta(ParticipantId a, long t) { this.author = a; this.lastModifiedTime = t; }
  }

  /** Returns a map blipId -> meta for the given wavelet's current snapshot. */
  public static Map<String, BlipMeta> listBlips(WaveletProvider provider, WaveletName wn) throws WaveServerException {
    org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot snap = provider.getSnapshot(wn);
    ReadableWaveletData data = (snap != null) ? snap.snapshot : null;
    if (data == null) return Collections.emptyMap();
    Set<String> ids = data.getDocumentIds();
    Map<String, BlipMeta> out = new LinkedHashMap<>();
    for (String id : ids) {
      if (id != null && id.startsWith("b+")) {
        org.waveprotocol.wave.model.wave.data.ReadableBlipData b = data.getDocument(id);
        if (b != null) out.put(id, new BlipMeta(b.getAuthor(), b.getLastModifiedTime()));
      }
    }
    return out;
  }

  /** Slice around start id in given direction; if start is null, take from beginning. */
  public static List<String> slice(Map<String, BlipMeta> metas, String startId, String direction, int limit) {
    if (metas.isEmpty() || limit <= 0) return Collections.emptyList();
    List<String> keys = new ArrayList<>(metas.keySet());
    // Stable order as returned by underlying set (implementation dependent); consider improving via manifest order later.
    int idx = 0;
    if (startId != null && metas.containsKey(startId)) idx = keys.indexOf(startId);
    List<String> out = new ArrayList<>(limit);
    if ("backward".equalsIgnoreCase(direction)) {
      for (int i = Math.max(0, idx - limit + 1); i <= idx && out.size() < limit; i++) out.add(keys.get(i));
    } else {
      for (int i = idx; i < keys.size() && out.size() < limit; i++) out.add(keys.get(i));
    }
    return out;
  }
}
