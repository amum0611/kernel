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
package org.wso2.carbon.user.mgt;

import org.apache.axis2.AxisFault;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.core.AbstractAdmin;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.user.mgt.common.*;
import org.wso2.carbon.user.mgt.internal.UserMgtDSComponent;

import javax.activation.DataHandler;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;

public class UserAdmin extends AbstractAdmin implements IUserAdmin {

    private static Log log = LogFactory.getLog(UserAdmin.class);
    
    public UserAdmin() {

    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.wso2.carbon.user.mgt.UserAdmin#listInternalUsers(java.lang.String)
     */
    /*
     * (non-Javadoc)
     * 
     * @see org.wso2.carbon.user.mgt.TestClass#listUsers(java.lang.String)
     */
    public String[] listUsers(String filter) throws UserAdminException {
        UserRealmProxy userAdminCore = new UserRealmProxy(super.getUserRealm());
        return userAdminCore.listUsers(filter);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.wso2.carbon.user.mgt.UserAdmin#getInternalRoles()
     */
    /*
     * (non-Javadoc)
     * 
     * @see org.wso2.carbon.user.mgt.TestClass#getAllRolesNames()
     */
    public FlaggedName[] getAllRolesNames() throws UserAdminException {
        UserRealmProxy userAdminCore = new UserRealmProxy(super.getUserRealm());
        FlaggedName[] roleNames = userAdminCore.getAllRolesNames();
        Arrays.sort(roleNames, new Comparator<FlaggedName>() {
            public int compare(FlaggedName o1, FlaggedName o2) {
                return o1.getItemName().toLowerCase().compareTo(o2.getItemName().toLowerCase());
            }
        });
        return roleNames;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.wso2.carbon.user.mgt.TestClass#isWritable()
     */
    public UserStoreInfo getUserStoreInfo() throws UserAdminException {
        UserRealmProxy userAdminCore = new UserRealmProxy(super.getUserRealm());
        return userAdminCore.getUserStoreInfo();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.wso2.carbon.user.mgt.UserAdmin#addUserToInternalStore(java.lang.String
     * , java.lang.String, java.lang.String[])
     */
    /*
     * (non-Javadoc)
     * 
     * @see org.wso2.carbon.user.mgt.TestClass#addUser(java.lang.String,
     * java.lang.String, java.lang.String[], java.util.Map, java.lang.String)
     */
    public void addUser(String userName, String password, String[] roles, ClaimValue[] claims,
            String profileName) throws UserAdminException {
        UserRealmProxy userAdminCore = new UserRealmProxy(super.getUserRealm());
        userAdminCore.addUser(userName, password, roles, claims, profileName);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.wso2.carbon.user.mgt.UserAdmin#changePassword(java.lang.String,
     * java.lang.String)
     */
    /*
     * (non-Javadoc)
     * 
     * @see org.wso2.carbon.user.mgt.TestClass#changePassword(java.lang.String,
     * java.lang.String)
     */
    public void changePassword(String userName, String newPassword) throws UserAdminException {
        //Checking whether the passwords are managed externally, if so throw an exception
        UserStoreInfo userStoreInfo = getUserStoreInfo();
        if (null != userStoreInfo) {
            if (userStoreInfo.isPasswordsExternallyManaged()) {
                throw new UserAdminException("Passwords are managed externally. Therefore cannot change password.");
            }
        } else {

            throw new UserAdminException("An error occurred while changing the password. The UserStoreInfo object is null");
        }

        UserRealmProxy userAdminCore = new UserRealmProxy(super.getUserRealm());
        userAdminCore.changePassword(userName, newPassword);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.wso2.carbon.user.mgt.UserAdmin#deleteUserFromInternalStore(java.lang
     * .String)
     */
    /*
     * (non-Javadoc)
     * 
     * @see org.wso2.carbon.user.mgt.TestClass#deleteUser(java.lang.String)
     */
    public void deleteUser(String userName) throws UserAdminException {
        UserRealmProxy userAdminCore = new UserRealmProxy(super.getUserRealm());
        userAdminCore.deleteUser(userName, super.getConfigSystemRegistry());
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.wso2.carbon.user.mgt.UserAdmin#addRoleToInternalStore(java.lang.String
     * , java.lang.String[], java.lang.String[])
     */
    /*
     * (non-Javadoc)
     * 
     * @see org.wso2.carbon.user.mgt.TestClass#addRole(java.lang.String,
     * java.lang.String[], java.util.Map)
     */
    public void addRole(String roleName, String[] userList, String[] permissions)
            throws UserAdminException {
        UserRealmProxy userAdminCore = new UserRealmProxy(super.getUserRealm());
        userAdminCore.addRole(roleName, userList, permissions);
        setPermissionUpdateTimestampUserAdmin();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.wso2.carbon.user.mgt.UserAdmin#deleteRoleFromInternalStore(java.lang
     * .String)
     */
    /*
     * (non-Javadoc)
     * 
     * @see org.wso2.carbon.user.mgt.TestClass#deleteRole(java.lang.String)
     */
    public void deleteRole(String roleName) throws UserAdminException {
        UserRealmProxy userAdminCore = new UserRealmProxy(super.getUserRealm());
        userAdminCore.deleteRole(roleName);
        setPermissionUpdateTimestampUserAdmin();
    }

    public void updateRoleName(String roleName, String newRoleName) throws UserAdminException {
        UserRealmProxy userAdminCore = new UserRealmProxy(super.getUserRealm());
        userAdminCore.updateRoleName(roleName, newRoleName);
        setPermissionUpdateTimestampUserAdmin();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.wso2.carbon.user.mgt.UserAdmin#getUsersInRole(java.lang.String)
     */
    /*
     * (non-Javadoc)
     * 
     * @see
     * org.wso2.carbon.user.mgt.TestClass#getUsersInfoOfRole(java.lang.String,
     * java.lang.String)
     */
    public FlaggedName[] getUsersOfRole(String roleName, String filter) throws UserAdminException {
        UserRealmProxy userAdminCore = new UserRealmProxy(super.getUserRealm());
        return userAdminCore.getUsersOfRole(roleName, filter);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.wso2.carbon.user.mgt.UserAdmin#updateUsersOfRole(java.lang.String,
     * java.lang.String[], java.lang.String[])
     */
    /*
     * (non-Javadoc)
     * 
     * @see
     * org.wso2.carbon.user.mgt.TestClass#updateUsersOfRole(java.lang.String,
     * java.lang.String[], java.lang.String[])
     */
    public void updateUsersOfRole(String roleName, FlaggedName[] userList)
            throws UserAdminException {
        UserRealmProxy userAdminCore = new UserRealmProxy(super.getUserRealm());
        userAdminCore.updateUsersOfRole(roleName, userList);
        setPermissionUpdateTimestampUserAdmin();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.wso2.carbon.user.mgt.UserAdmin#getUsersInRole(java.lang.String)
     */
    /*
     * (non-Javadoc)
     * 
     * @see
     * org.wso2.carbon.user.mgt.TestClass#getRoleInfoOfUser(java.lang.String)
     */
    public FlaggedName[] getRolesOfUser(String userName) throws UserAdminException {
        UserRealmProxy userAdminCore = new UserRealmProxy(super.getUserRealm());
        return userAdminCore.getRolesOfUser(userName);
    }

    // FIXME: Fix the documentation of this class including this.
    public FlaggedName[] getRolesOfCurrentUser() throws UserAdminException {
        return getRolesOfUser(CarbonContext.getCurrentContext().getUsername());
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.wso2.carbon.user.mgt.TestClass#updateRolesOfUser(java.lang.String,
     * java.lang.String)
     */
    public void updateRolesOfUser(String userName, String[] newUserList) throws UserAdminException {
        UserRealmProxy userAdminCore = new UserRealmProxy(super.getUserRealm());
        userAdminCore.updateRolesOfUser(userName, newUserList);
        setPermissionUpdateTimestampUserAdmin();
    }

    public UIPermissionNode getAllUIPermissions() throws UserAdminException {
        try {
            UserRealmProxy userAdminCore = new UserRealmProxy(super.getUserRealm());
            int tenantId = CarbonContext.getCurrentContext().getTenantId();
            return userAdminCore.getAllUIPermissions(tenantId, 
                                                     UserMgtDSComponent.
                                                                       getRegistryService().
                                                                       getGovernanceSystemRegistry());
        } catch (RegistryException e) {
            log.error(e.getMessage(), e);
            throw new UserAdminException();
        }
    }

    public UIPermissionNode getRolePermissions(String roleName) throws UserAdminException {
        try {
            UserRealmProxy userAdminCore = new UserRealmProxy(super.getUserRealm());
            int tenantId = CarbonContext.getCurrentContext().getTenantId();
            return userAdminCore.
                                 getRolePermissions(roleName, tenantId, UserMgtDSComponent.
                                                                                          getRegistryService().
                                                                                          getGovernanceSystemRegistry());
        } catch (RegistryException e) {
            log.error(e.getMessage(), e);
            throw new UserAdminException();
        }
    }

    public void setRoleUIPermission(String roleName, String[] rawResources)
            throws UserAdminException {
        UserRealmProxy userAdminCore = new UserRealmProxy(super.getUserRealm());
        userAdminCore.setRoleUIPermission(roleName, rawResources);
        setPermissionUpdateTimestampUserAdmin();
    }

    public void bulkImportUsers(String fileName, DataHandler handler, String defaultPassword)
            throws UserAdminException {
        if(fileName == null || handler == null || defaultPassword == null) {
            throw new UserAdminException("Required data not provided");
        }
        try {
            UserRealmProxy userAdminCore = new UserRealmProxy(super.getUserRealm());
            InputStream inStream = handler.getInputStream();
            userAdminCore.bulkImportUsers(fileName, inStream, defaultPassword);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new UserAdminException();
        }

    }

    public void changePasswordByUser(String oldPassword, String newPassword)
            throws UserAdminException, AxisFault {

        //Checking whether the passwords are managed externally, if so throw an exception
        UserStoreInfo userStoreInfo = getUserStoreInfo();
        if (null != userStoreInfo) {
            if (userStoreInfo.isPasswordsExternallyManaged()) {
                throw new UserAdminException("Passwords are managed externally. Therefore cannot change password.");
            }
        } else {

            throw new UserAdminException("An error occurred while changing the password. The UserStoreInfo object is null");
        }

        UserRealmProxy userAdminCore = new UserRealmProxy(super.getUserRealm());
        try {
        userAdminCore.changePasswordByUser(oldPassword, newPassword);
        } catch (Exception e) {
            String msg = e.getMessage();
            throw new AxisFault(msg, e);            
        }
    }

    private void setPermissionUpdateTimestampUserAdmin() throws UserAdminException {
        try {
            super.setPermissionUpdateTimestamp();
        } catch (RegistryException e) {
            throw new UserAdminException("An error occurred while saving the timestamp", e);
        }
    }

}
