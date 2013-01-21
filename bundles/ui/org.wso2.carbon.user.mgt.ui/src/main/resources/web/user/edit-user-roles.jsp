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
<%@page session="true" %>
<%@page import="org.apache.axis2.context.ConfigurationContext" %>
<%@page import="org.wso2.carbon.CarbonConstants" %>
<%@page import="org.wso2.carbon.ui.CarbonUIMessage" %>
<%@page import="org.wso2.carbon.ui.CarbonUIUtil" %>
<%@page import="org.wso2.carbon.user.mgt.common.FlaggedName" %>
<%@page import="org.wso2.carbon.user.mgt.common.IUserAdmin" %>
<%@page import="org.wso2.carbon.user.mgt.common.UserStoreInfo" %>
<%@page import="org.wso2.carbon.user.mgt.ui.UserAdminClient"%>
<%@page import="org.wso2.carbon.utils.ServerConstants"%>


<%@page import="org.wso2.carbon.ui.util.CharacterEncoder"%><script type="text/javascript" src="../userstore/extensions/js/vui.js"></script>
<script type="text/javascript" src="../admin/js/main.js"></script>
<jsp:include page="../dialog/display_messages.jsp"/>
<jsp:include page="../userstore/display-messages.jsp"/>
<%
	FlaggedName[] data = null; 
        String username = CharacterEncoder.getSafeText(request.getParameter("username"));
        UserStoreInfo userStoreInfo = null;
        String currentUser = null;
        try {
            currentUser = (String)session.getAttribute("logged-user");
            userStoreInfo = (UserStoreInfo)session.getAttribute(UserAdminClient.USER_STORE_INFO);
            String cookie = (String) session.getAttribute(ServerConstants.ADMIN_SERVICE_COOKIE);
            String backendServerURL = CarbonUIUtil.getServerURL(config.getServletContext(), session);
            ConfigurationContext configContext =
                    (ConfigurationContext) config.getServletContext().getAttribute(CarbonConstants.CONFIGURATION_CONTEXT);
            IUserAdmin proxy =
            (IUserAdmin) CarbonUIUtil.
                    getServerProxy(new UserAdminClient(cookie, backendServerURL, configContext),
                                         IUserAdmin.class, session);
            data = proxy.getRolesOfUser(username);
            
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
<carbon:breadcrumb label="roles.of.user"
        resourceBundle="org.wso2.carbon.userstore.ui.i18n.Resources"
        topPage="false" request="<%=request%>" />

    <script type="text/javascript">
        function doValidation() {
            var fldUser = document.getElementById("username");
            var value = fldUser.value;
            if(value == "<%=userStoreInfo.getAdminUser()%>"){
                var elems = document.getElementsByName("selectedRoles");
                var found = false;
                var counter=0;
                for (counter=0; counter < elems.length; counter++) {
                    if(elems[counter].checked == true){ 
                        var role = elems[counter].value;
                        if(role == "<%=userStoreInfo.getAdminRole()%>"){
                            found = true;
                        }
                    }
                }
            }
            
            if(found == false){
                CARBON.showWarningDialog("<fmt:message key="admin.user.must.select"/>");
                return false;            
            }
            
           return true;
        }

        function doUpdate(){
            <%
            if(!currentUser.equals(userStoreInfo.getAdminUser()) && currentUser.equals(username)){
            %>
              CARBON.showConfirmationDialog('You are trying to update a role for your user. You will be signed out when you update. Do you want to proceed?',
                function(){
                    document.edit_user_roles.logout.value = 'true';
                    document.edit_user_roles.submit() ;
                },
                function(){
                    location.href = 'user-mgt.jsp?ordinal=1';
                }
              )
            <%
            } else {
            %>
                document.edit_user_roles.submit() ;
            <%
            }
            %>
        }

        function doCancel() {
            location.href = 'user-mgt.jsp?ordinal=1';
        }
       
    </script>
    <div id="middle">
        <h2><fmt:message key="role.list.of.user"/> <%=username%></h2>
        <div id="workArea">
            <form method="post" action="edit-user-roles-finish.jsp" onsubmit="return doValidation();" name="edit_user_roles" id="edit_user_roles">
                <input type="hidden" id="username" name="username" value="<%=username%>"/>
                <input type="hidden" name="selectedRoles" value="<%=userStoreInfo.getEveryOneRole()%>" checked="checked"/>
                <input type="hidden" name="logout" value="false"/>
                <table class="styledLeft">
                    <thead>
                    <tr>
                        <th><fmt:message key="assigned.roles"/></th>
                    </tr>
                    </thead>
                    <tr>
                    <td class="formRow">
                    <table class="normal">
                    <%
                    	if (data != null && data.length > 0) {
                    %>
                    <tr>
                        <td colspan="2">
                            <%
                            	String disabled = "";
                                                                if (data != null) {
                                                                    for (FlaggedName name : data) {
                                                                        if(CarbonConstants.REGISTRY_ANONNYMOUS_ROLE_NAME.equals(name.getItemName())) {
                                                                            continue;
                                                                        }
                                                                        if(userStoreInfo.getEveryOneRole().equals(name.getItemName())){
                                                                            disabled = "disabled=\"disabled\"" ;
                                                                        } else {
                                                                            disabled = "";
                                                                        }
                                                                        if (name.isSelected()) {
                            %>
                                <input type="checkbox" name="selectedRoles"
                                               value="<%=name.getItemName()%>" checked="checked" <%=disabled%>/><%=CharacterEncoder.getSafeText(name.getItemName())%><br/>
                                    <%
                                    	}
                                                                            }
                                                                        }
                                    %>
                                </td>
                    </tr>
                    <%
                    	}
                    %>
                    </table>
                    </td>
                    </tr>
                </table>
                    <br/>        
                <table class="styledLeft">
                    <thead>
                    <tr>
                        <th><fmt:message key="unassigned.roles"/></th>
                    </tr>
                    </thead>
                    <tr>
                    <td class="formRow">
                    <table class="normal">
                    <%
                    	if (data != null && data.length > 0) {
                    %>
                    <tr>
                        <td colspan="2">
                            <%
                            	if (data != null) {
                                                                    for (FlaggedName name : data) {
                                                                        if(CarbonConstants.REGISTRY_ANONNYMOUS_ROLE_NAME.equals(name.getItemName())){
                                                                            continue;
                                                                        }
                                                                        if (!name.isSelected()) {
                            %>
                                <input type="checkbox" name="selectedRoles"
                                               value="<%=name.getItemName()%>"/><%=CharacterEncoder.getSafeText(name.getItemName())%><br/>
                                    <%
                                            }
                                        }
                                    }
                                %>
                                </td>
                    </tr>
                    <%}%>
                            </table>
                        </td>
                    </tr>
                </table> 
                <table>                 
                    <tr>
                        <td class="buttonRow">
                            <input class="button" type="button" value="<fmt:message key="update"/>" onclick="doUpdate()"/>
                            <input class="button" type="button" value="<fmt:message key="cancel"/>" onclick="doCancel()"/>
                        </td>
                    </tr>
                </table>
            </form>
        </div>
    </div>
</fmt:bundle>
