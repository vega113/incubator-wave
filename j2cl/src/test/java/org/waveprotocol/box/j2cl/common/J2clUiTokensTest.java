package org.waveprotocol.box.j2cl.common;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.HashSet;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;

/**
 * #1269: pins the {@link J2clUiTokens} values to their exact wire strings. These
 * are the DOM contract with the host page / Lit layer — a change here without a
 * matching Lit-side change silently breaks the runtime, so lock them down.
 */
@J2clTestInput(J2clUiTokensTest.class)
public class J2clUiTokensTest {

  @Test
  public void navEventValuesAreStable() {
    Assert.assertEquals("wave-nav-recent-requested", J2clUiTokens.EVENT_NAV_RECENT);
    Assert.assertEquals("wave-nav-next-unread-requested", J2clUiTokens.EVENT_NAV_NEXT_UNREAD);
    Assert.assertEquals("wave-nav-previous-requested", J2clUiTokens.EVENT_NAV_PREVIOUS);
    Assert.assertEquals("wave-nav-next-requested", J2clUiTokens.EVENT_NAV_NEXT);
    Assert.assertEquals("wave-nav-end-requested", J2clUiTokens.EVENT_NAV_END);
    Assert.assertEquals("wave-nav-prev-mention-requested", J2clUiTokens.EVENT_NAV_PREV_MENTION);
    Assert.assertEquals("wave-nav-next-mention-requested", J2clUiTokens.EVENT_NAV_NEXT_MENTION);
    Assert.assertEquals("wave-nav-archive-toggle-requested", J2clUiTokens.EVENT_NAV_ARCHIVE_TOGGLE);
    Assert.assertEquals("wave-nav-pin-toggle-requested", J2clUiTokens.EVENT_NAV_PIN_TOGGLE);
    Assert.assertEquals(
        "wave-nav-version-history-requested", J2clUiTokens.EVENT_NAV_VERSION_HISTORY);
  }

  @Test
  public void depthAndShellEventValuesAreStable() {
    Assert.assertEquals("wavy-depth-drill-in", J2clUiTokens.EVENT_DEPTH_DRILL_IN);
    Assert.assertEquals("wavy-depth-up", J2clUiTokens.EVENT_DEPTH_UP);
    Assert.assertEquals("wavy-depth-root", J2clUiTokens.EVENT_DEPTH_ROOT);
    Assert.assertEquals("wavy-depth-jump-to-crumb", J2clUiTokens.EVENT_DEPTH_JUMP_TO_CRUMB);
    Assert.assertEquals("wavy-new-wave-requested", J2clUiTokens.EVENT_NEW_WAVE_REQUESTED);
    Assert.assertEquals("wave-new-with-participants-requested", J2clUiTokens.EVENT_NEW_WITH_PARTICIPANTS);
    Assert.assertEquals("wave-add-participant-requested", J2clUiTokens.EVENT_ADD_PARTICIPANT);
    Assert.assertEquals("wave-publicity-toggle-requested", J2clUiTokens.EVENT_PUBLICITY_TOGGLE);
    Assert.assertEquals("wave-root-lock-toggle-requested", J2clUiTokens.EVENT_ROOT_LOCK_TOGGLE);
    Assert.assertEquals("wavy-back-to-inbox-clicked", J2clUiTokens.EVENT_BACK_TO_INBOX_CLICKED);
    Assert.assertEquals(
        "wavy-selected-wave-refresh-requested", J2clUiTokens.EVENT_SELECTED_WAVE_REFRESH);
    Assert.assertEquals("wave-blip-task-toggled", J2clUiTokens.EVENT_BLIP_TASK_TOGGLED);
  }

  @Test
  public void dataAttrAndElementValuesAreStable() {
    Assert.assertEquals("data-j2cl-inline-rich-composer", J2clUiTokens.DATA_ATTR_INLINE_RICH_COMPOSER);
    Assert.assertEquals("data-j2cl-read-surface-preview", J2clUiTokens.DATA_ATTR_READ_SURFACE_PREVIEW);
    Assert.assertEquals("wavy-depth-nav-bar", J2clUiTokens.CUSTOM_ELEMENT_DEPTH_NAV_BAR);
  }

  @Test
  public void navEventArrayIsCompleteAndUnique() {
    Assert.assertEquals(10, J2clUiTokens.EVENTS_NAV.length);
    Set<String> unique = new HashSet<String>();
    for (String event : J2clUiTokens.EVENTS_NAV) {
      Assert.assertTrue("duplicate nav event: " + event, unique.add(event));
    }
    Assert.assertTrue(unique.contains(J2clUiTokens.EVENT_NAV_RECENT));
    Assert.assertTrue(unique.contains(J2clUiTokens.EVENT_NAV_VERSION_HISTORY));
    // The depth-chrome subset is the up/root/jump-to-crumb clicks.
    Assert.assertEquals(3, J2clUiTokens.EVENTS_DEPTH_CHROME.length);
  }
}
