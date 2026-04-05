package org.waveprotocol.box.server.waveserver;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.waveprotocol.box.server.persistence.AnalyticsCounterStore.HourlyBucket;
import org.waveprotocol.box.server.persistence.memory.MemoryAnalyticsCounterStore;

public class AnalyticsRecorderTest {

  private static final long HOUR_MS = 3_600_000L;
  private static final long BASE_TIME = 1775386200000L;
  private static final long BASE_HOUR = BASE_TIME - (BASE_TIME % HOUR_MS);

  private MemoryAnalyticsCounterStore store;
  private AnalyticsRecorder recorder;

  @Before
  public void setUp() {
    store = new MemoryAnalyticsCounterStore();
    recorder = new AnalyticsRecorder(store);
  }

  @Test public void testIncrementPageViews() {
    recorder.incrementPageViews(BASE_TIME);
    recorder.incrementPageViews(BASE_TIME + 1000);
    List<HourlyBucket> buckets = store.getHourlyBuckets(BASE_HOUR, BASE_HOUR + HOUR_MS);
    assertEquals(1, buckets.size());
    assertEquals(2, buckets.get(0).getPageViews());
  }

  @Test public void testIncrementApiViews() {
    recorder.incrementApiViews(BASE_TIME);
    List<HourlyBucket> buckets = store.getHourlyBuckets(BASE_HOUR, BASE_HOUR + HOUR_MS);
    assertEquals(1, buckets.size());
    assertEquals(1, buckets.get(0).getApiViews());
  }

  @Test public void testRecordActiveUser() {
    recorder.recordActiveUser("alice@example.com", BASE_TIME);
    List<HourlyBucket> buckets = store.getHourlyBuckets(BASE_HOUR, BASE_HOUR + HOUR_MS);
    assertEquals(1, buckets.size());
    assertEquals(1, buckets.get(0).getActiveUserIds().size());
  }

  @Test public void testIncrementUsersRegistered() {
    recorder.incrementUsersRegistered(BASE_TIME);
    List<HourlyBucket> buckets = store.getHourlyBuckets(BASE_HOUR, BASE_HOUR + HOUR_MS);
    assertEquals(1, buckets.size());
    assertEquals(1, buckets.get(0).getUsersRegistered());
  }

  @Test public void testRecordWaveCreated() {
    recorder.recordWaveCreated(BASE_TIME);
    List<HourlyBucket> buckets = store.getHourlyBuckets(BASE_HOUR, BASE_HOUR + HOUR_MS);
    assertEquals(1, buckets.size());
    assertEquals(1, buckets.get(0).getWavesCreated());
  }

  @Test public void testRecordBlipsCreated() {
    recorder.recordBlipsCreated(3, BASE_TIME);
    List<HourlyBucket> buckets = store.getHourlyBuckets(BASE_HOUR, BASE_HOUR + HOUR_MS);
    assertEquals(1, buckets.size());
    assertEquals(3, buckets.get(0).getBlipsCreated());
  }
}
