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
<%@page import="org.wso2.carbon.user.mgt.common.IUserAdmin" %>
<%@page import="org.wso2.carbon.user.mgt.common.UserStoreInfo"%>
<%@page import="org.wso2.carbon.user.mgt.ui.UserAdminClient"%>
<%@page import="org.wso2.carbon.utils.ServerConstants"%>
<%@ page import="java.util.Arrays" %>
<%@page import="java.util.List"%>
<%@ page import="org.wso2.carbon.user.mgt.ui.RoleBean" %>

<%
try{
%>

<jsp:useBean id="roleBean" type="org.wso2.carbon.user.mgt.ui.RoleBean" scope="session"/>
<jsp:setProperty name="roleBean" property="*" />

<%
    }
    catch(InstantiationException e){
        CarbonUIMessage.sendCarbonUIMessage("Your session has timed out. Please try again.", CarbonUIMessage.ERROR, request);
%>
        <script type="text/javascript">
            function forward() {
                location.href = "role-mgt.jsp?ordinal=1";
            }
        </script>

        <script type="text/javascript">
            forward();
        </script>
<%
        return;
    }
%>

<script type="text/javascript" src="../userstore/extensions/js/vui.js"></script>
<script type="text/javascript" src="../admin/js/main.js"></script>

<%
       boolean showFilterMessage = false;
       String[] datas = null;
       String[] selected = null;
    
       String filter = (String)request.getParameter("org.wso2.usermgt.role.add.filter");
       if(filter == null){
          filter = "";
       }else{
          filter = filter.trim();
       }
       
        String userType = (String)session.getAttribute("org.wso2.carbon.user.userType");
        UserStoreInfo userStoreInfo = null;
        try {
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
                datas = proxy.listUsers(filter);
                if(datas == null || datas.length == 0){
                    showFilterMessage = true;
                }
            }
            selected = ((RoleBean)session.getAttribute("roleBean")).getSelectedUsers();
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
    <carbon:breadcrumb label="add.users"
                       resourceBundle="org.wso2.carbon.userstore.ui.i18n.Resources"
                       topPage="false" request="<%=request%>"/>

    <script type="text/javascript">
        function doValidation() {
            var reason = "";
            return true;
        }

        function doCancel() {
            location.href = 'role-mgt.jsp?ordinal=1';
        }
        

        function goBack() {           
            <%
            String urlData = request.getQueryString();            
            %>
        	location.href = 'add-step2.jsp?<%=urlData%>';
        }
    </script>

   

    <div id="middle">
        <h2><fmt:message key="add.user.role"/></h2>

	    <script type="text/javascript">
	    
	       <% if(showFilterMessage == true){ %>
	            CARBON.showInfoDialog('<fmt:message key="no.users.filtered"/>', null, null);
	       <% } %>
	       
	    </script>
        <div id="workArea">
            <h3><fmt:message key="step.3.role"/></h3>
            <form name="filterForm" method="post" action="add-step3.jsp">
                <table class="normal">
                    <tr>
                        <td><fmt:message key="list.users"/></td>
                        <td>
                           <input type="text" name="org.wso2.usermgt.role.add.filter" value="<%=filter%>"/>
                        </td>
                        <td>
                        <input class="button" type="submit" value="<fmt:message key="user.search"/>" />
                        </td>
                    </tr>
                </table>
            </form>
            <form method="get" name="dataForm" action="add-finish.jsp">
                <table class="styledLeft">
                    <thead>
                    <tr>
                        <th><fmt:message key="add.users"/></th>
                    </tr>
                    </thead>
                    <tr>
                        <td class="formRaw">
                            <table class="normal">
                            	<% if (datas != null && datas.length > 1) { %>
                                <tr>
                                     <td>
                                         <a href="#" onclick="doSelectAll('selectedUsers');"/><fmt:message key="select.all"/></a> | 
                                         <a href="#" onclick="doUnSelectAll('selectedUsers');"/><fmt:message key="unselect.all"/></a>
                                     </td>
                                 </tr>
                                 <% } %>
                                  <%
                                       if (datas != null) {
                                           int count = 0;
                                           List<String> list = Arrays.asList(selected); 
                                           for (String data : datas) {
                                               if (data != null) {
                                                   
                                                   if(CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME.equals(data)){
                                               	      continue;
                                                   }
                                                   
                                                   if(count == userStoreInfo.getMaxUserListCount()){
                                                       break;
                                                   }
                                                   count ++;
                                                   
                                                   String docheck =  "";
                                                   if (list.contains(data)) {
                                                       docheck = "checked=\"checked\"";
                                                   }
                                   %>
                                   <tr>
                                       <td><input type="checkbox"
                                                  name="selectedUsers"
                                                  value="<%=data%>"  <%=docheck%>/><%=data%>
                                       </td>
                                   </tr>
                                   <%
                                               }
                                           }
                                       }
                                   %>
                            </table>
                            <!-- normal table -->
                        </td>
                    </tr>
                    <%
                        if(datas != null){
                            int length = datas.length; 
                            if(length > userStoreInfo.getMaxUserListCount()){
                            %>
                            <tr><td><strong><fmt:message key="more.users"/></strong></td></tr>
                            <%
                            }
                        }
                    %>
                    <tr>
                        <td class="buttonRow">
                            <input type="button" class="button" value="< <fmt:message key="back"/>" onclick="goBack();"/>
                            <input type="submit" class="button" value="<fmt:message key="finish"/>"/>
                            <input type="button" class="button" value="<fmt:message key="cancel"/>" onclick="doCancel();"/>
                        </td>
                    </tr>
                </table>
            </form>
        </div>
    </div>
</fmt:bundle>