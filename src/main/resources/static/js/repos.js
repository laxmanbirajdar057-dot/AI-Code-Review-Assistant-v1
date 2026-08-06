requireAuth();

const repoListEl = document.getElementById("repo-list");
const emptyStateEl = document.getElementById("empty-state");
const errorEl = document.getElementById("error");

let selectedRepoId = null;

async function loadRepos() {
  errorEl.classList.add("hidden");
  try {
    const repos = await apiFetch("/repos");
    renderRepos(repos);
  } catch (err) {
    errorEl.textContent = err.message || "Could not load repos.";
    errorEl.classList.remove("hidden");
  }
}

function renderRepos(repos) {
  document.getElementById("repos-heading").textContent =
    repos.length === 0 ? "Connected repos" : `Connected repos (${repos.length})`;
  repoListEl.innerHTML = "";
  emptyStateEl.classList.toggle("hidden", repos.length > 0);

  repos.forEach((repo) => {
    const row = document.createElement("div");
    row.className = "repo-row";
    row.innerHTML = `
      <div style="display: flex; align-items: center; gap: 10px;">
        <span class="repo-icon">&lt;/&gt;</span>
        <div>
          <div class="repo-url">${escapeHtml(repo.repoUrl)}</div>
          <div class="repo-meta">${escapeHtml(repo.webhookUrl)}</div>
        </div>
      </div>
      <div style="display: flex; gap: 8px;">
        <button data-action="view" data-id="${repo.id}">View review</button>
        <button data-action="delete" data-id="${repo.id}">Remove</button>
      </div>
    `;
    repoListEl.appendChild(row);
  });
}

repoListEl.addEventListener("click", (e) => {
  const btn = e.target.closest("button[data-action]");
  if (!btn) return;
  const id = btn.getAttribute("data-id");

  if (btn.dataset.action === "view") {
    selectedRepoId = id;
    document.getElementById("pr-modal").classList.remove("hidden");
  }

  if (btn.dataset.action === "delete") {
    deleteRepo(id);
  }
});

async function deleteRepo(id) {
  if (!confirm("Remove this repo? Its webhook will stop sending reviews.")) return;
  try {
    await apiFetch(`/repos/${id}`, { method: "DELETE" });
    loadRepos();
  } catch (err) {
    errorEl.textContent = err.message || "Could not remove repo.";
    errorEl.classList.remove("hidden");
  }
}

// Add repo modal
const addRepoModal = document.getElementById("add-repo-modal");
const addRepoForm = document.getElementById("add-repo-form");
const addRepoSuccess = document.getElementById("add-repo-success");
const addRepoError = document.getElementById("add-repo-error");

document.getElementById("add-repo-btn").addEventListener("click", () => {
  addRepoForm.classList.remove("hidden");
  addRepoSuccess.classList.add("hidden");
  addRepoError.classList.add("hidden");
  document.getElementById("repo-url").value = "";
  document.getElementById("webhook-secret").value = "";
  addRepoModal.classList.remove("hidden");
});

document.getElementById("cancel-add-btn").addEventListener("click", () => {
  addRepoModal.classList.add("hidden");
});

document.getElementById("submit-add-btn").addEventListener("click", async () => {
  const repoUrl = document.getElementById("repo-url").value.trim();
  const webhookSecret = document.getElementById("webhook-secret").value.trim();
  addRepoError.classList.add("hidden");

  if (!repoUrl || !webhookSecret) {
    addRepoError.textContent = "Both fields are required.";
    addRepoError.classList.remove("hidden");
    return;
  }

  try {
    const repo = await apiFetch("/repos", {
      method: "POST",
      body: JSON.stringify({ repoUrl, webhookSecret }),
    });
    document.getElementById("webhook-url-display").textContent = repo.webhookUrl;
    addRepoForm.classList.add("hidden");
    addRepoSuccess.classList.remove("hidden");
    loadRepos();
  } catch (err) {
    addRepoError.textContent = err.message || "Could not add repo. Check the URL and try again.";
    addRepoError.classList.remove("hidden");
  }
});

document.getElementById("done-btn").addEventListener("click", () => {
  addRepoModal.classList.add("hidden");
});

// PR review modal
document.getElementById("cancel-pr-btn").addEventListener("click", () => {
  document.getElementById("pr-modal").classList.add("hidden");
});

document.getElementById("go-pr-btn").addEventListener("click", () => {
  const prNumber = document.getElementById("pr-number").value;
  if (!prNumber || !selectedRepoId) return;
  window.location.href = `/review.html?repoId=${selectedRepoId}&prNumber=${prNumber}`;
});

document.getElementById("logout-btn").addEventListener("click", () => {
  clearToken();
  window.location.href = "/login";
});

loadRepos();