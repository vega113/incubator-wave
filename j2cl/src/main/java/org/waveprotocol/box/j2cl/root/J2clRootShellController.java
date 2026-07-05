package org.waveprotocol.box.j2cl.root;

import elemental2.core.JsArray;
import elemental2.dom.DomGlobal;
import elemental2.dom.Event;
import elemental2.dom.HTMLElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import jsinterop.base.Js;
import org.waveprotocol.box.j2cl.compose.J2clComposeSurfaceController;
import org.waveprotocol.box.j2cl.compose.J2clComposeSurfaceController.CreateSuccessHandler;
import org.waveprotocol.box.j2cl.compose.J2clComposeSurfaceController.ReplySuccessHandler;
import org.waveprotocol.box.j2cl.compose.J2clComposeSurfaceView;
import org.waveprotocol.box.j2cl.common.J2clUiTokens;
import org.waveprotocol.box.j2cl.notify.J2clDomNotificationService;
import org.waveprotocol.box.j2cl.notify.J2clNotificationService;
import org.waveprotocol.box.j2cl.search.J2clSearchGateway;
import org.waveprotocol.box.j2cl.search.J2clSearchPanelController;
import org.waveprotocol.box.j2cl.search.J2clSearchPanelView;
import org.waveprotocol.box.j2cl.search.J2clSidecarRouteController;
import org.waveprotocol.box.j2cl.search.J2clSelectedWaveController;
import org.waveprotocol.box.j2cl.search.J2clSelectedWaveView;
import org.waveprotocol.box.j2cl.search.J2clSidecarWriteSession;
import org.waveprotocol.box.j2cl.search.J2clSidecarRouteState;
import org.waveprotocol.box.j2cl.telemetry.J2clClientTelemetry;
import org.waveprotocol.box.j2cl.toolbar.J2clToolbarSurfaceController;
import org.waveprotocol.box.j2cl.toolbar.J2clToolbarSurfaceView;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveDocument;

public final class J2clRootShellController implements org.waveprotocol.box.j2cl.common.Disposable {
  private final HTMLElement host;
  private boolean started;
  // #1268: retained for teardown so a pagehide / destroy() releases the
  // selected-wave view's listeners (which cascade to the read surface).
  private J2clSelectedWaveView selectedWaveView;
  // #1268: this shell's own global body/window listeners, released on destroy().
  private final org.waveprotocol.box.j2cl.common.J2clDisposeRegistry disposeRegistry =
      new org.waveprotocol.box.j2cl.common.J2clDisposeRegistry();

  public J2clRootShellController(HTMLElement host) {
    this.host = host;
  }

