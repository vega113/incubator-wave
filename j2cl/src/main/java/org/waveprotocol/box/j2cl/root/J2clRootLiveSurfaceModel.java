package org.waveprotocol.box.j2cl.root;

import org.waveprotocol.box.j2cl.i18n.J2clI18n;
import org.waveprotocol.box.j2cl.search.J2clSidecarRouteState;
import org.waveprotocol.box.j2cl.search.J2clSidecarRouteCodec;

public final class J2clRootLiveSurfaceModel {
  // #1277: English fallbacks; the localized text lives in the Lit catalog under
  // these keys and is resolved through J2clI18n at each transition.
  private static final String ROUTE_READY_STATUS = "Workspace is ready.";
  private static final String SELECTED_WAVE_STATUS = "Selected wave is active.";
  private static final String STARTING_STATUS = "Loading workspace.";

  // Connection chip states understood by the compact wavy-header topbar
  // (`netstatus[data-state]`): online | connecting | offline.
  static final String CONNECTION_ONLINE = "online";
  static final String CONNECTION_CONNECTING = "connecting";
  static final String CONNECTION_OFFLINE = "offline";
  // Save chip states (`savestatus[data-state]`): saved | saving | unsaved.
  static final String SAVE_SAVED = "saved";
  static final String SAVE_SAVING = "saving";
  static final String SAVE_UNSAVED = "unsaved";

  private final String routeUrl;
  private final String query;
  private final String selectedWaveId;
  private final String statusText;
  private final String connectionState;
  private final String saveState;

  private J2clRootLiveSurfaceModel(
      String routeUrl,
      String query,
      String selectedWaveId,
      String statusText,
      String connectionState,
      String saveState) {
    this.routeUrl = nullToEmpty(routeUrl);
    this.query = nullToEmpty(query);
    this.selectedWaveId = emptyToNull(selectedWaveId);
    this.statusText = nullToEmpty(statusText);
    this.connectionState = normalizeConnectionState(connectionState);
    this.saveState = normalizeSaveState(saveState);
  }

  public static J2clRootLiveSurfaceModel starting() {
    return new J2clRootLiveSurfaceModel(
        "", "", null, startingStatus(), CONNECTION_ONLINE, SAVE_SAVED);
  }

  public J2clRootLiveSurfaceModel withRouteUrl(String nextRouteUrl) {
    String normalizedRouteUrl = nullToEmpty(nextRouteUrl);
    if (normalizedRouteUrl.isEmpty()) {
      return new J2clRootLiveSurfaceModel(
          "", "", null, startingStatus(), connectionState, saveState);
    }
    J2clSidecarRouteState routeState = parseRouteUrl(normalizedRouteUrl);
    return new J2clRootLiveSurfaceModel(
        normalizedRouteUrl,
        routeState.getQuery(),
        routeState.getSelectedWaveId(),
        statusFor(normalizedRouteUrl, routeState.getQuery(), routeState.getSelectedWaveId()),
        connectionState,
        saveState);
  }

  public J2clRootLiveSurfaceModel withRouteState(J2clSidecarRouteState routeState) {
    if (routeState == null) {
      return this;
    }
    String nextSelectedWaveId = emptyToNull(routeState.getSelectedWaveId());
    return new J2clRootLiveSurfaceModel(
        routeUrl,
        routeState.getQuery(),
        nextSelectedWaveId,
        statusFor(routeUrl, routeState.getQuery(), nextSelectedWaveId),
        connectionState,
        saveState);
  }

  public J2clRootLiveSurfaceModel withSelectedWaveId(String nextSelectedWaveId) {
    String normalizedSelectedWaveId = emptyToNull(nextSelectedWaveId);
    return new J2clRootLiveSurfaceModel(
        routeUrl,
        query,
        normalizedSelectedWaveId,
        statusFor(routeUrl, query, normalizedSelectedWaveId),
        connectionState,
        saveState);
  }

  /**
   * Returns a model carrying the given real transport connection state. Unknown
   * values fall back to {@code online}. All route/selection fields are preserved
   * so a route change while offline does not reset the chip.
   */
  public J2clRootLiveSurfaceModel withConnectionState(String nextConnectionState) {
    String normalized = normalizeConnectionState(nextConnectionState);
    if (normalized.equals(connectionState)) {
      return this;
    }
    return new J2clRootLiveSurfaceModel(
        routeUrl, query, selectedWaveId, statusText, normalized, saveState);
  }

  /**
   * Returns a model carrying the given real save state. Unknown values fall back
   * to {@code saved}. All other fields are preserved.
   */
  public J2clRootLiveSurfaceModel withSaveState(String nextSaveState) {
    String normalized = normalizeSaveState(nextSaveState);
    if (normalized.equals(saveState)) {
      return this;
    }
    return new J2clRootLiveSurfaceModel(
        routeUrl, query, selectedWaveId, statusText, connectionState, normalized);
  }

  public String getRouteUrl() {
    return routeUrl;
  }

  public String getQuery() {
    return query;
  }

  public String getSelectedWaveId() {
    return selectedWaveId;
  }

  public String getStatusText() {
    return statusText;
  }

  public String getRouteState() {
    if (selectedWaveId != null) {
      return "selected-wave";
    }
    if (!query.isEmpty()) {
      return "search";
    }
    if (routeUrl.isEmpty()) {
      return "loading";
    }
    return "ready";
  }

  public String getConnectionState() {
    return connectionState;
  }

  public String getSaveState() {
    return saveState;
  }

  static String normalizeConnectionState(String value) {
    if (CONNECTION_OFFLINE.equals(value)) {
      return CONNECTION_OFFLINE;
    }
    if (CONNECTION_CONNECTING.equals(value)) {
      return CONNECTION_CONNECTING;
    }
    return CONNECTION_ONLINE;
  }

  static String normalizeSaveState(String value) {
    if (SAVE_SAVING.equals(value)) {
      return SAVE_SAVING;
    }
    if (SAVE_UNSAVED.equals(value)) {
      return SAVE_UNSAVED;
    }
    return SAVE_SAVED;
  }

  private static String startingStatus() {
    return J2clI18n.t("rootStatus.loading", STARTING_STATUS);
  }

  private static String routeStatus(String routeUrl, String query) {
    String normalizedQuery = nullToEmpty(query);
    if (!normalizedQuery.isEmpty()) {
      return J2clI18n.format(
          "rootStatus.searchResults",
          "Showing search results for {query}.",
          "{query}",
          normalizedQuery);
    }
    String normalizedRouteUrl = nullToEmpty(routeUrl);
    if (normalizedRouteUrl.isEmpty()) {
      return startingStatus();
    }
    return J2clI18n.t("rootStatus.ready", ROUTE_READY_STATUS);
  }

  private static String selectedWaveStatus() {
    return J2clI18n.t("rootStatus.selectedWave", SELECTED_WAVE_STATUS);
  }

  private static String statusFor(String routeUrl, String query, String selectedWaveId) {
    return selectedWaveId == null ? routeStatus(routeUrl, query) : selectedWaveStatus();
  }

  private static J2clSidecarRouteState parseRouteUrl(String routeUrl) {
    String search = routeUrl;
    String hash = "";
    int queryStart = search.indexOf('?');
    if (queryStart >= 0) {
      search = search.substring(queryStart);
    }
    int hashStart = search.indexOf('#');
    if (hashStart >= 0) {
      hash = search.substring(hashStart);
      search = search.substring(0, hashStart);
    }
    return J2clSidecarRouteCodec.parse(search, hash);
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static String emptyToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }
}
