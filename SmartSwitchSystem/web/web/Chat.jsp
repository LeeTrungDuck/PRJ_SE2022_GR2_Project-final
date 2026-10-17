<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:if test="${empty requestScope.chatView}">
    <c:redirect url="/MainController"><c:param name="action" value="CHAT"/></c:redirect>
</c:if>
<!DOCTYPE html>
<html lang="en" class="chat-root">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>AI Assistant | SmartSwitchSystem</title>
    <c:if test="${chatView.authenticated and chatView.busy}">
        <c:url var="chatRefreshUrl" value="/MainController"><c:param name="action" value="CHAT"/></c:url>
        <meta http-equiv="refresh" content="2;url=<c:out value='${chatRefreshUrl}'/>">
    </c:if>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/chat.css?v=20261016-chat-enter">
</head>
<body class="chat-frame">
    <header class="chat-header">
        <div class="chat-brand">
            <span class="chat-brand-mark" aria-hidden="true">
                <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M12 3v3M6 7h12a3 3 0 0 1 3 3v7a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3v-7a3 3 0 0 1 3-3Z"/><path d="M8 11v3m8-3v3m-7 3h6"/></svg>
            </span>
            <div><h1>AI Assistant</h1><p>SmartSwitch help, right here.</p></div>
        </div>
        <c:if test="${chatView.authenticated}">
            <form action="${pageContext.request.contextPath}/MainController" method="post">
                <input type="hidden" name="action" value="CHAT">
                <input type="hidden" name="uiOp" value="clear">
                <input type="hidden" name="csrf" value="<c:out value='${chatView.csrf}'/>">
                <input type="hidden" name="requestId" value="<c:out value='${chatView.requestId}'/>">
                <button class="chat-new" type="submit" title="Clear conversation" <c:if test="${chatView.busy}">disabled</c:if>>New chat</button>
            </form>
        </c:if>
    </header>
    <section class="chat-thread" aria-label="Conversation">
        <div class="chat-messages">
            <c:choose>
                <c:when test="${not chatView.authenticated}">
                    <div class="chat-welcome"><h2>Sign in to chat</h2><p><c:out value="${chatView.error}"/></p>
                        <a class="chat-sign-in" target="_top" href="${pageContext.request.contextPath}/web/login.jsp">Go to sign in</a>
                    </div>
                </c:when>
                <c:when test="${empty chatView.messages}">
                    <div class="chat-welcome">
                        <span class="chat-eyebrow">YOUR DASHBOARD GUIDE</span>
                        <h2>How can I help?</h2>
                        <p>Ask about switches, schedules, permissions or your account.</p>
                        <div class="chat-suggestions">
                            <c:forEach var="suggestion" items="${['How do I create a schedule?', 'How do device permissions work?', 'How do I change my password?']}">
                                <form action="${pageContext.request.contextPath}/MainController" method="post">
                                    <input type="hidden" name="action" value="CHAT">
                                    <input type="hidden" name="uiOp" value="send">
                                    <input type="hidden" name="csrf" value="<c:out value='${chatView.csrf}'/>">
                                    <input type="hidden" name="requestId" value="<c:out value='${chatView.requestId}'/>">
                                    <button type="submit" name="question" value="<c:out value='${suggestion}'/>" <c:if test="${not chatView.configured or chatView.busy}">disabled</c:if>><c:out value="${suggestion}"/><span aria-hidden="true">↗</span></button>
                                </form>
                            </c:forEach>
                        </div>
                    </div>
                </c:when>
                <c:otherwise>
                    <c:forEach var="message" items="${chatView.messages}">
                        <article class="chat-message ${message.role eq 'user' ? 'chat-user' : 'chat-assistant'}">
                            <h2 class="chat-message-author">${message.role eq 'user' ? 'You' : 'AI Assistant'}</h2>
                            <p><c:out value="${message.text}"/></p>
                            <c:if test="${not empty message.sources}">
                                <nav class="chat-sources" aria-label="Related pages">
                                    <span>Related pages</span>
                                    <c:forEach var="source" items="${message.sources}">
                                        <c:url var="sourceUrl" value="/MainController"><c:param name="action" value="${source.action}"/></c:url>
                                        <a target="_top" href="<c:out value='${sourceUrl}'/>"><c:out value="${source.title}"/></a>
                                    </c:forEach>
                                </nav>
                            </c:if>
                        </article>
                    </c:forEach>
                </c:otherwise>
            </c:choose>
        </div>
    </section>
    <c:if test="${chatView.authenticated}">
        <footer class="chat-composer">
            <c:choose>
                <c:when test="${chatView.busy}"><p class="chat-notice chat-pending" role="status">Preparing your reply. The conversation will refresh automatically.</p></c:when>
                <c:when test="${not empty chatView.error}"><p class="chat-notice" role="alert"><c:out value="${chatView.error}"/></p></c:when>
                <c:when test="${not chatView.configured}"><p class="chat-notice" role="status">The assistant is not configured yet. Please contact your administrator.</p></c:when>
            </c:choose>
            <form class="chat-send-form" action="${pageContext.request.contextPath}/MainController" method="post">
                <input type="hidden" name="action" value="CHAT">
                <input type="hidden" name="uiOp" value="send">
                <input type="hidden" name="csrf" value="<c:out value='${chatView.csrf}'/>">
                <input type="hidden" name="requestId" value="<c:out value='${chatView.requestId}'/>">
                <label class="chat-sr-only" for="chat-question">Your question. Press Enter to send.</label>
                <input type="text" id="chat-question" name="question" value="<c:out value='${chatView.question}'/>" maxlength="1000" required autocomplete="off" enterkeyhint="send" <c:if test="${chatView.busy}">readonly aria-disabled="true"</c:if> placeholder="Ask about your dashboard…">
                <button class="chat-send" type="submit" aria-label="Send question" title="Send question" <c:if test="${not chatView.configured or chatView.busy}">disabled</c:if>>
                    <svg viewBox="0 0 24 24" width="21" height="21" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="m4 4 17 8-17 8 3-8-3-8Z"/><path d="M7 12h14"/></svg>
                </button>
            </form>
            <p class="chat-footer-note">Uses permitted database records and system help guides.</p>
        </footer>
    </c:if>
</body>
</html>
