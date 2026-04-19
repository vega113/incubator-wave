package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

@J2clTestInput(SidecarSearchResponseTest.class)
public class SidecarSearchResponseTest {
  @Test
  public void constructorDefensivelyCopiesDigestAndParticipantLists() {
    List<String> participants = new ArrayList<>();
    participants.add("user@example.com");
    SidecarSearchResponse.Digest digest =
        new SidecarSearchResponse.Digest(
            "Inbox wave",
            "Snippet",
            "example.com/w+abc123",
            12345L,
            2,
            4,
            participants,
            "user@example.com",
            true);
    List<SidecarSearchResponse.Digest> digests = new ArrayList<>();
    digests.add(digest);

    SidecarSearchResponse response = new SidecarSearchResponse("in:inbox", 1, digests);

    participants.add("teammate@example.com");
    digests.clear();

    Assert.assertEquals(1, response.getDigests().size());
    Assert.assertEquals(1, response.getDigests().get(0).getParticipants().size());
    try {
      response.getDigests().add(digest);
      Assert.fail("Expected digests view to stay immutable");
    } catch (UnsupportedOperationException expected) {
      // expected
    }
    try {
      response.getDigests().get(0).getParticipants().add("third@example.com");
      Assert.fail("Expected participants view to stay immutable");
    } catch (UnsupportedOperationException expected) {
      // expected
    }
  }
}
