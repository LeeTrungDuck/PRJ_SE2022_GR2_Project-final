<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
    <%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
        <%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
            <c:if test="${empty sessionScope.LOGIN_USER or empty requestScope.ui}">
                <c:redirect url="/MainController">
                    <c:param name="action" value="ACCOUNT_MANAGER" />
                </c:redirect>
            </c:if>
            <c:set var="activePage" value="ACCOUNT_MANAGER" scope="request" />
            <!DOCTYPE html>
            <html lang="en">

            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Account Settings | ESP32 IoT Hub</title>
                <link rel="preconnect" href="https://fonts.googleapis.com">
                <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
                <link
                    href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap"
                    rel="stylesheet">
                <link rel="stylesheet" href="${pageContext.request.contextPath}/css/userManagerCss.css">
                <link rel="stylesheet" href="${pageContext.request.contextPath}/css/accountManagerCss.css">
                <link rel="stylesheet" href="${pageContext.request.contextPath}/css/noJsUi.css?v=1">
            </head>

            <body class="user-manager-page account-manager-page">
                <div class="ui-shell" ${ui.dialog ? 'inert' : '' }>
                    <%@ include file="sideBar.jspf" %>
                        
                        <main class="um-main ac-main" id="main-content">
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
                            <header class="ac-page-heading">
                                <span class="ac-heading-dot" aria-hidden="true">
                                </span>
                                <h1>Profile &amp; Account Settings <span>(Profile / Account)</span>
                                </h1>
                                <p>Manage your account identity and contact details.</p>
                            </header>
                            <div class="ac-workspace">
                                <aside class="ac-profile-card">
                                    <div class="ac-profile-identity">
                                       
                                        <div class="ac-profile-text">
                                            <h2>
                                                <c:out value="${ui.profile.fullName}" />
                                            </h2>
                                            <p class="ac-handle">@
                                                <c:out value="${ui.profile.userName}" />
                                            </p>
                                            <span class="ac-role-badge">${ui.profile.role}</span>
                                        </div>
                                    </div>
                                    <dl class="ac-profile-details">
                                        <div>
                                            <dt>User ID</dt>
                                            <dd>${ui.profile.userId}</dd>
                                        </div>
                                        <div>
                                            <dt>Status</dt>
                                            <dd class="ac-status">Active</dd>
                                        </div>
                                        
                                        <div>
                                            <dt>Role</dt>
                                            <dd class="ac-root-role">${ui.profile.role}</dd>
                                        </div>
                                    </dl>
                                </aside>
                                <div class="ac-forms">
                                    <section class="ac-panel">
                                        <header class="ac-panel-heading">
                                            <i class="fa-solid fa-id-card-clip" aria-hidden="true">
                                            </i>
                                            <div>
                                                <h2>Edit Personal Information</h2>
                                                <p>Update your full name, email address and phone number.</p>
                                            </div>
                                            <span class="ac-form-number">FORM 01/02</span>
                                        </header>
                                        <form action="${pageContext.request.contextPath}/MainController" method="post">
                                            <%@ include file="uiPostFields.jspf" %>
                                                <div class="ac-personal-grid">
                                                    <div class="ac-field">
                                                        <label for="ac-fullname">Full Name</label>
                                                        <div class="ac-input-wrap">
                                                            <i class="fa-regular fa-user" aria-hidden="true">
                                                            </i>
                                                            <input id="ac-fullname" name="fullName"
                                                                value="<c:out value='${ui.edit.fullName}' />"
                                                                maxlength="50" autocomplete="name" required>
                                                        </div>
                                                    </div>
                                                    <div class="ac-field">
                                                        <label for="ac-username">Username (Read-only)</label>
                                                        <div class="ac-input-wrap ac-readonly">
                                                            <i class="fa-solid fa-at" aria-hidden="true">
                                                            </i>
                                                            <input id="ac-username"
                                                                value="<c:out value='${ui.profile.userName}' />"
                                                                readonly>
                                                        </div>
                                                    </div>
                                                    <div class="ac-field">
                                                        <label for="ac-email">Alert Email Address</label>
                                                        <div class="ac-input-wrap">
                                                            <i class="fa-regular fa-envelope" aria-hidden="true">
                                                            </i>
                                                            <input id="ac-email" type="email" name="email"
                                                                value="<c:out value='${ui.edit.email}' />"
                                                                maxlength="50" required>
                                                        </div>
                                                    </div>
                                                    <div class="ac-field">
                                                        <label for="ac-phone">Support Phone Number</label>
                                                        <div class="ac-input-wrap">
                                                            <i class="fa-solid fa-phone" aria-hidden="true">
                                                            </i>
                                                            <input id="ac-phone" name="phone" type="tel"
                                                                value="<c:out value='${ui.edit.phone}' />"
                                                                maxlength="10" pattern="[0-9]{10}" required>
                                                        </div>
                                                    </div>
                                                </div>
                                                <footer class="ac-panel-footer">
                                                    <p>
                                                        <i class="fa-solid fa-circle-info" aria-hidden="true">
                                                        </i> Your username and role cannot be changed here.
                                                    </p>
                                                    <button class="um-button um-button-primary" name="uiOp"
                                                        value="saveProfile" type="submit">Save Changes</button>
                                                </footer>
                                        </form>
                                    </section>
                                    <section class="ac-panel">
                                        <header class="ac-panel-heading">
                                            <i class="fa-solid fa-clock-rotate-left" aria-hidden="true">
                                            </i>
                                            <div>
                                                <h2>Change Password</h2>
                                                <p>At least 10 characters, including an uppercase letter, a number and a special character.</p>
                                            </div>
                                            <span class="ac-form-number">FORM 02/02</span>
                                        </header>
                                        <form action="${pageContext.request.contextPath}/MainController" method="post">
                                            <%@ include file="uiPostFields.jspf" %>
                                                <div class="ac-password-grid">
                                                    <div class="ac-field">
                                                        <label for="ac-current-password">Current Password</label>
                                                        <div class="ac-input-wrap">
                                                            <i class="fa-solid fa-key" aria-hidden="true">
                                                            </i>
                                                            <input id="ac-current-password" name="currentPassword"
                                                                type="password" autocomplete="current-password"
                                                                maxlength="128" required>
                                                        </div>
                                                    </div>
                                                    <div class="ac-field">
                                                        <label for="ac-new-password">New Password</label>
                                                        <div class="ac-input-wrap">
                                                            <i class="fa-solid fa-lock" aria-hidden="true">
                                                            </i>
                                                            <input id="ac-new-password" name="newPassword"
                                                                type="password" autocomplete="new-password"
                                                                minlength="10" maxlength="128" required>
                                                        </div>
                                                    </div>
                                                    <div class="ac-field">
                                                        <label for="ac-confirm-password">Confirm Password</label>
                                                        <div class="ac-input-wrap">
                                                            <i class="fa-solid fa-check" aria-hidden="true">
                                                            </i>
                                                            <input id="ac-confirm-password" name="confirmPassword"
                                                                type="password" autocomplete="new-password"
                                                                minlength="10" maxlength="128" required>
                                                        </div>
                                                    </div>
                                                </div>
                                                <footer class="ac-panel-footer">
                                                    <p>Your current password is verified. The new password is hashed before storage.
                                                    </p>
                                                    <button class="um-button um-button-primary" name="uiOp"
                                                        value="changePassword" type="submit">Update Password</button>
                                                </footer>
                                        </form>
                                    </section>
                                </div>
                            </div>
                            
                        </main>
                </div>
                </body></html>