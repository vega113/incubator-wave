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

package org.waveprotocol.wave.client.wavepanel.view.impl;

import org.waveprotocol.wave.client.wavepanel.view.BlipView;
import org.waveprotocol.wave.client.wavepanel.view.InlineConversationView;
import org.waveprotocol.wave.client.wavepanel.view.IntrinsicInlineConversationView;

/**
 * Implements a conversation view by delegating primitive state matters to a
 * view object, and structural state matters to a helper. The intent is that the
 * helper is a flyweight handler.
 *
 * @param <I> intrinsic thread implementation
 */
public final class InlineConversationViewImpl<I extends IntrinsicInlineConversationView> // \u2620
    extends AbstractConversationViewImpl<I, InlineConversationViewImpl.Helper<? super I>> // \u2620
    implements InlineConversationView {

  /**
   * Handles structural queries on thread views.
   *
   * @param <I> intrinsic thread implementation
   */
  public interface Helper<I> extends AbstractConversationViewImpl.Helper<I> {
    BlipView getParent(I thread);
  }

  public InlineConversationViewImpl(Helper<? super I> helper, I impl) {
    super(helper, impl);
  }

  @Override
  public Type getType() {
    return Type.INLINE_CONVERSATION;
  }

  @Override
  public BlipView getParent() {
    return helper.getParent(impl);
  }

  @Override
  public void setCollapsed(boolean collapsed) {
    impl.setCollapsed(collapsed);
  }

  @Override
  public boolean isCollapsed() {
    return impl.isCollapsed();
  }
}
