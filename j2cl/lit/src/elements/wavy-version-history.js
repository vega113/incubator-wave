import { LitElement, css, html } from "lit";

/**
 * <wavy-version-history> — F-2.S4 (#1048, K.1–K.6) full-bleed version
 * history overlay with a time slider, "Show changes" + "Text only"
 * toggles, a destructive Restore action (gated by `restoreEnabled`),
 * and an Exit affordance.
 *
 * The overlay consumes the existing GWT WaveletProvider.getHistory
 * seam unchanged via the JS `versionLoader` callback the renderer
 * wires up. S4 ships the overlay chrome only; the data wire is gated
 * behind `versionLoader` (no-op default) and the destructive Restore
 * is gated behind `restoreEnabled` (false default). A follow-up issue
 * (filed at PR-open time) wires the seam and flips the gate.
 *
 * Properties:
 *   - open: boolean — overlay visibility (reflected as `open` attribute
 *     and as the standard `hidden` attribute on the host so tab order
 *     and CSS hooks both reflect the state).
 *   - versions: Array<{ index: number, label: string, timestamp: string }>
 *     — the time-slider rail's data points. Default [].
 *   - value: number — current playhead index 0..N. Default 0.
 *   - showChanges: boolean — K.3.
 *   - textOnly: boolean — K.4.
 *   - restoreEnabled: boolean — K.5 gate. Default false. Renderer flips
 *     to true only after wiring a real versionLoader.
 *   - versionLoader: (rangeStart, rangeEnd) => Promise<Version[]> — async
 *     hook the renderer wires up. Slider calls this on first open if set;
 *     otherwise stays inert.
 *
 * Events emitted (CustomEvent, bubbles + composed):
 *   - `wavy-version-changed` — `{detail: {index, version}}` on slider input.
 *   - `wavy-show-changes-toggled` — `{detail: {showChanges}}`.
 *   - `wavy-text-only-toggled` — `{detail: {textOnly}}`.
 *   - `wavy-version-restore-confirmed` — `{detail: {index, version}}` after
 *     the user confirms the inline confirm dialog. Only emitted when
 *     restoreEnabled is true.
 *   - `wavy-version-history-exited` — emitted when the user closes the
 *     overlay (Exit button or Escape key while open).
 */
export class WavyVersionHistory extends LitElement {
  static properties = {
    open: { type: Boolean, reflect: true },
    versions: { attribute: false },
    value: { type: Number, reflect: true },
    showChanges: { type: Boolean, attribute: "show-changes", reflect: true },
    textOnly: { type: Boolean, attribute: "text-only", reflect: true },
    restoreEnabled: {
      type: Boolean,
      attribute: "restore-enabled",
      reflect: true
    }
  };

  static styles = css`
    :host {
      position: fixed;
      inset: 0;
      z-index: 300;
      display: block;
    }
    :host([hidden]) {
      display: none;
    }
    .backdrop {
      position: absolute;
      inset: 0;
      background: rgba(11, 19, 32, 0.84);
    }
    .panel {
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      min-width: 320px;
      max-width: 92vw;
      background: var(--wavy-bg-base, #0b1320);
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
      border: 1px solid var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
      border-radius: var(--wavy-radius-card, 12px);
      padding: var(--wavy-spacing-5, 24px);
      display: grid;
      gap: var(--wavy-spacing-3, 12px);
    }
    h2 {
      margin: 0;
      font: var(--wavy-type-section, 0.875rem / 1.4 sans-serif);
      letter-spacing: 0.04em;
      text-transform: uppercase;
      color: var(--wavy-text-muted, rgba(232, 240, 255, 0.62));
    }
    .slider-row {
      display: grid;
      gap: var(--wavy-spacing-2, 8px);
    }
    input[type="range"] {
      width: 100%;
      accent-color: var(--wavy-signal-cyan, #22d3ee);
    }
    .toggles {
      display: inline-flex;
      gap: var(--wavy-spacing-2, 8px);
      flex-wrap: wrap;
    }
    button {
      font: var(--wavy-type-label, 0.75rem / 1.35 sans-serif);
      padding: var(--wavy-spacing-2, 8px) var(--wavy-spacing-3, 12px);
      background: transparent;
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
      border: 1px solid var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
      border-radius: var(--wavy-radius-pill, 9999px);
      cursor: pointer;
    }
    button:focus-visible {
      box-shadow: var(--wavy-focus-ring, 0 0 0 2px #22d3ee);
      outline: none;
    }
    button[aria-pressed="true"] {
      background: var(--wavy-signal-cyan, #22d3ee);
      color: var(--wavy-bg-base, #0b1320);
      border-color: var(--wavy-signal-cyan, #22d3ee);
    }
    .actions {
      display: inline-flex;
      gap: var(--wavy-spacing-2, 8px);
      align-items: center;
      flex-wrap: wrap;
      margin-top: var(--wavy-spacing-2, 8px);
    }
    button.restore {
      color: var(--wavy-signal-red, #ef4444);
      border-color: var(--wavy-signal-red, #ef4444);
    }
    button.restore[disabled] {
      opacity: 0.55;
      cursor: not-allowed;
    }
    .restore-hint {
      font: var(--wavy-type-label, 0.75rem / 1.35 sans-serif);
      color: var(--wavy-text-quiet, rgba(232, 240, 255, 0.42));
    }
    .exit {
      position: absolute;
      top: var(--wavy-spacing-3, 12px);
      right: var(--wavy-spacing-3, 12px);
      width: var(--wavy-spacing-5, 24px);
      height: var(--wavy-spacing-5, 24px);
      padding: 0;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      border-radius: var(--wavy-radius-pill, 9999px);
      border: 0;
      background: transparent;
      color: var(--wavy-text-muted, rgba(232, 240, 255, 0.62));
      cursor: pointer;
    }
    dialog {
      border: 1px solid var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
      border-radius: var(--wavy-radius-card, 12px);
      background: var(--wavy-bg-base, #0b1320);
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
      padding: var(--wavy-spacing-4, 16px);
      max-width: 380px;
    }
    dialog::backdrop {
      background: rgba(11, 19, 32, 0.7);
    }
    .confirm-actions {
      display: inline-flex;
      gap: var(--wavy-spacing-2, 8px);
      margin-top: var(--wavy-spacing-3, 12px);
    }
  `;

