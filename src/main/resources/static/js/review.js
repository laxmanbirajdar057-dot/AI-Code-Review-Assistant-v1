requireAuth();

const params = new URLSearchParams(window.location.search);
const repoId = params.get("repoId");
const prNumber = params.get("prNumber");

const titleEl = document.getElementById("review-title");
const statusBadgeEl = document.getElementById("status-badge");
const errorEl = document.getElementById("error");
const loadingEl = document.getElementById("loading");
const containerEl = document.getElementById("comments-container");

titleEl.textContent = `Pull request #${prNumber}`;

async function loadReview() {
  try {
    const review = await apiFetch(`/reviews/${repoId}/${prNumber}`);
    loadingEl.classList.add("hidden");
    renderStatus(review.status);
    renderComments(review.comments || []);
  } catch (err) {
    loadingEl.classList.add("hidden");
    errorEl.textContent = err.message || "Could not load this review.";
    errorEl.classList.remove("hidden");
  }
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

function renderComments(comments) {
  if (comments.length === 0) {
    containerEl.innerHTML = `<div class="empty-state">
      <p>No comments on this review yet.</p>
      <p style="font-size: 12.5px;">If the review just triggered, check back shortly.</p>
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
  window.location.href = "/login.html";
});

loadReview();
