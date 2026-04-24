package org.waveprotocol.box.j2cl.overlay;

public final class J2clMentionCandidate {
  private final String address;
  private final String displayName;
  private final String avatarToken;
  private final String sortKey;
  private final boolean currentUser;

  public J2clMentionCandidate(
      String address,
      String displayName,
      String avatarToken,
      String sortKey,
      boolean currentUser) {
    this.address = address == null ? "" : address;
    this.displayName = displayName == null || displayName.isEmpty() ? this.address : displayName;
    this.avatarToken = avatarToken == null ? "" : avatarToken;
    this.sortKey = sortKey == null || sortKey.isEmpty() ? this.displayName : sortKey;
    this.currentUser = currentUser;
  }

  public String getAddress() {
    return address;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getAvatarToken() {
    return avatarToken;
  }

  public String getSortKey() {
    return sortKey;
  }

  public boolean isCurrentUser() {
    return currentUser;
  }
}
