import { LitElement, css, html } from "lit";

export class ShellRootSignedOut extends LitElement {
  static styles = css`
    :host {
      display: grid;
      grid-template-rows: auto 1fr auto;
      min-height: 100vh;
      background: var(--shell-color-surface-page, #f7fbff);
      color: var(--shell-color-text-primary, #102b3f);
    }
  `;

  render() {
    return html`
      <slot name="skip-link"></slot>
      <slot name="header"></slot>
      <slot name="main"></slot>
      <slot name="status"></slot>
    `;
  }
}

customElements.define("shell-root-signed-out", ShellRootSignedOut);
