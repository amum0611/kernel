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
<%@page import="org.apache.axis2.context.ConfigurationContext"%>
<%@page import="org.wso2.carbon.CarbonConstants" %>
<%@page import="org.wso2.carbon.ui.CarbonUIMessage"%>
<%@page import="org.wso2.carbon.ui.CarbonUIUtil"%>
<%@page import="org.wso2.carbon.user.mgt.common.IUserAdmin"%>
<%@page import="org.wso2.carbon.user.mgt.ui.UserAdminClient"%>
<%@page import="org.wso2.carbon.user.mgt.ui.Util"%>
<%@ page import="org.wso2.carbon.utils.ServerConstants" %>
<%@ page import="java.text.MessageFormat" %>
<%@page import="java.util.ResourceBundle"%>
<%@page import="org.wso2.carbon.ui.util.CharacterEncoder"%>
<%@ page import="org.wso2.carbon.user.mgt.common.FlaggedName" %>
<%@ page import="org.wso2.carbon.user.mgt.ui.RoleBean" %>

<%
	String BUNDLE = "org.wso2.carbon.userstore.ui.i18n.Resources";
    ResourceBundle resourceBundle = ResourceBundle.getBundle(BUNDLE, request.getLocale());
    String forwardTo = null;
    boolean logout = false;
    if(request.getParameter("logout") != null){
        logout = Boolean.parseBoolean(request.getParameter("logout"));
    }
    try {
%>
        <jsp:useBean id="roleBeanEditUsers" class="org.wso2.carbon.user.mgt.ui.RoleBean" scope="session"/>
        <jsp:setProperty name="roleBeanEditUsers" property="*" />
<%
        String cookie = (String)session.getAttribute(ServerConstants.ADMIN_SERVICE_COOKIE);
        String backendServerURL = CarbonUIUtil.getServerURL(config.getServletContext(), session);
        ConfigurationContext configContext =
            (ConfigurationContext) config.getServletContext().getAttribute(CarbonConstants.CONFIGURATION_CONTEXT);
        IUserAdmin proxy =
            (IUserAdmin) CarbonUIUtil.
                    getServerProxy(new UserAdminClient(cookie, backendServerURL, configContext),
                                         IUserAdmin.class, session);
        String roleName = CharacterEncoder.getSafeText(roleBeanEditUsers.getRoleName());
        String userType = (String)session.getAttribute("org.wso2.carbon.user.userType");
        String message = MessageFormat.format(resourceBundle.getString("role.update"), roleName);
        CarbonUIMessage.sendCarbonUIMessage(message, CarbonUIMessage.INFO, request);
        proxy.updateUsersOfRole(roleBeanEditUsers.getRoleName(), Util.buildFalggedArray(roleBeanEditUsers.getShownUsers(),
                                     roleBeanEditUsers.getSelectedUsers()));
        session.removeAttribute("org.wso2.carbon.user.userType");
        session.removeAttribute("roleBeanEditUsers");
        if(logout){
            forwardTo = "../admin/logout_action.jsp";
        }else{
            forwardTo = "edit-users.jsp?roleName="+roleName;
        }
    } catch(InstantiationException e){
        CarbonUIMessage.sendCarbonUIMessage("Your session has timed out. Please try again.", CarbonUIMessage.ERROR, request);
        forwardTo = "role-mgt.jsp?ordinal=1";
    } catch (Exception e) {
	    String message = MessageFormat.format(resourceBundle.getString("role.cannot.update"),((RoleBean)session.getAttribute("roleBeanEditUsers")).getRoleName(), e.getMessage());
	    CarbonUIMessage.sendCarbonUIMessage(message, CarbonUIMessage.ERROR, request);
	    forwardTo = "edit-users.jsp?ordinal=1";
    }
%>

<script type="text/javascript">

    function forward(){
        location.href = "<%=forwardTo%>";
    }

    forward();
 </script>