  public void start() {
    if (started) {
      return;
    }
    started = true;
    if (isReadSurfacePreviewHost(host)) {
      startReadSurfacePreviewMode();
      return;
    }
    J2clRootShellView shellView = new J2clRootShellView(host);
    J2clSearchGateway gateway = new J2clSearchGateway();
    J2clClientTelemetry.Sink telemetrySink = J2clClientTelemetry.browserStatsSink();
    final J2clSidecarRouteController[] routeControllerRef = new J2clSidecarRouteController[1];
    final J2clSelectedWaveController[] selectedWaveControllerRef =
        new J2clSelectedWaveController[1];
    final J2clToolbarSurfaceController[] toolbarControllerRef =
        new J2clToolbarSurfaceController[1];
    final J2clSelectedWaveView[] selectedWaveViewRef = new J2clSelectedWaveView[1];
    // The route controller is wired below; the starter runs only after that assignment.
    // F-2 slice 5 (#1055, R-3.7 G.4): the starter ALSO re-hydrates the
    // depth-nav-bar from the URL state so a deep-linked &depth=<blip-id>
    // survives reload.
    J2clRootLiveSurfaceController liveSurfaceController =
        new J2clRootLiveSurfaceController(
            shellView,
            () -> {
              routeControllerRef[0].start();
              rehydrateDepthFromRoute(
                  selectedWaveViewRef[0], routeControllerRef[0]);
            });
    J2clSearchPanelView searchView =
        new J2clSearchPanelView(
            shellView.getWorkflowHost(), J2clSearchPanelView.ShellPresentation.ROOT_SHELL);
    J2clSelectedWaveView selectedWaveView =
        new J2clSelectedWaveView(searchView.getSelectedWaveHost(), telemetrySink);
    selectedWaveViewRef[0] = selectedWaveView;
    this.selectedWaveView = selectedWaveView;
    HTMLElement selectedWaveComposeHost = selectedWaveView.getComposeHost();
    HTMLElement selectedCreateHost =
        createSiblingHostBefore(selectedWaveComposeHost, J2clUiTokens.CSS_CLASS_ROOT_CREATE_HOST);
    HTMLElement selectedToolbarHost =
        createChildHost(selectedWaveComposeHost, J2clUiTokens.CSS_CLASS_ROOT_TOOLBAR_HOST);
    HTMLElement selectedReplyHost =
        createChildHost(selectedWaveComposeHost, J2clUiTokens.CSS_CLASS_ROOT_REPLY_HOST);
    boolean inlineRichComposerEnabled = isInlineRichComposerEnabled(host);
    String rootShellSessionSeed = buildRootShellSessionSeed();
    final J2clSearchPanelController[] searchControllerRef = new J2clSearchPanelController[1];
    // J-UI-3 (#1081, R-5.1): on a successful create, prepend an optimistic
    // digest in the rail (so the new wave is visible without waiting for
    // the search index to catch up) THEN route to the new wave so it opens
    // in the right region. The legacy single-arg overload still routes for
    // back-compat with callers that have not adopted the title path.
    CreateSuccessHandler createSuccessHandler =
        new CreateSuccessHandler() {
          @Override
          public void onWaveCreated(String waveId) {
            onWaveCreated(waveId, "");
          }

          @Override
          public void onWaveCreated(String waveId, String title) {
            if (searchControllerRef[0] != null) {
              searchControllerRef[0].onOptimisticDigest(waveId, title);
            }
            routeControllerRef[0].selectWave(waveId);
          }
        };
    // #1233: route every compose write through a save-state-tracking decorator
    // so the topbar savestatus chip reflects real in-flight/acknowledged ops
    // instead of a hardcoded "saved".
    J2clComposeSurfaceController.Gateway composeGateway =
        new J2clRootSaveStateGateway(gateway, liveSurfaceController::onSaveState);
    J2clComposeSurfaceController composeController =
        new J2clComposeSurfaceController(
            composeGateway,
            new J2clComposeSurfaceView(selectedCreateHost, selectedReplyHost),
            J2clComposeSurfaceController.richContentDeltaFactory(rootShellSessionSeed),
            J2clComposeSurfaceController.attachmentControllerFactory(rootShellSessionSeed, telemetrySink),
            createSuccessHandler,
            new ReplySuccessHandler() {
              @Override
              public void onReplySubmitted(String waveId) {
                onReplySubmitted(waveId, -1L);
              }

              @Override
              public void onReplySubmitted(String waveId, long resultingVersion) {
                onReplySubmitted(waveId, resultingVersion, "");
              }

              @Override
              public void onReplySubmitted(
                  String waveId, long resultingVersion, String submittedBlipId) {
                if (selectedWaveControllerRef[0] != null) {
                  selectedWaveControllerRef[0].onReplySubmitted(
                      waveId, resultingVersion, submittedBlipId);
                }
              }
            },
            telemetrySink);
    J2clToolbarSurfaceController toolbarController =
        new J2clToolbarSurfaceController(
            new J2clToolbarSurfaceView(selectedToolbarHost),
            action -> {
              if (!composeController.onToolbarAction(action)) {
                toolbarControllerRef[0].onActionUnavailable(
                    action, "This toolbar action is not wired in the J2CL root shell yet.");
              }
            });
    // F-2 slice 5 (#1055, A.3): the wavy <wavy-wave-nav-row> already
    // mounts the canonical view-action chrome (E.1–E.10), so disable the
    // legacy view actions here. Edit actions still render when a
    // composer is active.
    toolbarController.setViewActionsEnabled(false);
    toolbarControllerRef[0] = toolbarController;
    J2clSelectedWaveController selectedWaveController =
        new J2clSelectedWaveController(
            gateway,
            selectedWaveView,
            (selectedWaveId, writeSession, participantIds) -> {
              composeController.onSelectedWaveComposeContextChanged(
                  selectedWaveId, writeSession, participantIds);
              toolbarController.onWriteSessionChanged(writeSession);
              toolbarController.onEditStateChanged(
                  editStateForWriteSession(writeSession, inlineRichComposerEnabled));
            },
            telemetrySink);
    selectedWaveControllerRef[0] = selectedWaveController;
    // #1233: feed real selected-wave socket connection transitions into the
    // root live-surface so the netstatus chip tracks online/connecting/offline.
    selectedWaveController.setConnectionStateListener(liveSurfaceController::onConnectionState);
    J2clSearchPanelController controller =
        new J2clSearchPanelController(
            gateway,
            searchView,
            liveSurfaceController.routeStateHandler(
                (state, digestItem, userNavigation) ->
                    routeControllerRef[0].onRouteStateChanged(state, digestItem, userNavigation)),
            resolveViewportWidth());
    searchControllerRef[0] = controller;
    // J-UI-3 (#1081, R-5.1) — codex P2 PRRT_kwDOBwxLXs5-CyWx: stamp the
    // active search query onto the next pending optimistic stub at the
    // moment the user clicks submit, so a query change between submit
    // and server-response cannot leak the stub into an unrelated rail.
    composeController.setPreCreateSubmitHook(controller::markCreateSubmitted);
    // J-UI-3 (#1081, R-5.1) — codex P2 PRRT_kwDOBwxLXs5-DA7T: pair the
    // pre-submit stamp with a failure-time drop so a failed create does
    // not leave a stale submit-query stamp that scopes the next
    // successful create's stub to the wrong rail.
    composeController.setCreateFailureHook(controller::discardOldestSubmitStamp);
    // #1271: mount the shared notification service (toast + ARIA live region)
    // under the shell host and route compose failures through it.
    J2clNotificationService notificationService = new J2clDomNotificationService(host);
    composeController.setNotificationService(notificationService);
    // J-UI-3 (#1081, R-5.1): the rail's New Wave button focuses the create
    // form's title input. Listening on document.body so the event bubbles
    // up regardless of where the rail is currently mounted.
    disposeRegistry.addListener(elemental2.dom.DomGlobal.document.body, 
        J2clUiTokens.EVENT_NEW_WAVE_REQUESTED,
        evt -> composeController.focusCreateSurface(newWaveTriggerFromEvent(evt)));
    disposeRegistry.addListener(elemental2.dom.DomGlobal.document.body, 
        J2clUiTokens.EVENT_NEW_WITH_PARTICIPANTS,
        evt -> composeController.onCreateRequestedWithParticipants(participantsFromEvent(evt)));
    disposeRegistry.addListener(elemental2.dom.DomGlobal.document.body, 
        J2clUiTokens.EVENT_ADD_PARTICIPANT,
        evt ->
            composeController.onAddParticipantsRequested(
                sourceWaveIdFromEvent(evt), addParticipantAddressesFromEvent(evt)));
    disposeRegistry.addListener(elemental2.dom.DomGlobal.document.body, 
        J2clUiTokens.EVENT_PUBLICITY_TOGGLE,
        evt ->
            composeController.onPublicityToggleRequested(
                sourceWaveIdFromEvent(evt), nextPublicFromEvent(evt)));
    disposeRegistry.addListener(elemental2.dom.DomGlobal.document.body, 
        J2clUiTokens.EVENT_ROOT_LOCK_TOGGLE,
        evt ->
            composeController.onLockStateToggleRequested(
                sourceWaveIdFromEvent(evt),
                lockStateFromEvent(evt, "currentLockState"),
                lockStateFromEvent(evt, "nextLockState")));
    // F-4 (#1039 / R-4.4): bridge the selected-wave controller's live read
    // state into the search panel so the matching digest's unread badge
    // decrements without re-rendering the whole list.
    selectedWaveController.setReadStateListener(controller::onReadStateChanged);
    J2clSidecarRouteController routeController =
        new J2clSidecarRouteController(
            new J2clSidecarRouteController.BrowserHistoryAdapter(),
            controller,
            liveSurfaceController.selectedWaveController(
                (waveId, digestItem) -> {
                  toolbarController.onSelectedWaveStateChanged(
                      // TODO(#971): publish real archive/pin/mention state from the root-live or
                      // selected-wave model before enabling folder-specific toolbar controls.
                      new J2clToolbarSurfaceController.SelectedWaveState(
                          waveId != null && !waveId.isEmpty(), false, false, true, false, false));
                  selectedWaveController.onWaveSelected(waveId, digestItem);
                }),
            "view=j2cl-root",
            url -> {
              liveSurfaceController.onRouteUrlChanged(url);
              rehydrateDepthFromRoute(selectedWaveViewRef[0], routeControllerRef[0]);
            });
    routeControllerRef[0] = routeController;
    searchView.setSessionSummary("Mounted inside the J2CL root shell.");
    // F-3.S3 (#1038, R-5.5): forward per-blip reaction snapshots from
    // the selected-wave view's render path to the compose controller
    // so the toggle handler can compute adding-vs-removing direction
    // against the same data the chips render from.
    selectedWaveView.setReactionSnapshotPublisher(composeController::setReactionSnapshots);
    // F-3.S3 (#1038, R-5.5): the compose controller learns the
    // signed-in address as soon as the first bootstrap completes;
    // forward it to the selected-wave view so the per-chip
    // aria-pressed state ("this is your own reaction") tracks the
    // signed-in user without a separate gateway round-trip.
    composeController.setCurrentUserAddressListener(selectedWaveView::setCurrentUserAddress);
    composeController.start();
    toolbarController.start();
    toolbarController.onEditStateChanged(new J2clToolbarSurfaceController.EditState(false));
    // F-2 slice 5 (#1055, R-3.7 G.4 + G.5): wire depth-nav events to the
    // route controller so URL state survives reload + back/forward.
    // The rehydration runs inside the live-surface starter so the URL
    // depth value is applied right after route.start().
    bindDepthEventsToRoute(selectedWaveView, routeController);
    // Mobile list<->wave switch (GWT parity): <wavy-back-to-inbox> emits a
    // cancelable wavy-back-to-inbox-clicked before its anchor navigates.
    // Intercept it to clear the selection through the route controller so
    // returning to the inbox is an instant in-shell transition (the anchor
    // href stays functional for middle-click / no-JS fallbacks).
    disposeRegistry.addListener(DomGlobal.document.body, 
        J2clUiTokens.EVENT_BACK_TO_INBOX_CLICKED,
        event -> {
          event.preventDefault();
          // #1271: a wave switch should not carry stale error toasts across.
          notificationService.clear();
          routeControllerRef[0].selectWave(null);
        });
    // #1268: final teardown on navigation away releases the selected-wave view's
    // listeners (which cascade to the read surface) so they do not leak.
    disposeRegistry.addListener(DomGlobal.window, J2clUiTokens.EVENT_PAGE_HIDE, event -> destroy());
    liveSurfaceController.start();
  }

