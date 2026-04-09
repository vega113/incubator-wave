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

package org.waveprotocol.wave.model.conversation;

import junit.framework.TestCase;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Unit tests for shared task metadata formatting and parsing helpers.
 */
public class TaskMetadataUtilTest extends TestCase {

  public void testFormatTaskOwnerLabelStripsDomainForEmailAddress() {
    assertEquals("Owner alice",
        TaskMetadataUtil.formatTaskOwnerLabel("alice@example.com"));
  }

  public void testFormatParticipantDisplayStripsDomainForEmailAddress() {
    assertEquals("alice",
        TaskMetadataUtil.formatParticipantDisplay("alice@example.com"));
  }

  public void testFormatTaskOwnerLabelKeepsOpaqueIdentifier() {
    assertEquals("Owner build-bot",
        TaskMetadataUtil.formatTaskOwnerLabel("build-bot"));
  }

  public void testParseDateInputValueRoundTripsThroughFormatter() {
    long dueTs = TaskMetadataUtil.parseDateInputValue("2026-04-15");
    assertEquals("2026-04-15", TaskMetadataUtil.formatDateInputValue(dueTs));
  }

  public void testParseDateInputValueRejectsInvalidDate() {
    assertEquals(-1L, TaskMetadataUtil.parseDateInputValue("2026-13-40"));
  }

  public void testFormatTaskDueLabelUsesMonthDay() {
    long dueTs = TaskMetadataUtil.parseDateInputValue("2026-04-15");
    assertEquals("Due Apr 15", TaskMetadataUtil.formatTaskDueLabel(dueTs));
  }

  public void testDateParsingAndFormattingIgnoreDefaultTimezone() {
    TimeZone original = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
      long expected = utcMillis(2026, Calendar.APRIL, 15);
      long parsed = TaskMetadataUtil.parseDateInputValue("2026-04-15");
      assertEquals(expected, parsed);
      assertEquals("2026-04-15", TaskMetadataUtil.formatDateInputValue(parsed));
      assertEquals("Due Apr 15", TaskMetadataUtil.formatTaskDueLabel(parsed));
    } finally {
      TimeZone.setDefault(original);
    }
  }

  private static long utcMillis(int year, int month, int dayOfMonth) {
    Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    calendar.clear();
    calendar.set(year, month, dayOfMonth, 0, 0, 0);
    return calendar.getTimeInMillis();
  }
}
