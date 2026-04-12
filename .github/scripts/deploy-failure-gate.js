function parseBoolean(value) {
  if (typeof value === "boolean") {
    return value;
  }
  return String(value).toLowerCase() === "true";
}

function classifyDeployFailure({
  deployOutcome = "",
  jobCancelled = false,
  jobFailed = false,
}) {
  if (parseBoolean(jobCancelled)) {
    return { reportFailure: false, reason: "job-cancelled" };
  }

  if (deployOutcome === "failure") {
    return { reportFailure: true, reason: "deploy-step-failed" };
  }

  if (parseBoolean(jobFailed)) {
    return { reportFailure: true, reason: "job-failed-before-deploy" };
  }

  return { reportFailure: false, reason: "no-failure" };
}

module.exports = {
  classifyDeployFailure,
};