  /**
   * #1268: release the surfaces this shell owns. Idempotent — safe to call from
   * both {@code pagehide} and an explicit host teardown.
   */
  @Override
  public void destroy() {
    // Release this shell's own global body/window listeners (incl. pagehide)...
    disposeRegistry.destroy();
    // ...then cascade to the selected-wave view (which cascades to the read surface).
    if (selectedWaveView != null) {
      selectedWaveView.destroy();
    }
  }

  /**
   * Transitional overload: defaults to inline-rich-composer mode (inlineRichComposerEnabled=true).
   *
   * @deprecated Prefer {@link #editStateForWriteSession(J2clSidecarWriteSession, boolean)} and
   *     pass the resolved flag value explicitly.
   */
  @Deprecated
  static J2clToolbarSurfaceController.EditState editStateForWriteSession(
      J2clSidecarWriteSession writeSession) {
    return editStateForWriteSession(writeSession, true);
  }

  static J2clToolbarSurfaceController.EditState editStateForWriteSession(
      J2clSidecarWriteSession writeSession, boolean inlineRichComposerEnabled) {
    return new J2clToolbarSurfaceController.EditState(
        writeSession != null && !inlineRichComposerEnabled);
  }

  static boolean isInlineRichComposerEnabled(HTMLElement host) {
    if (host == null) {
      return false;
    }
    // data-j2cl-inline-rich-composer is emitted on <shell-root>, not on the
    // #j2cl-root-shell-workflow section where the controller is mounted.
    elemental2.dom.Element shellRoot = host.closest("shell-root");
    elemental2.dom.Element target = shellRoot != null ? shellRoot : host;
    return "true".equals(target.getAttribute(J2clUiTokens.DATA_ATTR_INLINE_RICH_COMPOSER));
  }