  constructor() {
    super();
    this.open = false;
    this.versions = [];
    this.value = 0;
    this.showChanges = false;
    this.textOnly = false;
    this.restoreEnabled = false;
    this.versionLoader = null;
    this._loaderRan = false;
    this._onKeyDown = this._onKeyDown.bind(this);
  }

  connectedCallback() {
    super.connectedCallback();
    this._syncOpen();
    this.addEventListener("keydown", this._onKeyDown);
  }

  disconnectedCallback() {
    this.removeEventListener("keydown", this._onKeyDown);
    super.disconnectedCallback();
  }

  willUpdate(changed) {
    if (changed.has("open")) {
      this._syncOpen();
      if (this.open && !this._loaderRan && typeof this.versionLoader === "function") {
        this._loaderRan = true;
        try {
          // Range (0, Infinity) signals the loader to fetch the full
          // version history. Renderer can wrap with windowing as needed.
          const result = this.versionLoader(0, Number.POSITIVE_INFINITY);
          // Assign the resolved versions to `this.versions` so the slider
          // and current-label render against the loader's data. Swallow
          // rejected Promises so a failing loader does not break the
          // overlay chrome (toggles + Exit still work). The success +
          // failure handlers ride on the same `.then(...)` so a rejected
          // loader Promise does not surface as an unhandled rejection.
          if (result && typeof result.then === "function") {
            result.then(
              (versions) => {
                if (Array.isArray(versions)) {
                  this.versions = versions;
                }
              },
              () => {}
            );
          }
        } catch (_e) {
          // Same — sync throws must not break the overlay chrome.
        }
      }
    }
  }

  _syncOpen() {
    if (this.open) {
      this.removeAttribute("hidden");
      this.setAttribute("role", "dialog");
      this.setAttribute("aria-modal", "true");
      this.setAttribute("aria-label", "Version history");
      this.removeAttribute("aria-hidden");
      // Make the host focusable so Escape delivered via real keyboard
      // (not synthetic dispatch) reaches the host's keydown handler.
      // tabindex=-1 keeps it out of the tab sequence — focus is moved
      // here explicitly on open.
      this.setAttribute("tabindex", "-1");
      Promise.resolve().then(() => {
        if (this.open && typeof this.focus === "function") {
          try { this.focus({ preventScroll: true }); } catch (_e) { this.focus(); }
        }
      });
    } else {
      this.setAttribute("hidden", "");
      this.setAttribute("aria-hidden", "true");
      this.removeAttribute("tabindex");
      // If the inline confirm <dialog> was left open (e.g. user clicked
      // Restore then closed the overlay via Exit/backdrop), close it
      // alongside the overlay so reopening does not show a stale modal.
      this._closeConfirmDialog();
    }
  }

  _closeConfirmDialog() {
    if (!this.renderRoot) return;
    const dlg = this.renderRoot.querySelector("dialog.confirm");
    if (dlg && dlg.hasAttribute("open")) {
      try { dlg.close(); } catch (_e) { dlg.removeAttribute("open"); }
    }
  }

  open_() {
    this.open = true;
  }

  close_() {
    this.open = false;
  }

  _onKeyDown(event) {
    if (event.key === "Escape" && this.open) {
      // If the inline confirm <dialog> is currently open, let the
      // native dialog handle Escape (closes the dialog) instead of
      // exiting the whole overlay.
      const dlg = this.renderRoot && this.renderRoot.querySelector("dialog.confirm");
      if (dlg && dlg.hasAttribute("open")) {
        return;
      }
      event.preventDefault();
      this._exit();
    }
  }

