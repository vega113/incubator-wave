const CODERABBIT_LOGIN = "coderabbitai[bot]";
const CODEX_LOGIN = "chatgpt-codex-connector[bot]";
const CODEX_REVIEW_LABEL = "codex-reviewed";
const CODERABBIT_REVIEW_LABEL = "coderabbitai-reviewed";
const REVIEW_WINDOW_MS = 5 * 60 * 1000;

function evaluateCodexReviewGate({
  pullRequest,
  defaultBranchName,
  nowMs = Date.now(),
}) {
  const result = pullRequest
    ? evaluatePullRequestGate({ pullRequest, defaultBranchName, nowMs })
    : failure("Pull request not found");

  return result;
}

function evaluatePullRequestGate({ pullRequest, defaultBranchName, nowMs }) {
  const labels = getLabelNames(pullRequest);
  const latestCommit = getLatestCommit(pullRequest);
  const headRefOid = pullRequest.headRefOid ?? latestCommit?.oid ?? "";
  const statusNodes = getStatusNodes(latestCommit);
  const reviewNodes = getReviewNodes(pullRequest);
  const commentNodes = getCommentNodes(pullRequest);
  const reviewThreadNodes = getReviewThreadNodes(pullRequest);
  const unresolvedThreads = reviewThreadNodes.filter((thread) => !thread.isResolved);
  const codeRabbitSkipped = [...commentNodes, ...reviewNodes].some(isCodeRabbitSkippedSignal);
  const codeRabbitApproved = hasCodeRabbitApproval({
    labels,
    statusNodes,
    codeRabbitSkipped,
  });
  const isStackedPr =
    typeof pullRequest.baseRefName === "string" &&
    pullRequest.baseRefName !== defaultBranchName;
  const codexApproved = hasCodexCoverage({
    labels,
    reviewNodes,
    headRefOid,
    isStackedPr,
  });
  const latestCommitAt = Date.parse(latestCommit?.committedDate ?? "");
  const commitAgeMs = Number.isFinite(latestCommitAt)
    ? nowMs - latestCommitAt
    : Number.POSITIVE_INFINITY;

  let result;

  if (pullRequest.isDraft) {
    result = failure("Draft PRs cannot pass Codex Review Gate");
  } else if (unresolvedThreads.length > 0) {
    result = failure(
      `Pull request has ${unresolvedThreads.length} unresolved review thread(s)`,
    );
  } else if (codexApproved) {
    result = success("Review gate passed via Codex coverage on the current head commit");
  } else if (codeRabbitSkipped) {
    result = failure(
      isStackedPr
        ? "CodeRabbit skipped review on this stacked PR, so explicit Codex coverage on the current head commit is required"
        : "CodeRabbit skipped review on this PR, so explicit Codex coverage is required",
    );
  } else if (isStackedPr) {
    result = failure(
      "Stacked PRs require explicit Codex coverage on the current head commit; CodeRabbit status alone is not enough",
    );
  } else if (!codeRabbitApproved) {
    result = failure(
      `Missing required review signal: ${CODEX_REVIEW_LABEL}, ${CODERABBIT_REVIEW_LABEL}, or successful CodeRabbit status`,
    );
  } else if (commitAgeMs < REVIEW_WINDOW_MS) {
    const remainingMinutes = Math.ceil((REVIEW_WINDOW_MS - commitAgeMs) / 60000);
    result = failure(
      `CodeRabbit is green, but the PR is still inside the 5 minute Codex-review window (${remainingMinutes} minute(s) remaining)`,
    );
  } else {
    result = success("Review gate passed after the 5 minute Codex-review window using CodeRabbit");
  }

  return result;
}

function getLabelNames(pullRequest) {
  return pullRequest.labels?.nodes?.map((label) => label.name) ?? [];
}

function getLatestCommit(pullRequest) {
  return pullRequest.commits?.nodes?.[0]?.commit ?? null;
}

function getStatusNodes(latestCommit) {
  return latestCommit?.statusCheckRollup?.contexts?.nodes ?? [];
}

function getReviewNodes(pullRequest) {
  return pullRequest.reviews?.nodes ?? [];
}

function getCommentNodes(pullRequest) {
  return pullRequest.comments?.nodes ?? [];
}

function getReviewThreadNodes(pullRequest) {
  return pullRequest.reviewThreads?.nodes ?? [];
}

function hasCodeRabbitApproval({ labels, statusNodes, codeRabbitSkipped }) {
  const hasLabel = labels.includes(CODERABBIT_REVIEW_LABEL);
  const hasStatus = statusNodes.some(isSuccessfulCodeRabbitStatus);

  return hasLabel || (hasStatus && !codeRabbitSkipped);
}

function hasCodexCoverage({ labels, reviewNodes, headRefOid, isStackedPr }) {
  const hasLabel = labels.includes(CODEX_REVIEW_LABEL) && !isStackedPr;
  const hasHeadReview = reviewNodes.some((review) => {
    const authorLogin = review.author?.login ?? "";
    const reviewedCommitOid = review.commit?.oid ?? "";
    const acceptedState = review.state === "APPROVED" || review.state === "COMMENTED";

    return (
      authorLogin === CODEX_LOGIN &&
      reviewedCommitOid === headRefOid &&
      acceptedState
    );
  });

  return hasLabel || hasHeadReview;
}

function isSuccessfulCodeRabbitStatus(node) {
  return (
    (node.__typename === "StatusContext" &&
      node.context === "CodeRabbit" &&
      node.state === "SUCCESS") ||
    (node.__typename === "CheckRun" &&
      node.name === "CodeRabbit" &&
      node.conclusion === "SUCCESS")
  );
}

function isCodeRabbitSkippedSignal(note) {
  const authorLogin = note.author?.login ?? "";
  const body = note.body ?? "";

  return authorLogin === CODERABBIT_LOGIN && /review skipped/i.test(body);
}

function failure(message) {
  return { ok: false, message };
}

function success(message) {
  return { ok: true, message };
}

module.exports = {
  evaluateCodexReviewGate,
  evaluatePullRequestGate,
  hasCodexCoverage,
  hasCodeRabbitApproval,
  isCodeRabbitSkippedSignal,
  isSuccessfulCodeRabbitStatus,
};
