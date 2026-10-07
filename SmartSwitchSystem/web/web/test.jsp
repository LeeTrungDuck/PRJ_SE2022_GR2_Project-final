<%-- 
    Document   : test
    Created on : Oct 5, 2026, 8:19:09 PM
    Author     : ltrun
--%>

<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>JSP Page</title>
        <%@include file="includeFile.jspf" %>
    </head>
    <body>
        <div class="col-xs-12 col-sm-6 col-md-4" style="margin-bottom: 20px;">
                                <div class="device-card">

                                    <!-- Header Card: ID & Nút Xóa -->
                                    <div class="card-header-row">
                                        <span class="badge-gpio">ID: ${i.id}</span>
                                        <details>
                                            <summary class="btn-delete" title="Xóa thiết bị"><i class="fa-solid fa-trash-can"></i> Xóa</summary>
                                            <p>Bạn có chắc chắn muốn xóa thiết bị này?</p>
                                            <a href="${pageContext.request.contextPath}/DeleteTypeController?id=${i.id}" class="btn-delete">Xác nhận xóa</a>
                                        </details>
                                    </div>

                                    <!-- Body Card: Tên thiết bị -->
                                    <div class="card-body-row">
                                        <div class="device-icon">
                                            <i class="fa-solid fa-microchip"></i>
                                        </div>
                                        <div class="device-meta">
                                            <h4 class="device-title">${i.name}</h4>
                                            <span class="device-mode">CONNECTED</span>
                                        </div>
                                    </div>

                                    <!-- Footer Card: Trạng thái & Thao tác -->
                                    <div class="card-footer-row">
                                        <div class="status-box">
                                            <span class="status-label">TRẠNG THÁI</span>
                                            <span class="status-text text-on">ACTIVE</span>
                                        </div>
                                        <label class="switch-toggle">
                                            <input type="checkbox" checked>
                                            <span class="slider"></span>
                                        </label>
                                    </div>

                                </div>
                            </div>
    </body>
</html>