  static boolean isReadSurfacePreviewHost(boolean hostMarked, boolean bodyMarked) {
    return hostMarked || bodyMarked;
  }

  private boolean isReadSurfacePreviewHost(HTMLElement candidate) {
    boolean hostMarked =
        candidate != null
            && "true".equals(candidate.getAttribute(J2clUiTokens.DATA_ATTR_READ_SURFACE_PREVIEW));
    HTMLElement body = (HTMLElement) elemental2.dom.DomGlobal.document.body;
    boolean bodyMarked =
        body != null && "true".equals(body.getAttribute(J2clUiTokens.DATA_ATTR_READ_SURFACE_PREVIEW));
    return isReadSurfacePreviewHost(hostMarked, bodyMarked);
  }

  private void startReadSurfacePreviewMode() {
    J2clRootShellView shellView = new J2clRootShellView(host);
    J2clSearchPanelView searchView =
        new J2clSearchPanelView(
            shellView.getWorkflowHost(), J2clSearchPanelView.ShellPresentation.ROOT_SHELL);
    new J2clSelectedWaveView(searchView.getSelectedWaveHost(), J2clClientTelemetry.browserStatsSink());
  }

  static List<String> participantsFromEvent(Event event) {
    return stringArrayFromEvent(event, "participants");
  }

