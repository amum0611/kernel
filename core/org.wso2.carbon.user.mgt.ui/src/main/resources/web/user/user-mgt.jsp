<!--
~ Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
~
~ WSO2 Inc. licenses this file to you under the Apache License,
~ Version 2.0 (the "License"); you may not use this file except
~ in compliance with the License.
~ You may obtain a copy of the License at
~
~ http://www.apache.org/licenses/LICENSE-2.0
~
~ Unless required by applicable law or agreed to in writing,
~ software distributed under the License is distributed on an
~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
~ KIND, either express or implied. See the License for the
~ specific language governing permissions and limitations
~ under the License.
-->
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib uri="http://wso2.org/projects/carbon/taglibs/carbontags.jar" prefix="carbon" %>
<%@page import="org.apache.axis2.context.ConfigurationContext" %>
<%@page import="org.wso2.carbon.CarbonConstants" %>
<%@page import="org.wso2.carbon.ui.CarbonUIMessage" %>
<%@page import="org.wso2.carbon.ui.CarbonUIUtil" %>
<%@page import="org.wso2.carbon.user.core.UserCoreConstants" %>
<%@page import="org.wso2.carbon.user.mgt.common.IUserAdmin" %>

<%@page import="org.wso2.carbon.user.mgt.common.UserStoreInfo" %>
<%@ page import="org.wso2.carbon.user.mgt.ui.UserAdminClient" %>

<%@page import="org.wso2.carbon.utils.ServerConstants" %>
<%@page import="org.wso2.carbon.ui.util.CharacterEncoder" %>
<%@ page import="org.wso2.carbon.user.mgt.ui.Util" %>

<%@ page import="org.wso2.carbon.user.mgt.ui.PaginatedNamesBean" %>
<script type="text/javascript" src="../userstore/extensions/js/vui.js"></script>
<script type="text/javascript" src="../admin/js/main.js"></script>

<%
    //to preven repeated request to backend on error.
    boolean doUserList = true;
    java.lang.String errorAttribute = (java.lang.String) session.getAttribute(UserAdminClient.DO_USER_LIST);
    if (errorAttribute != null) {
        doUserList = false;
        session.removeAttribute(UserAdminClient.DO_USER_LIST);
    }
%>
<jsp:include page="../dialog/display_messages.jsp"/>
<title>WSO2 Carbon - Security Configuration</title>
<%
    boolean showFilterMessage = false;

    session.removeAttribute("userBean");
    java.lang.String[] datas = null;
    java.lang.String filter = (java.lang.String) request.getParameter(UserAdminClient.USER_LIST_FILTER);
    if (filter == null) {
        filter = (java.lang.String) session.getAttribute(UserAdminClient.USER_LIST_FILTER);
    }
    if (filter == null || filter.trim().length() == 0) {
        filter = "*";
        session.setAttribute(UserAdminClient.USER_LIST_FILTER, filter);
    } else {
        filter = filter.trim();
        session.setAttribute(UserAdminClient.USER_LIST_FILTER, filter);
    }
    java.lang.String currentUser = (java.lang.String) session.getAttribute("logged-user");
    UserStoreInfo userStoreInfo = null;
    userStoreInfo = (UserStoreInfo) session.getAttribute(UserAdminClient.USER_STORE_INFO);
    if (doUserList) { // don't call the back end if some kind of message is showing
        try {
            java.lang.String cookie = (java.lang.String) session
                    .getAttribute(ServerConstants.ADMIN_SERVICE_COOKIE);
            java.lang.String backendServerURL = CarbonUIUtil.getServerURL(config.getServletContext(),
                                                                          session);
            ConfigurationContext configContext = (ConfigurationContext) config
                    .getServletContext()
                    .getAttribute(CarbonConstants.CONFIGURATION_CONTEXT);
            IUserAdmin proxy = (IUserAdmin) CarbonUIUtil.getServerProxy(
                    new UserAdminClient(cookie, backendServerURL, configContext),
                    IUserAdmin.class, session);
            if (userStoreInfo == null) {
                userStoreInfo = proxy.getUserStoreInfo();
                session.setAttribute(UserAdminClient.USER_STORE_INFO, userStoreInfo);
            }

            if (filter.length() > 0) {
                datas = proxy.listUsers(filter);
                if (datas == null || datas.length == 0) {
                    showFilterMessage = true;
                }
            }

        } catch (Exception e) {
            session.setAttribute(UserAdminClient.DO_USER_LIST, "error");
            CarbonUIMessage.sendCarbonUIMessage(e.getMessage(), CarbonUIMessage.ERROR,
                                                request);
%>
<script type="text/javascript">
    location.href = "user-mgt.jsp";
</script>
<%
            return;
        }
    }
