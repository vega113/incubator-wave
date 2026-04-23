package org.waveprotocol.box.j2cl.search;

import elemental2.dom.DomGlobal;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveReadState;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveUpdate;
import org.waveprotocol.box.j2cl.transport.SidecarSessionBootstrap;

public final class J2clSelectedWaveController
    implements J2clSidecarRouteController.SelectedWaveController {
  private static final int INITIAL_RECONNECT_DELAY_MS = 250;
  // Keep retries bounded, but leave enough budget for a local WIAB restart on the same port.
  private static final int MAX_RECONNECT_DELAY_MS = 2000;
  private static final int MAX_RECONNECT_ATTEMPTS = 8;
  // Trailing-edge debounce for per-update read-state fetches. Short enough to
  // feel live, long enough to coalesce server-initiated flurries without
  // amplifying HTTP pressure.
  private static final int READ_STATE_DEBOUNCE_MS = 250;

  public interface Gateway {
    void fetchRootSessionBootstrap(
        J2clSearchPanelController.SuccessCallback<SidecarSessionBootstrap> onSuccess,
        J2clSearchPanelController.ErrorCallback onError);

    Subscription openSelectedWave(
        SidecarSessionBootstrap bootstrap,
        String waveId,
        J2clSearchPanelController.SuccessCallback<SidecarSelectedWaveUpdate> onUpdate,
        J2clSearchPanelController.ErrorCallback onError,
        Runnable onDisconnect);

    /**
     * Fetches the authenticated user's unread/read state for the given wave.
     * Errors must not terminate the selected-wave subscription — the caller
     * preserves the prior read state and flips a soft "stale" flag instead.
     */
    void fetchSelectedWaveReadState(
        String waveId,
        J2clSearchPanelController.SuccessCallback<SidecarSelectedWaveReadState> onSuccess,
        J2clSearchPanelController.ErrorCallback onError);
  }

  public interface View {
    void render(J2clSelectedWaveModel model);
  }

  public interface RetryScheduler {
    void scheduleRetry(int delayMs, Runnable action);
  }

  /**
   * Dedicated scheduler for the read-state fetch debounce — kept separate from
   * {@link RetryScheduler} so reconnect tests can observe the reconnect delay
   * sequence without being polluted by the per-update debounce timer.
   */
  public interface ReadStateFetchScheduler {
    void scheduleFetch(int delayMs, Runnable action);
  }

  public interface Subscription {
    void close();
  }

  /** Registers a callback invoked when the browser tab returns to visible. */
  public interface VisibilitySource {
    void addVisibilityListener(Runnable onVisible);
  }

  @FunctionalInterface
  public interface WriteSessionListener {
    void onWriteSessionChanged(J2clSidecarWriteSession writeSession);
  }

  private final Gateway gateway;
  private final View view;
  private final RetryScheduler retryScheduler;
  private final ReadStateFetchScheduler readStateFetchScheduler;
  private final WriteSessionListener writeSessionListener;
  private Subscription currentSubscription;
  private SidecarSessionBootstrap currentBootstrap;
  private SidecarSelectedWaveUpdate lastUpdate;
  private String selectedWaveId;
  private J2clSearchDigestItem selectedDigestItem;
  private J2clSelectedWaveModel currentModel;
  private int reconnectCount;
  private int requestGeneration;
  private SidecarSelectedWaveReadState currentReadState;
  private boolean readStateStale;
  private int readStateFetchSeq;
  private int latestReadStateApplied;
  private int pendingDebounceToken;

  public J2clSelectedWaveController(Gateway gateway, View view) {
    this(
        gateway,
        view,
        defaultRetryScheduler(),
        defaultReadStateFetchScheduler(),
        null,
        defaultVisibilitySource());
  }

  public J2clSelectedWaveController(Gateway gateway, View view, RetryScheduler retryScheduler) {
    this(
        gateway,
        view,
        retryScheduler,
        defaultReadStateFetchScheduler(),
        null,
        defaultVisibilitySource());
  }

  /** Test-friendly constructor: explicit reconnect + read-state schedulers. */
  public J2clSelectedWaveController(
      Gateway gateway,
      View view,
      RetryScheduler retryScheduler,
      ReadStateFetchScheduler readStateFetchScheduler) {
    this(
        gateway,
        view,
        retryScheduler,
        readStateFetchScheduler,
        null,
        null);
  }

  public J2clSelectedWaveController(
      Gateway gateway, View view, WriteSessionListener writeSessionListener) {
    this(
        gateway,
        view,
        defaultRetryScheduler(),
        defaultReadStateFetchScheduler(),
        writeSessionListener,
        defaultVisibilitySource());
  }

  public J2clSelectedWaveController(
      Gateway gateway,
      View view,
      RetryScheduler retryScheduler,
      WriteSessionListener writeSessionListener) {
    this(
        gateway,
        view,
        retryScheduler,
        defaultReadStateFetchScheduler(),
        writeSessionListener,
        defaultVisibilitySource());
  }

  public J2clSelectedWaveController(
      Gateway gateway,
      View view,
      RetryScheduler retryScheduler,
      ReadStateFetchScheduler readStateFetchScheduler,
      WriteSessionListener writeSessionListener,
      VisibilitySource visibilitySource) {
    this.gateway = gateway;
    this.view = view;
    this.retryScheduler = retryScheduler;
    this.readStateFetchScheduler = readStateFetchScheduler;
    this.writeSessionListener = writeSessionListener;
    this.currentModel = J2clSelectedWaveModel.empty();
    this.view.render(currentModel);
    publishWriteSession();
    if (visibilitySource != null) {
      visibilitySource.addVisibilityListener(this::onVisible);
    }
  }

  public void onWaveSelected(String waveId) {
    onWaveSelected(waveId, null);
  }

  public void refreshSelectedWave() {
    if (selectedWaveId == null || selectedWaveId.isEmpty()) {
      return;
    }
    int generation = ++requestGeneration;
    closeSubscription();
    reconnectCount = 0;
    // A refresh happens after the reply already committed on the server, so transient bootstrap or
    // open failures should recover like a reconnect instead of strand the panel on stale content.
    fetchBootstrapAndOpenSelectedWave(generation, 0, true);
  }

  @Override
  public void onWaveSelected(String waveId, J2clSearchDigestItem digestItem) {
    if (waveId != null
        && waveId.equals(selectedWaveId)
        && currentSubscription != null
        && requestGeneration > 0) {
      selectedDigestItem = digestItem;
      if (lastUpdate != null) {
        currentModel =
            J2clSelectedWaveProjector.project(
                selectedWaveId,
                selectedDigestItem,
                lastUpdate,
                currentModel,
                reconnectCount,
                currentReadState,
                readStateStale);
        view.render(currentModel);
        publishWriteSession();
      }
      return;
    }

    int generation = ++requestGeneration;
    closeSubscription();
    resetReadStateFetchTracking();

    if (waveId == null || waveId.isEmpty()) {
      selectedWaveId = null;
      selectedDigestItem = null;
      currentBootstrap = null;
      lastUpdate = null;
      reconnectCount = 0;
      currentReadState = null;
      readStateStale = false;
      currentModel = J2clSelectedWaveModel.empty();
      view.render(currentModel);
      publishWriteSession();
      return;
    }

    selectedWaveId = waveId;
    selectedDigestItem = digestItem;
    lastUpdate = null;
    reconnectCount = 0;
    currentReadState = null;
    readStateStale = false;
    fetchBootstrapAndOpenSelectedWave(generation, 0, false);
  }

  private void fetchBootstrapAndOpenSelectedWave(
      int generation, int reconnectCount, boolean retryOnFailure) {
    if (selectedWaveId == null) {
      return;
    }
    this.reconnectCount = reconnectCount;
    currentModel =
        J2clSelectedWaveModel.loading(selectedWaveId, selectedDigestItem, reconnectCount, currentModel);
    view.render(currentModel);
    publishWriteSession();
    gateway.fetchRootSessionBootstrap(
        bootstrap -> {
          if (!isCurrentGeneration(generation)) {
            return;
          }
          currentBootstrap = bootstrap;
          openSelectedWave(generation, reconnectCount, retryOnFailure);
        },
        error -> {
          if (!isCurrentGeneration(generation)) {
            return;
          }
          clearActiveSubscription();
          currentBootstrap = null;
          if (retryOnFailure) {
            scheduleReconnectOrFail(generation, reconnectCount);
            return;
          }
          currentModel =
              J2clSelectedWaveModel.error(
                  selectedWaveId,
                  selectedDigestItem,
                  "Unable to open selected wave.",
                  error,
                  currentModel);
          view.render(currentModel);
          publishWriteSession();
        });
  }

  private void openSelectedWave(int generation, int reconnectCount, boolean retryOnFailure) {
    if (selectedWaveId == null || currentBootstrap == null) {
      return;
    }
    final boolean[] terminalStateHandled = new boolean[] {false};
    // Mutable so successful updates reset the budget, keeping MAX_RECONNECT_ATTEMPTS per outage.
    final int[] activeReconnectCount = {reconnectCount};
    currentSubscription =
        gateway.openSelectedWave(
            currentBootstrap,
            selectedWaveId,
            update -> {
              if (!isCurrentGeneration(generation) || isChannelEstablishmentUpdate(update)) {
                return;
              }
              int projectedReconnectCount = activeReconnectCount[0];
              lastUpdate = update;
              currentModel =
                  J2clSelectedWaveProjector.project(
                      selectedWaveId,
                      selectedDigestItem,
                      update,
                      currentModel,
                      projectedReconnectCount,
                      currentReadState,
                      readStateStale);
              view.render(currentModel);
              publishWriteSession();
              activeReconnectCount[0] = 0;
              this.reconnectCount = projectedReconnectCount;
              scheduleReadStateFetch(generation);
            },
            error -> {
              if (!isCurrentGeneration(generation)) {
                return;
              }
              if (terminalStateHandled[0]) {
                return;
              }
              terminalStateHandled[0] = true;
              closeSubscription();
              if (retryOnFailure || lastUpdate != null) {
                scheduleReconnectOrFail(generation, activeReconnectCount[0]);
                return;
              }
              currentModel =
                  J2clSelectedWaveModel.error(
                      selectedWaveId,
                      selectedDigestItem,
                      "Selected wave stream failed.",
                      error,
                      currentModel);
              view.render(currentModel);
              publishWriteSession();
            },
            () -> {
              if (!isCurrentGeneration(generation) || selectedWaveId == null) {
                return;
              }
              if (terminalStateHandled[0]) {
                return;
              }
              terminalStateHandled[0] = true;
              clearActiveSubscription();
              scheduleReconnectOrFail(generation, activeReconnectCount[0]);
            });
  }

  private void scheduleReconnectOrFail(int generation, int reconnectCount) {
    if (reconnectCount >= MAX_RECONNECT_ATTEMPTS) {
      currentModel =
          J2clSelectedWaveModel.error(
              selectedWaveId,
              selectedDigestItem,
              "Selected wave disconnected.",
              "The selected-wave sidecar stopped retrying after "
                  + MAX_RECONNECT_ATTEMPTS
                  + " reconnect attempts.",
              currentModel);
      view.render(currentModel);
      publishWriteSession();
      return;
    }
    int nextReconnectCount = reconnectCount + 1;
    retryScheduler.scheduleRetry(
        buildReconnectDelayMs(reconnectCount),
        () -> {
          if (!isCurrentGeneration(generation) || selectedWaveId == null) {
            return;
          }
          fetchBootstrapAndOpenSelectedWave(generation, nextReconnectCount, true);
        });
  }

  private boolean isCurrentGeneration(int generation) {
    return generation == requestGeneration;
  }

  static boolean isChannelEstablishmentUpdate(SidecarSelectedWaveUpdate update) {
    String waveletName = update.getWaveletName();
    // The socket open handshake reuses ProtocolWaveletUpdate to deliver the initial channel id
    // before any real wavelet data is streamed. Those synthetic frames target ~/dummy+root and
    // must not overwrite the selected-wave panel or they would flash a fake wavelet into view.
    return waveletName != null && waveletName.endsWith("/~/dummy+root");
  }

  private void closeSubscription() {
    if (currentSubscription != null) {
      currentSubscription.close();
      currentSubscription = null;
    }
  }

  private void clearActiveSubscription() {
    currentSubscription = null;
  }

  private static int buildReconnectDelayMs(int reconnectCount) {
    int delayMs = INITIAL_RECONNECT_DELAY_MS << reconnectCount;
    return Math.min(delayMs, MAX_RECONNECT_DELAY_MS);
  }

  private void publishWriteSession() {
    if (writeSessionListener != null) {
      writeSessionListener.onWriteSessionChanged(currentModel.getWriteSession());
    }
  }

  // --- Read-state fetch orchestration ----------------------------------------

  private void scheduleReadStateFetch(int generation) {
    final int scheduledGeneration = generation;
    final int scheduledToken = ++pendingDebounceToken;
    readStateFetchScheduler.scheduleFetch(
        READ_STATE_DEBOUNCE_MS,
        () -> {
          if (scheduledToken != pendingDebounceToken) {
            return;
          }
          if (!isCurrentGeneration(scheduledGeneration)) {
            return;
          }
          dispatchReadStateFetch();
        });
  }

  private void cancelPendingReadStateDebounce() {
    // Bumping the token is enough: any pending callback checks the token and
    // no-ops, which keeps the scheduling seam identical for both the reconnect
    // and read-state fetch paths.
    pendingDebounceToken++;
  }

  private void dispatchReadStateFetch() {
    if (selectedWaveId == null || selectedWaveId.isEmpty()) {
      return;
    }
    final int thisFetchSeq = ++readStateFetchSeq;
    final int dispatchGeneration = requestGeneration;
    gateway.fetchSelectedWaveReadState(
        selectedWaveId,
        readState -> {
          if (thisFetchSeq <= latestReadStateApplied) {
            return;
          }
          if (!isCurrentGeneration(dispatchGeneration)) {
            return;
          }
          latestReadStateApplied = thisFetchSeq;
          currentReadState = readState;
          readStateStale = false;
          applyReadStateToModel();
        },
        error -> {
          if (thisFetchSeq <= latestReadStateApplied) {
            return;
          }
          if (!isCurrentGeneration(dispatchGeneration)) {
            return;
          }
          latestReadStateApplied = thisFetchSeq;
          // Preserve the prior read-state value; only raise the "stale" flag so
          // the panel keeps displaying the last known count rather than flapping
          // back to the digest fallback on a transient endpoint failure.
          if (currentReadState != null) {
            readStateStale = true;
            applyReadStateToModel();
          }
        });
  }

  private void applyReadStateToModel() {
    currentModel =
        J2clSelectedWaveProjector.reprojectReadState(
            currentModel, selectedDigestItem, currentReadState, readStateStale);
    view.render(currentModel);
    publishWriteSession();
  }

  private void resetReadStateFetchTracking() {
    cancelPendingReadStateDebounce();
    // Bump seq so any in-flight response is ignored.
    latestReadStateApplied = ++readStateFetchSeq;
  }

  private void onVisible() {
    if (selectedWaveId == null || selectedWaveId.isEmpty()) {
      return;
    }
    // Closes the UDW-only-change gap: reading the wave in a second tab bumps the
    // UDW but never emits a conv+root update for this socket, so the per-update
    // refetch would never fire. Visibility-driven refresh catches it cheaply.
    scheduleReadStateFetch(requestGeneration);
  }

  private static RetryScheduler defaultRetryScheduler() {
    return (delayMs, action) -> DomGlobal.setTimeout(ignored -> action.run(), delayMs);
  }

  private static ReadStateFetchScheduler defaultReadStateFetchScheduler() {
    return (delayMs, action) -> DomGlobal.setTimeout(ignored -> action.run(), delayMs);
  }

  private static VisibilitySource defaultVisibilitySource() {
    return onVisible ->
        DomGlobal.document.addEventListener(
            "visibilitychange",
            event -> {
              if (!"hidden".equals(DomGlobal.document.visibilityState)) {
                onVisible.run();
              }
            });
  }
}
