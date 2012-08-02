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
 * distributed under the License is distributed on an "AS IS"//also need to clear role authorization
        userRealm.getAuthorizationManager().cle BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.user.core.jdbc;

import org.apache.axiom.om.util.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.CarbonConstants;
import org.wso2.carbon.user.api.RealmConfiguration;
import org.wso2.carbon.user.core.Permission;
import org.wso2.carbon.user.core.UserCoreConstants;
import org.wso2.carbon.user.core.UserRealm;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.claim.ClaimManager;
import org.wso2.carbon.user.core.claim.ClaimMapping;
import org.wso2.carbon.user.core.common.AbstractUserStoreManager;
import org.wso2.carbon.user.core.hybrid.HybridJDBCConstants;
import org.wso2.carbon.user.core.hybrid.HybridRoleManager;
import org.wso2.carbon.user.core.profile.ProfileConfigurationManager;
import org.wso2.carbon.user.core.tenant.Tenant;
import org.wso2.carbon.user.core.util.DatabaseUtil;
import org.wso2.carbon.user.core.util.JDBCRealmUtil;
import org.wso2.carbon.user.core.util.UserCoreUtil;
import org.wso2.carbon.utils.dbcreator.DatabaseCreator;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;

import javax.sql.DataSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.*;
import java.util.Date;



public class JDBCUserStoreManager extends AbstractUserStoreManager{

    protected DataSource jdbcDataSource = null;
    protected UserRealm jdbcUserRealm = null;
    protected int tenantId;
    protected boolean useOnlyInternalRoles;
    protected Random random = new Random();
    
    private static Log log = LogFactory.getLog(JDBCUserStoreManager.class);

