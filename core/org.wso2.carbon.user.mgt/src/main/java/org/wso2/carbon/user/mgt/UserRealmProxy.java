/*
 * Copyright 2005-2007 WSO2, Inc. (http://wso2.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.user.mgt;

import org.apache.axis2.context.MessageContext;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.CarbonConstants;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.registry.core.Collection;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.RegistryConstants;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.user.api.RealmConfiguration;
import org.wso2.carbon.user.core.*;
import org.wso2.carbon.user.core.ldap.LDAPConstants;
import org.wso2.carbon.user.core.util.UserCoreUtil;
import org.wso2.carbon.user.mgt.bulkimport.BulkImportConfig;
import org.wso2.carbon.user.mgt.bulkimport.CSVUserBulkImport;
import org.wso2.carbon.user.mgt.bulkimport.ExcelUserBulkImport;
import org.wso2.carbon.user.mgt.common.*;
import org.wso2.carbon.user.mgt.permission.ManagementPermissionUtil;
import org.wso2.carbon.utils.ServerConstants;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.InputStream;
import java.util.*;

public class UserRealmProxy {

    private static Log log = LogFactory.getLog(UserRealmProxy.class);

    private UserRealm realm = null;

    public UserRealmProxy(UserRealm userRealm) {
        this.realm = userRealm;
    }

    public String[] listUsers(String filter) throws UserAdminException {
        try {
            return realm.getUserStoreManager().listUsers(filter, -1);
        } catch (UserStoreException e) {
            // previously logged so logging not needed
            throw new UserAdminException(e.getMessage(), e);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new UserAdminException(e.getMessage(), e);
        }
    }

    public FlaggedName[] getAllRolesNames() throws UserAdminException {
        try {
            UserStoreManager userStoreMan = realm.getUserStoreManager();
            //get all roles
            String[] roleNames = userStoreMan.getRoleNames();
            //get hybrid roles
            String[] hybridRoles = userStoreMan.getHybridRoles();
            Arrays.sort(hybridRoles);
            FlaggedName[] flaggedNames = new FlaggedName[roleNames.length];
            for (int i = 0; i < roleNames.length; i++) {
                FlaggedName fName = new FlaggedName();
                fName.setItemName(roleNames[i]);
                fName.setEditable(true);
                //check whether role is a hybrid role or not
                if (Arrays.binarySearch(hybridRoles, roleNames[i])<0){
                    fName.setRoleType("External");
                } else{
                    fName.setRoleType("Internal");
                }
                //either if user store read only or external groups are read only, set
                // external roles editable false
                if ((userStoreMan.isReadOnly() && Arrays.binarySearch(hybridRoles, roleNames[i]) < 0)
                    || (("false").equals(realm.getRealmConfiguration().getUserStoreProperty(
                        LDAPConstants.WRITE_EXTERNAL_ROLES))) &&
                       (Arrays.binarySearch(hybridRoles, roleNames[i]) < 0)){
                    fName.setEditable(false); //external role
                }
                flaggedNames[i] = fName;
            }
            return flaggedNames;
        } catch (UserStoreException e) {
            // previously logged so logging not needed
            throw new UserAdminException(e.getMessage(), e);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new UserAdminException(e.getMessage(), e);
        }
    }

    public UserStoreInfo getUserStoreInfo() throws UserAdminException {
        try {
            RealmConfiguration realmConfig = realm.getRealmConfiguration();
            UserStoreInfo info = new UserStoreInfo();
            if ("true".equals(realmConfig
                    .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_READ_ONLY))) {
                info.setReadOnly(true);
            } else {
                info.setReadOnly(false);
            }
            info.setPasswordsExternallyManaged(realmConfig.isPasswordsExternallyManaged());
            info.setJsRegEx(realmConfig
                    .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_JS_REG_EX));
            info.setUserNameRegEx(
                realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_USER_NAME_JS_REG_EX));
            info.setRoleNameRegEx(
                realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_ROLE_NAME_JS_REG_EX));
            info.setExternalIdP(realmConfig.
                                           getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_EXTERNAL_IDP));
            
            MessageContext msgContext = MessageContext.getCurrentMessageContext();
            HttpServletRequest request = (HttpServletRequest) msgContext
                    .getProperty(HTTPConstants.MC_HTTP_SERVLETREQUEST);
            HttpSession httpSession = request.getSession(false);
            if (httpSession != null) {
                String userName = (String) httpSession.getAttribute(ServerConstants.USER_LOGGED_IN);
                if (realm.getAuthorizationManager().isUserAuthorized(userName,
                        "/permission/admin/configure/security",
                        CarbonConstants.UI_PERMISSION_ACTION)) {
                    info.setAdminRole(realmConfig.getAdminRoleName());
                    info.setAdminUser(realmConfig.getAdminUserName());
                    info.setEveryOneRole(realmConfig.getEveryOneRoleName());
                    info.setMaxUserListCount(Integer.parseInt(realmConfig
                            .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_MAX_USER_LIST)));
                }
            }

            info.setBulkImportSupported(this.isBulkImportSupported());

            return info;
        } catch (UserStoreException e) {
            // previously logged so logging not needed
            throw new UserAdminException(e.getMessage(), e);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new UserAdminException(e.getMessage(), e);
        }
    }


    private boolean isBulkImportSupported() throws UserAdminException {
        try {
            UserStoreManager userStoreManager = this.realm.getUserStoreManager();
            if (userStoreManager != null) {
                return userStoreManager.isBulkImportSupported();
            } else {
                throw new UserAdminException("Unable to retrieve user store manager from realm.");
            }

        } catch (UserStoreException e) {
            throw new UserAdminException("An error occurred while retrieving user store from realm.", e);
        }
    }

    public void addUser(String userName, String password, String[] roles, ClaimValue[] claims,
            String profileName) throws UserAdminException {
        try {
            RealmConfiguration realmConfig = realm.getRealmConfiguration();
            if (realmConfig.
                           getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_EXTERNAL_IDP) != null) {
                throw new UserAdminException(
                                             "Please contact your external Identity Provider to add users");
            }
            if (roles != null) {
                boolean isContained = false;
                String[] temp = new String[roles.length + 1];
                for (int i = 0; i < roles.length; i++) {
                    temp[i] = roles[i];
                    if (roles[i].equals(realmConfig.getEveryOneRoleName())) {
                        isContained = true;
                        break;
                    }
                }

                if (!isContained) {
                    temp[roles.length] = realmConfig.getEveryOneRoleName();
                    roles = temp;
                }
            }
            UserStoreManager admin = realm.getUserStoreManager();
            Map<String, String> claimMap = new HashMap<String, String>();
            if (claims != null) {
                for (ClaimValue claimValue : claims) {
                    claimMap.put(claimValue.getClaimURI(), claimValue.getValue());
                }
            }
            admin.addUser(userName, password, roles, claimMap, profileName, false);
        } catch (UserStoreException e) {
            // previously logged so logging not needed
            throw new UserAdminException(e.getMessage(), e);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new UserAdminException(e.getMessage(), e);
        }
    }

    public void changePassword(String userName, String newPassword) throws UserAdminException {
        try {
            realm.getUserStoreManager().updateCredentialByAdmin(userName, newPassword);
        } catch (UserStoreException e) {
            // previously logged so logging not needed
            throw new UserAdminException(e.getMessage(), e);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new UserAdminException(e.getMessage(), e);
        }
    }

    public void deleteUser(String userName, Registry registry) throws UserAdminException {
        try {
            realm.getUserStoreManager().deleteUser(userName);
            String path = RegistryConstants.PROFILES_PATH + userName;
            if (registry.resourceExists(path)) {
                registry.delete(path);
            }
        } catch (RegistryException e) {
            String msg = "Error deleting user from registry, " + e.getMessage();
            log.error(msg, e);
            throw new UserAdminException(msg, e);
        } catch (UserStoreException e) {
            // previously logged so logging not needed
            throw new UserAdminException(e.getMessage(), e);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new UserAdminException(e.getMessage(), e);
        }
    }

    public void addRole(String roleName, String[] userList, String[] permissions)
            throws UserAdminException {
        try {
            UserStoreManager usAdmin = realm.getUserStoreManager();
            usAdmin.addRole(roleName, userList, null);
            ManagementPermissionUtil.updateRoleUIPermission(roleName, permissions);
        } catch (UserStoreException e) {
            // previously logged so logging not needed
            throw new UserAdminException(e.getMessage(), e);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new UserAdminException(e.getMessage(), e);
        }
    }

    public void updateRoleName(String roleName, String newRoleName)
            throws UserAdminException {
        try {
            UserStoreManager usAdmin = realm.getUserStoreManager();
            usAdmin.updateRoleName(roleName, newRoleName);
        } catch (UserStoreException e) {
            // previously logged so logging not needed
            throw new UserAdminException(e.getMessage(), e);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new UserAdminException(e.getMessage(), e);
        }
    }

    public void deleteRole(String roleName) throws UserAdminException {
        try {
            realm.getUserStoreManager().deleteRole(roleName);
        } catch (UserStoreException e) {
            // previously logged so logging not needed
            throw new UserAdminException(e.getMessage(), e);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new UserAdminException(e.getMessage(), e);
        }
    }

    public FlaggedName[] getUsersOfRole(String roleName, String filter) throws UserAdminException {
        try {
            UserStoreManager usMan = realm.getUserStoreManager();
            String[] userNames = usMan.listUsers(filter, -1);
            String[] usersOfRole = usMan.getUserListOfRole(roleName);
            Arrays.sort(usersOfRole);
            FlaggedName[] flaggedNames = new FlaggedName[userNames.length];
            for (int i = 0; i < userNames.length; i++) {
                FlaggedName fName = new FlaggedName();
                fName.setItemName(userNames[i]);
                if (Arrays.binarySearch(usersOfRole, userNames[i]) > -1) {
                    fName.setSelected(true);
                }
                flaggedNames[i] = fName;
            }
            return flaggedNames;
        } catch (Exception e) {
            // previously logged so logging not needed
            throw new UserAdminException(e.getMessage(), e);
        }
    }

    public void updateUsersOfRole(String roleName, FlaggedName[] userList)
            throws UserAdminException {

        try {

            if (CarbonConstants.REGISTRY_ANONNYMOUS_ROLE_NAME.equals(roleName)) {
                log.error("Security Alert! Carbon anonymous role is being manipulated");
                throw new UserStoreException("Invalid data");// obscure error
                                                             // message
            }

            if (realm.getRealmConfiguration().getEveryOneRoleName().equals(roleName)) {
                log.error("Security Alert! Carbon Everyone role is being manipulated");
                throw new UserStoreException("Invalid data");// obscure error
                                                             // message
            }

            UserStoreManager admin = realm.getUserStoreManager();
            String[] oldUserList = admin.getUserListOfRole(roleName);
            Arrays.sort(oldUserList);

            List<String> delUsers = new ArrayList<String>();
            List<String> addUsers = new ArrayList<String>();

            for (FlaggedName fName : userList) {
                boolean isSelected = fName.isSelected();
                String userName = fName.getItemName();
                if (CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME.equals(userName)) {
                    log.error("Security Alert! Carbon anonymous user is being manipulated");
                    return;
                }
                int oldindex = Arrays.binarySearch(oldUserList, userName);
                if (oldindex > -1 && !isSelected) {
                    // deleted
                    delUsers.add(userName);
                } else if (oldindex < 0 && isSelected) {
                    // added
                    addUsers.add(userName);
                }
            }
            admin.updateUserListOfRole(roleName, delUsers.toArray(new String[delUsers.size()]),
                    addUsers.toArray(new String[addUsers.size()]));
        } catch (UserStoreException e) {
            // previously logged so logging not needed
            log.error(e.getMessage(), e);
            throw new UserAdminException(e.getMessage(), e);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new UserAdminException(e.getMessage(), e);
        }
    }

    public FlaggedName[] getRolesOfUser(String userName) throws UserAdminException {
        try {
            UserStoreManager admin = realm.getUserStoreManager();
            String[] userroles = admin.getRoleListOfUser(userName);
            String[] allRoles = admin.getRoleNames();
            FlaggedName[] flaggedNames = new FlaggedName[allRoles.length];
            Arrays.sort(userroles);
            for (int i = 0; i < allRoles.length; i++) {
                String role = allRoles[i];
                FlaggedName fname = new FlaggedName();
                fname.setItemName(role);
                if (Arrays.binarySearch(userroles, role) > -1) {
                    fname.setSelected(true);
                }
                flaggedNames[i] = fname;
            }
            return flaggedNames;
        } catch (Exception e) {
            // previously logged so logging not needed
            throw new UserAdminException(e.getMessage(), e);
        }
    }

    public void updateRolesOfUser(String userName, String[] roleList) throws UserAdminException {
        try {

            if (CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME.equals(userName)) {
                log.error("Security Alert! Carbon anonymous user is being manipulated");
                throw new UserAdminException("Invalid data");// obscure error
                                                             // message
            }

            UserStoreManager admin = realm.getUserStoreManager();
            String[] oldRoleList = admin.getRoleListOfUser(userName);
            Arrays.sort(roleList);
            Arrays.sort(oldRoleList);

            List<String> delRoles = new ArrayList<String>();
            List<String> addRoles = new ArrayList<String>();

            for (String name : roleList) {
                int oldindex = Arrays.binarySearch(oldRoleList, name);
                if (oldindex < 0) {
                    addRoles.add(name);
                }
            }

            for (String name : oldRoleList) {
                int newindex = Arrays.binarySearch(roleList, name);
                if (newindex < 0) {
                    if (realm.getRealmConfiguration().getEveryOneRoleName().equals(name)) {
                        log.error("Security Alert! Carbon everyone role is being manipulated");
                        throw new UserAdminException("Invalid data");// obscure
                                                                     // error
                                                                     // message
                    }
                    delRoles.add(name);
                }
            }

            admin.updateRoleListOfUser(userName, delRoles.toArray(new String[delRoles.size()]),
                    addRoles.toArray(new String[addRoles.size()]));
        } catch (UserStoreException e) {
            // previously logged so logging not needed
            throw new UserAdminException(e.getMessage(), e);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new UserAdminException(e.getMessage(), e);
        }
    }

    public UIPermissionNode getAllUIPermissions(int tenantId, Registry registry)
            throws UserAdminException {
        UIPermissionNode nodeRoot;
        try {
            Collection regRoot;
            if (tenantId == MultitenantConstants.SUPER_TENANT_ID) {
                if (CarbonContext.getCurrentContext().getTenantId() != MultitenantConstants.SUPER_TENANT_ID) {
                    log.error("Illegal access attempt");
                    throw new UserStoreException("Illegal access attempt");
                }
                regRoot = (Collection) registry.get(UserMgtConstants.UI_PERMISSION_ROOT);
                String displayName = regRoot.getProperty(UserMgtConstants.DISPLAY_NAME);
                nodeRoot = new UIPermissionNode(UserMgtConstants.UI_PERMISSION_ROOT, displayName);
            } else {
                regRoot = (Collection) registry.get(UserMgtConstants.UI_ADMIN_PERMISSION_ROOT);
                String displayName = regRoot.getProperty(UserMgtConstants.DISPLAY_NAME);
                nodeRoot = new UIPermissionNode(UserMgtConstants.UI_ADMIN_PERMISSION_ROOT,
                        displayName);
            }
            buildUIPermissionNode(regRoot, nodeRoot, registry, null, null, null);
            return nodeRoot;
        } catch (UserStoreException e) {
            // previously logged so logging not needed
            throw new UserAdminException(e.getMessage(), e);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new UserAdminException(e.getMessage(), e);
        }
    }

    public UIPermissionNode getRolePermissions(String roleName, int tenantId, Registry registry)
            throws UserAdminException {
        UIPermissionNode nodeRoot;
        try {
            Collection regRoot;
            if (tenantId == MultitenantConstants.SUPER_TENANT_ID) {
                regRoot = (Collection) registry.get(UserMgtConstants.UI_PERMISSION_ROOT);
                String displayName = regRoot.getProperty(UserMgtConstants.DISPLAY_NAME);
                nodeRoot = new UIPermissionNode(UserMgtConstants.UI_PERMISSION_ROOT, displayName);
            } else {
                regRoot = (Collection) registry.get(UserMgtConstants.UI_ADMIN_PERMISSION_ROOT);
                String displayName = regRoot.getProperty(UserMgtConstants.DISPLAY_NAME);
                nodeRoot = new UIPermissionNode(UserMgtConstants.UI_ADMIN_PERMISSION_ROOT,
                        displayName);
            }
            buildUIPermissionNode(regRoot, nodeRoot, registry, realm.getAuthorizationManager(),
                    roleName, null);
            return nodeRoot;
        } catch (UserStoreException e) {
            // previously logged so logging not needed
            throw new UserAdminException(e.getMessage(), e);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new UserAdminException(e.getMessage(), e);
        }
    }

    public void setRoleUIPermission(String roleName, String[] rawResources)
            throws UserAdminException {
        try {
            if (realm.getRealmConfiguration().getAdminRoleName().equals(roleName)) {
                String msg = "UI permissions of Admin is not allowed to change";
                log.error(msg);
                throw new UserAdminException(msg);
            }
            String[] optimizedList = UserCoreUtil.optimizePermissions(rawResources);
            AuthorizationManager authMan = realm.getAuthorizationManager();
            authMan.clearRoleActionOnAllResources(roleName, UserMgtConstants.EXECUTE_ACTION);
            for (String path : optimizedList) {
                authMan.authorizeRole(roleName, path, UserMgtConstants.EXECUTE_ACTION);
            }
        } catch (UserStoreException e) {
            log.error(e.getMessage(), e);
            throw new UserAdminException(e.getMessage(), e);
        }
    }

    public void bulkImportUsers(String fileName, InputStream inStream, String defaultPassword)
            throws UserAdminException {
        try {
            BulkImportConfig config = new BulkImportConfig(inStream, fileName);
            if (defaultPassword != null && defaultPassword.trim().length() > 0) {
                config.setDefaultPassword(defaultPassword.trim());
            }
            UserStoreManager userStore = this.realm.getUserStoreManager();
            if (fileName.endsWith("csv")) {
                CSVUserBulkImport csvAdder = new CSVUserBulkImport(config);
                csvAdder.addUserList(userStore);
            } else if (fileName.endsWith("xls") || fileName.endsWith("xlsx")) {
                ExcelUserBulkImport excelAdder = new ExcelUserBulkImport(config);
                excelAdder.addUserList(userStore);
            } else {
                throw new UserAdminException("Unsupported format");
            }
        } catch (UserStoreException e) {
            // previously logged so logging not needed
            throw new UserAdminException(e.getMessage(), e);
        }

    }

    public void changePasswordByUser(String oldPassword, String newPassword)
            throws UserAdminException {
        try {
            UserStoreManager userStore = this.realm.getUserStoreManager();
            HttpServletRequest request = (HttpServletRequest) MessageContext
                    .getCurrentMessageContext().getProperty(HTTPConstants.MC_HTTP_SERVLETREQUEST);
            HttpSession httpSession = request.getSession(false);
            String userName = (String) httpSession.getAttribute(ServerConstants.USER_LOGGED_IN);
            userStore.updateCredential(userName, newPassword, oldPassword);
        } catch (UserStoreException e) {
            // previously logged so logging not needed
            throw new UserAdminException(e.getMessage(), e);
        }
    }

    private void buildUIPermissionNode(Collection parent, UIPermissionNode parentNode,
            Registry registry, AuthorizationManager authMan, String roleName, String userName)
            throws RegistryException, UserStoreException {

        boolean isSelected = false;
        if (roleName != null) {
            isSelected = authMan.isRoleAuthorized(roleName, parentNode.getResourcePath(),
                    UserMgtConstants.EXECUTE_ACTION);
        } else if (userName != null) {
            isSelected = authMan.isUserAuthorized(userName, parentNode.getResourcePath(),
                    UserMgtConstants.EXECUTE_ACTION);
        }
        if(isSelected){
            buildUIPermissionNodeAllSelected(parent, parentNode, registry);
            parentNode.setSelected(true);
        }  else {
             buildUIPermissionNodeNotAllSelected(parent, parentNode,registry,
                     authMan, roleName, userName);
        }
    }

    private void buildUIPermissionNodeAllSelected (Collection parent, UIPermissionNode parentNode,
            Registry registry)
            throws RegistryException, UserStoreException {

        String [] children = parent.getChildren();
        UIPermissionNode[] childNodes = new UIPermissionNode[children.length];
        for (int i = 0 ; i < children.length; i++) {
            String child = children[i];
            Resource resource = registry.get(child);

            childNodes[i] = getUIPermissionNode(resource, registry, true);
            if (resource instanceof Collection) {
                buildUIPermissionNodeAllSelected((Collection) resource, childNodes[i], registry);
            }
        }
        parentNode.setNodeList(childNodes);
    }

    private void buildUIPermissionNodeNotAllSelected(Collection parent, UIPermissionNode parentNode,
            Registry registry, AuthorizationManager authMan, String roleName, String userName)
            throws RegistryException, UserStoreException {

        String [] children = parent.getChildren();
        UIPermissionNode[] childNodes = new UIPermissionNode[children.length];

        for (int i = 0 ; i < children.length; i++) {
            String child = children[i];
            Resource resource = registry.get(child);
            boolean isSelected = false;
            if (roleName != null) {
                isSelected = authMan.isRoleAuthorized(roleName, child,
                        UserMgtConstants.EXECUTE_ACTION);
            } else if (userName != null) {
                isSelected = authMan.isUserAuthorized(userName, child,
                        UserMgtConstants.EXECUTE_ACTION);
            }
            childNodes[i] = getUIPermissionNode(resource, registry, isSelected);
            if (resource instanceof Collection) {
                buildUIPermissionNodeNotAllSelected((Collection) resource, childNodes[i],
                        registry, authMan,roleName, userName);
            }
        }
        parentNode.setNodeList(childNodes);
    }

    private UIPermissionNode getUIPermissionNode(Resource resource, Registry registry,
            boolean isSelected) throws RegistryException {
        String displayName = resource.getProperty(UserMgtConstants.DISPLAY_NAME);
        return new UIPermissionNode(resource.getPath(), displayName, isSelected);
    }

}
