/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.waveprotocol.wave.client.wavepanel.impl.collapse;

/**
 * Builds and installs the thread slide navigation feature alongside the
 * existing collapse/expand feature.
 *
 * <p>This builder creates a {@link ThreadNavigationPresenter} and a
 * {@link BreadcrumbWidget}, wires them together, and registers the
 * navigator with the {@link CollapsePresenter} so that toggle events on
 * deeply nested threads use slide navigation instead of normal
 * collapse/expand.
 *
 * <p>Phase 3 additions: installs a {@link SlideNavigationKeyHandler} for
 * Esc / Alt+Left keyboard shortcuts (using {@code NativePreviewHandler}
 * to avoid KeySignalRouter conflicts), and enables browser history
 * integration so the browser Back button works with slide navigation.
 *
 * <p>The navigation logic is handled inside {@link CollapseController}
 * (which checks the navigator before performing a standard toggle),
 * avoiding the need for a separate mouse-down handler registration that
 * would conflict with the existing collapse handler.
 */
public final class ThreadNavigationBuilder {
  private ThreadNavigationBuilder() {
  }

  /**
   * Builds and installs the thread navigation feature.
   *
   * <p>This installs:
   * <ul>
   *   <li>The breadcrumb widget for visual navigation</li>
   *   <li>The navigator wired into the collapse presenter</li>
   *   <li>Keyboard shortcuts (Esc, Alt+Left) via NativePreviewHandler</li>
   *   <li>Browser history integration for the Back button</li>
   * </ul>
   *
   * @param collapser the existing collapse presenter (which also owns
   *                  the single TOGGLE mouse-down handler)
   * @return the thread navigation presenter
   */
  public static ThreadNavigationPresenter createAndInstallIn(
      CollapsePresenter collapser) {
    ThreadNavigationPresenter navigator = new ThreadNavigationPresenter();

    // Create and wire up the breadcrumb widget
    BreadcrumbWidget breadcrumb = new BreadcrumbWidget();
    breadcrumb.setPresenter(navigator);
    navigator.setBreadcrumb(breadcrumb);

    // Wire the navigator into the collapse presenter so that
    // CollapseController can delegate to it for deep threads.
    collapser.setNavigator(navigator);

    // Install keyboard shortcuts (Esc, Alt+Left) via NativePreviewHandler.
    // We use NativePreviewHandler instead of KeySignalRouter.register() to
    // avoid "Feature conflict" errors, since ESC is already handled by
    // EditSession when in editing mode.
    SlideNavigationKeyHandler keyHandler = new SlideNavigationKeyHandler(navigator);
    keyHandler.install();

    // Enable browser history integration so the browser Back button
    // can be used to navigate back through slide navigation levels.
    navigator.enableHistoryIntegration();

    return navigator;
  }

  /**
   * Builds and installs the thread navigation feature with a custom
   * depth threshold.
   *
   * @param collapser      the existing collapse presenter
   * @param depthThreshold the depth threshold for slide navigation
   * @return the thread navigation presenter
   */
  public static ThreadNavigationPresenter createAndInstallIn(
      CollapsePresenter collapser, int depthThreshold) {
    ThreadNavigationPresenter navigator = createAndInstallIn(collapser);
    navigator.setDepthThreshold(depthThreshold);
    return navigator;
  }
}
