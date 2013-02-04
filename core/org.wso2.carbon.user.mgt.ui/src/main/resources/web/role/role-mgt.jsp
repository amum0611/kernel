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
<%@ page import="org.apache.axis2.context.ConfigurationContext" %>
<%@page import="org.wso2.carbon.CarbonConstants" %>
<%@page import="org.wso2.carbon.ui.CarbonUIMessage" %>
<%@page import="org.wso2.carbon.ui.CarbonUIUtil" %>
<%@page import="org.wso2.carbon.user.mgt.common.FlaggedName" %>
<%@page import="org.wso2.carbon.user.mgt.common.IUserAdmin"%>
<%@ page import="org.wso2.carbon.user.mgt.common.UserStoreInfo" %>

<%@page import="org.wso2.carbon.user.mgt.ui.UserAdminClient"%>
<%@page import="org.wso2.carbon.utils.ServerConstants"%>
<%@page import="org.wso2.carbon.ui.util.CharacterEncoder"%><script type="text/javascript" src="../userstore/extensions/js/vui.js"></script>
<script type="text/javascript" src="../admin/js/main.js"></script>
<jsp:include page="../dialog/display_messages.jsp"/>

<%
	FlaggedName[] datas = null;
        session.removeAttribute("org.wso2.usermgt.role.edit.filter"); 
        session.removeAttribute("roleBean");
        UserStoreInfo userStoreInfo = null;
        userStoreInfo = (UserStoreInfo)session.getAttribute(UserAdminClient.USER_STORE_INFO);
        boolean hasMultipleUserStores;
        try {
            String cookie = (String) session.getAttribute(ServerConstants.ADMIN_SERVICE_COOKIE);
            String backendServerURL = CarbonUIUtil.getServerURL(config.getServletContext(), session);
            ConfigurationContext configContext =
                    (ConfigurationContext) config.getServletContext().getAttribute(CarbonConstants.CONFIGURATION_CONTEXT);
            IUserAdmin proxy =
            (IUserAdmin) CarbonUIUtil.
                    getServerProxy(new UserAdminClient(cookie, backendServerURL, configContext),
                                         IUserAdmin.class, session);
            datas = proxy.getAllRolesNames();
            hasMultipleUserStores = proxy.hasMultipleUserStores();
            if(userStoreInfo == null){
                userStoreInfo = proxy.getUserStoreInfo();
                session.setAttribute(UserAdminClient.USER_STORE_INFO, userStoreInfo);
            }
        } catch (Exception e) {
            CarbonUIMessage uiMsg = new CarbonUIMessage(CarbonUIMessage.ERROR, e.getMessage(), e);
            session.setAttribute(CarbonUIMessage.ID, uiMsg);
%>
            <jsp:include page="../admin/error.jsp"/>
<%
	return;
        }
%>
<fmt:bundle basename="org.wso2.carbon.userstore.ui.i18n.Resources">
<carbon:breadcrumb label="roles"
		resourceBundle="org.wso2.carbon.userstore.ui.i18n.Resources"
		topPage="false" request="<%=request%>" />
		
    <script type="text/javascript">

        function deleteUserGroup(role) {
            function doDelete(){
                var roleName = role;
                location.href = 'delete-role.jsp?roleName=' + roleName +'&userType=internal';
            }
            CARBON.showConfirmationDialog('<fmt:message key="confirm.delete.role"/> ' + role + '?', doDelete, null);
        }
        
        /*function doDelete(){
            location.href = 'delete-role.jsp?roleName=' + this.role+'&userType=internal';
        }*/
    </script>
    <script type="text/javascript">

        function updateUserGroup(role) {
                var roleName = role;
                location.href = 'rename-role.jsp?roleName=' + roleName +'&userType=internal';
        }

    </script>
    <div id="middle">
        <h2><fmt:message key="roles"/></h2>

        <div id="workArea">
            <table class="styledLeft" id="roleTable">
                <thead>
                <tr>
                    <th><fmt:message key="name"/></th>
                    <%if(hasMultipleUserStores){%>
                    <th><fmt:message key="domainName"/></th>
                    <%}
                    %>
                    <th><fmt:message key="actions"/></th>
                </tr>
                </thead>
                <tbody>
                <%
                	if (datas != null) {
                                        for (FlaggedName data : datas) {
                                            if (data != null) { //Confusing!!. Sometimes a null object comes. Maybe a bug in Axis!!
                                                if(CarbonConstants.REGISTRY_ANONNYMOUS_ROLE_NAME.equals(data.getItemName())) {
                                                    continue;
                                                }
                                            String roleName = CharacterEncoder.getSafeText(data.getItemName());
                %>
                <tr>
                    <td><%=roleName%>
                    </td>
                    <%if(hasMultipleUserStores){%>
                    	<td>
                            <%if(data.getDomainName() != null){%>
                            <%data.getDomainName();%>
                            <%} %>
                        </td>
                    <%}%>
                    <td>
                    <% if(data.getItemName().equals(userStoreInfo.getAdminRole()) == false && data.getItemName().equals(userStoreInfo.getEveryOneRole()) == false && data.isEditable()){%>
<a href="#" onclick="updateUserGroup('<%=roleName%>')" class="icon-link" style="background-image:url(images/edit.gif);"><fmt:message key="rename"/></a>
                    <% }  %>
                    <% if(!data.getItemName().equals(userStoreInfo.getAdminRole())) {%>
<a href="edit-permissions.jsp?roleName=<%=roleName%>" class="icon-link" style="background-image:url(images/edit.gif);"><fmt:message key="edit.permissions"/></a>
                    <% } %>
                    <% if (!userStoreInfo.getEveryOneRole().equals(data.getItemName()) && data.isEditable()) { %>
<a href="edit-users.jsp?roleName=<%=roleName%>" class="icon-link" style="background-image:url(images/edit.gif);"><fmt:message key="edit.users"/></a>
                    <% } %>
                     <% if (!userStoreInfo.getEveryOneRole().equals(data.getItemName()) && data.isEditable()) { %>
                        <a href="view-users.jsp?roleName=<%=roleName%>" class="icon-link" style="background-image:url(images/view.gif);"><fmt:message key="view.users"/></a>
                      <% } %>

                        <%
                            if (CarbonUIUtil.isContextRegistered(config, "/identity-authorization/" ) &&
                                    CarbonUIUtil.isUserAuthorized(request, "/permission/admin/configure/security/")) {
                        %>
                            <a href="../identity-authorization/permission-root.jsp?roleName=<%=data.getItemName()%>&fromUserMgt=true"
                               class="icon-link"
                               style="background-image:url(../admin/images/edit.gif);"><fmt:message key="authorization"/></a>
                        <%
                            }
                         %>

                    <% if(data.getItemName().equals(userStoreInfo.getAdminRole()) == false && data.getItemName().equals(userStoreInfo.getEveryOneRole()) == false && data.isEditable()){%>
<a href="#" onclick="deleteUserGroup('<%=roleName%>')" class="icon-link" style="background-image:url(images/delete.gif);"><fmt:message key="delete"/></a>
                    <% }  %>

                    </td>
                </tr>
                <%
                            }
                        }
                    }%>
                </tbody>
            </table>
<table width="100%" border="0" cellpadding="0" cellspacing="0">
            <tr>
                <td>
<a href="add-step1.jsp?userType=internal" class="icon-link" style="background-image:url(images/add.gif);"><fmt:message key="add.new.role"/></a>
</td>
            </tr>
        </table>
            
        </div>
    </div>
    <script type="text/javascript">
        alternateTableRows('roleTable', 'tableEvenRow', 'tableOddRow');
    </script>
</fmt:bundle>