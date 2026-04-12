const assert = require("node:assert/strict");
const test = require("node:test");

const { classifyDeployFailure } = require("./deploy-failure-gate");

test("reports a failure when a pre-deploy step fails before the deploy step runs", () => {
  assert.deepEqual(
    classifyDeployFailure({
      deployOutcome: "skipped",
      jobCancelled: "false",
      jobFailed: "true",
    }),
    { reason: "job-failed-before-deploy", reportFailure: true },
  );
});

test("reports a failure when the deploy step itself fails", () => {
  assert.deepEqual(
    classifyDeployFailure({
      deployOutcome: "failure",
      jobCancelled: false,
      jobFailed: true,
    }),
    { reason: "deploy-step-failed", reportFailure: true },
  );
});

test("does not report a failure when the run is cancelled by concurrency", () => {
  assert.deepEqual(
    classifyDeployFailure({
      deployOutcome: "skipped",
      jobCancelled: "true",
      jobFailed: "false",
    }),
    { reason: "job-cancelled", reportFailure: false },
  );
});

test("does not report a failure for a successful deploy", () => {
  assert.deepEqual(
    classifyDeployFailure({
      deployOutcome: "success",
      jobCancelled: false,
      jobFailed: false,
    }),
    { reason: "no-failure", reportFailure: false },
  );
});
