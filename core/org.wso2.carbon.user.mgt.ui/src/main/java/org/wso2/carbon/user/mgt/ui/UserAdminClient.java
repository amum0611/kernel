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

import org.apache.axis2.client.Options;
import org.apache.axis2.client.ServiceClient;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.user.mgt.common.ClaimValue;
import org.wso2.carbon.user.mgt.common.FlaggedName;
import org.wso2.carbon.user.mgt.common.IUserAdmin;
import org.wso2.carbon.user.mgt.common.UIPermissionNode;
import org.wso2.carbon.user.mgt.common.UserAdminException;
import org.wso2.carbon.user.mgt.common.UserStoreInfo;
import org.wso2.carbon.user.mgt.stub.*;

import javax.activation.DataHandler;
import java.rmi.RemoteException;

public class UserAdminClient implements IUserAdmin {

    public static final String USER_STORE_INFO = "org.wso2.carbon.userstore.info";
    public static final String DO_USER_LIST = "org.wso2.usermgt.user.list.error";
    public static final String USER_LIST_FILTER = "org.wso2.usermgt.internal.filter";
    public static final String EMAIL_CLAIM_URI = "http://wso2.org/claims/emailaddress";
    protected UserAdminStub stub = null;

    protected static Log log = LogFactory.getLog(UserAdminClient.class);

    public UserAdminClient(String cookie, String url, String serviceName,
            ConfigurationContext configContext) throws java.lang.Exception {
        try {
            stub = new UserAdminStub(configContext, url + serviceName);
            ServiceClient client = stub._getServiceClient();
            Options option = client.getOptions();
            option.setManageSession(true);


            option.setProperty(org.apache.axis2.transport.http.HTTPConstants.COOKIE_STRING, cookie);
        } catch (java.lang.Exception e) {
            handleException(e.getMessage(), e);
        }
    }

    public UserAdminClient(String cookie, String url, ConfigurationContext configContext)
            throws java.lang.Exception {
        try {

            stub = new UserAdminStub(configContext, url + "UserAdmin");
            ServiceClient client = stub._getServiceClient();
            Options option = client.getOptions();
            option.setManageSession(true);
            option.setProperty(org.apache.axis2.transport.http.HTTPConstants.COOKIE_STRING, cookie);
        } catch (java.lang.Exception e) {
            handleException(e.getMessage(), e);
        }
    }

    public void addRole(String roleName, String[] userList, String[] permissions)
            throws UserAdminException {
        try {
            stub.addRole(roleName, userList, permissions);
        } catch (Exception e) {
            handleException(e.getMessage(), e);
        }
    }

    public void addUser(String userName, String password, String[] roles, ClaimValue[] claims,
            String profileName) throws UserAdminException {
        try {
            stub.addUser(userName, password, roles, Util.toADBClaimValues(claims), profileName);
        } catch (Exception e) {
            handleException(e.getMessage(), e);
        }
    }

    public void changePassword(String userName, String newPassword) throws UserAdminException {
        try {
            stub.changePassword(userName, newPassword);
        } catch (Exception e) {
            handleException(e.getMessage(), e);
        }

    }

    public void deleteRole(String roleName) throws UserAdminException {
        try {
            stub.deleteRole(roleName);
        } catch (Exception e) {
            handleException(e.getMessage(), e);
        }

    }

    public void updateRoleName(String roleName, String newRoleName) throws UserAdminException {
        try {
            stub.updateRoleName(roleName, newRoleName);
        } catch (Exception e) {
            handleException(e.getMessage(), e);
        }

    }

    public void deleteUser(String userName) throws UserAdminException {
        try {
            stub.deleteUser(userName);
        } catch (RemoteException e) {
            handleException(e.getMessage(),e);
        } catch (DeleteUserUserAdminExceptionException e) {
            if(e.getFaultMessage().isUserAdminExceptionSpecified()){
                handleException(e.getFaultMessage().getUserAdminException().getErrorMessage(),e);
            }
            handleException(e.getMessage(),e);
        }

    }

    public FlaggedName[] getAllRolesNames() throws UserAdminException {
        try {
            return Util.toCommonFlaggedNames(stub.getAllRolesNames());
        } catch (Exception e) {
            handleException(e.getMessage(), e);
        }
        return (new FlaggedName[0]);
    }

    public FlaggedName[] getRolesOfUser(String userName) throws UserAdminException {
        try {
            return Util.toCommonFlaggedNames(stub.getRolesOfUser(userName));
        } catch (Exception e) {
            handleException(e.getMessage(), e);
        }
        return (new FlaggedName[0]);
    }

