package org.waveprotocol.box.j2cl.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SidecarSearchResponse {
  public static final class Digest {
    private final String title;
    private final String snippet;
    private final String waveId;
    private final long lastModified;
    private final int unreadCount;
    private final int blipCount;
    private final List<String> participants;
    private final String author;
    private final boolean pinned;

    public Digest(
        String title,
        String snippet,
        String waveId,
        long lastModified,
        int unreadCount,
        int blipCount,
        List<String> participants,
        String author,
        boolean pinned) {
      this.title = title;
      this.snippet = snippet;
      this.waveId = waveId;
      this.lastModified = lastModified;
      this.unreadCount = unreadCount;
      this.blipCount = blipCount;
      this.participants =
          participants == null
              ? Collections.<String>emptyList()
              : Collections.unmodifiableList(new ArrayList<>(participants));
      this.author = author;
      this.pinned = pinned;
    }

    public String getTitle() {
      return title;
    }

    public String getSnippet() {
      return snippet;
    }

    public String getWaveId() {
      return waveId;
    }

    public long getLastModified() {
      return lastModified;
    }

    public int getUnreadCount() {
      return unreadCount;
    }

    public int getBlipCount() {
      return blipCount;
    }

    public List<String> getParticipants() {
      return participants;
    }

    public String getAuthor() {
      return author;
    }

    public boolean isPinned() {
      return pinned;
    }
  }

  private final String query;
  private final int totalResults;
  private final List<Digest> digests;

  public SidecarSearchResponse(String query, int totalResults, List<Digest> digests) {
    this.query = query;
    this.totalResults = totalResults;
    this.digests =
        digests == null
            ? Collections.<Digest>emptyList()
            : Collections.unmodifiableList(new ArrayList<>(digests));
  }

  public String getQuery() {
    return query;
  }

  public int getTotalResults() {
    return totalResults;
  }

  public List<Digest> getDigests() {
    return digests;
  }
}
