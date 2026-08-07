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
    showError("Paste some code first.");
    return;
  }

  buttons.forEach((b) => {
    b.disabled = true;
    b.classList.toggle("mode-active", b.getAttribute("data-mode") === mode);
  });

  resultPanel.innerHTML = `
    <div class="loading-state">
      <span class="spinner" aria-hidden="true"></span>
      <span>${loadingLabel(mode)}</span>
    </div>`;

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
    resultPanel.innerHTML = `<p class="empty-hint">Nothing to show yet.</p>`;
    showError(err.message || "Something went wrong. Try again.");
  } finally {
    buttons.forEach((b) => (b.disabled = false));
  }
}

function showError(message) {
  errorEl.innerHTML = `<span class="error-icon">&#9888;</span><span>${escapeHtml(message)}</span>`;
  errorEl.classList.remove("hidden");
}

function loadingLabel(mode) {
  switch (mode) {
    case "REVIEW":
      return "Reviewing your code...";
    case "EXPLAIN":
      return "Reading through the code...";
    case "FORMAT":
      return "Reformatting...";
    default:
      return "Thinking...";
  }
}

buttons.forEach((btn) => {
  btn.addEventListener("click", () => runMode(btn.getAttribute("data-mode")));
});

// Minimal markdown rendering: fenced code blocks, headings-as-bold severity tags,
// bullet lists, inline code, bold, and paragraphs.
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
      return renderTextBlock(part);
    })
    .join("");
}

function renderTextBlock(block) {
  const lines = block.split("\n");
  let html = "";
  let inList = false;

  const flushList = () => {
    if (inList) {
      html += "</ul>";
      inList = false;
    }
  };

  let paragraphBuffer = [];
  const flushParagraph = () => {
    if (paragraphBuffer.length) {
      html += `<p>${paragraphBuffer.join("<br>")}</p>`;
      paragraphBuffer = [];
    }
  };

  for (const rawLine of lines) {
    const line = rawLine.trim();

    if (!line) {
      flushParagraph();
      flushList();
      continue;
    }

    const listMatch = line.match(/^[-*]\s+(.*)$/);
    if (listMatch) {
      flushParagraph();
      if (!inList) {
        html += "<ul>";
        inList = true;
      }
      html += `<li>${inlineFormat(listMatch[1])}</li>`;
      continue;
    }

    flushList();
    paragraphBuffer.push(inlineFormat(line));
  }

  flushParagraph();
  flushList();
  return html;
}

function inlineFormat(line) {
  let formatted = line
    .replace(/\*\*\[(HIGH|MEDIUM|LOW)\]\*\*/gi, (m, sev) => severityTag(sev))
    .replace(/\*\*Detected language:\*\*\s*([^\n<]+)/i, (m, lang) =>
      `<span class="detected-language">Detected: ${lang.trim()}</span>`)
    .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
    .replace(/`([^`]+)`/g, "<code>$1</code>");
  return formatted;
}

function severityTag(sev) {
  const cls = `severity-${sev.toLowerCase()}`;
  return `<span class="severity-tag ${cls}">${sev.toUpperCase()}</span>`;
}

document.getElementById("logout-btn").addEventListener("click", () => {
  clearToken();
  window.location.href = "/login";
});
