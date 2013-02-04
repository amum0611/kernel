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

<%@page import="org.wso2.carbon.user.mgt.common.UserStoreInfo"%>
<%@page import="org.wso2.carbon.user.mgt.ui.UserAdminClient"%>
<%@page import="org.wso2.carbon.utils.ServerConstants"%>
<%@page import="org.wso2.carbon.ui.CarbonUIUtil"%>
<%@page import="org.apache.axis2.context.ConfigurationContext"%>
<%@page import="org.wso2.carbon.CarbonConstants"%>
<%@page import="org.wso2.carbon.user.mgt.common.IUserAdmin"%>
<%@page import="org.wso2.carbon.ui.CarbonUIMessage"%>

<script type="text/javascript" src="../userstore/extensions/js/vui.js"></script>
<script type="text/javascript" src="../admin/js/main.js"></script>

<jsp:include page="../userstore/display-messages.jsp"/>

<%
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

<jsp:useBean id="roleBean" type="org.wso2.carbon.user.mgt.ui.RoleBean" class="org.wso2.carbon.user.mgt.ui.RoleBean" scope="session"/>

<fmt:bundle basename="org.wso2.carbon.userstore.ui.i18n.Resources">
    <carbon:breadcrumb label="add.user.role"
                       resourceBundle="org.wso2.carbon.userstore.ui.i18n.Resources"
                       topPage="false" request="<%=request%>"/>

    <script type="text/javascript">

        function validateTextForIllegal(fld) {

           var illegalChars = /([?#^\|<>\"\'])/;
           var illegalCharsInput = /(\<[a-zA-Z0-9\s\/]*>)/;
             if (illegalChars.test(fld) || illegalCharsInput.test(fld)) {
                return false;
             } else {
               return true;
             }
         }

        function validateString(fld1name, regString) {
            var stringValue = document.getElementsByName(fld1name)[0].value;
            var errorMessage = "";
            if(regString != "null" && !stringValue.match(new RegExp(regString))) {
                errorMessage = "No conformance";
                return errorMessage;
            } else if (regString != "null" && stringValue=="") {
                return errorMessage;
            }

            if (stringValue == '') {
                errorMessage = "Empty string";
                return errorMessage;
            }

            return errorMessage;
        }

        function doValidation() {
            
            reason = validateString("roleName", "<%=userStoreInfo.getRoleNameRegEx()%>");
            if (reason != "") {
                if (reason == "No conformance") {
                    CARBON.showWarningDialog("<fmt:message key="enter.role.name.not.conforming"/>");
                } else if (reason == "Empty string") {
                    CARBON.showWarningDialog("<fmt:message key="enter.role.name.empty"/>");
                }
                return false;
            }

            return true;
        }

        function doCancel() {
            location.href = 'role-mgt.jsp?ordinal=1';
        }
        
        function doNext() {
            if(!validateTextForIllegal(document.getElementsByName("roleName")[0].value)) {
                CARBON.showWarningDialog(org_wso2_carbon_registry_common_ui_jsi18n["the"] + " "+ "role name "+" " + org_wso2_carbon_registry_common_ui_jsi18n["contains.illegal.chars"]);
                return false;
            }
            document.addRoleForm.action="add-step2.jsp";
            if(doValidation() == true) {
                document.addRoleForm.submit();
            }
        }
        
       
    </script>
   
    <div id="middle">
        <h2><fmt:message key="add.user.role"/></h2>
        
        <div id="workArea">
            <h3><fmt:message key="step.1.role"/></h3>
            <form method="post" name="addRoleForm" onsubmit="return doValidation();" action="add-finish.jsp">
                <table class="styledLeft">
                    <thead>
                    <tr>
                        <th><fmt:message key="enter.role.details"/></th>
                    </tr>
                    </thead>
                    <tr>
                        <td class="formRaw">
                            <table class="normal">
                                <tr>
                                    <td><fmt:message key="role.name"/><font color="red">*</font>
                                    </td>
                                    <td><input type="text" name="roleName" value="<jsp:getProperty name="roleBean" property="roleName" />"/></td>
                                </tr>
                            </table>
                            <!-- normal table -->
                        </td>
                    </tr>
                    <tr>
                        <td class="buttonRow">
                            <input type="button" class="button" value="<fmt:message key="next"/> >" onclick="doNext();"/>
                            <input type="submit" class="button" value="<fmt:message key="finish"/>">
                            <input type="button" class="button" value="<fmt:message key="cancel"/>" onclick="doCancel();"/>
                        </td>
                    </tr>
                </table>
            </form>
        </div>
    </div>

</fmt:bundle>