    public JDBCUserStoreManager(RealmConfiguration realmConfig, int tenantId) {
        this.realmConfig = realmConfig;
        this.tenantId = tenantId;
        realmConfig.setUserStoreProperties(JDBCRealmUtil.getSQL(realmConfig
                                           .getUserStoreProperties()));
        if ("true".equals(realmConfig
                .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_INTERNAL_ROLES_ONLY))) {
            useOnlyInternalRoles = true;
        }        
        /*Initialize user roles cache as implemented in AbstractUserStoreManager*/
	    initUserRolesCache();
    }

    /**
     * This constructor is used by the support IS
     * 
     * @param ds
     * @param realmConfig
     * @param tenantId
     * @param addInitData
     * @param tenantId
     */
    public JDBCUserStoreManager(DataSource ds, RealmConfiguration realmConfig, int tenantId,
            boolean addInitData) throws UserStoreException {
    	/* 
    	 * To accommodate PasswordUpdater called from chpasswd script
    	 */
    	this(ds, realmConfig, tenantId, addInitData, false);
    }

	/* 
	 * To accommodate PasswordUpdater called from chpasswd script
	 */
    public JDBCUserStoreManager(DataSource ds, RealmConfiguration realmConfig, int tenantId,
            boolean addInitData, boolean isChpasswd) throws UserStoreException {
        this(realmConfig, tenantId);
        if (log.isDebugEnabled()) {
            log.debug("Started " + System.currentTimeMillis());
        }
        realmConfig.setUserStoreProperties(JDBCRealmUtil.getSQL(realmConfig
                                           .getUserStoreProperties()));
        if ("true".equals(realmConfig
                .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_INTERNAL_ROLES_ONLY))) {
            useOnlyInternalRoles = true;
        }
        
        this.jdbcDataSource = ds;

        if (dataSource == null) {
        	/*
        	 * To accommodate PasswordUpdater called from chpasswd script
        	 */
        	if (isChpasswd) {
        		dataSource = DatabaseUtil.getRealmDataSourceForChpasswd(realmConfig);
        	} else {
        		dataSource = DatabaseUtil.getRealmDataSource(realmConfig);
        	}
		}
		if (dataSource == null) {
			throw new UserStoreException("User Management Data Source is null");
		}

        hybridRoleManager = new HybridRoleManager(dataSource, tenantId, realmConfig, jdbcUserRealm);

        if (addInitData) {
            this.addInitialData();
        }

        if (log.isDebugEnabled()) {
            log.debug("Ended " + System.currentTimeMillis());
        }    	
    }

    public JDBCUserStoreManager(RealmConfiguration realmConfig, Map<String, Object> properties,
            ClaimManager claimManager, ProfileConfigurationManager profileManager, UserRealm realm,
            Integer tenantId) throws UserStoreException {
        this(realmConfig, tenantId);
        if (log.isDebugEnabled()) {
            log.debug("Started " + System.currentTimeMillis());
        }
        this.claimManager = claimManager;
        this.profileManager = profileManager;
        this.jdbcUserRealm = realm;

        if ("true".equals(realmConfig
                .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_INTERNAL_ROLES_ONLY))) {
            useOnlyInternalRoles = true;
        }
        
        jdbcDataSource = loadUserStoreSpacificDataSoruce();

        if (jdbcDataSource == null) {
            jdbcDataSource = (DataSource) properties.get(UserCoreConstants.DATA_SOURCE);
        }

        if (jdbcDataSource == null) {
            jdbcDataSource = DatabaseUtil.getRealmDataSource(realmConfig);
            properties.put(UserCoreConstants.DATA_SOURCE, jdbcDataSource);
        }
        if (jdbcDataSource == null) {
            throw new UserStoreException("User Store Data Source is null");
        }

		dataSource = (DataSource) properties.get(UserCoreConstants.DATA_SOURCE);
		if (dataSource == null) {
			dataSource = DatabaseUtil.getRealmDataSource(realmConfig);
		}
		if (dataSource == null) {
			throw new UserStoreException("User Management Data Source is null");
		}

		properties.put(UserCoreConstants.DATA_SOURCE, dataSource);

        if (log.isDebugEnabled()) {
            log.debug("The jdbcDataSource being used by JDBCUserStoreManager :: "
                    + jdbcDataSource.hashCode());
        }
        realmConfig.setUserStoreProperties(JDBCRealmUtil.getSQL(realmConfig
                                           .getUserStoreProperties()));
        hybridRoleManager = new HybridRoleManager(dataSource, tenantId, realmConfig, jdbcUserRealm);
        this.addInitialData();
        if (log.isDebugEnabled()) {
            log.debug("Ended " + System.currentTimeMillis());
        }
        /*Initialize user roles cache as implemented in AbstractUserStoreManager*/
	    initUserRolesCache();         
    }

    public boolean isExistingUser(String userName) throws UserStoreException {

        if (CarbonConstants.REGISTRY_SYSTEM_USERNAME.equals(userName)) {
            return true;
        }
        
        return checkExistingUserName(userName);
    }

    public String[] listUsers(String filter, int maxItemLimit) throws UserStoreException {
        String[] users = new String[0];
        Connection dbConnection = null;
        String sqlStmt = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        if (maxItemLimit == 0) {
            return new String[0];
        }
        int givenMax = Integer.parseInt(realmConfig
                .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_MAX_USER_LIST));

        if (maxItemLimit < 0 || maxItemLimit > givenMax) {
            maxItemLimit = givenMax;
        }
        try {

            if (filter != null && filter.trim().length() != 0) {
                filter = filter.trim();
                filter = filter.replace("*", "%");
                filter = filter.replace("?", "_");
            } else {
                filter = "%";
            }
                        
            int i = 0;
            List<String> lst = new LinkedList<String>();

            dbConnection = getDBConnection();

            if (dbConnection == null) {
                throw new UserStoreException("null connection");
            }
            dbConnection.setAutoCommit(false);
            dbConnection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.GET_USER_FILTER);

            prepStmt = dbConnection.prepareStatement(sqlStmt);
            prepStmt.setString(1, filter);
            if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                prepStmt.setInt(2, tenantId);
            }

            rs = prepStmt.executeQuery();

            while (rs.next()) {
                if (i < maxItemLimit) {
                    String name = rs.getString(1);
                    if(CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME.equals(name)){
                        continue;
                    }
                    lst.add(name);
                } else {
                    break;
                }
                i++;
            }
            rs.close();

            if (lst.size() > 0) {
                users = (String[]) lst.toArray(new String[lst.size()]);
            }

        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            log.error("Using sql : " + sqlStmt);
            throw new UserStoreException(e.getMessage(), e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
        }
        return users;

    }

    public String[] getRoleNames() throws UserStoreException {
        String[] names = null;
        if(useOnlyInternalRoles){
            String[] hybrids = hybridRoleManager.getHybridRoles();
            names = UserCoreUtil.combineArrays(names, hybrids);
            return names;
        } else {
            String sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.GET_ROLE_LIST);
            if (sqlStmt == null) {
                throw new UserStoreException("The sql statement for retrieving role name is null");
            }
            names = getStringValuesFromDatabase(sqlStmt, tenantId);
            if (isReadOnly()) {
                String[] hybrids = hybridRoleManager.getHybridRoles();
                names = UserCoreUtil.combineArrays(names, hybrids);
            }
        }
        return names;
    }

    public String[] getRoleListOfUser(String userName) throws UserStoreException {
        String[] names = null;
        //check whether roles exist in cache
        try {
            names = getRoleListOfUserFromCache(this.tenantId, userName);
            if (names != null) {
                return names;
            }
        } catch (Exception e) {
            //if not exist in cache, continue
        }

        if(useOnlyInternalRoles){
            String[] hybrids = hybridRoleManager.getHybridRoleListOfUser(userName);
            names = UserCoreUtil.combineArrays(names, hybrids);
        } else {
            String sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.GET_USER_ROLE);
            if (sqlStmt == null) {
                throw new UserStoreException("The sql statement for retrieving user roles is null");
            }
            if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                names = getStringValuesFromDatabase(sqlStmt, userName, tenantId, tenantId, tenantId);
            } else {
                names = getStringValuesFromDatabase(sqlStmt, userName);
            }

            if (isReadOnly()) {
                String[] hybrids = hybridRoleManager.getHybridRoleListOfUser(userName);
                names = UserCoreUtil.combineArrays(names, hybrids);
            }
        }
        //add user roles into userRolesCache
        addToUserRolesCache(this.tenantId, userName, names);
        return names;
    }

    public boolean isUserInRole(String username, String roleName) throws UserStoreException {
        boolean hasRole = false;
        return hasRole;
    }
    
    public String[] getUserListOfRole(String roleName) throws UserStoreException {
        String[] names;
        if(useOnlyInternalRoles && hybridRoleManager.isExistingRole(roleName)){
            names = hybridRoleManager.getUserListOfHybridRole(roleName);
        } else {
            if (isReadOnly() && hybridRoleManager.isExistingRole(roleName)) {
                names = hybridRoleManager.getUserListOfHybridRole(roleName);
            } else {
                String sqlStmt = realmConfig
                        .getUserStoreProperty(JDBCRealmConstants.GET_USERS_IN_ROLE);
                if (sqlStmt == null) {
                    throw new UserStoreException("The sql statement for retrieving user roles is null");
                }
                if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                    names = getStringValuesFromDatabase(sqlStmt, roleName, tenantId, tenantId, tenantId);
                } else {
                    names = getStringValuesFromDatabase(sqlStmt, roleName);
                }
            }
        }
        return names;
    }

    public boolean isExistingRole(String roleName) throws UserStoreException {

        boolean isExisting = false;

        if(useOnlyInternalRoles){
            isExisting = hybridRoleManager.isExistingRole(roleName);
        } else {
            String sqlStmt = realmConfig
                    .getUserStoreProperty(JDBCRealmConstants.GET_IS_ROLE_EXISTING);
            if (sqlStmt == null) {
                throw new UserStoreException("The sql statement for is role existing role null");
            }

            if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                isExisting = isValueExisting(sqlStmt, null, roleName, tenantId);
            } else {
                isExisting = isValueExisting(sqlStmt, null, roleName);
            }

            if (!isExisting && isReadOnly()) {
                isExisting = hybridRoleManager.isExistingRole(roleName);
            }
        }


        return isExisting;
    }

    public String[] getAllProfileNames() throws UserStoreException {
        String sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.GET_PROFILE_NAMES);
        if (sqlStmt == null) {
            throw new UserStoreException("The sql statement for retrieving profile names is null");
        }
        String[] names;
        if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
            names = getStringValuesFromDatabase(sqlStmt, tenantId);
        } else {
            names = getStringValuesFromDatabase(sqlStmt);
        }

        return names;
    }

    public String[] getProfileNames(String userName) throws UserStoreException {
        String sqlStmt = realmConfig
                .getUserStoreProperty(JDBCRealmConstants.GET_PROFILE_NAMES_FOR_USER);
        if (sqlStmt == null) {
            throw new UserStoreException("The sql statement for retrieving  is null");
        }
        String[] names;
        if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
            names = getStringValuesFromDatabase(sqlStmt, userName, tenantId, tenantId);
        } else {
            names = getStringValuesFromDatabase(sqlStmt, userName);
        }
        if (names.length == 0) {
            names = new String[] { UserCoreConstants.DEFAULT_PROFILE };
        } else {
            Arrays.sort(names);
            if (Arrays.binarySearch(names, UserCoreConstants.DEFAULT_PROFILE) < 0) {
                // we have to add the default profile
                String[] newNames = new String[names.length + 1];
                int i = 0;
                for (i = 0; i < names.length; i++) {
                    newNames[i] = names[i];
                }
                newNames[i] = UserCoreConstants.DEFAULT_PROFILE;
                names = newNames;
            }
        }

        return names;
    }
    
    public int getUserId(String username) throws UserStoreException {
        String sqlStmt = realmConfig
                .getUserStoreProperty(JDBCRealmConstants.GET_USERID_FROM_USERNAME);
        if (sqlStmt == null) {
            throw new UserStoreException("The sql statement for retrieving ID is null");
        }
        int id = -1;
        Connection dbConnection = null;
        try {
            dbConnection = getDBConnection();
            if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                id = DatabaseUtil.getIntegerValueFromDatabase(dbConnection, sqlStmt, username, tenantId);
            } else {
                id = DatabaseUtil.getIntegerValueFromDatabase(dbConnection, sqlStmt, username);
            }
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            throw new UserStoreException(e.getMessage(), e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
        return id;
    }
    

    /**
     *
     * @param tenantId tenant id
     * @return array of users of the tenant.
     * @throws UserStoreException throws user store exception
     */
    public String[] getUserNames(int tenantId) throws UserStoreException {
        String sqlStmt = realmConfig
                .getUserStoreProperty(JDBCRealmConstants.GET_USERNAME_FROM_TENANT_ID);
        if (sqlStmt == null) {
            throw new UserStoreException("The sql statement for retrieving user names is null");
        }
        String[] userNames;
        Connection dbConnection = null;
        try {
            dbConnection = getDBConnection();
            userNames = DatabaseUtil.getStringValuesFromDatabase(dbConnection, sqlStmt, tenantId);
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            throw new UserStoreException(e.getMessage(), e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
        return userNames;
    }


    /**
     * @deprecated
     * 
     * Returns the admin users for the given tenant.
     * @return the admin user.
     * @throws org.wso2.carbon.user.core.UserStoreException from the getUserNames()
     */
    public String getAdminUser() throws UserStoreException {
        String[] users = getUserListOfRole(this.realmConfig.getAdminRoleName());
        if (users != null && users.length > 0) {
            return users[0];
        }
        return null;
    }

    public int getTenantId() throws UserStoreException {
        return this.tenantId;
    }

    public Map<String, String> getProperties(org.wso2.carbon.user.api.Tenant tenant)
            throws org.wso2.carbon.user.api.UserStoreException {
        return getProperties((Tenant) tenant);
    }

    public int getTenantId(String username) throws UserStoreException {
        if (this.tenantId != MultitenantConstants.SUPER_TENANT_ID) {
            throw new UserStoreException("Not allowed to perform this operation");
        }
        String sqlStmt = realmConfig
                .getUserStoreProperty(JDBCRealmConstants.GET_TENANT_ID_FROM_USERNAME);
        if (sqlStmt == null) {
            throw new UserStoreException("The sql statement for retrieving ID is null");
        }
        int id = -1;
        Connection dbConnection = null;
        try {
            dbConnection = getDBConnection();
            id = DatabaseUtil.getIntegerValueFromDatabase(dbConnection, sqlStmt, username);
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            throw new UserStoreException(e.getMessage(), e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
        return id;
    }

    // ******************** private methods ********************************

    public Map<String, String> getUserPropertyValues(String userName, String[] propertyNames,
            String profileName) throws UserStoreException {
        if (profileName == null) {
            profileName = UserCoreConstants.DEFAULT_PROFILE;
        }
        Connection dbConnection = null;
        String sqlStmt = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        String[] propertyNamesSorted = propertyNames.clone();
        Arrays.sort(propertyNamesSorted);
        Map<String, String> map = new HashMap<String, String>();
        try {
            dbConnection = getDBConnection();
            sqlStmt = realmConfig
                    .getUserStoreProperty(JDBCRealmConstants.GET_PROPS_FOR_PROFILE);
            prepStmt = dbConnection.prepareStatement(sqlStmt);
            prepStmt.setString(1, userName);
            prepStmt.setString(2, profileName);
            if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                prepStmt.setInt(3, tenantId);
                prepStmt.setInt(4, tenantId);
            }
            rs = prepStmt.executeQuery();
            while (rs.next()) {
                String name = rs.getString(1);
                String value = rs.getString(2);
                if (Arrays.binarySearch(propertyNamesSorted, name) < 0) {
                    continue;
                }
                map.put(name, value);
            }

            return map;
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            throw new UserStoreException(e.getMessage(), e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
        }
    }

    private String[] getStringValuesFromDatabase(String sqlStmt, Object... params)
            throws UserStoreException {
        String[] values = new String[0];
        Connection dbConnection = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        try {            
            dbConnection = getDBConnection();
            values = DatabaseUtil.getStringValuesFromDatabase(dbConnection, sqlStmt, params);
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            log.error("Using sql : " + sqlStmt);
            throw new UserStoreException(e.getMessage(), e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
        }
        return values;
    }

    protected Connection getDBConnection() throws SQLException {
        Connection dbConnection = jdbcDataSource.getConnection();
        dbConnection.setAutoCommit(false);
        dbConnection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);        
        return dbConnection;
    }

    private boolean isValueExisting(String sqlStmt, Connection dbConnection, Object... params) throws UserStoreException {
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        boolean isExisting = false;
        boolean doClose = false;
        try {
            if (dbConnection == null) {
                dbConnection = getDBConnection();
                doClose = true; //because we created it
            }
            if (DatabaseUtil.getIntegerValueFromDatabase(dbConnection, sqlStmt, params) > -1) {
                isExisting = true;
            }
            return isExisting;
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            log.error("Using sql : " + sqlStmt);
            throw new UserStoreException(e.getMessage(), e);
        } finally {
            if (doClose) {
                DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
            }
        }
    }
    
    protected boolean checkExistingUserName(String userName) throws UserStoreException {
        String sqlStmt = realmConfig
                .getUserStoreProperty(JDBCRealmConstants.GET_IS_USER_EXISTING);
        if (sqlStmt == null) {
            throw new UserStoreException("The sql statement for is user existing null");
        }
        boolean isExisting = false;

        String isUnique = realmConfig
                .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_USERNAME_UNIQUE);
        if ("true".equals(isUnique) && !CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME.equals(userName)) {
            String uniquenesSql = realmConfig
                    .getUserStoreProperty(JDBCRealmConstants.USER_NAME_UNIQUE);
            isExisting = isValueExisting(uniquenesSql, null, userName);
            if(log.isDebugEnabled()) {
                log.debug("The username should be unique across tenants.");
            }
        } else {
            if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                isExisting = isValueExisting(sqlStmt, null, userName, tenantId);
            } else {
                isExisting = isValueExisting(sqlStmt, null, userName);
            }
        }

        return isExisting;
    }

    public boolean doAuthenticate(String userName, Object credential) throws UserStoreException {

        if (!checkUserNameValid(userName)) {
            return false;
        }

        if (!checkUserPasswordValid(credential)) {
            return false;
        }
        
        if (CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME.equals(userName)) {
            log.error("Anonnymous user trying to login");
            return false;
        }

        Connection dbConnection = null;
        ResultSet rs = null;
        PreparedStatement prepStmt = null;
        String sqlstmt = null;
        String password = (String) credential;
        boolean isAuthed = false;

        try {
            dbConnection = getDBConnection();
            dbConnection.setAutoCommit(false);

            sqlstmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.SELECT_USER);

            if (log.isDebugEnabled()) {
                log.debug(sqlstmt);
            }

            prepStmt = dbConnection.prepareStatement(sqlstmt);
            prepStmt.setString(1, userName);
            if (sqlstmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                prepStmt.setInt(2, tenantId);
            }

            rs = prepStmt.executeQuery();

            if (rs.next() == true) {
                String storedPassword = rs.getString(3);
                String saltValue = null;
                if ("true".equals(realmConfig
                        .getUserStoreProperty(JDBCRealmConstants.STORE_SALTED_PASSWORDS))) {
                    saltValue = rs.getString(4);
                }

                boolean requireChange = rs.getBoolean(5);
                Timestamp changedTime = rs.getTimestamp(6);

                GregorianCalendar gc = new GregorianCalendar();
                gc.add(GregorianCalendar.HOUR, -24);
                Date date = gc.getTime();

                if (requireChange == true && changedTime.before(date)) {
                    isAuthed = false;
                } else {
                    password = this.preparePassword(password, saltValue);
                    if ((storedPassword != null) && (storedPassword.equals(password))) {
                        isAuthed = true;
                    }
                }
            }
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            log.error("Using sql : " + sqlstmt);
            throw new UserStoreException("Authentication Failure");
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
        }

        if (log.isDebugEnabled()) {
            log.debug("User " + userName + " login attempt. Login success :: " + isAuthed);
        }

        return isAuthed;
    }

    public boolean isReadOnly() throws UserStoreException {
        if ("true".equals(realmConfig
                .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_READ_ONLY))) {
            return true;
        }
        return false;
    }

    public void doAddUser(String userName, Object credential, String[] roleList,
            Map<String, String> claims, String profileName) throws UserStoreException {
        this.doAddUser(userName, credential, roleList, claims, profileName, false);
    }

    public void doAddUser(String userName, Object credential, String[] roleList,
            Map<String, String> claims, String profileName, boolean requirePasswordChange)
            throws UserStoreException {
        
        // persist the user info. in the database.
        persistUser(userName, credential, roleList, claims, profileName, requirePasswordChange);

    }

    /*
    This method persists the user information in the database.
     */
    protected void persistUser(String userName, Object credential, String[] roleList,
                             Map<String, String> claims, String profileName,
                             boolean requirePasswordChange) throws UserStoreException {
        if (!checkUserNameValid(userName)) {
            throw new UserStoreException(
                "Username not valid. User name must be a non null string with following format, " +
                    realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_USER_NAME_JAVA_REG_EX));

        }

        if (!checkUserPasswordValid(credential)) {
            throw new UserStoreException(
                "Credential not valid. Credential must be a non null string with following format, " +
                    realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_JAVA_REG_EX));

        }
        
        boolean isExisting = checkExistingUserName(userName);
        if (isExisting) {
            throw new UserStoreException("Username '" + userName
                    + "' already exists in the system. Please pick another username.");
        }

        Connection dbConnection = null;
        String password = (String) credential;
        try {
            dbConnection = getDBConnection();
            String sqlStmt1 = realmConfig.getUserStoreProperty(JDBCRealmConstants.ADD_USER);

            String saltValue = null;

            if ("true".equals(realmConfig.getUserStoreProperties().get(
                    JDBCRealmConstants.STORE_SALTED_PASSWORDS))) {
                byte[] bytes = new byte[16];
                random.nextBytes(bytes);
                saltValue = Base64.encode(bytes);
            }

            password = this.preparePassword(password, saltValue);

            // do all 4 possibilities
            if (sqlStmt1.contains(UserCoreConstants.UM_TENANT_COLUMN) && (saltValue == null)) {
                this.updateStringValuesToDatabase(dbConnection, sqlStmt1, userName, password, "",
                        requirePasswordChange, new Date(), tenantId);
            } else if (sqlStmt1.contains(UserCoreConstants.UM_TENANT_COLUMN) && (saltValue != null)) {
                this.updateStringValuesToDatabase(dbConnection, sqlStmt1, userName, password,
                        saltValue, requirePasswordChange, new Date(), tenantId);
            } else if (!sqlStmt1.contains(UserCoreConstants.UM_TENANT_COLUMN)
                    && (saltValue == null)) {
                this.updateStringValuesToDatabase(dbConnection, sqlStmt1, userName, password,
                        null, requirePasswordChange, new Date());
            } else {
                this.updateStringValuesToDatabase(dbConnection, sqlStmt1, userName, password,
                        requirePasswordChange, new Date());
            }

            String[] roles = null;
            if (CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME.equals(userName)) {
                roles = new String[0];
            } else {
                if (roleList == null || roleList.length == 0) {
                    roles = new String[] { this.realmConfig.getEveryOneRoleName() };
                } else {
                    Arrays.sort(roleList);
                    if (Arrays.binarySearch(roleList, realmConfig.getEveryOneRoleName()) < 0) {
                        roles = new String[roleList.length + 1];
                        int i = 0;
                        for (i = 0; i < roleList.length; i++) {
                            roles[i] = roleList[i];
                        }
                        roles[i] = realmConfig.getEveryOneRoleName();
                    } else {
                        roles = roleList;
                    }
                }
            }

            // add user to role.
            if (useOnlyInternalRoles) {
                hybridRoleManager.updateHybridRoleListOfUser(userName,null,roleList);
            } else {
                String sqlStmt2 = null;
                String type = DatabaseCreator.getDatabaseType(dbConnection);
                sqlStmt2 = realmConfig.getUserStoreProperty(JDBCRealmConstants.ADD_ROLE_TO_USER
                                                            + "-" + type);
                if (sqlStmt2 == null) {
                    sqlStmt2 = realmConfig
                            .getUserStoreProperty(JDBCRealmConstants.ADD_ROLE_TO_USER);
                }
                if (sqlStmt2.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                    if(UserCoreConstants.OPENEDGE_TYPE.equals(type)) {
                        DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt2,
                                                                      tenantId, roles, tenantId, userName, tenantId);
                    } else {
                        DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt2, roles,
                                                                      tenantId, userName, tenantId, tenantId);
                    }
                } else {
                    DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt2, roles,
                                                                  tenantId, userName);
                }
            }


            if (claims != null) {
                // add the properties
                if (profileName == null) {
                    profileName = UserCoreConstants.DEFAULT_PROFILE;
                }

                Iterator<Map.Entry<String, String>> ite = claims.entrySet().iterator();
                while (ite.hasNext()) {
                    Map.Entry<String, String> entry = ite.next();
                    String claimURI = entry.getKey();
                    String propName = claimManager.getAttributeName(claimURI);
                    String propValue = entry.getValue();
                    addProperty(dbConnection, userName, propName, propValue, profileName);
                }
            }

            dbConnection.commit();
        } catch (Throwable e) {
        	try {
				dbConnection.rollback();
			} catch (SQLException e1) {
	            log.error(e.getMessage(), e1);
	            throw new UserStoreException(e.getMessage(), e1);
			}
            log.error(e.getMessage(), e);
            throw new UserStoreException(e.getMessage(), e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
    }

    public void addRole(String roleName, String[] userList, Permission[] permissions)
            throws UserStoreException {
        Connection dbConnection = null;
        try {

            if (!roleNameValid(roleName)) {
                throw new UserStoreException(
                    "Role name not valid. Role name must be a non null string with following format, " +
                        realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_ROLE_NAME_JAVA_REG_EX));
            }

            if (isExistingRole(roleName)) {
                throw new UserStoreException(
                        "Role name: "+roleName+" in the system. Please pick another role name.");
            }
            dbConnection = getDBConnection();

            if(useOnlyInternalRoles){
                hybridRoleManager.addHybridRole(roleName, userList);
            } else {
                if (isReadOnly()) {
                    hybridRoleManager.addHybridRole(roleName, userList);
                } else {
                    String sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.ADD_ROLE);
                    if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                        this.updateStringValuesToDatabase(dbConnection, sqlStmt, roleName, tenantId);
                    } else {
                        this.updateStringValuesToDatabase(dbConnection, sqlStmt, roleName);
                    }
                    if (userList != null) {
                        // add role to user
                        String type = DatabaseCreator.getDatabaseType(dbConnection);
                        String sqlStmt2 = realmConfig
                                .getUserStoreProperty(JDBCRealmConstants.ADD_USER_TO_ROLE +"-"+type);
                        if (sqlStmt2 == null) {
                            sqlStmt2 = realmConfig
                                    .getUserStoreProperty(JDBCRealmConstants.ADD_USER_TO_ROLE);
                        }
                        if (sqlStmt2.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                            if (UserCoreConstants.OPENEDGE_TYPE.equals(type)) {
                                DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt2,
                                        tenantId, userList, tenantId, roleName, tenantId);
                            } else {
                                DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt2,
                                        userList, tenantId, roleName, tenantId, tenantId);
                            }
                        } else {
                            DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt2,
                                    userList, tenantId, roleName);
                        }
                    }
                }
            }
            if (permissions != null) {
                for (Permission permission : permissions) {
                    String resourceId = permission.getResourceId();
                    String action = permission.getAction();
                    jdbcUserRealm.getAuthorizationManager().authorizeRole(roleName, resourceId, action);
                }
            }

            dbConnection.commit();
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            throw new UserStoreException(e.getMessage(), e);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new UserStoreException(e.getMessage(), e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
        //if existing users are added to role, need to update user role cache
        if ((userList != null) && (userList.length > 0)) {
            clearUserRolesCacheByTenant(this.tenantId);
        }

    }


    public void updateRoleName(String roleName, String newRoleName) throws UserStoreException {

        if(useOnlyInternalRoles){
            hybridRoleManager.updateHybridRoleName(roleName, newRoleName);            
        } else {
            if (isReadOnly()) {
                hybridRoleManager.updateHybridRoleName(roleName, newRoleName);
            } else {
                if (realmConfig.getAdminRoleName().equals(roleName)) {
                    throw new UserStoreException("Cannot delete admin role");
                }
                if (realmConfig.getEveryOneRoleName().equals(roleName)) {
                    throw new UserStoreException("Cannot delete everyone role");
                }
                if (isExistingRole(newRoleName)) {
                    throw new UserStoreException(
                            "Role name: "+newRoleName+" in the system. Please pick another role name.");
                }
                String sqlStmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.UPDATE_ROLE_NAME);
                if (sqlStmt == null) {
                    throw new UserStoreException("The sql statement for update role name is null");
                }
                Connection dbConnection = null;
                try {

                    dbConnection = getDBConnection();
                    if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                        this.updateStringValuesToDatabase(dbConnection, sqlStmt, newRoleName, roleName,
                                tenantId);
                    } else {
                        this.updateStringValuesToDatabase(dbConnection, sqlStmt, newRoleName, roleName);
                    }
                    dbConnection.commit();
                    this.jdbcUserRealm.getAuthorizationManager().resetPermissionOnUpdateRole(roleName,
                                                                                         newRoleName);

                } catch (SQLException e) {
                    log.error(e.getMessage(), e);
                    log.error("Using sql : " + sqlStmt);
                    throw new UserStoreException(e.getMessage(), e);
                } finally {
                    DatabaseUtil.closeAllConnections(dbConnection);
                }
            }
        }

        //need to update user role cache upon update of role names
        clearUserRolesCacheByTenant(this.tenantId);
    }

    /**
     * JDBC User store supports bulk import.
     * @return Always <code>true<code>.
     */
    public boolean isBulkImportSupported() {
        return true;
    }

    /**
     * This method is to check whether multiple profiles are allowed with a particular user-store.
     * For an example, currently, JDBC user store supports multiple profiles and where as ApacheDS
     * does not allow.
     * Currently, JDBC user store allows multiple profiles. Hence return true.
     * @return boolean
     */
    public boolean isMultipleProfilesAllowed() {
        return true;  //To change body of implemented methods use File | Settings | File Templates.
    }


    public void deleteRole(String roleName) throws UserStoreException {

        if(useOnlyInternalRoles && hybridRoleManager.isExistingRole(roleName)){
            hybridRoleManager.deleteHybridRole(roleName);    
        } else {
            if (isReadOnly() && hybridRoleManager.isExistingRole(roleName)) {
                hybridRoleManager.deleteHybridRole(roleName);
            } else {
                if (realmConfig.getAdminRoleName().equals(roleName)) {
                    throw new UserStoreException("Cannot delete admin role");
                }

                if (realmConfig.getEveryOneRoleName().equals(roleName)) {
                    throw new UserStoreException("Cannot delete everyone role");
                }
                String sqlStmt1 = realmConfig
                        .getUserStoreProperty(JDBCRealmConstants.ON_DELETE_ROLE_REMOVE_USER_ROLE);
                if (sqlStmt1 == null) {
                    throw new UserStoreException(
                            "The sql statement for delete user-role mapping is null");
                }

                String sqlStmt2 = realmConfig.getUserStoreProperty(JDBCRealmConstants.DELETE_ROLE);
                if (sqlStmt2 == null) {
                    throw new UserStoreException("The sql statement for delete role is null");
                }

                Connection dbConnection = null;
                try {
                    dbConnection = getDBConnection();
                    if (sqlStmt1.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                        this.updateStringValuesToDatabase(dbConnection, sqlStmt1, roleName, tenantId,
                                tenantId);
                        this.updateStringValuesToDatabase(dbConnection, sqlStmt2, roleName, tenantId);
                    } else {
                        this.updateStringValuesToDatabase(dbConnection, sqlStmt1, roleName);
                        this.updateStringValuesToDatabase(dbConnection, sqlStmt2, roleName);
                    }
                    this.jdbcUserRealm.getAuthorizationManager().clearRoleAuthorization(roleName);
                    dbConnection.commit();

                } catch (SQLException e) {
                    log.error(e.getMessage(), e);
                    log.error("Using sql : " + sqlStmt1);
                    throw new UserStoreException(e.getMessage(), e);
                } finally {
                    DatabaseUtil.closeAllConnections(dbConnection);
                }
            }
        }
        //need to clear user role cache upon delete of roles
        clearUserRolesCacheByTenant(this.tenantId);
    }

    public void doDeleteUser(String userName) throws UserStoreException {

        String sqlStmt1 = realmConfig
                .getUserStoreProperty(JDBCRealmConstants.ON_DELETE_USER_REMOVE_USER_ROLE);
        if (sqlStmt1 == null) {
            throw new UserStoreException("The sql statement for delete user-role mapping is null");
        }

        String sqlStmt2 = realmConfig
                .getUserStoreProperty(JDBCRealmConstants.ON_DELETE_USER_REMOVE_ATTRIBUTE);
        if (sqlStmt2 == null) {
            throw new UserStoreException("The sql statement for delete user attribute is null");
        }

        String sqlStmt3 = realmConfig.getUserStoreProperty(JDBCRealmConstants.DELETE_USER);
        if (sqlStmt3 == null) {
            throw new UserStoreException("The sql statement for delete user is null");
        }

        Connection dbConnection = null;
        try {
            dbConnection = getDBConnection();
            if (sqlStmt1.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                if(useOnlyInternalRoles){
                    hybridRoleManager.updateHybridRoleListOfUser(userName, getRoleNames(), null);
                } else {
                    this.updateStringValuesToDatabase(dbConnection, sqlStmt1, userName, tenantId,
                            tenantId);
                }
                this.updateStringValuesToDatabase(dbConnection, sqlStmt2, userName, tenantId,
                        tenantId);
                this.updateStringValuesToDatabase(dbConnection, sqlStmt3, userName, tenantId);
            } else {
                if(useOnlyInternalRoles){
                    hybridRoleManager.updateHybridRoleListOfUser(userName, getRoleNames(), null);
                } else {
                    this.updateStringValuesToDatabase(dbConnection, sqlStmt1, userName);
                }
                this.updateStringValuesToDatabase(dbConnection, sqlStmt2, userName);
                this.updateStringValuesToDatabase(dbConnection, sqlStmt3, userName);
            }
            this.jdbcUserRealm.getAuthorizationManager().clearUserAuthorization(userName);
            dbConnection.commit();
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            log.error("Using sql : " + sqlStmt1 + " :: " + sqlStmt2);
            throw new UserStoreException(e.getMessage(), e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
        //needs to clear roles cache upon deletion of a user
        clearUserRolesCacheByTenant(this.tenantId);
    }

    public void addRole(String roleName, String[] userList,
                        org.wso2.carbon.user.api.Permission[] permissions)
            throws org.wso2.carbon.user.api.UserStoreException {
        addRole(roleName, userList, (Permission[]) permissions);
    }

    public void doUpdateUserListOfRole(String roleName, String deletedUsers[], String[] newUsers)
            throws UserStoreException {

        if(useOnlyInternalRoles && hybridRoleManager.isExistingRole(roleName)){
            hybridRoleManager.updateUserListOfHybridRole(roleName, deletedUsers, newUsers);
        } else {
            if (isReadOnly() && hybridRoleManager.isExistingRole(roleName)) {
                hybridRoleManager.updateUserListOfHybridRole(roleName, deletedUsers, newUsers);
            } else {
                String sqlStmt1 = realmConfig
                        .getUserStoreProperty(JDBCRealmConstants.REMOVE_USER_FROM_ROLE);
                if (sqlStmt1 == null) {
                    throw new UserStoreException("The sql statement for remove user from role is null");
                }

                Connection dbConnection = null;
                try {
                    dbConnection = getDBConnection();
                    String type = DatabaseCreator.getDatabaseType(dbConnection);
                    String sqlStmt2 = realmConfig
                            .getUserStoreProperty(JDBCRealmConstants.ADD_USER_TO_ROLE + "-" + type);
                    if (sqlStmt2 == null) {
                        sqlStmt2 = realmConfig
                                .getUserStoreProperty(JDBCRealmConstants.ADD_USER_TO_ROLE);
                    }
                    if (sqlStmt2 == null) {
                        throw new UserStoreException("The sql statement for add user to role is null");
                    }
                    if (deletedUsers != null) {
                        if (sqlStmt1.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                            DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt1,
                                    deletedUsers, tenantId, roleName, tenantId, tenantId);
                        } else {
                            DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt1,
                                    deletedUsers, tenantId, roleName);
                        }
                        if (deletedUsers.length > 0) {
                            //authz cache of deleted users from role, needs to be updated
                            for (String deletedUser : deletedUsers) {
                                jdbcUserRealm.getAuthorizationManager().clearUserAuthorization(deletedUser);
                            }
                        }
                    }
                    if (newUsers != null) {
                        if (sqlStmt1.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                            if (UserCoreConstants.OPENEDGE_TYPE.equals(type)) {
                                DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt2,
                                        tenantId, newUsers, tenantId, roleName, tenantId);
                            } else {
                                DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt2,
                                    newUsers, tenantId, roleName, tenantId, tenantId);
                            }
                        } else {
                            DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt2,
                                    newUsers, tenantId, roleName);
                        }
                    }
                    dbConnection.commit();

                } catch (SQLException e) {
                    log.error(e.getMessage(), e);
                    throw new UserStoreException(e.getMessage(), e);
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                    throw new UserStoreException(e.getMessage(), e);
                } finally {
                    DatabaseUtil.closeAllConnections(dbConnection);
                }
            }
        }
        //need to clear user roles cache upon roles update
        clearUserRolesCacheByTenant(this.tenantId);
    }

    public void doUpdateRoleListOfUser(String userName, String[] deletedRoles, String[] newRoles)
            throws UserStoreException {

        if(useOnlyInternalRoles){
            hybridRoleManager.updateHybridRoleListOfUser(userName, deletedRoles, newRoles);            
        } else {
            if (isReadOnly()) {
                hybridRoleManager.updateHybridRoleListOfUser(userName, deletedRoles, newRoles);
            } else {
                String sqlStmt1 = realmConfig
                        .getUserStoreProperty(JDBCRealmConstants.REMOVE_ROLE_FROM_USER);
                if (sqlStmt1 == null) {
                    throw new UserStoreException("The sql statement for remove user from role is null");
                }
                Connection dbConnection = null;
                try {
                    dbConnection = getDBConnection();
                    String type = DatabaseCreator.getDatabaseType(dbConnection);
                    String sqlStmt2 = realmConfig
                            .getUserStoreProperty(JDBCRealmConstants.ADD_ROLE_TO_USER + "-" + type);
                    if (sqlStmt2 == null) {
                        sqlStmt2 = realmConfig
                                .getUserStoreProperty(JDBCRealmConstants.ADD_ROLE_TO_USER);
                    }
                    if (sqlStmt2 == null) {
                        throw new UserStoreException("The sql statement for add user to role is null");
                    }

                    if (deletedRoles != null) {
                        if (sqlStmt1.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                            DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt1,
                                    deletedRoles, tenantId, userName, tenantId, tenantId);
                        } else {
                            DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt1,
                                    deletedRoles, tenantId, userName);
                        }
                    }

                    if (newRoles != null) {
                        if (sqlStmt1.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                            // System.out.println("" +
                            // DatabaseUtil.getIntegerValueFromDatabase(dbConnection,
                            // sqlStmt, params));
                            // System.out.println("" +
                            // DatabaseUtil.getIntegerValueFromDatabase(dbConnection,
                            // sqlStmt, params));
                            if (UserCoreConstants.OPENEDGE_TYPE.equals(type)) {
                                DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt2,
                                    tenantId, newRoles, tenantId, userName, tenantId);
                            } else {
                                DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt2,
                                    newRoles, tenantId, userName, tenantId, tenantId);
                            }
                        } else {
                            DatabaseUtil.udpateUserRoleMappingInBatchMode(dbConnection, sqlStmt2,
                                    newRoles, tenantId, userName);
                        }
                    }
                    dbConnection.commit();
                    //authz cache of user should also be updated if deleted roles are involved
                    if (deletedRoles != null && deletedRoles.length > 0) {
                        jdbcUserRealm.getAuthorizationManager().clearUserAuthorization(userName);
                    }
                } catch (SQLException e) {
                    log.error(e.getMessage(), e);
                    throw new UserStoreException(e.getMessage(), e);
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                    throw new UserStoreException(e.getMessage(), e);
                } finally {
                    DatabaseUtil.closeAllConnections(dbConnection);
                }
            }
        }
        //need to clear user roles cache upon roles update
        clearUserRolesCacheByTenant(this.tenantId);
    }

    public void doSetUserClaimValue(String userName, String claimURI, String claimValue,
            String profileName) throws UserStoreException {
        if (profileName == null) {
            profileName = UserCoreConstants.DEFAULT_PROFILE;
        }
        if (claimValue == null) {
            throw new UserStoreException("Cannot set null values.");
        }
        Connection dbConnection = null;
        try {
            dbConnection = getDBConnection();
            ClaimMapping cMapping = (ClaimMapping) claimManager.getClaimMapping(claimURI);
            String property;
            if (cMapping != null) {
                property = cMapping.getMappedAttribute();
            } else {
                property = claimURI;
            }
            String value = getProperty(dbConnection, userName, property, profileName);
            if (value == null) {
                addProperty(dbConnection, userName, property, claimValue, profileName);
            } else {
                updateProperty(dbConnection, userName, property, claimValue, profileName);
            }
            dbConnection.commit();
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            throw new UserStoreException(e.getMessage(), e);
        } catch (org.wso2.carbon.user.api.UserStoreException e) {
            throw new UserStoreException(e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
    }

    public void doSetUserClaimValues(String userName, Map<String, String> claims, String profileName)
            throws UserStoreException {
        Connection dbConnection = null;
        if (profileName == null) {
            profileName = UserCoreConstants.DEFAULT_PROFILE;
        }

        if (claims.get(UserCoreConstants.PROFILE_CONFIGURATION) == null) {
            claims.put(UserCoreConstants.PROFILE_CONFIGURATION,
                    UserCoreConstants.DEFAULT_PROFILE_CONFIGURATION);
        }

        try {
            dbConnection = getDBConnection();
            Iterator<Map.Entry<String, String>> ite = claims.entrySet().iterator();
            while (ite.hasNext()) {
                Map.Entry<String, String> entry = ite.next();
                String claimURI = entry.getKey();
                ClaimMapping cMapping = (ClaimMapping) claimManager.getClaimMapping(claimURI);
                String property = null;
                if (cMapping != null) {
                    property = cMapping.getMappedAttribute();
                } else {
                    property = claimURI;
                }
                String value = entry.getValue();
                String existingValue = getProperty(dbConnection, userName, property, profileName);
                if (existingValue == null) {
                    addProperty(dbConnection, userName, property, value, profileName);
                } else {
                    updateProperty(dbConnection, userName, property, value, profileName);
                }
            }
            dbConnection.commit();
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            throw new UserStoreException(e.getMessage(), e);
        } catch (org.wso2.carbon.user.api.UserStoreException e) {
            throw new UserStoreException(e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
    }

    public void doDeleteUserClaimValue(String userName, String claimURI, String profileName)
            throws UserStoreException {
        Connection dbConnection = null;
        if (profileName == null) {
            profileName = UserCoreConstants.DEFAULT_PROFILE;
        }
        try {
            String property = null;
            if (UserCoreConstants.PROFILE_CONFIGURATION.equals(claimURI)) {
                property = UserCoreConstants.PROFILE_CONFIGURATION;
            } else {
                property = claimManager.getClaimMapping(claimURI).getMappedAttribute();
            }

            dbConnection = getDBConnection();
            this.deleteProperty(dbConnection, userName, property, profileName);
            dbConnection.commit();
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            throw new UserStoreException(e.getMessage(), e);
        } catch (org.wso2.carbon.user.api.UserStoreException e) {
            throw new UserStoreException(e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
    }

    public void doDeleteUserClaimValues(String userName, String[] claims, String profileName)
            throws UserStoreException {
        Connection dbConnection = null;
        if (profileName == null) {
            profileName = UserCoreConstants.DEFAULT_PROFILE;
        }
        try {
            dbConnection = getDBConnection();
            for (String claimURI : claims) {
                String property = claimManager.getClaimMapping(claimURI).getMappedAttribute();
                this.deleteProperty(dbConnection, userName, property, profileName);
            }
            dbConnection.commit();
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            throw new UserStoreException(e.getMessage(), e);
        } catch (org.wso2.carbon.user.api.UserStoreException e) {
            throw new UserStoreException(e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
    }

    public void doUpdateCredential(String userName, Object newCredential, Object oldCredential)
            throws UserStoreException {

        if (this.authenticate(userName, oldCredential)) {
            this.updateCredentialByAdmin(userName, newCredential);
        } else {
            log.error("Wrong username/password provided");
            throw new UserStoreException("Wrong username/password provided");
        }
    }

    public void doUpdateCredentialByAdmin(String userName, Object newCredential)
            throws UserStoreException {

        if (!checkUserPasswordValid(newCredential)) {
            throw new UserStoreException(
                "Credential not valid. Credential must be a non null string with following format, " +
                    realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_JAVA_REG_EX));

        }

        String sqlStmt = realmConfig
                .getUserStoreProperty(JDBCRealmConstants.UPDATE_USER_PASSWORD);
        if (sqlStmt == null) {
            throw new UserStoreException("The sql statement for delete user claim value is null");
        }
        String saltValue = null;
        if ("true".equals(realmConfig.getUserStoreProperties().get(
                JDBCRealmConstants.STORE_SALTED_PASSWORDS))) {
            byte[] bytes = new byte[16];
            random.nextBytes(bytes);
            saltValue = Base64.encode(bytes);
        }

        String password = this.preparePassword((String) newCredential, saltValue);

        if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN) && saltValue == null) {
            updateStringValuesToDatabase(null, sqlStmt, password, false, new Date(), userName,
                    tenantId);
        } else if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN) && saltValue != null) {
            updateStringValuesToDatabase(null, sqlStmt, password, saltValue, false, new Date(),
                    userName, tenantId);
        } else if (!sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN) && saltValue == null) {
            updateStringValuesToDatabase(null, sqlStmt, password, false, new Date(), userName);
        } else {
            updateStringValuesToDatabase(null, sqlStmt, password, saltValue, false, new Date(),
                    userName);
        }
    }

    public String[] getHybridRoles() throws UserStoreException {
        String[] names = new String[0];
        if (isReadOnly() || useOnlyInternalRoles) {
            names = hybridRoleManager.getHybridRoles();
        }
        return names;
    }

    public Date getPasswordExpirationTime(String userName) throws UserStoreException {
        Connection dbConnection = null;
        ResultSet rs = null;
        PreparedStatement prepStmt = null;
        String sqlstmt = null;
        Date date = null;

        try {
            dbConnection = getDBConnection();
            dbConnection.setAutoCommit(false);

            sqlstmt = realmConfig.getUserStoreProperty(JDBCRealmConstants.SELECT_USER);

            if (log.isDebugEnabled()) {
                log.debug(sqlstmt);
            }

            prepStmt = dbConnection.prepareStatement(sqlstmt);
            prepStmt.setString(1, userName);
            if (sqlstmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                prepStmt.setInt(2, tenantId);
            }

            rs = prepStmt.executeQuery();

            if (rs.next() == true) {
                boolean requireChange = rs.getBoolean(5);
                Timestamp changedTime = rs.getTimestamp(6);
                if (requireChange) {
                    GregorianCalendar gc = new GregorianCalendar();
                    gc.setTime(changedTime);
                    gc.add(GregorianCalendar.HOUR, 24);
                    date = gc.getTime();
                }
            }
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            log.error("Using sql : " + sqlstmt);
            throw new UserStoreException(e.getMessage());
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
        }
        return date;
    }

    private void updateStringValuesToDatabase(Connection dbConnection, String sqlStmt,
            Object... params) throws UserStoreException {
        PreparedStatement prepStmt = null;
        boolean localConnection = false;
        try {
            if (dbConnection == null) {
                localConnection = true;
                dbConnection = getDBConnection();
            }
            prepStmt = dbConnection.prepareStatement(sqlStmt);
            if (params != null && params.length > 0) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    if (param == null) {
                        throw new UserStoreException("Invalid data provided");
                    } else if (param instanceof String) {
                        prepStmt.setString(i + 1, (String) param);
                    } else if (param instanceof Integer) {
                        prepStmt.setInt(i + 1, (Integer) param);
                    } else if (param instanceof Date) {
                        //Timestamp timestamp = new Timestamp(((Date) param).getTime());
                        //prepStmt.setTimestamp(i + 1, timestamp);
                        prepStmt.setTimestamp(i + 1,new Timestamp(System.currentTimeMillis()));
                    } else if (param instanceof Boolean) {
                        prepStmt.setBoolean(i + 1, (Boolean) param);
                    }
                }
            }
            int count = prepStmt.executeUpdate();
            if(count == 0) {
                log.info("No rows were updated");
            }
            if (log.isDebugEnabled()) {
                log.debug("Executed querry is " + sqlStmt + " and number of updated rows :: "
                        + count);
            }

            if (localConnection) {
                dbConnection.commit();
            }
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            log.error("Using sql : " + sqlStmt);
            throw new UserStoreException(e.getMessage(), e);
        } finally {
            if (localConnection) {
                DatabaseUtil.closeAllConnections(dbConnection);
            }
            DatabaseUtil.closeAllConnections(null, prepStmt);
        }
    }

    public void addProperty(Connection dbConnection, String userName, String propertyName,
            String value, String profileName) throws UserStoreException {
        try {
            String type = DatabaseCreator.getDatabaseType(dbConnection);
            String sqlStmt = realmConfig
                    .getUserStoreProperty(JDBCRealmConstants.ADD_USER_PROPERTY + "-" + type);
            if (sqlStmt == null) {
                sqlStmt = realmConfig
                        .getUserStoreProperty(JDBCRealmConstants.ADD_USER_PROPERTY);
            }
            if (sqlStmt == null) {
                throw new UserStoreException("The sql statement for add user property sql is null");
            }
            if (UserCoreConstants.OPENEDGE_TYPE.equals(type)) {
                updateStringValuesToDatabase(dbConnection, sqlStmt, propertyName, value, profileName, tenantId, userName, tenantId);
            } else {
                updateStringValuesToDatabase(dbConnection, sqlStmt, userName, tenantId, propertyName,
                        value, profileName, tenantId);
            }
        } catch (UserStoreException e) {
            throw e;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new UserStoreException(e.getMessage(), e);
        }
    }

    protected void updateProperty(Connection dbConnection, String userName, String propertyName,
            String value, String profileName) throws UserStoreException {
        String sqlStmt = realmConfig
                .getUserStoreProperty(JDBCRealmConstants.UPDATE_USER_PROPERTY);
        if (sqlStmt == null) {
            throw new UserStoreException("The sql statement for add user property sql is null");
        }
        updateStringValuesToDatabase(dbConnection, sqlStmt, value, userName, tenantId,
                propertyName, profileName, tenantId);
    }

    protected void deleteProperty(Connection dbConnection, String userName, String propertyName,
            String profileName) throws UserStoreException {
        String sqlStmt = realmConfig
                .getUserStoreProperty(JDBCRealmConstants.DELETE_USER_PROPERTY);
        if (sqlStmt == null) {
            throw new UserStoreException("The sql statement for add user property sql is null");
        }
        updateStringValuesToDatabase(dbConnection, sqlStmt, userName, tenantId, propertyName,
                profileName, tenantId);
    }

    protected String getProperty(Connection dbConnection, String userName, String propertyName,
            String profileName) throws UserStoreException {
        String sqlStmt = realmConfig
                .getUserStoreProperty(JDBCRealmConstants.GET_PROP_FOR_PROFILE);
        if (sqlStmt == null) {
            throw new UserStoreException("The sql statement for add user property sql is null");
        }
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        String value = null;
        try {
            prepStmt = dbConnection.prepareStatement(sqlStmt);
            prepStmt.setString(1, userName);
            prepStmt.setString(2, propertyName);
            prepStmt.setString(3, profileName);
            if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                prepStmt.setInt(4, tenantId);
                prepStmt.setInt(5, tenantId);
            }

            rs = prepStmt.executeQuery();
            while (rs.next()) {
                value = rs.getString(1);
            }
            return value;
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            log.error("Using sql : " + sqlStmt);
            throw new UserStoreException(e.getMessage(), e);
        } finally {
            DatabaseUtil.closeAllConnections(null, rs, prepStmt);
        }
    }

    protected String preparePassword(String password, String saltValue) throws UserStoreException {
        try {
            String digestInput = password;
            if (saltValue != null) {
                digestInput = password + saltValue;
            }
            String digsestFunction = realmConfig.getUserStoreProperties().get(
                    JDBCRealmConstants.DIGEST_FUNCTION);
            if (digsestFunction != null) {

                if (digsestFunction.
                        equals(UserCoreConstants.RealmConfig.PASSWORD_HASH_METHOD_PLAIN_TEXT)) {
                    return password;
                }

                MessageDigest dgst = MessageDigest.getInstance(digsestFunction);
                byte[] byteValue = dgst.digest(digestInput.getBytes());
                password = Base64.encode(byteValue);
            }
            return password;
        } catch (NoSuchAlgorithmException e) {
            log.error(e.getMessage(), e);
            throw new UserStoreException(e.getMessage(), e);
        }
    }

    private void addInitialData() throws UserStoreException {
        boolean isAdminRoleAdded = false;
        if (!isExistingRole(realmConfig.getAdminRoleName())) {
            this.addRole(realmConfig.getAdminRoleName(), null, null);
            isAdminRoleAdded = true;
        }

        if (!isExistingRole(realmConfig.getEveryOneRoleName())) {
            this.addRole(realmConfig.getEveryOneRoleName(), null, null);
        }

        String adminUserName = getAdminUser();
        if (adminUserName != null) {
            realmConfig.setAdminUserName(adminUserName);
        } else {
            if (!isExistingUser(realmConfig.getAdminUserName())) {
                if ("true".equals(realmConfig
                        .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_READ_ONLY))) {
                    log.error("Admin user name is not valid");
                    throw new UserStoreException("Admin user name is not valid");
                }
                // it is not required to notify to the listeners, just persist data.
                this.persistUser(realmConfig.getAdminUserName(), realmConfig.getAdminPassword(),
                        null, null, null, false);
            }
        }

        // use isUserInRole method
        if (isAdminRoleAdded) {
            this.updateRoleListOfUser(realmConfig.getAdminUserName(), null,
                    new String[] { realmConfig.getAdminRoleName() });
        }
     
        // anonymous user and role
        if (!isExistingUser(CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME) && !this.isReadOnly()) {
            byte[] password = new byte[12];
            random.nextBytes(password);
            this.persistUser(CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME, Base64.encode(password),
                    null, null, null, false);

        }
        // if the realm is read only the role will be hybrid
        if (!isExistingRole(CarbonConstants.REGISTRY_ANONNYMOUS_ROLE_NAME)) {
            this.addRole(CarbonConstants.REGISTRY_ANONNYMOUS_ROLE_NAME,
                    new String[] { CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME }, null);
        }

    }

    private DataSource loadUserStoreSpacificDataSoruce() throws UserStoreException {
        return DatabaseUtil.createUserStoreDataSource(realmConfig);
    }

    public Map<String, String> getProperties(Tenant tenant) throws UserStoreException {
        return this.realmConfig.getUserStoreProperties();
    }

    public void addRememberMe(String userName, String token)
            throws org.wso2.carbon.user.api.UserStoreException {
        Connection dbConnection = null;
        try {
            dbConnection = getDBConnection();
            String[] values = DatabaseUtil.getStringValuesFromDatabase(dbConnection,
                    HybridJDBCConstants.GET_REMEMBERME_VALUE_SQL, userName, tenantId);
            Date createdTime = Calendar.getInstance().getTime();
            if (values != null && values.length > 0 && values[0].length() > 0) {
                // udpate
                DatabaseUtil.updateDatabase(dbConnection,
                        HybridJDBCConstants.UPDATE_REMEMBERME_VALUE_SQL, token, createdTime,
                        userName, tenantId);
            } else {
                // add
                DatabaseUtil.updateDatabase(dbConnection,
                        HybridJDBCConstants.ADD_REMEMBERME_VALUE_SQL, userName, token, createdTime,
                        tenantId);
            }
            dbConnection.commit();
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            throw new UserStoreException(e.getMessage(), e);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new UserStoreException(e.getMessage(), e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection);
        }
    }
    
    /**
     * Checks whether the token is existing or not.
     * 
     * @param userName
     * @param token
     * @return
     * @throws org.wso2.carbon.user.api.UserStoreException
     */
    public boolean isExistingRememberMeToken(String userName, String token)
                                                                           throws org.wso2.carbon.user.api.UserStoreException {
        boolean isValid = false;
        Connection dbConnection = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        String value = null;
        Date createdTime = null;
        try {
            dbConnection = getDBConnection();
            prepStmt = dbConnection.prepareStatement(HybridJDBCConstants.GET_REMEMBERME_VALUE_SQL);
            prepStmt.setString(1, userName);
            prepStmt.setInt(2, tenantId);
            rs = prepStmt.executeQuery();
            while (rs.next()) {
                value = rs.getString(1);
                createdTime = rs.getTimestamp(2);
            }
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            log.error("Using sql : " + HybridJDBCConstants.GET_REMEMBERME_VALUE_SQL);
            throw new UserStoreException(e.getMessage(), e);
        } finally {
            DatabaseUtil.closeAllConnections(null, rs, prepStmt);
        }

        if (value != null && createdTime != null) {
            Calendar calendar = Calendar.getInstance();
            Date nowDate = calendar.getTime();
            calendar.setTime(createdTime);
            calendar.add(Calendar.SECOND, CarbonConstants.REMEMBER_ME_COOKIE_TTL);
            Date expDate = calendar.getTime();
            if (expDate.before(nowDate)) {
                // Do nothing remember me expired.
                // Return the user gracefully
                log.debug("Remember me token has expired !!");
            } else {

                // We also need to compare the token
                if (value.equals(token)) {
                    isValid = true;
                } else {
                    log.debug("Remember me token in DB and token in request are different !!");
                    isValid = false;
                }
            }
        }

        return isValid;
    }

    public boolean isValidRememberMeToken(String userName, String token)
                                                                        throws org.wso2.carbon.user.api.UserStoreException {
        try {
            if (isExistingUser(userName)) {
                return isExistingRememberMeToken(userName, token);
            }
        } catch (Exception e) {
            log.error("Validating remember me token failed for" + userName);
            // not throwing exception.
            // because we need to seamlessly direct them to login uis
        }

        return false;
    }

    @Override
    public String[] getUserListFromProperties(String property, String value, String profileName)
                                                                throws UserStoreException {

        if (profileName == null) {
            profileName = UserCoreConstants.DEFAULT_PROFILE;
        }

        String[] users = new String[0];
        Connection dbConnection = null;
        String sqlStmt = null;
        PreparedStatement prepStmt = null;
        ResultSet rs = null;

        List<String> list = new ArrayList<String>();
        try {
            dbConnection = getDBConnection();
            sqlStmt = realmConfig
                    .getUserStoreProperty(JDBCRealmConstants.GET_USERS_FOR_PROP);
            prepStmt = dbConnection.prepareStatement(sqlStmt);
            prepStmt.setString(1, property);
            prepStmt.setString(2, value);
            prepStmt.setString(3, profileName);
            if (sqlStmt.contains(UserCoreConstants.UM_TENANT_COLUMN)) {
                prepStmt.setInt(4, tenantId);
                prepStmt.setInt(5, tenantId);
            }
            rs = prepStmt.executeQuery();
            while (rs.next()) {
                String name = rs.getString(1);
                list.add(name);
            }

            if(list.size() > 0){
                users =  list.toArray(new String[list.size()]);
            }

        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            throw new UserStoreException(e.getMessage(), e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt);
        }

        return users;
    }
}
