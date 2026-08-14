requireAuth();

const params = new URLSearchParams(window.location.search);
const repoId = params.get("repoId");
const prNumber = params.get("prNumber");

const titleEl = document.getElementById("review-title");
const statusBadgeEl = document.getElementById("status-badge");
const errorEl = document.getElementById("error");
const loadingEl = document.getElementById("loading");
const statsBarEl = document.getElementById("stats-bar");
const containerEl = document.getElementById("comments-container");
const scoreBadgeEl = document.getElementById("score-badge");

let allComments = [];
let activeFilter = "ALL";

titleEl.textContent = `Pull request #${prNumber}`;

async function loadReview() {
  try {
    const review = await apiFetch(`/reviews/${repoId}/${prNumber}`);
    loadingEl.classList.add("hidden");
    renderStatus(review.status);
    renderScore(review.overallScore, review.riskLevel);
    allComments = review.comments || [];
    renderStats(allComments);
    renderComments(filteredComments());
  } catch (err) {
    loadingEl.classList.add("hidden");
    errorEl.textContent = err.message || "Could not load this review.";
    errorEl.classList.remove("hidden");
  }
}

function renderScore(overallScore, riskLevel) {
  if (overallScore === null || overallScore === undefined) {
    scoreBadgeEl.classList.add("hidden");
    return;
  }
  scoreBadgeEl.classList.remove("hidden");
  scoreBadgeEl.textContent = `Score ${overallScore}/100 · ${riskLevel} risk`;
  scoreBadgeEl.className = "badge " + (riskLevel === "HIGH" ? "badge-critical" : riskLevel === "MEDIUM" ? "badge-medium" : "badge-low");
}

function renderStatus(status) {
  statusBadgeEl.textContent = status;
  if (status === "FAILED") {
    statusBadgeEl.style.background = "var(--danger-bg)";
    statusBadgeEl.style.color = "var(--danger)";
  } else if (status === "COMPLETED") {
    statusBadgeEl.style.background = "#ecfdf5";
    statusBadgeEl.style.color = "var(--success)";
  }
}

function renderStats(comments) {
  if (comments.length === 0) {
    statsBarEl.classList.add("hidden");
    return;
  }

  const counts = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0, INFO: 0 };
  let resolvedCount = 0;
  comments.forEach((c) => {
    const sev = (c.severity || "").toUpperCase();
    if (counts[sev] !== undefined) counts[sev]++;
    if (c.resolved) resolvedCount++;
  });

  const filters = [
    { key: "ALL", label: `All (${comments.length})` },
    { key: "CRITICAL", label: `Critical (${counts.CRITICAL})` },
    { key: "HIGH", label: `High (${counts.HIGH})` },
    { key: "MEDIUM", label: `Medium (${counts.MEDIUM})` },
    { key: "LOW", label: `Low (${counts.LOW})` },
    { key: "INFO", label: `Info (${counts.INFO})` },
    { key: "UNRESOLVED", label: `Unresolved (${comments.length - resolvedCount})` },
  ];

  statsBarEl.classList.remove("hidden");
  statsBarEl.innerHTML = `
    <div class="stats-cards">
      <div class="stat-card"><span class="stat-value">${comments.length}</span><span class="stat-label">Total issues</span></div>
      <div class="stat-card"><span class="stat-value" style="color: var(--danger)">${counts.CRITICAL + counts.HIGH}</span><span class="stat-label">Critical + High</span></div>
      <div class="stat-card"><span class="stat-value">${resolvedCount}</span><span class="stat-label">Resolved</span></div>
    </div>
    <div class="filter-tabs">
      ${filters
        .map(
          (f) => `<button class="filter-tab${f.key === activeFilter ? " active" : ""}" data-filter="${f.key}">${f.label}</button>`
        )
        .join("")}
    </div>
  `;
}

function filteredComments() {
  if (activeFilter === "ALL") return allComments;
  if (activeFilter === "UNRESOLVED") return allComments.filter((c) => !c.resolved);
  return allComments.filter((c) => (c.severity || "").toUpperCase() === activeFilter);
}

statsBarEl.addEventListener("click", (e) => {
  const btn = e.target.closest("button[data-filter]");
  if (!btn) return;
  activeFilter = btn.getAttribute("data-filter");
  renderStats(allComments);
  renderComments(filteredComments());
});

function renderComments(comments) {
  if (comments.length === 0) {
    const message =
      allComments.length === 0
        ? { title: "No comments on this review yet.", sub: "If the review just triggered, check back shortly." }
        : { title: "No issues match this filter.", sub: "Try a different tab above." };
    containerEl.innerHTML = `<div class="empty-state">
      <p>${message.title}</p>
      <p style="font-size: 12.5px;">${message.sub}</p>
    </div>`;
    return;
  }

  const byFile = {};
  comments.forEach((c) => {
    if (!byFile[c.file]) byFile[c.file] = [];
    byFile[c.file].push(c);
  });

  containerEl.innerHTML = "";
  Object.entries(byFile).forEach(([file, fileComments]) => {
    const group = document.createElement("div");
    group.className = "file-group";
    group.innerHTML = `<div class="file-group-title">${escapeHtml(file)}</div>`;

    fileComments
      .sort((a, b) => a.line - b.line)
      .forEach((comment) => {
        const card = document.createElement("div");
        card.className = `comment-card${comment.resolved ? " resolved" : ""}`;
        card.innerHTML = `
          <div class="comment-header">
            <span class="${severityBadgeClass(comment.severity)}">${escapeHtml(comment.severity)}</span>
            <span>line ${comment.line}</span>
          </div>
          <p class="comment-message">${escapeHtml(comment.message)}</p>
          <button data-id="${comment.id ?? ""}" data-resolved="${comment.resolved}">
            ${comment.resolved ? "Mark unresolved" : "Mark resolved"}
          </button>
        `;
        group.appendChild(card);
      });

    containerEl.appendChild(group);
  });
}

containerEl.addEventListener("click", async (e) => {
  const btn = e.target.closest("button[data-id]");
  if (!btn) return;
  const id = btn.getAttribute("data-id");
  const nextResolved = btn.getAttribute("data-resolved") !== "true";

  btn.disabled = true;
  try {
    await apiFetch(`/reviews/comments/${id}`, {
      method: "PATCH",
      body: JSON.stringify({ resolved: nextResolved }),
    });
    loadReview();
  } catch (err) {
    errorEl.textContent = err.message || "Could not update this comment.";
    errorEl.classList.remove("hidden");
    btn.disabled = false;
  }
});

document.getElementById("logout-btn").addEventListener("click", () => {
  clearToken();
  window.location.href = "/login";
});

loadReview();