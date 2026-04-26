package org.waveprotocol.box.j2cl.search;

public final class J2clSidecarRouteState {
  private final String query;
  private final String selectedWaveId;
  private final String depthBlipId;

  public J2clSidecarRouteState(String query, String selectedWaveId) {
    this(query, selectedWaveId, null);
  }

  /**
   * F-2 slice 5 (#1055, R-3.7 G.4): include the depth-focus blip id in
   * the route state so it round-trips through reload + back/forward.
   */
  public J2clSidecarRouteState(String query, String selectedWaveId, String depthBlipId) {
    this.query = J2clSearchResultProjector.normalizeQuery(query);
    this.selectedWaveId =
        selectedWaveId == null || selectedWaveId.isEmpty() ? null : selectedWaveId;
    this.depthBlipId =
        depthBlipId == null || depthBlipId.isEmpty() ? null : depthBlipId;
  }

  public String getQuery() {
    return query;
  }

  public String getSelectedWaveId() {
    return selectedWaveId;
  }

  public String getDepthBlipId() {
    return depthBlipId;
  }

  /**
   * F-2 slice 5 (#1055): produce a copy of this state with the supplied
   * depth blip id (null/empty clears the depth focus). Existing query
   * and selected-wave fields are preserved.
   */
  public J2clSidecarRouteState withDepthBlipId(String nextDepthBlipId) {
    return new J2clSidecarRouteState(query, selectedWaveId, nextDepthBlipId);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof J2clSidecarRouteState)) {
      return false;
    }
    J2clSidecarRouteState that = (J2clSidecarRouteState) other;
    return safeEquals(query, that.query)
        && safeEquals(selectedWaveId, that.selectedWaveId)
        && safeEquals(depthBlipId, that.depthBlipId);
  }

  @Override
  public int hashCode() {
    int result = query == null ? 0 : query.hashCode();
    result = 31 * result + (selectedWaveId == null ? 0 : selectedWaveId.hashCode());
    result = 31 * result + (depthBlipId == null ? 0 : depthBlipId.hashCode());
    return result;
  }

  private static boolean safeEquals(String left, String right) {
    if (left == null) {
      return right == null;
    }
    return left.equals(right);
  }
}
