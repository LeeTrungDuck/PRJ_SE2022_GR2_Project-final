
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
    <%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
        <%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
            <c:if test="${empty sessionScope.LOGIN_USER or empty requestScope.ui}">
                <c:redirect url="/MainController">
                    <c:param name="action" value="PERMISSION_MANAGER" />
                </c:redirect>
            </c:if>
            <c:set var="activePage" value="PERMISSION_MANAGER" scope="request" />
            <!DOCTYPE html>
            <html lang="en">

            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Device Permissions | ESP32 IoT Hub</title>
                <link rel="preconnect" href="https://fonts.googleapis.com">
                <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
                <link
                    href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap"
                    rel="stylesheet">
                <link rel="stylesheet" href="${pageContext.request.contextPath}/css/userManagerCss.css">
                <link rel="stylesheet" href="${pageContext.request.contextPath}/css/permissionManagerCss.css">
                <link rel="stylesheet" href="${pageContext.request.contextPath}/css/noJsUi.css?v=1">
            </head>

            <body class="user-manager-page permission-manager-page">
                <div class="ui-shell" ${ui.dialog ? 'inert' : '' }>
                    <%@ include file="sideBar.jspf" %>
                        
                        <main class="um-main pm-main" id="main-content">
                            <c:if test="${not empty uiMessage}">
                                <p class="ui-notice" role="status">
                                    <c:out value="${uiMessage}" />
                                </p>
                            </c:if>
                            <c:if test="${not empty uiError and not ui.dialog}">
                                <p class="ui-notice ui-error" role="alert">
                                    <c:out value="${uiError}" />
                                </p>
                            </c:if>
                            <header class="pm-page-heading">
                                <h1>Device Permissions <span>(Permission Management)</span>
                                </h1>
                                <p>Configure view access (<code>canView</code>) and control access (<code>canControl</code>)
                                    for each Admin, Operator or Viewer account on individual switches.</p>
                            </header>
                            <c:if test="${empty ui.activeViewer.id}"><p class="ui-notice">No active accounts are available for permission management.</p></c:if><div class="pm-workspace">
                                <aside class="pm-viewers-panel">
                                    <div class="pm-viewers-heading">
                                        <h2>
                                            <i class="fa-solid fa-id-card-clip" aria-hidden="true">
                                            </i> Accounts
                                        </h2>
                                        <span class="pm-account-count">${ui.viewerTotal} ACCOUNTS</span>
                                    </div>
                                    <form class="pm-search-wrap"
                                        action="${pageContext.request.contextPath}/MainController" method="get">
                                        <input name="action" value="PERMISSION_MANAGER" type="hidden">
                                        <input name="viewer" value="<c:out value='${ui.activeViewer.id}'/>"
                                            type="hidden">
                                        <label class="um-sr-only" for="pm-search">Search accounts</label>
                                        <input id="pm-search" type="search" name="q" value="<c:out value='${ui.q}'/>"
                                            placeholder="Search by username or full name...">
                                        <button class="ui-get-button" type="submit" aria-label="Search accounts">
                                            <i class="fa-solid fa-magnifying-glass" aria-hidden="true">
                                            </i>
                                        </button>
                                    </form>
                                    <div class="pm-viewer-list">
                                        <c:forEach var="viewer" items="${ui.viewers}">
                                            <button class="pm-viewer-card ${viewer.selected ? 'is-selected' : ''}"
                                                type="submit" form="pm-permission-form" name="uiOp"
                                                value="selectViewer:${fn:escapeXml(viewer.id)}" aria-pressed="${viewer.selected}">
                                                <span class="pm-viewer-identity">
                                                   
                                                    <span class="pm-viewer-info">
                                                        <strong class="pm-viewer-name">
                                                            <c:out value="${viewer.name}" />
                                                        </strong>
                                                        <span class="pm-viewer-handle">
                                                            <c:out value="${viewer.id}" /> · <c:out value="${viewer.role}"/>
                                                        </span>
                                                    </span>
                                                    <i class="fa-solid fa-eye" aria-hidden="true">
                                                    </i>
                                                </span>
                                               
                                                
                                            </button>
                                        </c:forEach>
                                        <c:if test="${empty ui.viewers}">
                                            <p class="ui-empty">No accounts found.</p>
                                        </c:if>
                                    </div>
                                </aside>
                                <form id="pm-permission-form" class="pm-permissions"
                                    action="${pageContext.request.contextPath}/MainController" method="post"><fieldset style="margin:0;padding:0;border:0;min-width:0" ${empty ui.activeViewer.id ? 'disabled' : ''}>
                                    <%@ include file="uiPostFields.jspf" %>
                                        <input name="viewer" value="<c:out value='${ui.activeViewer.id}'/>"
                                            type="hidden">
                                        <section class="pm-target-panel">
                                            <div class="pm-target-identity">
                                                <span class="pm-target-icon">
                                                    <i class="fa-solid fa-sliders" aria-hidden="true">
                                                    </i>
                                                </span>
                                                <div>
                                                    <span class="pm-target-label">CONFIGURING PERMISSIONS FOR:</span>
                                                    <span class="pm-target-handle">
                                                        <c:out value="${ui.activeViewer.id}" />
                                                    </span>
                                                    <h2>
                                                        <c:out value="${ui.activeViewer.name}" />
                                                    </h2>
                                                    <p class="pm-target-description">
                                                        <c:out value="${ui.activeViewer.description}" />
                                                    </p>
                                                </div>
                                            </div>
                                            <div class="pm-toolbar">
                                                <div class="pm-bulk-actions">
                                                    <button class="um-button um-button-secondary" name="uiOp"
                                                        value="confirmRevoke" type="submit">Revoke All Permissions</button>
                                                </div>
                                                <div class="pm-save-actions">
                                                    <span class="pm-save-state">${ui.dirty ? 'Unsaved draft' :
                                                        'No unsaved changes'}</span>
                                                    <button type="submit" class="pm-reset-button" name="uiOp"
                                                        value="resetPermissions">Reset Changes</button>
                                                    <button class="um-button um-button-primary" type="submit"
                                                        name="uiOp" value="savePermissions">
                                                        <i class="fa-solid fa-floppy-disk" aria-hidden="true">
                                                        </i> Save Permissions</button>
                                                </div>
                                            </div>
                                        </section>
                                        <p class="pm-rule-note">
                                            <i class="fa-solid fa-circle-info" aria-hidden="true">
                                            </i> When saved, control access includes view access. Drafts are kept when
                                            switching accounts.
                                        </p>
                                        <div class="pm-device-groups">
                                            <c:forEach var="device" items="${ui.permissionDevices}">
                                                <section class="pm-device-group">
                                                    <header class="pm-device-heading">
                                                        <div class="pm-device-identity">
                                                            <i class="fa-solid fa-microchip" aria-hidden="true">
                                                            </i>
                                                            <div>
                                                                <div class="pm-device-title">
                                                                    <h3>
                                                                        <c:out value="${device.name}" />
                                                                    </h3>
                                                                    <code class="pm-device-ip">HOST: <c:out value="${device.ip}"/>
