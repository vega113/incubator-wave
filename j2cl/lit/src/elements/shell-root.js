import { LitElement, css, html } from "lit";

export class ShellRoot extends LitElement {
  static styles = css`
    :host {
      display: grid;
      grid-template-rows: auto 1fr auto;
      min-height: 100vh;
      background: var(--shell-color-surface-page, #f7fbff);
      color: var(--shell-color-text-primary, #102b3f);
    }

    .body {
      display: flex;
      min-height: 0;
    }

    slot[name="nav"] {
      flex: 0 0 auto;
    }

    slot[name="main"] {
      flex: 1;
      min-width: 0;
    }
  `;

  render() {
    return html`
      <slot name="skip-link"></slot>
      <slot name="header"></slot>
      <div class="body">
        <slot name="nav"></slot>
        <slot name="main"></slot>
      </div>
      <slot name="status"></slot>
    `;
  }
}

customElements.define("shell-root", ShellRoot);
