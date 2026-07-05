package org.waveprotocol.box.j2cl.transport;

import elemental2.dom.DomGlobal;
import elemental2.dom.XMLHttpRequest;

/**
 * #1276: the single, lifecycle-aware HTTP primitive for text/JSON fetches in the
 * J2CL UI, replacing the copy-pasted {@code XMLHttpRequest + onload + status
 * check} pattern (gateways, sandbox, attachment clients).
 *
 * <p>Adds what the ad-hoc sites lacked: a per-request timeout, bounded retry on
 * 5xx / network errors / timeouts, and cancellation — {@link #getText}/{@link
 * #postJson} return a {@link Request} handle whose {@link Request#cancel()}
 * aborts the in-flight attempt and suppresses any pending retry, so a late
 * response can never be processed after the owning controller is destroyed
 * (coordinates with the #1268 dispose contract).
 *
 * <p>The physical HTTP attempt is injected via {@link SingleAttempt} so the
 * retry/cancel orchestration is unit-testable off-browser; the default is a real
 * {@link XMLHttpRequest}.
 */
public final class J2clHttpClient {

  /** Default per-attempt timeout. */
  public static final int DEFAULT_TIMEOUT_MS = 15000;

  /** Default number of retries for 5xx / network / timeout failures. */
  public static final int DEFAULT_MAX_RETRIES = 2;

  private static final int INITIAL_RETRY_DELAY_MS = 200;
  private static final int MAX_RETRY_DELAY_MS = 2000;

  /** Delivered a successful body. */
  @FunctionalInterface
  public interface TextCallback {
    void onSuccess(String responseText);
  }

  /** Delivered a terminal failure message (after retries are exhausted / non-retryable). */
  @FunctionalInterface
  public interface FailureCallback {
    void onFailure(String message);
  }

  /** Cancellable handle, consistent with the Subscription / Disposable contract. */
  public interface Request {
    void cancel();
  }

  /** Sink for the outcome of a single physical attempt. Exactly one method fires. */
  public interface AttemptSink {
    void onStatus(int status, String responseText);

    void onNetworkError();

    void onTimeout();
  }

  /** Performs one physical HTTP attempt; returns a Runnable that aborts it. */
  public interface SingleAttempt {
    Runnable perform(
        String method,
        String url,
        String contentType,
        String body,
        int timeoutMs,
        AttemptSink sink);
  }

  /** Schedules a delayed retry (default: {@code DomGlobal.setTimeout}). */
  public interface RetryScheduler {
    void schedule(int delayMs, Runnable action);
  }

  private final SingleAttempt attempt;
  private final RetryScheduler scheduler;
  private final int timeoutMs;
  private final int maxRetries;

  public J2clHttpClient() {
    this(DEFAULT_TIMEOUT_MS, DEFAULT_MAX_RETRIES);
  }

  public J2clHttpClient(int timeoutMs, int maxRetries) {
    this(defaultAttempt(), defaultScheduler(), timeoutMs, maxRetries);
  }

  /** Test seam: inject the physical attempt + retry scheduler. */
  J2clHttpClient(
      SingleAttempt attempt, RetryScheduler scheduler, int timeoutMs, int maxRetries) {
    this.attempt = attempt;
    this.scheduler = scheduler;
    this.timeoutMs = timeoutMs;
    this.maxRetries = Math.max(0, maxRetries);
  }

  /** GET a text/JSON body. */
  public Request getText(String url, TextCallback onSuccess, FailureCallback onFailure) {
    return run("GET", url, null, null, onSuccess, onFailure);
  }

  /** POST a JSON body and read a text/JSON response. */
  public Request postJson(
      String url, String body, TextCallback onSuccess, FailureCallback onFailure) {
    return run("POST", url, "application/json", body, onSuccess, onFailure);
  }

  private Request run(
      String method,
      String url,
      String contentType,
      String body,
      TextCallback onSuccess,
      FailureCallback onFailure) {
    RequestHandle handle = new RequestHandle();
    startAttempt(handle, method, url, contentType, body, onSuccess, onFailure, 0);
    return handle;
  }

  private void startAttempt(
      RequestHandle handle,
      String method,
      String url,
      String contentType,
      String body,
      TextCallback onSuccess,
      FailureCallback onFailure,
      int attemptNo) {
    if (handle.cancelled) {
      return;
    }
    Runnable abort =
        attempt.perform(
            method,
            url,
            contentType,
            body,
            timeoutMs,
            new AttemptSink() {
              @Override
              public void onStatus(int status, String responseText) {
                if (handle.cancelled) {
                  return;
                }
                if (status >= 200 && status < 300) {
                  onSuccess.onSuccess(responseText);
                  return;
                }
                if (status >= 500 && attemptNo < maxRetries) {
                  scheduleRetry(handle, method, url, contentType, body, onSuccess, onFailure, attemptNo);
                  return;
                }
                onFailure.onFailure("HTTP " + status + " for " + url);
              }

              @Override
              public void onNetworkError() {
                retryOrFail(
                    handle, method, url, contentType, body, onSuccess, onFailure, attemptNo,
                    "Network failure for " + url);
              }

              @Override
              public void onTimeout() {
                retryOrFail(
                    handle, method, url, contentType, body, onSuccess, onFailure, attemptNo,
                    "Timeout for " + url);
              }
            });
    handle.currentAbort = abort;
  }

  private void retryOrFail(
      RequestHandle handle,
      String method,
      String url,
      String contentType,
      String body,
      TextCallback onSuccess,
      FailureCallback onFailure,
      int attemptNo,
      String terminalMessage) {
    if (handle.cancelled) {
      return;
    }
    if (attemptNo < maxRetries) {
      scheduleRetry(handle, method, url, contentType, body, onSuccess, onFailure, attemptNo);
      return;
    }
    onFailure.onFailure(terminalMessage);
  }

  private void scheduleRetry(
      RequestHandle handle,
      String method,
      String url,
      String contentType,
      String body,
      TextCallback onSuccess,
      FailureCallback onFailure,
      int attemptNo) {
    int nextAttempt = attemptNo + 1;
    scheduler.schedule(
        retryDelayMs(attemptNo),
        () -> startAttempt(handle, method, url, contentType, body, onSuccess, onFailure, nextAttempt));
  }

  private static int retryDelayMs(int attemptNo) {
    int delay = INITIAL_RETRY_DELAY_MS << attemptNo;
    return Math.min(delay, MAX_RETRY_DELAY_MS);
  }

  private static final class RequestHandle implements Request {
    private boolean cancelled;
    private Runnable currentAbort;

    @Override
    public void cancel() {
      cancelled = true;
      Runnable abort = currentAbort;
      currentAbort = null;
      if (abort != null) {
        abort.run();
      }
    }
  }

  private static SingleAttempt defaultAttempt() {
    return (method, url, contentType, body, timeoutMs, sink) -> {
      XMLHttpRequest request = new XMLHttpRequest();
      request.open(method, url);
      if (contentType != null) {
        request.setRequestHeader("Content-Type", contentType);
      }
      if (timeoutMs > 0) {
        request.timeout = timeoutMs;
      }
      request.onload = event -> sink.onStatus(request.status, request.responseText);
      request.onerror =
          event -> {
            sink.onNetworkError();
            return null;
          };
      request.ontimeout =
          event -> {
            sink.onTimeout();
          };
      if (body == null) {
        request.send();
      } else {
        request.send(body);
      }
      return () -> request.abort();
    };
  }

  private static RetryScheduler defaultScheduler() {
    return (delayMs, action) -> {
      DomGlobal.setTimeout(ignored -> action.run(), delayMs);
    };
  }
}