</code>
                                                                </div>
                                                                <p class="pm-device-description">
                                                                    <c:out value="${device.location}" />
                                                                </p>
                                                            </div>
                                                        </div>
                                                        <div class="pm-device-badges">
                                                            <span class="pm-device-count">${fn:length(device.switches)}
                                                                SWITCH</span>
                                                            <span class="pm-device-online">ACTIVE</span>
                                                        </div>
                                                    </header>
                                                    <div class="pm-switch-list">
                                                        <c:forEach var="sw" items="${device.switches}">
                                                            <article class="pm-switch-row">
                                                                <div class="pm-switch-identity">
                                                                    
                                                                    <div>
                                                                        <div class="pm-switch-title">
                                                                            <h4>
                                                                                <c:out value="${sw.name}" />
                                                                            </h4>
                                                                            <code
                                                                                class="pm-switch-gpio">GPIO ${sw.gpio}</code>
                                                                        </div>
                                                                        <p class="pm-switch-description">
                                                                            <c:out value="${sw.description}" />
                                                                        </p>
                                                                    </div>
                                                                </div>
                                                                <div class="pm-switch-permissions">
                                                                    
                                                                    <label>
                                                                        <input name="control_${fn:escapeXml(sw.id)}" value="1"
                                                                            type="checkbox" ${sw.canControl ? 'checked'
                                                                            : '' }>
                                                                        <span>canControl</span>
                                                                        <span class="um-sr-only"> cho
                                                                            <c:out value="${sw.name}" />
                                                                        </span>
                                                                    </label>
                                                                </div>
                                                            </article>
                                                        </c:forEach>
                                                    </div>
                                                </section>
                                            </c:forEach>
                                        </div>
                                </fieldset></form>
                            </div>
                            
                        </main>
                </div>
                <c:if test="${ui.dialog}">
                    <%@ include file="uiDialogStart.jspf" %>
                        <p class="um-dialog-description">Revoke view and control access for <strong>
                                <c:out value="${ui.activeViewer.name}" />
                            </strong> on all switches? Changes remain in the draft until you select Save Permissions.</p>
                        <form action="${pageContext.request.contextPath}/MainController" method="post">
                            <%@ include file="uiPostFields.jspf" %>
                                <input name="viewer" value="<c:out value='${ui.activeViewer.id}'/>" type="hidden">
                                <div class="um-dialog-footer">
                                    <a class="um-button um-button-secondary"
                                        href="${pageContext.request.contextPath}/MainController?action=PERMISSION_MANAGER&amp;viewer=${ui.activeViewer.id}">Cancel</a>
                                    <button class="um-button um-button-danger" name="uiOp" value="revokePermissions"
                                        type="submit">Revoke All Permissions</button>
                                </div>
                        </form>
                        </dialog>
                </c:if>
            </body>

            </html>
