requireAuth();

const codeInput = document.getElementById("code-input");
const languageSelect = document.getElementById("language-select");
const errorEl = document.getElementById("error");
const resultPanel = document.getElementById("result-panel");
const buttons = document.querySelectorAll("button[data-mode]");

async function runMode(mode) {
  const code = codeInput.value.trim();
  errorEl.classList.add("hidden");

  if (!code) {
    errorEl.textContent = "Paste some code first.";
    errorEl.classList.remove("hidden");
    return;
  }

  buttons.forEach((b) => (b.disabled = true));
  resultPanel.innerHTML = `<p class="empty-hint">Thinking...</p>`;

  try {
    const response = await apiFetch("/snippets/review", {
      method: "POST",
      body: JSON.stringify({
        code,
        language: languageSelect.value,
        mode,
      }),
    });
    resultPanel.innerHTML = renderMarkdown(response.result);
  } catch (err) {
    resultPanel.innerHTML = "";
    errorEl.textContent = err.message || "Something went wrong. Try again.";
    errorEl.classList.remove("hidden");
  } finally {
    buttons.forEach((b) => (b.disabled = false));
  }
}

buttons.forEach((btn) => {
  btn.addEventListener("click", () => runMode(btn.getAttribute("data-mode")));
});

// Minimal markdown rendering: fenced code blocks, bold, and paragraphs.
// Not a full markdown parser -- just enough for the LLM's typical response shape.
function renderMarkdown(text) {
  const escaped = escapeHtml(text || "");
  const parts = escaped.split(/```[a-zA-Z]*\n?/);

  return parts
    .map((part, i) => {
      const isCode = i % 2 === 1;
      if (isCode) {
        return `<pre class="code-block"><code>${part.replace(/```$/, "")}</code></pre>`;
      }
      return part
        .split(/\n{2,}/)
        .filter((p) => p.trim())
        .map((p) => `<p>${p.replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>").replace(/\n/g, "<br>")}</p>`)
        .join("");
    })
    .join("");
}

document.getElementById("logout-btn").addEventListener("click", () => {
  clearToken();
  window.location.href = "/login";
});
