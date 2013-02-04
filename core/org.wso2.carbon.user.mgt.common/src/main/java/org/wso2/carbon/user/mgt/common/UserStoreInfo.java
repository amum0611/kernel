/*
*  Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.carbon.user.mgt.common;

public class UserStoreInfo {

    private boolean isReadOnly = true;

    private boolean isPasswordsExternallyManaged = false;

    private String everyOneRole;

    private String adminRole;

    private String adminUser;

    private int maxUserListCount;

    private String jsRegEx;

    private String userNameRegEx;

    private String roleNameRegEx;

    private boolean isBulkImportSupported;
    
    private String externalIdP;

    private String[] requiredUserClaims;

    private String[] userClaims;

    public boolean isBulkImportSupported() {
        return isBulkImportSupported;
    }

    public void setBulkImportSupported(boolean bulkImportSupported) {
        isBulkImportSupported = bulkImportSupported;
    }

    public boolean isPasswordsExternallyManaged() {
        return isPasswordsExternallyManaged;
    }

    public void setPasswordsExternallyManaged(boolean passwordsExternallyManaged) {
        isPasswordsExternallyManaged = passwordsExternallyManaged;
    }

    public String getEveryOneRole() {
        return everyOneRole;
    }

    public String getAdminRole() {
        return adminRole;
    }

    public String getAdminUser() {
        return adminUser;
    }

    public void setEveryOneRole(String everyOneRole) {
        this.everyOneRole = everyOneRole;
    }

    public void setAdminRole(String adminRole) {
        this.adminRole = adminRole;
    }

    public void setAdminUser(String adminUser) {
        this.adminUser = adminUser;
    }

    public boolean isReadOnly() {
        return isReadOnly;
    }

    public void setReadOnly(boolean isReadOnly) {
        this.isReadOnly = isReadOnly;
    }

    public int getMaxUserListCount() {
        return maxUserListCount;
    }

    public void setMaxUserListCount(int maxUserListCount) {
        this.maxUserListCount = maxUserListCount;
    }

    public String getJsRegEx() {
        return jsRegEx;
    }

    public void setJsRegEx(String jsRegEx) {
        this.jsRegEx = jsRegEx;
    }

    public String getUserNameRegEx() {
        return userNameRegEx;
    }

    public void setUserNameRegEx(String userNameRegEx) {
        this.userNameRegEx = userNameRegEx;
    }

    public String getRoleNameRegEx() {
        return roleNameRegEx;
    }

    public void setRoleNameRegEx(String roleNameRegEx) {
        this.roleNameRegEx = roleNameRegEx;
    }

    public String getExternalIdP() {
        return externalIdP;
    }

    public void setExternalIdP(String externalIdP) {
        this.externalIdP = externalIdP;
    }

    public String[] getRequiredUserClaims() {
        return requiredUserClaims;
    }

    public void setRequiredUserClaims(String[] requiredUserClaims) {
        this.requiredUserClaims = requiredUserClaims;
    }

    public String[] getUserClaims() {
        return userClaims;
    }

    public void setUserClaims(String[] userClaims) {
        this.userClaims = userClaims;
    }
}