    public FlaggedName[] getRolesOfCurrentUser() throws UserAdminException {
        try {
            return Util.toCommonFlaggedNames(stub.getRolesOfUser());
        } catch (Exception e) {
            handleException(e.getMessage(), e);
        }
        return (new FlaggedName[0]);
    }

    public FlaggedName[] getUsersOfRole(String roleName, String filter) throws UserAdminException {
        try {
            return Util.toCommonFlaggedNames(stub.getUsersOfRole(roleName, filter));
        } catch (Exception e) {
            handleException(e.getMessage(), e);
        }
        return new FlaggedName[0];
    }

    public UserStoreInfo getUserStoreInfo() throws UserAdminException {
        try {
            return Util.toCommonUserStoreInfo(stub.getUserStoreInfo());
        } catch (Exception e) {
            handleException(e.getMessage(), e);
        }
        return null;
    }

    public String[] listUsers(String filter) throws UserAdminException {
        try {
            return stub.listUsers(filter);
        } catch (Exception e) {
            handleException("Error reading users.", e);
        }
        return new String[0];
    }

    public void updateRolesOfUser(String userName, String[] newUserList) throws UserAdminException {
        try {
            stub.updateRolesOfUser(userName, newUserList);
        } catch (Exception e) {
            if(e instanceof UpdateRolesOfUserUserAdminExceptionException){
                UpdateRolesOfUserUserAdminExceptionException userAdminException = (
                        UpdateRolesOfUserUserAdminExceptionException)e;
                if(userAdminException.getFaultMessage().isUserAdminExceptionSpecified()){
                    handleException(userAdminException.getFaultMessage().
                            getUserAdminException().getErrorMessage(), e);
                }
            }
            handleException(e.getMessage(), e);
        }

    }

    public void updateUsersOfRole(String roleName, FlaggedName[] userList)
            throws UserAdminException {
        try {
            stub.updateUsersOfRole(roleName, Util.toADBFlaggedNames(userList));
        } catch (Exception e) {
            if(e instanceof UpdateUsersOfRoleUserAdminExceptionException){
                UpdateUsersOfRoleUserAdminExceptionException userAdminException = (
                        UpdateUsersOfRoleUserAdminExceptionException)e;
                if(userAdminException.getFaultMessage().isUserAdminExceptionSpecified()){
                    handleException(userAdminException.getFaultMessage().
                            getUserAdminException().getErrorMessage(), e);
                }
            }
            handleException(e.getMessage(), e);
        }
    }

    public UIPermissionNode getAllUIPermissions() throws UserAdminException {
        try {
            return Util.toCommonUIPermissionNode(stub.getAllUIPermissions());
        } catch (Exception e) {
            handleException(e.getMessage(), e);
        }
        return null;
    }

    public UIPermissionNode getRolePermissions(String roleName) throws UserAdminException {
        try {
            return Util.toCommonUIPermissionNode(stub.getRolePermissions(roleName));
        } catch (Exception e) {
            handleException(e.getMessage(), e);
        }
        return null;
    }

    public void setRoleUIPermission(String roleName, String[] rawResources)
            throws UserAdminException {
        try {
            stub.setRoleUIPermission(roleName, rawResources);
        } catch (Exception e) {
            handleException(e.getMessage(), e);
        }
    }

    public void bulkImportUsers(String fileName, DataHandler handler, String defaultPassword)
            throws UserAdminException {
        try {
            stub.bulkImportUsers(fileName, handler, defaultPassword);
        } catch (Exception e) {
            handleException(e.getMessage(), e);
        }
    }

    public void changePasswordByUser(String oldPassword, String newPassword)
            throws UserAdminException {
        try {
            stub.changePasswordByUser(oldPassword, newPassword);
        } catch (RemoteException e) {
          handleException(e.getMessage(), e);
        } catch (ChangePasswordByUserUserAdminExceptionException e) {

            if (e.getFaultMessage().getUserAdminException()!=null) {
                String errorMessage=e.getFaultMessage().getUserAdminException().getErrorMessage();
                handleException(errorMessage, e);
            }
            handleException(e.getMessage(), e);
        } 
    }

    protected String[] handleException(String msg, Exception e)
            throws org.wso2.carbon.user.mgt.common.UserAdminException {
        log.error(msg, e);
        throw new org.wso2.carbon.user.mgt.common.UserAdminException(msg, e);
    }
}
