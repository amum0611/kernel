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

package org.wso2.carbon.user.core.authorization;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.CarbonConstants;
import org.wso2.carbon.caching.core.authorization.AuthorizationCacheException;
import org.wso2.carbon.user.api.RealmConfiguration;
import org.wso2.carbon.user.core.AuthorizationManager;
import org.wso2.carbon.user.core.UserCoreConstants;
import org.wso2.carbon.user.core.UserRealm;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.caching.core.authorization.AuthorizationCache;
import org.wso2.carbon.user.core.claim.ClaimManager;
import org.wso2.carbon.user.core.internal.UMListenerServiceComponent;
import org.wso2.carbon.user.core.listener.AuthorizationManagerListener;
import org.wso2.carbon.user.core.profile.ProfileConfigurationManager;
import org.wso2.carbon.user.core.util.DatabaseUtil;
import org.wso2.carbon.user.core.util.UserCoreUtil;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class JDBCAuthorizationManager implements AuthorizationManager {

    private DataSource dataSource = null;
    private PermissionTree permissionTree = null;
    private AuthorizationCache authorizationCache = null;
    private UserRealm userRealm = null;
    private RealmConfiguration realmConfig = null;
    private int tenantId;
    /**
     * The root node of the tree
     */
    private static Log log = LogFactory.getLog(JDBCAuthorizationManager.class);

    public JDBCAuthorizationManager(RealmConfiguration realmConfig, Map<String, Object> properties,
            ClaimManager claimManager, ProfileConfigurationManager profileManager, UserRealm realm,
            Integer tenantId) throws UserStoreException {

        authorizationCache = AuthorizationCache.getInstance();
        if(!"true".equals(realmConfig.getAuthorizationManagerProperty(UserCoreConstants.
                                        RealmConfig.PROPERTY_AUTHORIZATION_CACHE_ENABLED))){
            authorizationCache.disableCache();
        }

        dataSource = (DataSource) properties.get(UserCoreConstants.DATA_SOURCE);
        if (dataSource == null) {
            dataSource = DatabaseUtil.getRealmDataSource(realmConfig);
            properties.put(UserCoreConstants.DATA_SOURCE, dataSource);
        }
        this.permissionTree = new PermissionTree(tenantId, dataSource);
        this.realmConfig = realmConfig;
        this.userRealm = realm;
        this.tenantId = tenantId;
        if (log.isDebugEnabled()) {
            log.debug("The jdbcDataSource being used by JDBCAuthorizationManager :: "
                    + dataSource.hashCode());
        }
        this.populatePermissionTreeFromDB();
        this.addInitialData();
    }

    public boolean isRoleAuthorized(String roleName, String resourceId, String action) throws UserStoreException {
        for (AuthorizationManagerListener listener : UMListenerServiceComponent
                .getAuthorizationManagerListeners()) {
            if (!listener.isRoleAuthorized(roleName, resourceId, action, this)) {
                return false;
            }
        }
        permissionTree.updatePermissionTree();
        SearchResult sr = permissionTree.getRolePermission(roleName, PermissionTreeUtil
                .actionToPermission(action), null, null, PermissionTreeUtil
                .toComponenets(resourceId));


        if(log.isDebugEnabled()) {
            if (!sr.getLastNodeAllowedAccess()){
                log.debug(roleName + " role is not Authorized to perform "+ action + " on " + resourceId);
            }
        }
        
        return sr.getLastNodeAllowedAccess();
    }

    public boolean isUserAuthorized(String userName, String resourceId, String action)
            throws UserStoreException {

        if (CarbonConstants.REGISTRY_SYSTEM_USERNAME.equals(userName)) {
            return true;
        }

        for (AuthorizationManagerListener listener : UMListenerServiceComponent
                .getAuthorizationManagerListeners()) {
            if (!listener.isUserAuthorized(userName, resourceId, action, this)) {
                return false;
            }
        }

        try {
            boolean userAllowed = authorizationCache.isUserAuthorized(tenantId, userName,
                                                                                resourceId, action);
            if(log.isDebugEnabled()){
                if(!userAllowed) {
                    log.debug(userName + " user is not Authorized to perform "+ action +
                                                                               " on " + resourceId);
                }
            }
            return userAllowed;
        } catch (AuthorizationCacheException e) {
            // Entry not found in the cache. Just continue.
        }

        if(log.isDebugEnabled()){
            log.debug("Authorization cache entry is not found for username : " + userName);                         
        }

        permissionTree.updatePermissionTree();

        //following is related with user permission, and it is not hit in the current flow.
        SearchResult sr = permissionTree.getUserPermission(userName, PermissionTreeUtil
                .actionToPermission(action), null, null, PermissionTreeUtil
                .toComponenets(resourceId));
        if (sr.getLastNodeAllowedAccess()) {
            authorizationCache.addToCache(tenantId, userName, resourceId, action, true);
            return true;
        }

        String[] userRoles = userRealm.getUserStoreManager().getRoleListOfUser(userName);

        if(log.isDebugEnabled()){
            if(userRoles == null || userRoles.length < 1){
                log.debug("No roles are assigned to user : " + userName);
            }
        }

        boolean userAllowed = false;
        List<String> allowedRoles = Arrays.asList(getAllowedRolesForResource(resourceId, action));

        if(userRoles != null){
            for (String role : userRoles) {
                if (allowedRoles.contains(role)) {
                    userAllowed = true;
                    break;
                }
            }
        }
        
        //need to add the authorization decision taken by role based permission
        authorizationCache.addToCache(this.tenantId, userName, resourceId, action, userAllowed);
        
        if(log.isDebugEnabled()){
            if(!userAllowed) {
                log.debug(userName + " user is not Authorized to perform "+ action + " on " + resourceId);
            }
        }

        return userAllowed;
    }

    public String[] getAllowedRolesForResource(String resourceId, String action)
            throws UserStoreException {
        TreeNode.Permission permission = PermissionTreeUtil.actionToPermission(action);
        permissionTree.updatePermissionTree();
        SearchResult sr = permissionTree.getAllowedRolesForResource(null, null, permission,
                PermissionTreeUtil.toComponenets(resourceId));

        return sr.getAllowedEntities().toArray(new String[sr.getAllowedEntities().size()]);
    }

    public String[] getExplicitlyAllowedUsersForResource(String resourceId, String action)
            throws UserStoreException {
        TreeNode.Permission permission = PermissionTreeUtil.actionToPermission(action);
        permissionTree.updatePermissionTree();
        SearchResult sr = permissionTree.getAllowedUsersForResource(null, null, permission,
                PermissionTreeUtil.toComponenets(resourceId));

        return sr.getAllowedEntities().toArray(new String[sr.getAllowedEntities().size()]);
    }

    public String[] getDeniedRolesForResource(String resourceId, String action)
            throws UserStoreException {
        TreeNode.Permission permission = PermissionTreeUtil.actionToPermission(action);
        permissionTree.updatePermissionTree();
        SearchResult sr = permissionTree.getDeniedRolesForResource(null, null, permission,
                PermissionTreeUtil.toComponenets(resourceId));
        return sr.getDeniedEntities().toArray(new String[sr.getAllowedEntities().size()]);
    }

    public String[] getExplicitlyDeniedUsersForResource(String resourceId, String action)
            throws UserStoreException {
        TreeNode.Permission permission = PermissionTreeUtil.actionToPermission(action);
        permissionTree.updatePermissionTree();
        SearchResult sr = permissionTree.getDeniedUsersForResource(null, null, permission,
                PermissionTreeUtil.toComponenets(resourceId));
        return sr.getDeniedEntities().toArray(new String[sr.getAllowedEntities().size()]);
    }

    public String[] getAllowedUIResourcesForUser(String userName, String permissionRootPath)
            throws UserStoreException {
        List<String> lstPermissions = new ArrayList<String>();
        String[] roles = this.userRealm.getUserStoreManager().getRoleListOfUser(userName);
        permissionTree.updatePermissionTree();
        permissionTree.getUIResourcesForRoles(roles, lstPermissions, permissionRootPath);
        String[] permissions = lstPermissions.toArray(new String[lstPermissions.size()]);
        String[] optimizedList = UserCoreUtil.optimizePermissions(permissions);
        return optimizedList;
    }

    public void authorizeRole(String roleName, String resourceId, String action)
            throws UserStoreException {

        for (AuthorizationManagerListener listener : UMListenerServiceComponent
                .getAuthorizationManagerListeners()) {
            if (!listener.authorizeRole(roleName, resourceId, action, this)) {
                return;
            }
        }

        if (resourceId == null || action == null) {
            log.error("Invalid data provided at authorization code");
            throw new UserStoreException("Invalid data provided");
        }
        addAuthorizationForRole(roleName, resourceId, action, UserCoreConstants.ALLOW);
    }

    public void denyRole(String roleName, String resourceId, String action)
            throws UserStoreException {

        for (AuthorizationManagerListener listener : UMListenerServiceComponent
                .getAuthorizationManagerListeners()) {
            if (!listener.denyRole(roleName, resourceId, action, this)) {
                return;
            }
        }

        if (resourceId == null || action == null) {
            log.error("Invalid data provided at authorization code");
            throw new UserStoreException("Invalid data provided");
        }
        addAuthorizationForRole(roleName, resourceId, action, UserCoreConstants.DENY);
    }

    public void authorizeUser(String userName, String resourceId, String action)
            throws UserStoreException {

        for (AuthorizationManagerListener listener : UMListenerServiceComponent
                .getAuthorizationManagerListeners()) {
            if (!listener.authorizeUser(userName, resourceId, action, this)) {
                return;
            }
        }

        if (resourceId == null || action == null) {
            log.error("Invalid data provided at authorization code");
            throw new UserStoreException("Invalid data provided");
        }
        addAuthorizationForUser(userName, resourceId, action, UserCoreConstants.ALLOW);
    }

    public void denyUser(String userName, String resourceId, String action)
            throws UserStoreException {

        for (AuthorizationManagerListener listener : UMListenerServiceComponent
                .getAuthorizationManagerListeners()) {
            if (!listener.denyUser(userName, resourceId, action, this)) {
                return;
            }
        }

        if (resourceId == null || action == null) {
            log.error("Invalid data provided at authorization code");
            throw new UserStoreException("Invalid data provided");
        }
        addAuthorizationForUser(userName, resourceId, action, UserCoreConstants.DENY);
    }

    public void clearResourceAuthorizations(String resourceId) throws UserStoreException {

        for (AuthorizationManagerListener listener : UMListenerServiceComponent
                .getAuthorizationManagerListeners()) {
            if (!listener.clearResourceAuthorizations(resourceId, this)) {
                return;
            }
        }
        /**
         * Need to clear authz cache when resource authorization is cleared.
         */
        authorizationCache.clearCacheByResource(this.tenantId, resourceId);

        Connection dbConnection = null;
        try {
            dbConnection = getDBConnection();
            DatabaseUtil.updateDatabase(dbConnection,
                    DBConstants.ON_DELETE_PERMISSION_UM_ROLE_PERMISSIONS_SQL, resourceId, tenantId);
            DatabaseUtil.updateDatabase(dbConnection,
                    DBConstants.ON_DELETE_PERMISSION_UM_USER_PERMISSIONS_SQL, resourceId, tenantId);
            DatabaseUtil.updateDatabase(dbConnection, DBConstants.DELETE_PERMISSION_SQL,
                    resourceId, tenantId);
            permissionTree.clearResourceAuthorizations(resourceId);
            dbConnection.commit();
        } catch (SQLException e) {
            log.error("Error! " + e.getMessage(), e);
            throw new UserStoreException("Error! " + e.getMessage(), e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
    }

    public void clearRoleAuthorization(String roleName, String resourceId, String action)
            throws UserStoreException {

        for (AuthorizationManagerListener listener : UMListenerServiceComponent
                .getAuthorizationManagerListeners()) {
            if (!listener.clearRoleAuthorization(roleName, resourceId, action, this)) {
                return;
            }
        }
        /*need to clear tenant authz cache once role authorization is removed, currently there is
        no way to remove cache entry by role.*/
        authorizationCache.clearCacheByTenant(this.tenantId);
        
        Connection dbConnection = null;
        try {
            dbConnection = getDBConnection();
            DatabaseUtil.updateDatabase(dbConnection, DBConstants.DELETE_ROLE_PERMISSION_SQL,
                    roleName, resourceId, action, tenantId, tenantId);
            permissionTree.clearRoleAuthorization(roleName, resourceId, action);
            dbConnection.commit();
        } catch (SQLException e) {
            log.error("Error! " + e.getMessage(), e);
            throw new UserStoreException("Error! " + e.getMessage(), e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
    }

    public void clearUserAuthorization(String userName, String resourceId, String action)
            throws UserStoreException {

        for (AuthorizationManagerListener listener : UMListenerServiceComponent
                .getAuthorizationManagerListeners()) {
            if (!listener.clearUserAuthorization(userName, resourceId, action, this)) {
                return;
            }
        }

        this.authorizationCache.clearCacheEntry(tenantId, userName, resourceId, action);

        Connection dbConnection = null;
        PreparedStatement prepStmt = null;
        try {
            dbConnection = getDBConnection();
            int permissionId = this.getPermissionId(dbConnection, resourceId, action);
            if (permissionId == -1) {
                this.addPermissionId(dbConnection, resourceId, action);                
            }
            DatabaseUtil.updateDatabase(dbConnection, DBConstants.DELETE_USER_PERMISSION_SQL,
                    userName, resourceId, action, tenantId, tenantId);
            permissionTree.clearUserAuthorization(userName, resourceId, action);
            dbConnection.commit();
        } catch (SQLException e) {
            log.error("Error! " + e.getMessage(), e);
            throw new UserStoreException("Error! " + e.getMessage(), e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, prepStmt);
        }
    }

    public void clearRoleActionOnAllResources(String roleName, String action)
            throws UserStoreException {

        for (AuthorizationManagerListener listener : UMListenerServiceComponent
                .getAuthorizationManagerListeners()) {
            if (!listener.clearRoleActionOnAllResources(roleName, action, this)) {
                return;
            }
        }
        /*need to clear tenant authz cache once role authorization is removed, currently there is
        no way to remove cache entry by role.*/
        authorizationCache.clearCacheByTenant(this.tenantId);

        Connection dbConnection = null;
        PreparedStatement prepStmt = null;
        try {
            dbConnection = getDBConnection();
            permissionTree.clearRoleAuthorization(roleName, action);
            DatabaseUtil.updateDatabase(dbConnection,
                    DBConstants.DELETE_ROLE_PERMISSIONS_BASED_ON_ACTION, roleName, action,
                    tenantId, tenantId);
            dbConnection.commit();
        } catch (SQLException e) {
            log.error("Error! " + e.getMessage(), e);
            throw new UserStoreException("Error! " + e.getMessage(), e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, prepStmt);
        }
    }
    
    public void clearRoleAuthorization(String roleName) throws UserStoreException {

        for (AuthorizationManagerListener listener : UMListenerServiceComponent
                .getAuthorizationManagerListeners()) {
            if (!listener.clearRoleAuthorization(roleName, this)) {
                return;
            }
        }
        /*need to clear tenant authz cache once role authorization is removed, currently there is
        no way to remove cache entry by role.*/
        authorizationCache.clearCacheByTenant(this.tenantId);
        Connection dbConnection = null;
        PreparedStatement prepStmt = null;
        try {
            dbConnection = getDBConnection();
            permissionTree.clearRoleAuthorization(roleName);
            DatabaseUtil.updateDatabase(dbConnection,
                    DBConstants.ON_DELETE_ROLE_DELETE_PERMISSION_SQL, roleName, tenantId);
            dbConnection.commit();
        } catch (SQLException e) {
            log.error("Error! " + e.getMessage(), e);
            throw new UserStoreException("Error! " + e.getMessage(), e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, prepStmt);
        }
    }

    public void clearUserAuthorization(String userName) throws UserStoreException {

        for (AuthorizationManagerListener listener : UMListenerServiceComponent
                .getAuthorizationManagerListeners()) {
            if (!listener.clearUserAuthorization(userName, this)) {
                return;
            }
        }

        this.authorizationCache.clearCacheByUser(tenantId, userName);

        Connection dbConnection = null;
        PreparedStatement prepStmt = null;
        try {
            dbConnection = getDBConnection();
            permissionTree.clearUserAuthorization(userName);
            DatabaseUtil.updateDatabase(dbConnection,
                    DBConstants.ON_DELETE_USER_DELETE_PERMISSION_SQL, userName, tenantId);
            dbConnection.commit();
        } catch (SQLException e) {
            log.error("Error! " + e.getMessage(), e);
            throw new UserStoreException("Error! " + e.getMessage(), e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, prepStmt);
        }

    }

    public  void resetPermissionOnUpdateRole(String roleName, String newRoleName)
            throws UserStoreException {

        for (AuthorizationManagerListener listener : UMListenerServiceComponent
                .getAuthorizationManagerListeners()) {
            if (!listener.resetPermissionOnUpdateRole(roleName, newRoleName, this)) {
                return;
            }
        }
        /*need to clear tenant authz cache when role is updated, currently there is
        no way to remove cache entry by role.*/
        authorizationCache.clearCacheByTenant(this.tenantId);
        
        String sqlStmt = DBConstants.UPDATE_UM_ROLE_NAME_PERMISSION_SQL;
        if (sqlStmt == null) {
            throw new UserStoreException("The sql statement for update role name is null");
        }
        Connection dbConnection = null;
        PreparedStatement prepStmt = null;
        try {
            dbConnection = getDBConnection();
            permissionTree.updateRoleNameInCache(roleName, newRoleName);
            DatabaseUtil.updateDatabase(dbConnection, sqlStmt, newRoleName, roleName,tenantId);
            dbConnection.commit();
        } catch (SQLException e) {
            log.error("Error! " + e.getMessage(), e);
            throw new UserStoreException("Error! " + e.getMessage(), e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, prepStmt);
        }
    }
    
    private  void addAuthorizationForRole(String roleName, String resourceId, String action,
            short allow) throws UserStoreException {

        /*need to clear tenant authz cache once role authorization is added, currently there is
        no way to remove cache entry by role.*/
        authorizationCache.clearCacheByTenant(this.tenantId);

        Connection dbConnection = null;
        PreparedStatement prepStmt = null;
        try {
            dbConnection = getDBConnection();
            int permissionId = this.getPermissionId(dbConnection, resourceId, action);
            if (permissionId == -1) {
                this.addPermissionId(dbConnection, resourceId, action);
                permissionId = this.getPermissionId(dbConnection, resourceId, action);
            }
            DatabaseUtil.updateDatabase(dbConnection, DBConstants.DELETE_ROLE_PERMISSION_SQL,
                    roleName, resourceId, action, tenantId, tenantId);
            DatabaseUtil.updateDatabase(dbConnection, DBConstants.ADD_ROLE_PERMISSION_SQL,
                    permissionId, roleName, allow, tenantId);

            if (allow == UserCoreConstants.ALLOW) {
                permissionTree.authorizeRoleInTree(roleName, resourceId, action, true);
            } else {
                permissionTree.denyRoleInTree(roleName, resourceId, action, true);
            }
            dbConnection.commit();
        } catch (SQLException e) {
            log.error("Error! " + e.getMessage(), e);
            throw new UserStoreException("Error! " + e.getMessage(), e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, prepStmt);
        }
    }

    private void addAuthorizationForUser(String userName, String resourceId, String action,
            short allow) throws UserStoreException {
        /*need to clear tenant authz cache once role authorization is removed, currently there is
        no way to remove cache entry by role.*/
        authorizationCache.clearCacheByUser(this.tenantId, userName);
        
        Connection dbConnection = null;
        PreparedStatement prepStmt = null;
        try {
            dbConnection = getDBConnection();
            int permissionId = this.getPermissionId(dbConnection, resourceId, action);
            if (permissionId == -1) {
                this.addPermissionId(dbConnection, resourceId, action);
                permissionId = this.getPermissionId(dbConnection, resourceId, action);
            }
            DatabaseUtil.updateDatabase(dbConnection, DBConstants.DELETE_USER_PERMISSION_SQL,
                    userName, resourceId, action, tenantId, tenantId);
            DatabaseUtil.updateDatabase(dbConnection, DBConstants.ADD_USER_PERMISSION_SQL,
                    permissionId, userName, allow, tenantId);
            if (allow == UserCoreConstants.ALLOW) {
                permissionTree.authorizeUserInTree(userName, resourceId, action, true);
            } else {
                permissionTree.denyUserInTree(userName, resourceId, action, true);
                authorizationCache.clearCacheEntry(tenantId, userName, resourceId, action);
            }
            dbConnection.commit();
        } catch (SQLException e) {
            log.error("Error! " + e.getMessage(), e);
            throw new UserStoreException("Error! " + e.getMessage(), e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, prepStmt);
        }
    }

    private int getPermissionId(Connection dbConnection, String resourceId, String action)
            throws UserStoreException {
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        int value = -1;
        try {
            prepStmt = dbConnection.prepareStatement(DBConstants.GET_PERMISSION_ID_SQL);
            prepStmt.setString(1, resourceId);
            prepStmt.setString(2, action);
            prepStmt.setInt(3, tenantId);

            rs = prepStmt.executeQuery();
            if (rs.next()) {
                value = rs.getInt(1);
            }
            return value;
        } catch (SQLException e) {
            log.error("Error! " + e.getMessage(), e);
            throw new UserStoreException("Error! " + e.getMessage(), e);
        } finally {
            DatabaseUtil.closeAllConnections(null, rs, prepStmt);
        }
    }

    private void addPermissionId(Connection dbConnection, String resourceId, String action)
            throws UserStoreException {
        PreparedStatement prepStmt = null;
        try {
            prepStmt = dbConnection.prepareStatement(DBConstants.ADD_PERMISSION_SQL);
            prepStmt.setString(1, resourceId);
            prepStmt.setString(2, action);
            prepStmt.setInt(3, tenantId);
            int count = prepStmt.executeUpdate();
            if (log.isDebugEnabled()) {
                log.debug("Executed querry is " + DBConstants.ADD_PERMISSION_SQL
                        + " and number of updated rows :: " + count);
            }
        } catch (SQLException e) {
            log.error("Error! " + e.getMessage(), e);
            throw new UserStoreException("Error! " + e.getMessage(), e);
        } finally {
            DatabaseUtil.closeAllConnections(null, prepStmt);
        }
    }

    private Connection getDBConnection() throws SQLException {
        Connection dbConnection = dataSource.getConnection();
        dbConnection.setAutoCommit(false);
        return dbConnection;
    }

    public  void populatePermissionTreeFromDB() throws UserStoreException {
        permissionTree.updatePermissionTreeFromDB();
    }

    /**
     * This method will unload all permission data loaded from a database. This method is useful in a lazy loading
     * scenario.
     */
    public  void clearPermissionTree(){
        this.permissionTree.clear();
        this.authorizationCache.clearCache();
    }

    public int getTenantId() throws UserStoreException {
        return tenantId;
    }

    private void addInitialData() throws UserStoreException {
        String mgtPermissions = realmConfig
                .getAuthorizationManagerProperty(UserCoreConstants.RealmConfig.PROPERTY_EVERYONEROLE_AUTHORIZATION);
        if (mgtPermissions != null) {
            String everyoneRole = realmConfig.getEveryOneRoleName();
            String[] resourceIds = mgtPermissions.split(",");
            for (String resourceId : resourceIds) {
                if (!this.isRoleAuthorized(everyoneRole, resourceId,
                        CarbonConstants.UI_PERMISSION_ACTION)) {
                    this.authorizeRole(everyoneRole, resourceId,
                            CarbonConstants.UI_PERMISSION_ACTION);
                }
            }
        }

        mgtPermissions = realmConfig
                .getAuthorizationManagerProperty(UserCoreConstants.RealmConfig.PROPERTY_ADMINROLE_AUTHORIZATION);
        if (mgtPermissions != null) {
            String[] resourceIds = mgtPermissions.split(",");
            String adminRole = realmConfig.getAdminRoleName();
            for (String resourceId : resourceIds) {
                if (!this.isRoleAuthorized(adminRole, resourceId,
                        CarbonConstants.UI_PERMISSION_ACTION)) {
                    this.authorizeRole(adminRole, resourceId, CarbonConstants.UI_PERMISSION_ACTION);
                }
            }
        }
    }
    
}