  static List<String> addParticipantAddressesFromEvent(Event event) {
    return stringArrayFromEvent(event, "addresses");
  }

  static String sourceWaveIdFromEvent(Event event) {
    return normalizeSourceWaveId(detailValue(event, "sourceWaveId"));
  }

  static boolean nextPublicFromEvent(Event event) {
    Object value = detailValue(event, "nextPublic");
    return value instanceof Boolean
        ? ((Boolean) value).booleanValue()
        : Boolean.parseBoolean(String.valueOf(value));
  }

  static String lockStateFromEvent(Event event, String key) {
    return normalizeLockStateValue(detailValue(event, key));
  }

  static String normalizeSourceWaveId(Object sourceWaveId) {
    return sourceWaveId == null ? "" : String.valueOf(sourceWaveId).trim();
  }

  static String normalizeLockStateValue(Object value) {
    return SidecarSelectedWaveDocument.normalizeLockState(
        value == null ? null : String.valueOf(value).trim());
  }

  private static List<String> stringArrayFromEvent(Event event, String key) {
    if (event == null) {
      return Collections.emptyList();
    }
    Object participantsObject = detailValue(event, key);
    if (participantsObject == null || !JsArray.isArray(participantsObject)) {
      return Collections.emptyList();
    }
    JsArray<?> participants = Js.uncheckedCast(participantsObject);
    List<Object> values = new ArrayList<Object>();
    int length = participants.length;
    for (int i = 0; i < length; i++) {
      values.add(participants.getAt(i));
    }
    return normalizeParticipantValues(values);
  }

  private static Object detailValue(Event event, String key) {
    if (event == null || key == null || key.isEmpty()) {
      return null;
    }
    Object detail = Js.asPropertyMap(event).get("detail");
    if (detail == null) {
      return null;
    }
    return Js.asPropertyMap(detail).get(key);
  }

