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
package org.waveprotocol.box.server.rpc;

import org.junit.Test;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

public class AttachmentMaskingUtilTest {
  @Test
  public void maskString_masksMiddle() throws Exception {
    Method m = AttachmentInfoServlet.class.getDeclaredMethod("mask", String.class);
    m.setAccessible(true);
    assertEquals("abc***hi", m.invoke(null, "abcdefghi"));
    assertEquals("***", m.invoke(null, "abcd"));
  }

  @Test
  public void maskParticipant_masksLocalPart() throws Exception {
    Method mp = AttachmentInfoServlet.class.getDeclaredMethod("maskParticipant", ParticipantId.class);
    Method ms = AttachmentInfoServlet.class.getDeclaredMethod("mask", String.class);
    mp.setAccessible(true); ms.setAccessible(true);
    ParticipantId p = ParticipantId.ofUnsafe("user@example.com");
    String masked = (String) mp.invoke(null, p);
    assertTrue(masked.endsWith("@example.com"));
    assertFalse(masked.startsWith("user@"));
  }
}

