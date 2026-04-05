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
package org.waveprotocol.wave.client.editor.content.paragraph;

import junit.framework.TestCase;
import org.waveprotocol.wave.client.editor.content.paragraph.Paragraph.Direction;

public class DirectionTest extends TestCase {

  // fromValue("l") must return LTR
  public void testFromValueLtr() {
    assertEquals(Direction.LTR, Direction.fromValue("l"));
  }

  // fromValue("r") must return RTL
  public void testFromValueRtl() {
    assertEquals(Direction.RTL, Direction.fromValue("r"));
  }

  // fromValue(null) must return null (auto state — no d attribute)
  public void testFromValueNull() {
    assertNull(Direction.fromValue(null));
  }

  // In auto state (null), isApplied must return false for BOTH directions.
  // This ensures neither toolbar button lights up when direction is auto-detected.
  public void testIsAppliedInAutoState() {
    Direction auto = Direction.fromValue(null); // null
    assertFalse("LTR must not be applied in auto state", Direction.LTR == auto);
    assertFalse("RTL must not be applied in auto state", Direction.RTL == auto);
  }

  // cssValue() returns the HTML dir attribute string
  public void testCssValues() {
    assertEquals("ltr", Direction.LTR.cssValue());
    assertEquals("rtl", Direction.RTL.cssValue());
  }

  // LTR value attribute (stored as d="l")
  public void testLtrValue() {
    assertEquals("l", Direction.LTR.value);
  }

  // RTL value attribute (stored as d="r")
  public void testRtlValue() {
    assertEquals("r", Direction.RTL.value);
  }
}
