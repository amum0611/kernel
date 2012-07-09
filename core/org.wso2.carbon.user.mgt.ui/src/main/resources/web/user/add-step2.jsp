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
<%@page import="org.apache.axis2.context.ConfigurationContext" %>
<%@page import="org.wso2.carbon.CarbonConstants" %>
<%@page import="org.wso2.carbon.ui.CarbonUIMessage" %>
<%@page import="org.wso2.carbon.ui.CarbonUIUtil" %>
<%@page import="org.wso2.carbon.user.mgt.common.FlaggedName" %>
<%@page import="org.wso2.carbon.user.mgt.common.IUserAdmin"%>
<%@ page import="org.wso2.carbon.user.mgt.common.UserStoreInfo" %>
<%@page import="org.wso2.carbon.user.mgt.ui.UserAdminClient"%>
<%@page import="org.wso2.carbon.utils.ServerConstants"%>
<%@page import="org.wso2.carbon.ui.util.CharacterEncoder"%>

<script type="text/javascript" src="../userstore/extensions/js/vui.js"></script>
<script type="text/javascript" src="../admin/js/main.js"></script>


<%
    try{
%>

<jsp:useBean id="userBean" type="org.wso2.carbon.user.mgt.ui.UserBean" scope="session"/>
<jsp:setProperty name="userBean" property="*" />
<jsp:include page="../dialog/display_messages.jsp"/>

<fmt:bundle basename="org.wso2.carbon.userstore.ui.i18n.Resources">
<jsp:include page="../userstore/display-messages.jsp"/>
<carbon:breadcrumb label="select.roles" resourceBundle="org.wso2.carbon.userstore.ui.i18n.Resources" topPage="false" request="<%=request%>"/>
    <%

        FlaggedName[] groupData = new FlaggedName[0];
        UserStoreInfo userStoreInfo = null;
        String cookie = (String) session.getAttribute(ServerConstants.ADMIN_SERVICE_COOKIE);
        String backendServerURL = CarbonUIUtil.getServerURL(config.getServletContext(), session);
        ConfigurationContext configContext = (ConfigurationContext) config.getServletContext().getAttribute(CarbonConstants.CONFIGURATION_CONTEXT);
        IUserAdmin proxy = (IUserAdmin) CarbonUIUtil.getServerProxy(new UserAdminClient(cookie, backendServerURL, configContext), IUserAdmin.class, session);
        groupData = proxy.getAllRolesNames();
        userStoreInfo = proxy.getUserStoreInfo();

   %>

    <div id="middle">
        <h2><fmt:message key="add.user"/></h2>

        <div id="workArea">
            <h3><fmt:message key="step.2.user"/></h3>
            <form method="get" action="add-finish.jsp" name="dataForm">
                <table class="styledLeft" id="userAdd" width="60%">
                    <thead>
                    <tr>
                        <th><fmt:message key="select.roles"/></th>
                    </tr>
                    </thead>
                    <tr>
                        <td class="formRaw">
                            <table class="normal">
                                 <tr>
                                    <td colspan="2">
                                        <a href="#" onclick="doSelectAll('userRoles');"/><fmt:message key="select.all"/></a> | 
                                        <a href="#" onclick="doUnSelectAll('userRoles');"/><fmt:message key="unselect.all"/></a>
                                    </td>
                                 </tr> 
                                 <tr>
                                     <td>
                                        <table class="normal" id="rolesTableId">
                                            <%
                                            	String checked = "";
                                                if (groupData != null) {
                                                    for (FlaggedName data : groupData) {
                                                        if (data != null) { //Sometimes a null object comes. Maybe a bug in Axis!!
                                                            if(CarbonConstants.REGISTRY_ANONNYMOUS_ROLE_NAME.equals(data.getItemName())) {
                                                                continue;
                                                            }
                                                            if(data.getItemName().equals(userStoreInfo.getEveryOneRole())){
                                                                checked = "checked=\"checked\" disabled=\"disabled\"" ;
                                                            }else{
                                                                checked = "";
                                                            }
                                            %>
                                                    <tr>
                                                <td>
                                                    <input type="checkbox" name="userRoles" value="<%=data.getItemName()%>" <%=checked%> /><%=CharacterEncoder.getSafeText(data.getItemName())%>
                                                </td>
                                            </tr>
                                            <%
                                                        }
                                                    }
                                                }
                                            %>
                                        </table>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                    <tr>
                        <td class="buttonRow">
                            <input type="button" class="button" value="<fmt:message key="back"/>" onclick="doBack();"/>
                            <input type="submit" class="button" value="<fmt:message key="finish"/>">
                            <input type="button" class="button" value="<fmt:message key="cancel"/>" onclick="doCancel();"/>
                        </td>
                    </tr>
                </table>
            </form>
        </div>
        <p>&nbsp;</p>
    </div>
</fmt:bundle>

<%
    }
    catch(InstantiationException e){
        CarbonUIMessage.sendCarbonUIMessage("Your session has timed out. Please try again.", CarbonUIMessage.ERROR, request);
%>
        <script type="text/javascript">
            function forward() {
                location.href = 'user-mgt.jsp?ordinal=1';
            }

            forward();
        </script>
<%
    }catch(Exception e){
        CarbonUIMessage uiMsg = new CarbonUIMessage(e.getMessage(), CarbonUIMessage.ERROR, e);
        session.setAttribute(CarbonUIMessage.ID, uiMsg);
%>
        <jsp:include page="../admin/error.jsp"/>
<%
    }
%>

    <script type="text/javascript">
        function doValidation() {
            return true;
        }

        function doCancel() {
            location.href = 'user-mgt.jsp?ordinal=1';
        }

        function doBack() {
            history.go(-1);
        }

    </script>