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
<%@page import="org.wso2.carbon.utils.ServerConstants"%><script type="text/javascript" src="../userstore/extensions/js/vui.js"></script>
<script type="text/javascript" src="../admin/js/main.js"></script>

<jsp:useBean id="userBean" type="org.wso2.carbon.user.mgt.ui.UserBean"
             class="org.wso2.carbon.user.mgt.ui.UserBean" scope="session"/>

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

<fmt:bundle basename="org.wso2.carbon.userstore.ui.i18n.Resources">
<carbon:breadcrumb label="add.user"
                   resourceBundle="org.wso2.carbon.userstore.ui.i18n.Resources"
                   topPage="false" request="<%=request%>"/>

<script type="text/javascript">

    var skipPasswordValidation = false;

    function validateString(fld1name, regString) {
        var stringValue = document.getElementsByName(fld1name)[0].value;
        var errorMessage = "";
        if(regString != "null" && !stringValue.match(new RegExp(regString))){
            errorMessage = "No conformance";
            return errorMessage;
        }else if(regString != "null" && stringValue == ''){
            return errorMessage;
        }

        if (stringValue == '') {
            errorMessage = "Empty string";
            return errorMessage;
        }

        return errorMessage;
    }

    function doValidation() {
        var reason = "";
                
        reason = validateString("username", "<%=userStoreInfo.getUserNameRegEx()%>");
        if (reason != "") {
            if (reason == "No conformance") {
                CARBON.showWarningDialog("<fmt:message key="enter.user.name.not.conforming"/>");
            } else if (reason == "Empty string") {
            	CARBON.showWarningDialog("<fmt:message key="enter.user.name.empty"/>");
            }
            return false;
        }

        if(!skipPasswordValidation){
            reason = validatePasswordOnCreation("password", "retype", "<%=userStoreInfo.getJsRegEx()%>");
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
        } else {
            var emailPattern = /^[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,4}$/;            
            reason = validateString("email", emailPattern);
            if (reason != "") {
                if (reason == "Empty string") {
                    CARBON.showWarningDialog("<fmt:message key="enter.email.empty"/>");
                } else if (reason == "No conformance") {
                    CARBON.showWarningDialog("<fmt:message key="enter.email.not.conforming"/>");
                }
                return false;
            }
        }
        return true;
    }

    function showHideUsers(element) {
        element.style.display = (element.style.display != 'block') ? 'block' : 'none';
    }

    function doCancel() {
        location.href = 'user-mgt.jsp?ordinal=1';
    }

    function doNext() {
        document.dataForm.action = "add-step2.jsp";
        if (doValidation() == true) {
            document.dataForm.submit();
        }
    }

    function doFinish() {
        document.dataForm.action = "add-finish.jsp";
        if (doValidation() == true) {
            document.dataForm.submit();
        }
    }

    function definePasswordHere(){
        var passwordMethod = document.getElementById('defineHere');
        if(passwordMethod.checked){
            skipPasswordValidation = false;
            jQuery('#emailRow').hide();
            jQuery('#passwordRow').show();
            jQuery('#retypeRow').show();
        }
    }

    function askPasswordFromUser(){
        var emailInput = document.getElementsByName('email')[0];
        var passwordMethod = document.getElementById('askFromUser');
        if(passwordMethod.checked){
            skipPasswordValidation = true;
            jQuery('#passwordRow').hide();
            jQuery('#retypeRow').hide();
            if(emailInput == null) {
                var mainTable = document.getElementById('mainTable');
                var newTr = mainTable.insertRow(mainTable.rows.length);
                newTr.id = "emailRow";
                newTr.innerHTML = '<td><fmt:message key="enter.email"/><font color="red">*</font></td><td>' +
                        '<input type="text" name="email" style="width:150px"/></td>' ;
            } else {
                jQuery('#emailRow').show();    
            }
        }
    }


</script>
<div id="middle">
    <h2><fmt:message key="add.user"/></h2>

    <div id="workArea">
        <h3><fmt:message key="step.1.user"/></h3>

        <form method="post" action="add-finish.jsp" name="dataForm"  onsubmit="return doValidation();">
            <table class="styledLeft" id="userAdd" width="60%">
                <thead>
                    <tr>
                        <th><fmt:message key="enter.user.name"/></th>
                    </tr>
                </thead>
                <tr>
                    <td class="formRaw">
                        <table class="normal" id="mainTable">
                            <tr>
                                <td><fmt:message key="user.name"/><font color="red">*</font>
                                </td>
                                <td><input type="text" name="username"
                                           value="<jsp:getProperty name="userBean" property="username" />"
                                           style="width:150px"/></td>
                            </tr>
                            <%
                                if (CarbonUIUtil.isContextRegistered(config, "/identity-mgt/")) {
                            %>

                            <tr>
                                <td >
                                    <input type="radio" name="passwordMethod"  id="defineHere"
                                           value="defineHere" checked="checked" onclick="definePasswordHere();"/>
                                </td>
                                <td><fmt:message key="define.password.here"/></td>
                            </tr>
                            <tr>
                                <td>
                                    <input type="radio" name="passwordMethod"  id="askFromUser"
                                           value="askFromUser" onclick="askPasswordFromUser();" />
                                </td>
                                <td><fmt:message key="ask.password.user"/></td>
                            </tr>

                            <%
                                }
                            %>
                            <tr id="passwordRow">
                                <td><fmt:message key="password"/><font color="red">*</font></td>
                                <td><input type="password" name="password" style="width:150px"/></td>
                            </tr>
                            <tr id="retypeRow">
                                <td><fmt:message key="password.repeat"/><font color="red">*</font></td>
                                <td><input type="password" name="retype" style="width:150px"/></td>
                            </tr>
                        </table>
                    </td>
                </tr>
                <tr>
                    <td class="buttonRow">
                        <%
                            if(CarbonUIUtil.isUserAuthorized(request, "/permission/admin/configure/security")){
                        %>
                        <input type="button" class="button" value="<fmt:message key="next"/> >" onclick="doNext();"/>
                        <%
                            }
                        %>
                        <input type="button" class="button" value="<fmt:message key="finish"/>" onclick="doFinish();"/>
                        <input type="button" class="button" value="<fmt:message key="cancel"/>" onclick="doCancel();"/>
                    </td>
                </tr>
            </table>
        </form>
    </div>
    <p>&nbsp;</p>
</div>
</fmt:bundle>