%>

<fmt:bundle basename="org.wso2.carbon.userstore.ui.i18n.Resources">
    <carbon:breadcrumb label="users"
                       resourceBundle="org.wso2.carbon.userstore.ui.i18n.Resources"
                       topPage="false" request="<%=request%>"/>

    <script type="text/javascript">

        function deleteUser(user) {
            function doDelete() {
                var userName = user;
                location.href = 'delete-finish.jsp?username=' + userName;
            }

            CARBON.showConfirmationDialog("<fmt:message key="confirm.delete.user"/> \'" + user + "\'?", doDelete, null);
        }

        <%if (showFilterMessage == true) {%>
        jQuery(document).ready(function () {
            CARBON.showInfoDialog('<fmt:message key="no.users.filtered"/>', null, null);
        });
        <%}%>
    </script>

    <div id="middle">
        <h2><fmt:message key="users"/></h2>

        <div id="workArea">
            <form name="filterForm" method="post" action="user-mgt.jsp">
                <table class="normal">
                    <tr>
                        <td><fmt:message key="list.users"/></td>
                        <td>
                            <input type="text" name="org.wso2.usermgt.internal.filter"
                                   value="<%=filter%>"/>
                        </td>
                        <td>
                            <input class="button" type="submit"
                                   value="<fmt:message key="user.search"/>"/>
                        </td>
                    </tr>
                </table>
            </form>
            <p>&nbsp;</p>
            <% if (datas != null) {
                java.lang.String pageNumberStr = request.getParameter("pageNumber");
                if (pageNumberStr == null) {
                    pageNumberStr = "0";
                }
                int pageNumber = 0;
                try {
                    pageNumber = Integer.parseInt(pageNumberStr);
                } catch (NumberFormatException ignored) {
                    // page number format exception
                }
                int numberOfPages;
                int noOfPageLinksToDisplay = 5;  //default value is set to 5
                PaginatedNamesBean bean = Util.retrivePaginatedFlggedName(pageNumber, datas);
                java.lang.String[] users = bean.getNamesAsString();

            %>

            <carbon:paginator pageNumber="<%=pageNumber%>"
                              numberOfPages="<%=bean.getNumberOfPages()%>"
                              noOfPageLinksToDisplay="<%=noOfPageLinksToDisplay%>"
                              page="user-mgt.jsp" pageNumberParameterName="pageNumber"/>

            <table class="styledLeft" id="userTable">

                <%
                    if (users != null && users.length > 0) {
                %>
                <thead>
                <tr>
                    <th><fmt:message key="name"/></th>
                    <th><fmt:message key="actions"/></th>
                </tr>
                </thead>
                <%
                    }
                %>
                <tbody>
                <%
                    int count = 0;
                    if (datas != null) {
                        for (String data : users) {
                            data = CharacterEncoder.getSafeText(data);
                            if (data != null) { //Confusing!!. Sometimes a null object comes. Maybe a bug Axis!!
                                count++;
                                if (data.equals(CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME)) {
                                    continue;
                                }
                %>
                <tr>
                    <td><%=data%>
                    </td>
                    <td>
                        <%
                            if (!userStoreInfo.isReadOnly()) {
                        %>
                        <%
                            if (!userStoreInfo.isPasswordsExternallyManaged() &&
                                CarbonUIUtil.isUserAuthorized(request,
                                         "/permission/admin/configure/security/usermgt/passwords")) { //if passwords are managed externally do not allow to change passwords.
                        %>
                        <a href="change-passwd.jsp?username=<%=data%>" class="icon-link"
                           style="background-image:url(../admin/images/edit.gif);"><fmt:message
                                key="change.password"/></a>
                        <%
                            }
                        %>

                        <%
                            if(CarbonUIUtil.isUserAuthorized(request, "/permission/admin/configure/security")){
                        %>
                        <a href="edit-user-roles.jsp?username=<%=data%>" class="icon-link"
                           style="background-image:url(../admin/images/edit.gif);"><fmt:message
                                key="roles"/></a>
                        <%
                            }
                        %>

                        <%
                            if (CarbonUIUtil.isUserAuthorized(request,
                                "/permission/admin/configure/security/usermgt/users") && !data.equals(currentUser)
                                && !data.equals(userStoreInfo.getAdminUser())) {
                        %>
                        <a href="#" onclick="deleteUser('<%=data%>')" class="icon-link"
                           style="background-image:url(images/delete.gif);"><fmt:message
                                key="delete"/></a>
                        <%
                                }
                            }
                        %>

                        <%
                            if (CarbonUIUtil.isContextRegistered(config, "/userprofile/")
                                && CarbonUIUtil.isUserAuthorized(request,
                                                                 "/permission/admin/configure/security/usermgt/profiles")) {
                        %>
                        <a href="../userprofile/index.jsp?username=<%=data%>&fromUserMgt=true"
                           class="icon-link"
                           style="background-image:url(../userprofile/images/my-prof.gif);">User
                                                                                            Profile</a>
                        <%
                            }
                        %>

                    </td>
                </tr>
                <%
                            }
                        }
                    }
                %>
                </tbody>
            </table>
            <carbon:paginator pageNumber="<%=pageNumber%>"
                              numberOfPages="<%=bean.getNumberOfPages()%>"
                              noOfPageLinksToDisplay="<%=noOfPageLinksToDisplay%>"
                              page="user-mgt.jsp" pageNumberParameterName="pageNumber"/>
            <%
                }
            %>
            <p>&nbsp;</p>
            <%
                if (datas != null) {
                    int length = datas.length;
                    if (length >= userStoreInfo.getMaxUserListCount()) {
            %>
            <strong><fmt:message key="more.users"/></strong>
            <%
                    }
                }
            %>
            <%
                if (userStoreInfo.isReadOnly() == false && userStoreInfo.getExternalIdP() == null
                        && CarbonUIUtil.isUserAuthorized(request,
                                "/permission/admin/configure/security/usermgt/users")) {
            %>
            <table width="100%" border="0" cellpadding="0" cellspacing="0" style="margin-top:2px;">
                <tr>
                    <td class="addNewSecurity">
                        <a href="add-step1.jsp" class="icon-link"
                           style="background-image:url(images/add.gif);"><fmt:message
                                key="add.new.user"/></a>
                    </td>
                </tr>

                <%
                    if (userStoreInfo.isBulkImportSupported()) {
                %>
                <tr>
                    <td class="addNewSecurity">
                        <a href="bulk-import.jsp" class="icon-link"
                           style="background-image:url(images/bulk-import.gif);"><fmt:message
                                key="bulk.import.user"/></a>
                    </td>
                </tr>

                <%
                    }
                %>

            </table>

            <%
                }
            %>


        </div>
    </div>
    <script language="text/JavaScript">
        alternateTableRows('userTable', 'tableEvenRow', 'tableOddRow');
    </script>
</fmt:bundle>