// ClassDrop Browser Client
(function () {
    let authToken = null;
    let currentRoomCode = null;
    let deviceName = null;
    let pollInterval = null;

    // DOM Elements
    const joinSection = document.getElementById("join-section");
    const roomSection = document.getElementById("room-section");
    const joinForm = document.getElementById("join-form");
    const roomCodeInput = document.getElementById("room-code");
    const displayNameInput = document.getElementById("display-name");
    const joinError = document.getElementById("join-error");
    const connectedUser = document.getElementById("connected-user");
    const sessionCodeBadge = document.getElementById("session-code-badge");

    const dropZone = document.getElementById("drop-zone");
    const fileInput = document.getElementById("file-input");
    const uploadProgressList = document.getElementById("upload-progress-list");
    const fileListContainer = document.getElementById("file-list");
    const btnRefreshFiles = document.getElementById("btn-refresh-files");

    const clipboardInput = document.getElementById("clipboard-input");
    const clipboardLang = document.getElementById("clipboard-lang");
    const btnPostClipboard = document.getElementById("btn-post-clipboard");
    const clipboardList = document.getElementById("clipboard-list");

    const btnToggleQr = document.getElementById("btn-toggle-qr");
    const qrContainer = document.getElementById("qr-container");

    if (btnToggleQr && qrContainer) {
        btnToggleQr.addEventListener("click", () => {
            qrContainer.style.display = (qrContainer.style.display === "none") ? "block" : "none";
        });
    }

    // Auto-detect room code from URL: /r/{code}
    const pathParts = window.location.pathname.split("/");
    if (pathParts[1] === "r" && pathParts[2] && pathParts[2].length === 6) {
        roomCodeInput.value = pathParts[2];
        if (displayNameInput) displayNameInput.focus();
    }

    // Check query params: ?code=123456
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.has("code")) {
        roomCodeInput.value = urlParams.get("code");
    }

    // Join Form Submission
    joinForm.addEventListener("submit", async function (e) {
        e.preventDefault();
        joinError.style.display = "none";

        const code = roomCodeInput.value.trim();
        const name = displayNameInput.value.trim();

        if (code.length !== 6) {
            showError("Room code must be exactly 6 digits.");
            return;
        }

        try {
            const resp = await fetch("/join", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ roomCode: code, deviceName: name })
            });

            const data = await resp.json();
            if (!resp.ok) {
                showError(data.message || "Failed to join room.");
                return;
            }

            // Success
            authToken = data.token;
            currentRoomCode = code;
            deviceName = data.deviceName;

            // Keep phone awake during transfers
            try {
                if (typeof NoSleep !== "undefined") {
                    const noSleep = new NoSleep();
                    noSleep.enable();
                }
            } catch (_) {}

            connectedUser.textContent = `Joined as ${deviceName}`;
            sessionCodeBadge.textContent = `Code: ${currentRoomCode}`;

            joinSection.style.display = "none";
            roomSection.style.display = "block";

            // Start polling for files & clipboard
            pollRoomData();
            pollInterval = setInterval(pollRoomData, 3000);

            console.log("ClassDrop: Joined successfully with token", authToken);
        } catch (err) {
            showError("Could not connect to host. Check Wi-Fi / Hotspot.");
        }
    });

    // Drag-and-Drop Listeners
    if (dropZone) {
        ['dragenter', 'dragover'].forEach(eventName => {
            dropZone.addEventListener(eventName, (e) => {
                e.preventDefault();
                e.stopPropagation();
                dropZone.classList.add('dragover');
            }, false);
        });

        ['dragleave', 'drop'].forEach(eventName => {
            dropZone.addEventListener(eventName, (e) => {
                e.preventDefault();
                e.stopPropagation();
                dropZone.classList.remove('dragover');
            }, false);
        });

        dropZone.addEventListener('drop', (e) => {
            const files = e.dataTransfer.files;
            if (files && files.length > 0) {
                handleFilesUpload(files);
            }
        });
    }

    if (fileInput) {
        fileInput.addEventListener('change', (e) => {
            if (fileInput.files && fileInput.files.length > 0) {
                handleFilesUpload(fileInput.files);
                fileInput.value = ""; // Reset
            }
        });
    }

    if (btnRefreshFiles) {
        btnRefreshFiles.addEventListener('click', () => {
            pollRoomData();
        });
    }

    function handleFilesUpload(files) {
        for (let i = 0; i < files.length; i++) {
            uploadSingleFile(files[i]);
        }
    }

    function uploadSingleFile(file) {
        const row = document.createElement("div");
        row.className = "progress-row";
        row.innerHTML = `
            <div class="progress-info">
                <span class="file-name">${escapeHtml(file.name)}</span>
                <span class="file-status">Uploading (0%)...</span>
            </div>
            <div class="progress-bar-track">
                <div class="progress-bar-fill" style="width: 0%"></div>
            </div>
        `;
        uploadProgressList.appendChild(row);

        const statusLabel = row.querySelector(".file-status");
        const fillBar = row.querySelector(".progress-bar-fill");

        const xhr = new XMLHttpRequest();
        const formData = new FormData();
        formData.append("file", file);

        xhr.open("POST", "/upload");
        xhr.setRequestHeader("X-ClassDrop-Token", authToken);

        xhr.upload.onprogress = function (e) {
            if (e.lengthComputable) {
                const percent = Math.round((e.loaded / e.total) * 100);
                fillBar.style.width = percent + "%";
                statusLabel.textContent = `Uploading (${percent}%)...`;
            }
        };

        xhr.onload = function () {
            if (xhr.status === 200) {
                fillBar.style.width = "100%";
                fillBar.style.backgroundColor = "#22c55e";
                statusLabel.textContent = "Uploaded successfully!";
                setTimeout(() => {
                    row.remove();
                    fetchFiles();
                }, 1500);
            } else {
                fillBar.style.backgroundColor = "#ef4444";
                try {
                    const err = JSON.parse(xhr.responseText);
                    statusLabel.textContent = "Failed: " + (err.message || xhr.statusText);
                } catch (_) {
                    statusLabel.textContent = "Upload failed (" + xhr.status + ")";
                }
            }
        };

        xhr.onerror = function () {
            fillBar.style.backgroundColor = "#ef4444";
            statusLabel.textContent = "Network error during upload.";
        };

        xhr.send(formData);
    }

    // Shared Clipboard Functions
    if (btnPostClipboard) {
        btnPostClipboard.addEventListener("click", async function () {
            const body = clipboardInput.value.trim();
            if (!body) return;

            const lang = clipboardLang.value;

            try {
                const resp = await fetch("/clipboard", {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json",
                        "X-ClassDrop-Token": authToken
                    },
                    body: JSON.stringify({ body: body, language: lang })
                });

                if (resp.ok) {
                    clipboardInput.value = "";
                    fetchClipboard();
                } else {
                    const err = await resp.json();
                    alert("Clipboard error: " + (err.message || "Failed to post snippet"));
                }
            } catch (err) {
                console.warn("ClassDrop: Failed to post snippet:", err);
            }
        });
    }

    function pollRoomData() {
        fetchFiles();
        fetchClipboard();
    }

    async function fetchFiles() {
        if (!authToken) return;

        try {
            const resp = await fetch("/files", {
                headers: { "X-ClassDrop-Token": authToken }
            });

            if (resp.status === 410) {
                showError("Session has ended by the host.");
                if (pollInterval) clearInterval(pollInterval);
                return;
            }

            if (!resp.ok) return;

            const files = await resp.json();
            renderFileList(files);
        } catch (err) {
            console.warn("ClassDrop: Failed to poll files:", err);
        }
    }

    async function fetchClipboard() {
        if (!authToken) return;

        try {
            const resp = await fetch("/clipboard", {
                headers: { "X-ClassDrop-Token": authToken }
            });

            if (resp.status === 410) {
                return;
            }

            if (!resp.ok) return;

            const snippets = await resp.json();
            renderClipboardList(snippets);
        } catch (err) {
            console.warn("ClassDrop: Failed to poll clipboard:", err);
        }
    }

    function renderFileList(files) {
        if (!files || files.length === 0) {
            fileListContainer.innerHTML = '<p class="empty-hint">No files shared yet. Be the first to drop one!</p>';
            return;
        }

        let html = "";
        files.forEach(f => {
            const icon = f.kind === "IMAGE" ? "🖼️" : (f.kind === "SNIPPET" ? "💻" : "📄");
            const sizeStr = formatBytes(f.sizeBytes);
            const downloadUrl = `/download/${encodeURIComponent(f.id)}?token=${encodeURIComponent(authToken)}`;

            html += `
                <div class="file-item">
                    <div class="file-item-left">
                        <span class="file-kind-icon">${icon}</span>
                        <div class="file-details">
                            <span class="file-title">${escapeHtml(f.originalName)}</span>
                            <span class="file-meta">${sizeStr} &bull; ${f.kind}</span>
                        </div>
                    </div>
                    <a href="${downloadUrl}" class="btn btn-primary btn-small download-btn" download="${escapeHtml(f.originalName)}">
                        ⬇ Download
                    </a>
                </div>
            `;
        });

        fileListContainer.innerHTML = html;
    }

    function renderClipboardList(snippets) {
        if (!snippets || snippets.length === 0) {
            clipboardList.innerHTML = '<p class="empty-hint" style="padding: 10px;">No snippets shared yet.</p>';
            return;
        }

        let html = "";
        snippets.slice().reverse().forEach(s => {
            const langBadge = s.language ? s.language.toUpperCase() : "TEXT";
            html += `
                <div class="snippet-card">
                    <div class="snippet-header">
                        <span class="snippet-meta"><strong>${escapeHtml(s.deviceName)}</strong> &bull; <span class="badge badge-subtle">${escapeHtml(langBadge)}</span></span>
                        <button class="btn btn-secondary btn-small copy-btn" onclick="copySnippetText(this)">Copy</button>
                    </div>
                    <pre class="snippet-body"><code>${escapeHtml(s.body)}</code></pre>
                </div>
            `;
        });

        clipboardList.innerHTML = html;
    }

    window.copySnippetText = function (btn) {
        const pre = btn.closest(".snippet-card").querySelector("code");
        if (pre) {
            navigator.clipboard.writeText(pre.textContent).then(() => {
                const orig = btn.textContent;
                btn.textContent = "Copied!";
                setTimeout(() => { btn.textContent = orig; }, 1500);
            }).catch(() => {
                alert("Failed to copy text");
            });
        }
    };

    function formatBytes(bytes) {
        if (bytes === 0) return "0 B";
        const k = 1024;
        const sizes = ["B", "KB", "MB", "GB"];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + " " + sizes[i];
    }

    function showError(msg) {
        joinError.textContent = msg;
        joinError.style.display = "block";
    }

    function escapeHtml(text) {
        if (!text) return "";
        return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
    }
})();
