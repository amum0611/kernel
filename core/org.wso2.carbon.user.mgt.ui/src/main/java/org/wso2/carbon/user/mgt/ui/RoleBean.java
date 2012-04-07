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
package org.wso2.carbon.user.mgt.ui;

import java.util.Arrays;

public class RoleBean {
    
    private String roleName = "";
    private String[] selectedUsers = new String[0];
    private String[] selectedPermissions = new String[0];
    private String[] shownUsers = new String[0];
    
    private String storeType = "";
    
    public String getStoreType() {
        return storeType;
    }
    public void setStoreType(String storeType) {
        this.storeType = storeType;
    }
    public String getRoleName() {
        return roleName;
    }
    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }
    public String[] getSelectedUsers() {
        return Arrays.copyOf(selectedUsers, selectedUsers.length);
    }
    public void setSelectedUsers(String[] selectedUsers) {
        this.selectedUsers = Arrays.copyOf(selectedUsers, selectedUsers.length);
    }
    public String[] getSelectedPermissions() {
        return Arrays.copyOf(selectedPermissions, selectedPermissions.length);
    }
    public void setSelectedPermissions(String[] selectedPermissions) {
        this.selectedPermissions = Arrays.copyOf(selectedPermissions, selectedPermissions.length);
    }
    public String[] getShownUsers() {
        return Arrays.copyOf(shownUsers, shownUsers.length);
    }
    public void setShownUsers(String[] shownUsers) {
        this.shownUsers = Arrays.copyOf(shownUsers, shownUsers.length);
    }
    public void cleanup(){
        roleName = null;
        selectedUsers = null;
        selectedPermissions = null;
    }
    
    

}
