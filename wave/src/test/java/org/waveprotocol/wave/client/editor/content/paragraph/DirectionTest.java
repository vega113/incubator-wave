/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See NOTICE file for details.
 * Licensed under the Apache License, Version 2.0.
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

  // fromValue(null) must return null (auto state)
  public void testFromValueNull() {
    assertNull(Direction.fromValue(null));
  }

  // cssValue() returns the HTML dir attribute string
  public void testCssValues() {
    assertEquals("ltr", Direction.LTR.cssValue());
    assertEquals("rtl", Direction.RTL.cssValue());
  }

  // LTR value attribute
  public void testLtrValue() {
    assertEquals("l", Direction.LTR.value);
  }

  // RTL value attribute
  public void testRtlValue() {
    assertEquals("r", Direction.RTL.value);
  }
}
