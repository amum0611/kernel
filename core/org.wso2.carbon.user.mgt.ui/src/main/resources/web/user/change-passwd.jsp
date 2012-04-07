<!--
 ~ Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 ~
 ~ WSO2 Inc. licenses this file to you under the Apache License,
 ~ Version 2.0 (the "License"); you may not use this file except
 ~ in compliance with the License.
 ~ You may obtain a copy of the License at
 ~
 ~    http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing,
 ~ software distributed under the License is distributed on an
 ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 ~ KIND, either express or implied.  See the License for the
 ~ specific language governing permissions and limitations
 ~ under the License.
 -->
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib uri="http://wso2.org/projects/carbon/taglibs/carbontags.jar" prefix="carbon" %>

<%@page import="org.apache.axis2.context.ConfigurationContext"%>
<%@page import="org.wso2.carbon.CarbonConstants"%>
<%@page import="org.wso2.carbon.ui.CarbonUIMessage"%>
<%@page import="org.wso2.carbon.ui.CarbonUIUtil"%>
<%@page import="org.wso2.carbon.user.mgt.common.IUserAdmin"%>
<%@page import="org.wso2.carbon.user.mgt.common.UserStoreInfo"%>
<%@page import="org.wso2.carbon.user.mgt.ui.UserAdminClient"%>
<%@page import="org.wso2.carbon.utils.ServerConstants"%>
<%@page import="org.wso2.carbon.ui.util.CharacterEncoder"%><script type="text/javascript" src="../userstore/extensions/js/vui.js"></script>
<script type="text/javascript" src="../admin/js/main.js"></script>
<jsp:include page="../dialog/display_messages.jsp"/>

<%
	String isUserChange = request.getParameter("isUserChange");
    String returnPath = null;
    String username = null;
    String cancelPath = null;
    if(isUserChange != null) {
        returnPath = request.getParameter("returnPath");
        cancelPath = returnPath;
    }else {
        username = CharacterEncoder.getSafeText(request.getParameter("username"));
        cancelPath = "user-mgt.jsp?ordinal=1";
    }
    
    UserStoreInfo userStoreInfo = null;
    try{
        
        userStoreInfo = (UserStoreInfo)session.getAttribute(UserAdminClient.USER_STORE_INFO);
        if(userStoreInfo == null){
            String cookie = (String) session.getAttribute(ServerConstants.ADMIN_SERVICE_COOKIE);
            String backendServerURL = CarbonUIUtil.getServerURL(config.getServletContext(), session);
            ConfigurationContext configContext =
                (ConfigurationContext) config.getServletContext().getAttribute(CarbonConstants.CONFIGURATION_CONTEXT);

            IUserAdmin proxy =
                (IUserAdmin) CarbonUIUtil.
                getServerProxy(new UserAdminClient(cookie, backendServerURL, configContext),
                                     IUserAdmin.class, session);
            userStoreInfo = proxy.getUserStoreInfo();
            session.setAttribute(UserAdminClient.USER_STORE_INFO, userStoreInfo);
        }
    }catch(Exception e){
        CarbonUIMessage uiMsg = new CarbonUIMessage(e.getMessage(), CarbonUIMessage.ERROR, e);
        session.setAttribute(CarbonUIMessage.ID, uiMsg);
%>
        <jsp:include page="../admin/error.jsp"/>
    <%
        return;
        }
%>

<fmt:bundle basename="org.wso2.carbon.userstore.ui.i18n.Resources">
<carbon:breadcrumb label="change.password"
               resourceBundle="org.wso2.carbon.userstore.ui.i18n.Resources"
               topPage="false" request="<%=request%>"/>

    <script type="text/javascript">

        function doCancel() {
            location.href = '<%=cancelPath%>';
        }

        function doValidation() {
            var reason = "";

            reason = validatePasswordOnCreation("newPassword", "checkPassword", "<%=userStoreInfo.getJsRegEx()%>");
            if (reason != "") {
                if (reason == "Empty Password") {
                    CARBON.showWarningDialog("<fmt:message key="enter.the.same.password.twice"/>");
                } else if (reason == "Min Length") {
                    CARBON.showWarningDialog("<fmt:message key="password.mimimum.characters"/>");
                } else if (reason == "Invalid Character") {
                    CARBON.showWarningDialog("<fmt:message key="invalid.character.in.password"/>");
                } else if (reason == "Password Mismatch") {
                    CARBON.showWarningDialog("<fmt:message key="password.mismatch"/>");
                } else if (reason == "No conformance") {
                    CARBON.showWarningDialog("<fmt:message key="password.conformance"/>");
                }
                return false;
            }
            return true;
        }

    </script>
    <jsp:include page="../userstore/display-messages.jsp"/>
    <div id="middle">
        <h2><fmt:message key="change.password"/></h2>

        <div id="workArea">
            <form name="chgPassWdForm" method="post"
                  onsubmit="return doValidation();" action="change-passwd-finish.jsp">
                <input type="hidden" name="username" value="<%=username%>"/>
                <% if (isUserChange != null) { %>
                    <input type="hidden" name="isUserChange" value="<%=isUserChange%>"/>
                <% } %>
                <% if (returnPath != null) { %>
                    <input type="hidden" name="returnPath" value="<%=returnPath%>"/>
                <% } %>
                <table class="styledLeft" id="changePassword" width="60%">
                    <thead>
                        <tr>
                            <th><fmt:message key="type.new.password"/></th>
                        </tr>
                    </thead>
                    <tr>
                        <td class="formRaw">
                            <table class="normal">
                                <% if (isUserChange != null) { %>
                                <tr>
                                    <td><fmt:message key="current.password"/><font color="red">*</font></td>
                                    <td><input type="password" name="currentPassword"/></td>
                                </tr>
                                <% } %>
                                <tr>
                                    <td><fmt:message key="new.password"/><font color="red">*</font></td>
                                    <td><input type="password" name="newPassword"/></td>
                                </tr>
                                <tr>
                                    <td><fmt:message key="new.password.repeat"/><font color="red">*</font></td>
                                    <td><input type="password" name="checkPassword"/></td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                    <tr>
                        <td class="buttonRow">
                            <input class="button" type="submit" value="<fmt:message key="change"/>"/>
                            <input class="button" type="button" value="<fmt:message key="cancel"/>"
                                   onclick="doCancel();"/>
                        </td>
                    </tr>
                </table>
            </form>
        </div>
    </div>
</fmt:bundle>