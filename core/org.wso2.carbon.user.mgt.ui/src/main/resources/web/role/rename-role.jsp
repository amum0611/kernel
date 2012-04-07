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
 ~ specific langauage governing permissions and limitations
 ~ under the License.
 -->
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib uri="http://wso2.org/projects/carbon/taglibs/carbontags.jar" prefix="carbon" %>
<%@page import="org.wso2.carbon.ui.util.CharacterEncoder"%>
<%@ page import="org.wso2.carbon.user.mgt.common.UserStoreInfo" %>
<%@ page import="org.wso2.carbon.user.mgt.ui.UserAdminClient" %>

<script type="text/javascript" src="../userstore/extensions/js/vui.js"></script>
<jsp:include page="../userstore/display-messages.jsp"/>

<%
    UserStoreInfo userStoreInfo = null;
    if(session.getAttribute(UserAdminClient.USER_STORE_INFO) != null){
        userStoreInfo = (UserStoreInfo)session.getAttribute(UserAdminClient.USER_STORE_INFO);
    }else{
%>
        <script>window.location.href='../admin/logout_action.jsp'</script>
<%      return;
    }
%>

<fmt:bundle basename="org.wso2.carbon.userstore.ui.i18n.Resources">
    <carbon:breadcrumb label="rename.user.role"
                       resourceBundle="org.wso2.carbon.userstore.ui.i18n.Resources"
                       topPage="false" request="<%=request%>"/>

    <script type="text/javascript">

        function validateString(fld1name, regString) {
            var errorMessage = "";
            if(regString != "null" && !fld1name.match(new RegExp(regString))) {
                errorMessage = "No conformance";
                return errorMessage;
            } else if (regString != "null" && fld1name=="") {
                return errorMessage;
            }

            if (fld1name == '') {
                errorMessage = "Empty string";
                return errorMessage;
            }

            return errorMessage;
        }

        function doValidation() {
            var fld = document.getElementById("roleName");
            var reason = validateString(fld.value,"<%=userStoreInfo.getRoleNameRegEx()%>");
            if(reason != ""){
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

        function doRename() {
		    if(doValidation()){
                var oldRoleName = "<%=CharacterEncoder.getSafeText(request.getParameter("roleName"))%>";
                var newRoleName = document.getElementById("roleName").value;
                location.href = 'rename-role-finish.jsp?oldRoleName='+oldRoleName+'&newRoleName='+newRoleName;
            }
        }

    </script>


    <div id="middle">
        <h2><fmt:message key="rename.user.role"/></h2>
        <div id="workArea">
            <form>
                <table class="styledLeft">
                    <thead>
                    <tr>
                        <th><fmt:message key="enter.role.name"/></th>
                    </tr>
                    </thead>
                    <tr>
                        <td class="formRaw">
                            <table class="normal">
                                <tr>
                                    <td><fmt:message key="role.rename"/><font color="red">*</font>
                                    </td>
                                    <td><input type="text" id="roleName"/></td>
                                </tr>
                            </table>
                            <!-- normal table -->
                        </td>
                    </tr>
                    <tr>
                        <td class="buttonRow">
                            <input type="button" class="button" onclick="doRename();" value="<fmt:message key="finish"/>" />
                            <input type="button" class="button" onclick="doCancel();" value="<fmt:message key="cancel"/>" />
                        </td>
                    </tr>
                </table>
            </form>
        </div>
    </div>

</fmt:bundle>