  _onSliderInput(event) {
    const idx = Number(event.target.value) || 0;
    this.value = idx;
    const version = (this.versions || [])[idx] || null;
    this.dispatchEvent(
      new CustomEvent("wavy-version-changed", {
        bubbles: true,
        composed: true,
        detail: { index: idx, version }
      })
    );
  }

  _onShowChangesClick() {
    this.showChanges = !this.showChanges;
    this.dispatchEvent(
      new CustomEvent("wavy-show-changes-toggled", {
        bubbles: true,
        composed: true,
        detail: { showChanges: this.showChanges }
      })
    );
  }

  _onTextOnlyClick() {
    this.textOnly = !this.textOnly;
    this.dispatchEvent(
      new CustomEvent("wavy-text-only-toggled", {
        bubbles: true,
        composed: true,
        detail: { textOnly: this.textOnly }
      })
    );
  }

  _onRestoreClick() {
    if (!this.restoreEnabled) return;
    const dlg = this.renderRoot.querySelector("dialog.confirm");
    if (dlg && typeof dlg.showModal === "function") {
      dlg.showModal();
    } else if (dlg) {
      dlg.setAttribute("open", "");
    }
  }

  _onConfirmRestore() {
    // Clamp `value` against the current `versions` length so a shrunken
    // history (e.g. loader returned fewer entries than the slider's last
    // raw value) emits the version the UI currently shows, not null.
    const versions = Array.isArray(this.versions) ? this.versions : [];
    const max = versions.length > 0 ? versions.length - 1 : 0;
    const safeValue = Math.max(0, Math.min(this.value || 0, max));
    const version = versions[safeValue] || null;
    const dlg = this.renderRoot.querySelector("dialog.confirm");
    if (dlg) {
      try { dlg.close(); } catch (_e) { dlg.removeAttribute("open"); }
    }
    this.dispatchEvent(
      new CustomEvent("wavy-version-restore-confirmed", {
        bubbles: true,
        composed: true,
        detail: { index: safeValue, version }
      })
    );
  }

  _onCancelRestore() {
    const dlg = this.renderRoot.querySelector("dialog.confirm");
    if (dlg) {
      try { dlg.close(); } catch (_e) { dlg.removeAttribute("open"); }
    }
  }

  _onExitClick() {
    this._exit();
  }

  _exit() {
    // Close any open inline confirm dialog before exiting the overlay
    // so reopening the overlay does not show a stale modal.
    this._closeConfirmDialog();
    this.open = false;
    this.dispatchEvent(
      new CustomEvent("wavy-version-history-exited", {
        bubbles: true,
        composed: true
      })
    );
  }

  render() {
    const versions = Array.isArray(this.versions) ? this.versions : [];
    const max = versions.length > 0 ? versions.length - 1 : 0;
    const idx = Math.min(Math.max(this.value || 0, 0), max);
    const current = versions[idx] || null;
    const restoreLabel = current ? `Restore version ${current.label}` : "Restore version";

    return html`
      <div class="backdrop" @click=${this._onExitClick}></div>
      <div class="panel" role="document">
        <button
          class="exit"
          type="button"
          aria-label="Exit version history"
          @click=${this._onExitClick}
        >
          <span aria-hidden="true">×</span>
        </button>
        <h2>Version history</h2>
        <div class="slider-row">
          <input
            type="range"
            min="0"
            max=${String(max)}
            .value=${String(idx)}
            aria-label="Version history time slider"
            aria-valuemin="0"
            aria-valuemax=${String(max)}
            aria-valuenow=${String(idx)}
            @input=${this._onSliderInput}
          />
        </div>
        <div class="toggles">
          <button
            type="button"
            aria-pressed=${this.showChanges ? "true" : "false"}
            @click=${this._onShowChangesClick}
          >Show changes</button>
          <button
            type="button"
            aria-pressed=${this.textOnly ? "true" : "false"}
            @click=${this._onTextOnlyClick}
          >Text only</button>
        </div>
        <div class="actions">
          <button
            class="restore"
            type="button"
            ?disabled=${!this.restoreEnabled}
            aria-disabled=${this.restoreEnabled ? "false" : "true"}
            @click=${this._onRestoreClick}
          >Restore</button>
          ${this.restoreEnabled
            ? null
            : html`<span class="restore-hint">Preview only — restore not available</span>`}
        </div>
        <dialog class="confirm">
          <p>${restoreLabel}? This rewrites the wave to this point.</p>
          <div class="confirm-actions">
            <button type="button" @click=${this._onCancelRestore}>Cancel</button>
            <button class="restore" type="button" @click=${this._onConfirmRestore}>Restore</button>
          </div>
        </dialog>
      </div>
    `;
  }
}

if (!customElements.get("wavy-version-history")) {
  customElements.define("wavy-version-history", WavyVersionHistory);
}