  static List<String> normalizeParticipantValues(List<?> participants) {
    if (participants == null || participants.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> result = new ArrayList<String>();
    for (Object participant : participants) {
      if (participant == null) {
        continue;
      }
      if (!(participant instanceof String)) {
        continue;
      }
      String address = ((String) participant).trim();
      if (!address.isEmpty()) {
        result.add(address);
      }
    }
    return result;
  }

  /**
   * F-2 slice 5 (#1055, R-3.7 G.5): listen for depth-nav events emitted
   * by the {@code J2clReadSurfaceDomRenderer} (drill-in / drill-out /
   * root) on the selected-wave card and forward the resolved depth blip
   * id to the route controller.
   *
   * <p>The events bubble up to the card via the read-surface dispatch.
   * For drill-in we use the event detail's {@code blipId}; for
   * {@code wavy-depth-up} we resolve to the parent depth blip id from
   * the read-surface attribute (kept in sync by setDepthFocus); and for
   * {@code wavy-depth-root} we clear the depth.
   */
  // #1268: instance so the depth listeners bind through the shell's dispose
  // registry and are removed on destroy().
  private void bindDepthEventsToRoute(
      J2clSelectedWaveView view, J2clSidecarRouteController routeController) {
    HTMLElement card = view.getCardElement();
    if (card == null || routeController == null) {
      return;
    }
    disposeRegistry.addListener(
        card,
        J2clUiTokens.EVENT_DEPTH_DRILL_IN,
        evt -> {
          Object detail = Js.asPropertyMap(evt).get("detail");
          if (detail == null) {
            return;
          }
          Object blipId = Js.asPropertyMap(detail).get("blipId");
          if (blipId == null) {
            return;
          }
          String resolved = String.valueOf(blipId);
          if (!resolved.isEmpty()) {
            routeController.onDepthChanged(resolved);
            view.setDepthFocus(resolved, "", "");
          }
        });
    disposeRegistry.addListener(
        card,
        J2clUiTokens.EVENT_DEPTH_UP,
        evt -> {
          Object detail = Js.asPropertyMap(evt).get("detail");
          String parentId = "";
          if (detail != null) {
            Object resolved = Js.asPropertyMap(detail).get("toBlipId");
            if (resolved != null) {
              parentId = String.valueOf(resolved);
            }
          }
          // toBlipId may be empty when the parent is the wave root —
          // collapsing to empty clears the URL depth parameter.
          routeController.onDepthChanged(parentId.isEmpty() ? null : parentId);
          view.setDepthFocus(parentId.isEmpty() ? "" : parentId, "", "");
        });
    disposeRegistry.addListener(
        card,
        J2clUiTokens.EVENT_DEPTH_ROOT,
        (Event evt) -> {
          routeController.onDepthChanged(null);
          view.setDepthFocus("", "", "");
        });
    disposeRegistry.addListener(
        card,
        J2clUiTokens.EVENT_DEPTH_JUMP_TO_CRUMB,
        evt -> {
          Object detail = Js.asPropertyMap(evt).get("detail");
          String blipId = "";
          if (detail != null) {
            Object resolved = Js.asPropertyMap(detail).get("blipId");
            if (resolved != null) {
              blipId = String.valueOf(resolved);
            }
          }
          routeController.onDepthChanged(blipId.isEmpty() ? null : blipId);
          view.setDepthFocus(blipId, "", "");
        });
  }

  /**
   * F-2 slice 5 (#1055, R-3.7 G.4): re-hydrate the depth-nav-bar from
   * the parsed URL state. Called after {@code routeController.start()}
   * has populated currentState.
   */
  private static void rehydrateDepthFromRoute(
      J2clSelectedWaveView view, J2clSidecarRouteController routeController) {
    if (view == null || routeController == null) {
      return;
    }
    J2clSidecarRouteState state = routeController.getCurrentState();
    if (state == null) {
      return;
    }
    String depthBlipId = state.getDepthBlipId();
    if (depthBlipId == null || depthBlipId.isEmpty()) {
      view.setDepthFocus("", "", "");
      return;
    }
    view.setDepthFocus(depthBlipId, "", "");
  }

  private static String newWaveTriggerFromEvent(Event evt) {
    if (evt == null) {
      return "button";
    }
    Object detail = Js.asPropertyMap(evt).get("detail");
    if (detail == null) {
      return "button";
    }
    return newWaveTriggerFromSource(Js.asPropertyMap(detail).get("source"));
  }

  static String newWaveTriggerFromSource(Object source) {
    String normalized = source == null ? "" : String.valueOf(source).trim().toLowerCase(Locale.ROOT);
    if ("keyboard-shortcut".equals(normalized) || "shortcut".equals(normalized)) {
      return "shortcut";
    }
    if ("menu".equals(normalized)) {
      return "menu";
    }
    return "button";
  }

  private static HTMLElement createChildHost(HTMLElement parent, String className) {
    HTMLElement child = (HTMLElement) elemental2.dom.DomGlobal.document.createElement("div");
    child.className = className;
    parent.appendChild(child);
    return child;
  }

  private static HTMLElement createSiblingHostBefore(HTMLElement reference, String className) {
    HTMLElement child = (HTMLElement) elemental2.dom.DomGlobal.document.createElement("div");
    child.className = className;
    if (reference.parentNode == null) {
      throw new IllegalStateException("Reference host must be attached before creating sibling host.");
    }
    reference.parentNode.insertBefore(child, reference);
    return child;
  }

  private static double resolveViewportWidth() {
    return Double.parseDouble(String.valueOf(elemental2.dom.DomGlobal.window.innerWidth));
  }

  private static String buildRootShellSessionSeed() {
    long timestampSeed = System.currentTimeMillis();
    long randomSeed = (long) Math.floor(Math.random() * 0x7fffffff);
    return "j2cl-root" + Long.toHexString(timestampSeed) + Long.toHexString(randomSeed);
  }
}
