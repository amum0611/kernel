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


<%@page import="org.wso2.carbon.ui.util.CharacterEncoder"%>
<%@ page import="org.wso2.carbon.user.mgt.ui.PaginatedNamesBean" %>
<%@ page import="org.wso2.carbon.user.mgt.ui.Util" %>
<script type="text/javascript" src="../userstore/extensions/js/vui.js"></script>
<script type="text/javascript" src="../admin/js/main.js"></script>
<jsp:include page="../dialog/display_messages.jsp"/>
<jsp:include page="../userstore/display-messages.jsp"/>

<jsp:useBean id="roleBeanEditUsers" type="org.wso2.carbon.user.mgt.ui.RoleBean" class="org.wso2.carbon.user.mgt.ui.RoleBean" scope="session"/>
<jsp:setProperty name="roleBeanEditUsers" property="*"/>

 <%
 	boolean showFilterMessage = false;
         FlaggedName[] data = null; 
         String filter = (String)request.getParameter("org.wso2.usermgt.role.edit.filter");
         if(filter == null){
             filter = (String)session.getAttribute("org.wso2.usermgt.role.edit.filter");
         }
         
         if(filter == null){
            filter = "";
         }else{
            filter = filter.trim();
            session.setAttribute("org.wso2.usermgt.role.edit.filter", filter);
         }
         String roleName = CharacterEncoder.getSafeText(request.getParameter("roleName"));
         if(roleName == null || roleName.trim().length()==0){
             roleName = roleBeanEditUsers.getRoleName();
         }
         String userType = (String) request.getParameter("userType");
         session.setAttribute("org.wso2.carbon.user.userType", userType);
         UserStoreInfo userStoreInfo = null;
         String currentUser =  null;
         try {
             currentUser =  (String)session.getAttribute("logged-user");
             userStoreInfo = (UserStoreInfo)session.getAttribute(UserAdminClient.USER_STORE_INFO);
             String cookie = (String) session.getAttribute(ServerConstants.ADMIN_SERVICE_COOKIE);
             String backendServerURL = CarbonUIUtil.getServerURL(config.getServletContext(), session);
             ConfigurationContext configContext =
                     (ConfigurationContext) config.getServletContext().getAttribute(CarbonConstants.CONFIGURATION_CONTEXT);
             IUserAdmin proxy =
             (IUserAdmin) CarbonUIUtil.
                     getServerProxy(new UserAdminClient(cookie, backendServerURL, configContext),
                                          IUserAdmin.class, session);
             if(filter.length() > 0){
                 data = proxy.getUsersOfRole(roleName, filter);
                 if(data == null || data.length == 0){
                     showFilterMessage = true;
                 }
             }

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
<carbon:breadcrumb label="users.in.the.role"
		resourceBundle="org.wso2.carbon.userstore.ui.i18n.Resources"
		topPage="false" request="<%=request%>" />

    <script type="text/javascript">
        function doValidation() {
            var fldRole = document.getElementById("roleName");
            var value = fldRole.value;
            if(value == "<%=userStoreInfo.getAdminRole()%>"){

                var elems = document.getElementsByName("selectedUsers");
                var selected = false;
                var counter=0;
                for (counter=0; counter < elems.length; counter++) {
                    if(elems[counter].checked == true){ 
                        var user = elems[counter].value;
                        if(user == "<%=userStoreInfo.getAdminUser()%>"){
                        	selected = true;
                        }
                    }
                }

                elems = document.getElementsByName("shownUsers");
                shown = false;
                counter=0;
                for (counter=0; counter < elems.length; counter++) {
                    if(elems[counter].checked == true){ 
                        var user = elems[counter].value;
                        if(user == "<%=userStoreInfo.getAdminUser()%>"){
                        	shown = true;
                        }
                    }
                }
                
                
            }           
            
            
            if(shown == true && selected == false){
                CARBON.showWarningDialog("<fmt:message key="admin.user.must.select"/>");
                return false;            
            }
            
            return true;
        }

        function doUpdate(){
            <%
            if(!currentUser.equals(userStoreInfo.getAdminUser())){
                if(filter.length() > 0){
                    for(int i=0;i<data.length;i++){
                        if(data[i].getItemName().equals(currentUser)){
%>
                            CARBON.showConfirmationDialog('You are trying to update a role for your user. You will be signed out when you update. Do you want to proceed?',
                                function(){
                                    document.edit_users.logout.value = 'true';
                                    document.edit_users.submit() ;
                                },
                                function(){
                                    location.href = 'role-mgt.jsp?ordinal=1';
                                }
                            )
<%
                            break;
                        }
                         if(i==data.length-1){
%>
                            document.edit_users.submit() ;
<%
                         }
                    }
                }
            } else {
%>
                document.edit_users.submit() ;
<%
            }
%>
        }

	    function doCancel() {
            location.href = 'role-mgt.jsp?ordinal=1';
        }

	    <%if(showFilterMessage == true){%>
		jQuery(document).ready(function() {
            CARBON.showInfoDialog('<fmt:message key="no.users.filtered"/>', null, null);
   		});
       <%}%>
       
    </script>
   
   

    <div id="middle">
        <h2><fmt:message key="users.list.in.role"/> <%=roleName%></h2>

        <script type="text/javascript">
        
           <%if(showFilterMessage == true){%>
                CARBON.showInfoDialog('<fmt:message key="no.users.filtered"/>', null, null);
           <%}%>
           
        </script>
        <div id="workArea">
            <form name="filterForm" method="post" action="edit-users.jsp?roleName=<%=roleName%>">
                <table class="normal">
                    <tr>
                        <td><fmt:message key="list.users"/></td>
                        <td>
                           <input type="text" name="org.wso2.usermgt.role.edit.filter" value="<%=filter%>"/>
                        </td>
                        <td>
                        <input class="button" type="submit" value="<fmt:message key="user.search"/>" />
                        </td>
                    </tr>
                </table>
            </form>
            <p>&nbsp;</p>
            <% if (data != null) {
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
                PaginatedNamesBean bean = Util.retrivePaginatedFlggedName(pageNumber, data);
                FlaggedName[] users = bean.getNames();

            %>

            <carbon:paginator pageNumber="<%=pageNumber%>"
                              numberOfPages="<%=bean.getNumberOfPages()%>"
                              noOfPageLinksToDisplay="<%=noOfPageLinksToDisplay%>"
                              page="edit-users.jsp" pageNumberParameterName="pageNumber"
                              parameters="<%="roleName="+roleName%>" />
            <form method="post" action="edit-users-finish.jsp" onsubmit="return doValidation();" name="edit_users" id="edit_users">
                <input type="hidden" id="roleName" name="roleName" value="<%=roleName%>"/>
                <input type="hidden" id="logout" name="logout" value="false"/>
                <table class="styledLeft">
                    <thead>
                    <tr>
                        <th><fmt:message key="users.in.the.role"/></th>
                    </tr>
                    </thead>
                    <tr>
					<td class="formRow">
					<table class="normal">
                    <%
                    	if (data != null && data.length > 0) {
                    %>
                    <tr>
                        <!-- td><fmt:message key="users"/></td -->
                        <td colspan="2">
                               <a href="#" onclick="doSelectAll('selectedUsers');"/><fmt:message key="select.all"/></a> | 
                               <a href="#" onclick="doUnSelectAll('selectedUsers');"/><fmt:message key="unselect.all"/></a>
                        </td>
                    </tr>
                    <%
                    	}
                    %>
                    <tr>
                        <td colspan="2">
                            <%
                                                                if (data != null) {
                                                                    for (FlaggedName name : users) {
                                                                        if (name != null) {
                                                                            String doCheck = "";
                                                                            if(name.getItemName().equals(CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME)){
                                                                                continue;
                                                                            }
                                                                            if (name.isSelected()) {
                                                                                doCheck = "checked=\"checked\"";
                                                                            }
                            %>
                                <input type="checkbox" name="selectedUsers"
                                               value="<%=name.getItemName()%>" <%=doCheck%>/><%=CharacterEncoder.getSafeText(name.getItemName())%>
                                               <input type="hidden" name="shownUsers" value="<%=CharacterEncoder.getSafeText(name.getItemName())%>"/><br/>
                                    <%
                                            }
                                        }
                                    }

                                %>
                                </td>
</tr>
                            </table>

                        </td>
                    </tr>
                    <carbon:paginator pageNumber="<%=pageNumber%>"
                                      numberOfPages="<%=bean.getNumberOfPages()%>"
                                      noOfPageLinksToDisplay="<%=noOfPageLinksToDisplay%>"
                                      page="edit-users.jsp" pageNumberParameterName="pageNumber"
                                      parameters="<%="roleName="+roleName%>"/>
                    <%
                        }
                    %>
                    <tr>
                        <td class="buttonRow">
                            <input class="button" type="button" value="<fmt:message key="update"/>" onclick="doUpdate()"/>
                            <input class="button" type="button" value="<fmt:message key="finish"/>" onclick="doCancel()"/>
                            <input class="button" type="button" value="<fmt:message key="cancel"/>" onclick="doCancel()"/>
                        </td>
                    </tr>
                </table>
            </form>
        </div>
    </div>
</fmt:bundle>
