package org.waveprotocol.box.j2cl.transport;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

@J2clTestInput(J2clHttpClientTest.class)
public class J2clHttpClientTest {

  @Test
  public void getTextSuccessDeliversBody() {
    FakeAttempt attempt = new FakeAttempt();
    List<String> ok = new ArrayList<String>();
    List<String> err = new ArrayList<String>();
    J2clHttpClient client = new J2clHttpClient(attempt, autoScheduler(), 1000, 2);

    client.getText("/x", ok::add, err::add);
    attempt.last().sink.onStatus(200, "body");

    Assert.assertEquals(1, attempt.calls.size());
    Assert.assertEquals("GET", attempt.last().method);
    Assert.assertEquals(java.util.Arrays.asList("body"), ok);
    Assert.assertTrue(err.isEmpty());
  }

  @Test
  public void clientErrorDoesNotRetry() {
    FakeAttempt attempt = new FakeAttempt();
    List<String> err = new ArrayList<String>();
    int[] scheduled = {0};
    J2clHttpClient client = new J2clHttpClient(attempt, countingScheduler(scheduled), 1000, 2);

    client.getText("/x", s -> {}, err::add);
    attempt.last().sink.onStatus(404, "nope");

    Assert.assertEquals(1, attempt.calls.size());
    Assert.assertEquals(0, scheduled[0]);
    Assert.assertEquals(1, err.size());
    Assert.assertTrue(err.get(0).contains("HTTP 404"));
  }

  @Test
  public void serverErrorRetriesUpToMaxThenFails() {
    FakeAttempt attempt = new FakeAttempt();
    List<String> err = new ArrayList<String>();
    J2clHttpClient client = new J2clHttpClient(attempt, autoScheduler(), 1000, 2);

    client.getText("/x", s -> {}, err::add);
    attempt.last().sink.onStatus(500, "");
    attempt.last().sink.onStatus(500, "");
    attempt.last().sink.onStatus(500, "");

    Assert.assertEquals(3, attempt.calls.size()); // initial + 2 retries
    Assert.assertEquals(1, err.size());
    Assert.assertTrue(err.get(0).contains("HTTP 500"));
  }

  @Test
  public void networkErrorRetriesThenFails() {
    FakeAttempt attempt = new FakeAttempt();
    List<String> err = new ArrayList<String>();
    J2clHttpClient client = new J2clHttpClient(attempt, autoScheduler(), 1000, 1);

    client.getText("/x", s -> {}, err::add);
    attempt.last().sink.onNetworkError();
    attempt.last().sink.onNetworkError();

    Assert.assertEquals(2, attempt.calls.size()); // initial + 1 retry
    Assert.assertEquals(1, err.size());
    Assert.assertTrue(err.get(0).contains("Network failure"));
  }

  @Test
  public void timeoutRetriesThenFails() {
    FakeAttempt attempt = new FakeAttempt();
    List<String> err = new ArrayList<String>();
    J2clHttpClient client = new J2clHttpClient(attempt, autoScheduler(), 1000, 1);

    client.getText("/x", s -> {}, err::add);
    attempt.last().sink.onTimeout();
    attempt.last().sink.onTimeout();

    Assert.assertEquals(2, attempt.calls.size());
    Assert.assertTrue(err.get(0).contains("Timeout"));
  }

  @Test
  public void cancelStopsPendingRetryAndFiresNoCallback() {
    FakeAttempt attempt = new FakeAttempt();
    List<String> ok = new ArrayList<String>();
    List<String> err = new ArrayList<String>();
    ManualScheduler scheduler = new ManualScheduler();
    J2clHttpClient client = new J2clHttpClient(attempt, scheduler, 1000, 2);

    J2clHttpClient.Request request = client.getText("/x", ok::add, err::add);
    attempt.last().sink.onStatus(500, ""); // schedules a retry (pending, not run)
    request.cancel();
    scheduler.runPending(); // the retry action should no-op because cancelled

    Assert.assertTrue(attempt.last().aborted);
    Assert.assertEquals(1, attempt.calls.size()); // no second attempt started
    Assert.assertTrue(ok.isEmpty());
    Assert.assertTrue(err.isEmpty());
  }

  @Test
  public void cancelAbortsCurrentAttemptAndSuppressesLateSuccess() {
    FakeAttempt attempt = new FakeAttempt();
    List<String> ok = new ArrayList<String>();
    J2clHttpClient client = new J2clHttpClient(attempt, autoScheduler(), 1000, 2);

    J2clHttpClient.Request request = client.getText("/x", ok::add, s -> {});
    request.cancel();
    attempt.last().sink.onStatus(200, "late"); // arrives after cancel

    Assert.assertTrue(attempt.last().aborted);
    Assert.assertTrue("late response must be suppressed", ok.isEmpty());
  }

  @Test
  public void postJsonSendsBodyAndContentType() {
    FakeAttempt attempt = new FakeAttempt();
    J2clHttpClient client = new J2clHttpClient(attempt, autoScheduler(), 1000, 0);

    client.postJson("/y", "{\"k\":1}", s -> {}, s -> {});

    FakeAttempt.Call call = attempt.last();
    Assert.assertEquals("POST", call.method);
    Assert.assertEquals("application/json", call.contentType);
    Assert.assertEquals("{\"k\":1}", call.body);
  }

  private static J2clHttpClient.RetryScheduler autoScheduler() {
    return (delayMs, action) -> action.run();
  }

  private static J2clHttpClient.RetryScheduler countingScheduler(int[] counter) {
    return (delayMs, action) -> {
      counter[0]++;
      action.run();
    };
  }

  private static final class ManualScheduler implements J2clHttpClient.RetryScheduler {
    private Runnable pending;

    @Override
    public void schedule(int delayMs, Runnable action) {
      pending = action;
    }

    void runPending() {
      Runnable p = pending;
      pending = null;
      if (p != null) {
        p.run();
      }
    }
  }

  private static final class FakeAttempt implements J2clHttpClient.SingleAttempt {
    private final List<Call> calls = new ArrayList<Call>();

    static final class Call {
      String method;
      String url;
      String contentType;
      String body;
      int timeoutMs;
      J2clHttpClient.AttemptSink sink;
      boolean aborted;
    }

    @Override
    public Runnable perform(
        String method,
        String url,
        String contentType,
        String body,
        int timeoutMs,
        J2clHttpClient.AttemptSink sink) {
      final Call call = new Call();
      call.method = method;
      call.url = url;
      call.contentType = contentType;
      call.body = body;
      call.timeoutMs = timeoutMs;
      call.sink = sink;
      calls.add(call);
      return () -> call.aborted = true;
    }

    Call last() {
      return calls.get(calls.size() - 1);
    }
  }
}
