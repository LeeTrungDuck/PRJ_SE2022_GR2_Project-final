(function () {
    "use strict";

    function initChatWidget() {
        var widget = document.querySelector("[data-ai-chat]");
        if (!widget) return;

        var launcher = widget.querySelector("[data-ai-chat-open]");
        var close = widget.querySelector("[data-ai-chat-close]");
        var panel = widget.querySelector("[data-ai-chat-panel]");
        var form = widget.querySelector("[data-ai-chat-form]");
        var input = widget.querySelector("[data-ai-chat-input]");
        var messages = widget.querySelector("[data-ai-chat-messages]");

        function setOpen(open) {
            widget.classList.toggle("is-open", open);
            launcher.setAttribute("aria-expanded", String(open));
            panel.setAttribute("aria-hidden", String(!open));
            if (open) input.focus();
        }

        function addMessage(text, type) {
            var message = document.createElement("div");
            message.className = "ai-chat-message ai-chat-message--" + type;
            message.textContent = text;
            messages.appendChild(message);
            messages.scrollTop = messages.scrollHeight;
        }

        launcher.addEventListener("click", function () { setOpen(!widget.classList.contains("is-open")); });
        close.addEventListener("click", function () { setOpen(false); });
        document.addEventListener("keydown", function (event) {
            if (event.key === "Escape" && widget.classList.contains("is-open")) setOpen(false);
        });
        input.addEventListener("keydown", function (event) {
            if (event.key === "Enter" && !event.shiftKey) {
                event.preventDefault();
                form.requestSubmit();
            }
        });

        form.addEventListener("submit", function (event) {
            event.preventDefault();
            var text = input.value.trim();
            if (!text) return;
            addMessage(text, "user");
            input.value = "";
            input.style.height = "";
            var sendButton = form.querySelector("button[type='submit']");
            sendButton.disabled = true;
            addMessage("Dang tra cuu...", "assistant");
            fetch((window.SmartSwitchContextPath || "") + "/chat", {
                method: "POST",
                headers: {"Content-Type": "application/x-www-form-urlencoded; charset=UTF-8"},
                body: new URLSearchParams({userQuery: text})
            }).then(function (response) {
                return response.text().then(function (body) {
                    var data;
                    try {
                        data = body ? JSON.parse(body) : {};
                    } catch (parseError) {
                        throw new Error("Phản hồi máy chủ không phải JSON hợp lệ (HTTP " + response.status + ").");
                    }
                    if (!response.ok) throw new Error(data.error || "Khong the ket noi toi tro ly AI.");
                    return data;
                });
            }).then(function (data) {
                messages.lastElementChild.remove();
                addMessage(data.answer || "Tro ly AI khong co cau tra loi.", "assistant");
            }).catch(function (error) {
                messages.lastElementChild.remove();
                addMessage(error.message || "Khong the ket noi toi tro ly AI.", "assistant");
            }).finally(function () {
                sendButton.disabled = false;
                input.focus();
            });
        });
    }

    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", initChatWidget);
    else initChatWidget();
}());
