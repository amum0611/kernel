/*
 * Copyright (c) WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 * 
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.user.core.ldap;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.CarbonConstants;
import org.wso2.carbon.user.api.RealmConfiguration;
import org.wso2.carbon.user.core.Permission;
import org.wso2.carbon.user.core.UserCoreConstants;
import org.wso2.carbon.user.core.UserRealm;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.claim.ClaimManager;
import org.wso2.carbon.user.core.common.AbstractUserStoreManager;
import org.wso2.carbon.user.core.hybrid.HybridRoleManager;
import org.wso2.carbon.user.core.jdbc.JDBCUserStoreManager;
import org.wso2.carbon.user.core.profile.ProfileConfigurationManager;
import org.wso2.carbon.user.core.tenant.Tenant;
import org.wso2.carbon.user.core.util.DatabaseUtil;
import org.wso2.carbon.user.core.util.JNDIUtil;
import org.wso2.carbon.user.core.util.UserCoreUtil;
import org.wso2.carbon.utils.CarbonUtils;

public class ReadOnlyLDAPUserStoreManager extends AbstractUserStoreManager {

    protected LDAPConnectionContext connectionSource = null;
    protected UserRealm realm = null;
    protected String adminUserName = null;
    protected int tenantID;
    private final int MAX_USER_CACHE = 200;
    private Map<String, String> userCache = new ConcurrentHashMap<String, String>(MAX_USER_CACHE);
    
    protected static final String SERVER_PRINCIPAL_ATTRIBUTE_VALUE = "Service";

    private static Log log = LogFactory.getLog(ReadOnlyLDAPUserStoreManager.class);

    /**
     * Following variables are only used by ApacheDSUserStoreManager. *
     */
    protected boolean readLDAPUserGroups = false;
    protected boolean writeLDAPUserGroups = false;

    protected String userSearchBase = null;
    protected String groupSearchBase = null;

    /*
      * following is by default true since embedded-ldap allows it. If connected
      * to an external ldap
      * where empty roles not allowed, then following property should be set
      * accordingly
      * in user-mgt.xml
      */
    protected boolean emptyRolesAllowed = false;

    /**
     * Adding a default constructor.
     */
    public ReadOnlyLDAPUserStoreManager() {

    }

    /**
     * Constructor with Hybrid Role Manager
     *
     * @param realmConfig
     * @param properties
     * @param claimManager
     * @param profileManager
     * @param realm
     * @param tenantId
     * @throws UserStoreException
     */
    public ReadOnlyLDAPUserStoreManager(RealmConfiguration realmConfig,
                                        Map<String, Object> properties, ClaimManager claimManager,
                                        ProfileConfigurationManager profileManager,
                                        UserRealm realm, Integer tenantId)
            throws UserStoreException {

        if (log.isDebugEnabled()) {
            log.debug("Started " + System.currentTimeMillis());
        }

        this.realmConfig = realmConfig;
        this.claimManager = claimManager;
        this.profileManager = profileManager;
        this.userRealm = realm;
        this.tenantID = tenantId;

        // check if required configurations are in the user-mgt.xml
        checkRequiredUserStoreConfigurations();

        dataSource = (DataSource) properties.get(UserCoreConstants.DATA_SOURCE);
        if (dataSource == null) {
            // avoid returning null
            dataSource = DatabaseUtil.getRealmDataSource(realmConfig);
        }
        if (dataSource == null) {
            throw new UserStoreException("Data Source is null");
        }
        properties.put(UserCoreConstants.DATA_SOURCE, dataSource);

        hybridRoleManager = new HybridRoleManager(dataSource, tenantId, realmConfig, userRealm);
        /*
           * obtain the ldap connection source that was created in
           * DefaultRealmService.
           */
        this.connectionSource =
                (LDAPConnectionContext) properties.get(UserCoreConstants.LDAP_CONNECTION_SOURCE);

        if (connectionSource == null) {
            connectionSource = new LDAPConnectionContext(realmConfig);
        }

        try {
            connectionSource.getContext();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new UserStoreException(
                    "Cannot create connection to Active directory server. Error message " +
                    e.getMessage());
        }
        this.realm = realm;
        this.checkInitialData();
        if (log.isDebugEnabled()) {
            log.debug("Ended " + System.currentTimeMillis());
        }

        this.adminUserName = realmConfig.getAdminUserName();
        /*
           * Initialize user roles cache as implemented in
           * AbstractUserStoreManager
           */
        initUserRolesCache();

    }

    /**
     * This operates in the pure read-only mode without a connection to a
     * database. No handling of Internal roles.
     */
    public ReadOnlyLDAPUserStoreManager(RealmConfiguration realmConfig, ClaimManager claimManager,
                                        ProfileConfigurationManager profileManager)
            throws UserStoreException {

        if (log.isDebugEnabled()) {
            log.debug("Started " + System.currentTimeMillis());
        }
        this.realmConfig = realmConfig;
        this.claimManager = claimManager;
        this.profileManager = profileManager;

        // check if required configurations are in the user-mgt.xml
        checkRequiredUserStoreConfigurations();

        this.connectionSource = new LDAPConnectionContext(realmConfig);
    }

    protected void checkRequiredUserStoreConfigurations() throws UserStoreException {

        log.debug("Checking LDAP configurations ..");

        String maxUserNameListLength =
                realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_MAX_USER_LIST);
        if (maxUserNameListLength == null || maxUserNameListLength.equals("")) {
            throw new UserStoreException(
                    "Required maxUserNameListLength property is not set at the LDAP configurations");
        }
        String connectionURL = realmConfig.getUserStoreProperty(LDAPConstants.CONNECTION_URL);
        if (connectionURL == null || connectionURL.equals("")) {
            throw new UserStoreException(
                    "Required ConnectionURL property is not set at the LDAP configurations");
        }
        String connectionName = realmConfig.getUserStoreProperty(LDAPConstants.CONNECTION_NAME);
        if (connectionName == null || connectionName.equals("")) {
            throw new UserStoreException(
                    "Required ConnectionNme property is not set at the LDAP configurations");
        }
        String connectionPassword =
                realmConfig.getUserStoreProperty(LDAPConstants.CONNECTION_PASSWORD);
        if (connectionPassword == null || connectionPassword.equals("")) {
            throw new UserStoreException(
                    "Required ConnectionPassword property is not set at the LDAP configurations");
        }
        userSearchBase = realmConfig.getUserStoreProperty(LDAPConstants.USER_SEARCH_BASE);
        if (userSearchBase == null || userSearchBase.equals("")) {
            throw new UserStoreException(
                    "Required UserSearchBase property is not set at the LDAP configurations");
        }
        String usernameListFilter =
                realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_LIST_FILTER);
        if (usernameListFilter == null || usernameListFilter.equals("")) {
            throw new UserStoreException(
                    "Required UserNameListFilter property is not set at the LDAP configurations");
        }
        String usernameAttribute =
                realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_ATTRIBUTE);
        if (usernameAttribute == null || usernameAttribute.equals("")) {
            throw new UserStoreException(
                    "Required UserNameAttribute property is not set at the LDAP configurations");
        }
        String readLdapGroups = realmConfig.getUserStoreProperty(LDAPConstants.READ_LDAP_GROUPS);
        if (readLdapGroups == null || readLdapGroups.equals("")) {
            throw new UserStoreException(
                    "Required ReadLDAPGroups property is not set at the LDAP configurations");
        }
        readLDAPUserGroups = Boolean.parseBoolean(readLdapGroups);
        groupSearchBase = realmConfig.getUserStoreProperty(LDAPConstants.GROUP_SEARCH_BASE);
        if (groupSearchBase == null || groupSearchBase.equals("")) {
            throw new UserStoreException(
                    "Required GroupSearchBase property is not set at the LDAP configurations");
        }
        String groupNameListFilter =
                realmConfig.getUserStoreProperty(LDAPConstants.GROUP_NAME_LIST_FILTER);
        if (groupNameListFilter == null || groupNameListFilter.equals("")) {
            throw new UserStoreException(
                    "Required GroupNameListFilter property is not set at the LDAP configurations");
        }
        String groupNameAttribute =
                realmConfig.getUserStoreProperty(LDAPConstants.GROUP_NAME_ATTRIBUTE);
        if (groupNameAttribute == null || groupNameAttribute.equals("")) {
            throw new UserStoreException(
                    "Required GroupNameAttribute property is not set at the LDAP configurations");
        }
        String memebershipAttribute =
                realmConfig.getUserStoreProperty(LDAPConstants.MEMBERSHIP_ATTRIBUTE);
        if (memebershipAttribute == null || memebershipAttribute.equals("")) {
            throw new UserStoreException(
                    "Required MembershipAttribute property is not set at the LDAP configurations");
        }
    }

    public boolean doAuthenticate(String userName, Object credential) throws UserStoreException {

        if (userName == null || credential == null) {
            return false;
        }

        userName = userName.trim();
        // if replace escape characters enabled, modify username by replacing
        // escape characters.
        userName = replaceEscapeCharacters(userName);
        String password = (String) credential;
        password = password.trim();

        if (userName.equals("") || password.equals("")) {
            return false;
        }

        boolean bValue = false;
        String name = null;
        //read list of patterns from user-mgt.xml
        String patterns = realmConfig.getUserStoreProperty(LDAPConstants.USER_DN_PATTERN);

        if (patterns != null && !patterns.isEmpty()) {

            if ((name = userCache.get(userName)) != null) {
                try {
                    bValue = this.bindAsUser(name, (String) credential);
                } catch (NamingException e) {
                    // do nothing if bind fails since we check for other DN patterns as well.
                    if (log.isDebugEnabled()) {
                        log.debug("Checking authentication with UserDN " + name + "failed "
                                  + e.getStackTrace());
                    }
                }

                if (bValue) {
                    return bValue;
                }
            }

            //if the property is present, split it using # to see if there are multiple patterns specified.
            String[] userDNPatternList = patterns.split("#");
            if (userDNPatternList.length > 0) {
                for (String userDNPattern : userDNPatternList) {
                    name = MessageFormat.format(userDNPattern, userName);
                    try {
                        if (name != null) {
                            bValue = this.bindAsUser(name, (String) credential);
                            if (bValue) {
                                userCache.put(userName, name);
                                break;
                            }
                        }
                    } catch (NamingException e) {
                        // do nothing if bind fails since we check for other DN patterns as well.
                        if (log.isDebugEnabled()) {
                            log.debug("Checking authentication with UserDN " + userDNPattern
                                      + "failed " + e.getStackTrace());
                        }
                    }
                }
                if (!bValue) {
                    throw new UserStoreException("User: " + userName + " can not be authenticated. " +
                                                 "Please try again.");
                }

            }
        } else {
            name = getNameInSpaceForUserName(userName);
            try {
                if (name != null) {
                    bValue = this.bindAsUser(name, (String) credential);
                }
            } catch (NamingException e) {
                log.error(e.getMessage(), e);
                throw new UserStoreException(e.getMessage(), e);
            }
        }
        return bValue;
    }

    public String[] getAllProfileNames() throws UserStoreException {
        return new String[]{UserCoreConstants.DEFAULT_PROFILE};
    }

    public String[] getProfileNames(String userName) throws UserStoreException {
        return new String[]{UserCoreConstants.DEFAULT_PROFILE};
    }

    public Map<String, String> getUserPropertyValues(String userName, String[] propertyNames,
                                                     String profileName) throws UserStoreException {
        Map<String, String> values = new HashMap<String, String>();
        String searchFilter = realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_LIST_FILTER);
        String userNameProperty =
                realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_ATTRIBUTE);
        searchFilter = "(&" + searchFilter + "(" + userNameProperty + "=" + userName + "))";

        DirContext dirContext = this.connectionSource.getContext();
        NamingEnumeration<?> answer = null;
        NamingEnumeration<?> attrs = null;
        try {
            answer = this.searchForUser(searchFilter, propertyNames, dirContext);
            while (answer.hasMoreElements()) {
                SearchResult sr = (SearchResult) answer.next();
                Attributes attributes = sr.getAttributes();
                if (attributes != null) {
                    for (String name : propertyNames) {
                        Attribute attribute = attributes.get(name);
                        if (attribute != null) {
                            StringBuffer attrBuffer = new StringBuffer();
                            for (attrs = attribute.getAll(); attrs.hasMore();) {
                                String attr = (String) attrs.next();
                                if (attr != null && attr.trim().length() > 0) {
                                    attrBuffer.append(attr + ",");
                                }
                            }
                            String value = attrBuffer.toString();
                            /*
                                    * Length needs to be more than one for a valid
                                    * attribute, since we attach ",".
                                    */
                            if (value != null && value.trim().length() > 1) {
                                value = value.substring(0, value.length() - 1);
                                values.put(name, value);
                            }
                        }
                    }
                }
            }

        } catch (NamingException e) {
            log.error(e.getMessage(), e);
            throw new UserStoreException(e.getMessage(), e);
        } finally {
            // close the naming enumeration and free up resources
            JNDIUtil.closeNamingEnumeration(attrs);
            JNDIUtil.closeNamingEnumeration(answer);
            // close directory context
            JNDIUtil.closeContext(dirContext);
        }
        return values;
    }

    public String[] getUserRoles(String userName) throws UserStoreException {
        return new String[0];
    }

    public boolean isExistingRole(String roleName) throws UserStoreException {
        boolean isExisting = false;
        if (hybridRoleManager.isExistingRole(roleName)) {
            isExisting = true;
        } else if ("true".equals(realmConfig.getUserStoreProperty(LDAPConstants.READ_LDAP_GROUPS))) {
            String searchFilter =
                    realmConfig.getUserStoreProperty(LDAPConstants.GROUP_NAME_LIST_FILTER);
            String roleNameProperty =
                    realmConfig.getUserStoreProperty(LDAPConstants.GROUP_NAME_ATTRIBUTE);
            searchFilter = "(&" + searchFilter + "(" + roleNameProperty + "=" + roleName + "))";
            String searchBase = realmConfig.getUserStoreProperty(LDAPConstants.GROUP_SEARCH_BASE);

            SearchControls searchCtls = new SearchControls();
            searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            searchCtls.setReturningAttributes(new String[]{roleNameProperty});
            if (this.getListOfNames(searchBase, searchFilter, searchCtls, roleNameProperty).size() > 0) {
                isExisting = true;
            }
        }
        return isExisting;
    }

    public boolean isExistingUser(String userName) throws UserStoreException {

        if (CarbonConstants.REGISTRY_SYSTEM_USERNAME.equals(userName)) {
            return true;
        }

        boolean bFound = false;
        try {
            String name = getNameInSpaceForUserName(userName);
            if (name != null && name.length() > 0) {
                bFound = true;
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new UserStoreException(e.getMessage(), e);
        }

        return bFound;
    }

    public String[] listUsers(String filter, int maxItemLimit) throws UserStoreException {
        String[] userNames = new String[0];

        if (maxItemLimit == 0) {
            return userNames;
        }

        int givenMax =
                Integer.parseInt(realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_MAX_USER_LIST));

        if (maxItemLimit < 0 || maxItemLimit > givenMax) {
            maxItemLimit = givenMax;
        }

        SearchControls searchCtls = new SearchControls();
        searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        searchCtls.setCountLimit(maxItemLimit);

        if (filter.contains("?") || filter.contains("**")) {
            throw new UserStoreException(
                    "Invalid character sequence entered for user serch. Please enter valid sequence.");
        }

        StringBuffer searchFilter = null;
        searchFilter =
                new StringBuffer(
                        realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_LIST_FILTER));
        String searchBase = realmConfig.getUserStoreProperty(LDAPConstants.USER_SEARCH_BASE);

        String userNameProperty =
                realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_ATTRIBUTE);
        StringBuffer buff = new StringBuffer();
        buff.append("(&").append(searchFilter).append("(").append(userNameProperty).append("=")
                .append(filter).append("))");

        String serviceNameAttribute = "sn";
        String returnedAtts[] = {userNameProperty, serviceNameAttribute};

        searchCtls.setReturningAttributes(returnedAtts);
        DirContext dirContext = null;
        NamingEnumeration<SearchResult> answer = null;
        try {
            dirContext = connectionSource.getContext();
            answer = dirContext.search(searchBase, buff.toString(), searchCtls);
            List<String> list = new ArrayList<String>();
            int i = 0;
            while (answer.hasMoreElements() && i < maxItemLimit) {
                SearchResult sr = (SearchResult) answer.next();
                if (sr.getAttributes() != null) {
                    Attribute attr = sr.getAttributes().get(userNameProperty);

                    /*
                          * If this is a service principle, just ignore and iterate
                          * rest of the array. The entity is a service if value of
                          * surname is Service
                          */
                    Attribute attrSurname = sr.getAttributes().get(serviceNameAttribute);

                    if (attrSurname != null) {
                        String serviceName = (String) attrSurname.get();
                        if (serviceName != null &&
                            serviceName.equals(SERVER_PRINCIPAL_ATTRIBUTE_VALUE)) {
                            continue;
                        }
                    }

                    if (attr != null) {
                        String name = (String) attr.get();
                        list.add(name);
                        i++;
                    }
                }
            }
            userNames = list.toArray(new String[list.size()]);
            Arrays.sort(userNames);
        } catch (NamingException e) {
            log.error(e.getMessage(), e);
            throw new UserStoreException(e.getMessage(), e);
        } finally {
            JNDIUtil.closeNamingEnumeration(answer);
            JNDIUtil.closeContext(dirContext);
        }
        return userNames;
    }

    protected boolean bindAsUser(String dn, String credentials) throws NamingException,
                                                                       UserStoreException {
        boolean isAuthed = false;

        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, LDAPConstants.DRIVER_NAME);
        env.put(Context.SECURITY_PRINCIPAL, dn);
        env.put(Context.SECURITY_CREDENTIALS, credentials);
        env.put("com.sun.jndi.ldap.connect.pool", "true");
        /**
         * In carbon JNDI context we need to by pass specific tenant context
         * and we need the base context for LDAP operations.
         */
        env.put(CarbonConstants.REQUEST_BASE_CONTEXT, "true");

        String rawConnectionURL = realmConfig.getUserStoreProperty(LDAPConstants.CONNECTION_URL);
        String portInfo = rawConnectionURL.split(":")[2];

        String connectionURL = null;
        String port = null;
        // if the port contains a template string that refers to carbon.xml
        if ((portInfo.contains("${")) && (portInfo.contains("}"))) {
            port = Integer.toString(CarbonUtils.getPortFromServerConfig(portInfo));
            connectionURL = rawConnectionURL.replace(portInfo, port);
        }
        if (port == null) { // if not enabled, read LDAP url from user.mgt.xml
            connectionURL = realmConfig.getUserStoreProperty(LDAPConstants.CONNECTION_URL);
        }
        env.put(Context.PROVIDER_URL, connectionURL);
        env.put(Context.SECURITY_AUTHENTICATION, "simple");

        LdapContext cxt = null;
        try {
            cxt = new InitialLdapContext(env, null);
            isAuthed = true;
        } catch (AuthenticationException e) {
            /*StringBuilder stringBuilder = new StringBuilder("Authentication failed for user ");
            stringBuilder.append(dn).append(" ").append(e.getMessage());*/

            //we avoid throwing an exception here since we throw that exception in a one level above this.
            if (log.isDebugEnabled()) {
                log.debug(e.getMessage(), e);
                log.debug("Authentication failed " + e.getMessage());
            }
            
        } finally {
            JNDIUtil.closeContext(cxt);
        }
        return isAuthed;
    }

    protected NamingEnumeration<SearchResult> searchForUser(String searchFilter,
                                                            String[] returnedAtts,
                                                            DirContext dirContext)
            throws UserStoreException {
        SearchControls searchCtls = new SearchControls();
        searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        String searchBase = realmConfig.getUserStoreProperty(LDAPConstants.USER_SEARCH_BASE);
        if (returnedAtts != null && returnedAtts.length > 0) {
            searchCtls.setReturningAttributes(returnedAtts);
        }
        try {
            NamingEnumeration<SearchResult> answer =
                    dirContext.search(searchBase, searchFilter,
                                      searchCtls);
            return answer;
        } catch (NamingException e) {
            log.error("Search failed.", e);
            throw new UserStoreException(e.getMessage());
        }
    }

    public void addRole(String roleName, String[] userList, Permission[] permissions)
            throws UserStoreException {
        if (this.isExistingRole(roleName)) {
            throw new UserStoreException(
                    "Duplicate role name in the system. Please pick another name");
        }
        hybridRoleManager.addHybridRole(roleName, userList);
        // if user list is not null, need to clear the userRolesCache for it to
        // get updated next time
        // no need to update the authz cache according to authz cache key
        if ((userList != null) && (userList.length != 0)) {
            clearUserRolesCacheByTenant(this.tenantID);
        }
        if (permissions != null) {
            for (Permission permission : permissions) {
                String resourceId = permission.getResourceId();
                String action = permission.getAction();
                userRealm.getAuthorizationManager().authorizeRole(roleName, resourceId, action);
            }
        }
    }

    public void updateRoleName(String roleName, String newRoleName) throws UserStoreException {
        if (this.isExistingRole(newRoleName)) {
            throw new UserStoreException(
                    "Duplicate role name in the system. Please pick another name");
        }
        hybridRoleManager.updateHybridRoleName(roleName, newRoleName);

        // need to update the userRolesCache after updating role name. Hence,
        // clearing cache
        clearUserRolesCacheByTenant(this.tenantID);

    }

    /**
     * LDAP user store does not support bulk import.
     *
     * @return Always returns <code>false<code>.
     */
    public boolean isBulkImportSupported() {
        return false;
    }

    /**
     * This method is to check whether multiple profiles are allowed with a
     * particular user-store.
     * For an example, currently, JDBC user store supports multiple profiles and
     * where as ApacheDS
     * does not allow.
     * LDAP currently does not allow multiple profiles.
     *
     * @return boolean
     */
    public boolean isMultipleProfilesAllowed() {
        return false; // To change body of implemented methods use File |
        // Settings | File Templates.
    }

    public void deleteRole(String roleName) throws UserStoreException {
        hybridRoleManager.deleteHybridRole(roleName);
        /*
           * need to update the userRolesCache after deleting role. Hence,
           * clearing cache.
           */
        clearUserRolesCacheByTenant(this.tenantID);
    }

    public String[] getRoleNames() throws UserStoreException {
        List<String> externalRoles = new ArrayList<String>();
        if ("true".equals(realmConfig.getUserStoreProperty(LDAPConstants.READ_LDAP_GROUPS))) {
            SearchControls searchCtls = new SearchControls();
            searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);

            String searchFilter =
                    realmConfig.getUserStoreProperty(LDAPConstants.GROUP_NAME_LIST_FILTER);
            String searchBase = realmConfig.getUserStoreProperty(LDAPConstants.GROUP_SEARCH_BASE);

            String roleNameProperty =
                    realmConfig.getUserStoreProperty(LDAPConstants.GROUP_NAME_ATTRIBUTE);
            String returnedAtts[] = {roleNameProperty};
            searchCtls.setReturningAttributes(returnedAtts);
            externalRoles =
                    this.getListOfNames(searchBase, searchFilter, searchCtls,
                                        roleNameProperty);
        }
        String[] internalRoles = hybridRoleManager.getHybridRoles();
        String[] roles = UserCoreUtil.combine(internalRoles, externalRoles);
        return roles;
    }

    public String[] getUserListOfRole(String roleName) throws UserStoreException {
        String[] names = new String[0];
        if (hybridRoleManager.isExistingRole(roleName)) {
            names = hybridRoleManager.getUserListOfHybridRole(roleName);
        } else if ("true".equals(realmConfig.getUserStoreProperty(LDAPConstants.READ_LDAP_GROUPS))) {
            SearchControls searchCtls = new SearchControls();
            searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);

            String searchFilter =
                    realmConfig.getUserStoreProperty(LDAPConstants.GROUP_NAME_LIST_FILTER);
            String roleNameProperty =
                    realmConfig.getUserStoreProperty(LDAPConstants.GROUP_NAME_ATTRIBUTE);
            searchFilter = "(&" + searchFilter + "(" + roleNameProperty + "=" + roleName + "))";
            String searchBase = realmConfig.getUserStoreProperty(LDAPConstants.GROUP_SEARCH_BASE);

            String membershipProperty =
                    realmConfig.getUserStoreProperty(LDAPConstants.MEMBERSHIP_ATTRIBUTE);
            String returnedAtts[] = {membershipProperty};
            searchCtls.setReturningAttributes(returnedAtts);

            List<String> list =
                    this.getAttributeListOfOneElement(searchBase, searchFilter,
                                                      searchCtls);
            names = list.toArray(new String[list.size()]);
        }
        return names;
    }

    /**
     * This method will check whether back link support is enabled and will
     * return the
     * effective search base.
     * Read http://www.frickelsoft.net/blog/?p=130 for more details.
     *
     * @return The search base based on back link support. If back link support
     *         is enabled
     *         this will return user search base, else group search base.
     */
    protected String getEffectiveSearchBase() {

        String backLinksEnabled =
                realmConfig.getUserStoreProperty(LDAPConstants.BACK_LINKS_ENABLED);
        boolean isBackLinkEnabled = false;

        if (backLinksEnabled != null && !backLinksEnabled.equals("")) {
            isBackLinkEnabled = Boolean.parseBoolean(backLinksEnabled);
        }

        if (isBackLinkEnabled) {
            return realmConfig.getUserStoreProperty(LDAPConstants.USER_SEARCH_BASE);
        } else {
            return realmConfig.getUserStoreProperty(LDAPConstants.GROUP_SEARCH_BASE);
        }

    }

    public String[] getRoleListOfUser(String userName) throws UserStoreException {
        String[] roleList = null;
        // check whether roles exist in cache
        try {
            roleList = getRoleListOfUserFromCache(this.tenantID, userName);
            if (roleList != null) {
                return roleList;
            }
        } catch (Exception e) {
            // if not exist in cache, continue
        }
        String[] internalRoles = hybridRoleManager.getHybridRoleListOfUser(userName);
        List<String> list = new ArrayList<String>();
        /*
           * do not search REGISTRY_ANONNYMOUS_USERNAME or
           * REGISTRY_SYSTEM_USERNAME in LDAP because
           * it causes warn logs printed from embedded-ldap.
           */
        if (("true".equals(realmConfig.getUserStoreProperty(LDAPConstants.READ_LDAP_GROUPS))) &&
            (!userName.equals(CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME)) &&
            (!userName.equals(CarbonConstants.REGISTRY_SYSTEM_USERNAME))) {

            SearchControls searchCtls = new SearchControls();
            searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);

            // Get the effective search base
            String searchBase = this.getEffectiveSearchBase();

            String memberOfProperty =
                    realmConfig.getUserStoreProperty(LDAPConstants.MEMBEROF_ATTRIBUTE);
            if (memberOfProperty != null && memberOfProperty.length() > 0) {
                String searchFilter =
                        realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_LIST_FILTER);
                String userNameProperty =
                        realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_ATTRIBUTE);
                searchFilter = "(&" + searchFilter + "(" + userNameProperty + "=" + userName + "))";

                String returnedAtts[] = {memberOfProperty};
                searchCtls.setReturningAttributes(returnedAtts);

                list = this.getAttributeListOfOneElement(searchBase, searchFilter, searchCtls);

            } else {
                // read the roles with this membership property
                String searchFilter =
                        realmConfig.getUserStoreProperty(LDAPConstants.GROUP_NAME_LIST_FILTER);
                String membershipProperty =
                        realmConfig.getUserStoreProperty(LDAPConstants.MEMBERSHIP_ATTRIBUTE);

                if (membershipProperty == null || membershipProperty.length() < 1) {
                    throw new UserStoreException(
                            "Please set member of attribute or membership attribute");
                }
                String nameInSpace = this.getNameInSpaceForUserName(userName);
                searchFilter =
                        "(&" + searchFilter + "(" + membershipProperty + "=" + nameInSpace +
                        "))";
                String roleNameProperty =
                        realmConfig.getUserStoreProperty(LDAPConstants.GROUP_NAME_ATTRIBUTE);
                String returnedAtts[] = {roleNameProperty};
                searchCtls.setReturningAttributes(returnedAtts);
                list = this.getListOfNames(searchBase, searchFilter, searchCtls, roleNameProperty);
            }
        }
        roleList = UserCoreUtil.combine(internalRoles, list);
        // add user roles into userRolesCache
        addToUserRolesCache(this.tenantID, userName, roleList);

        return roleList;
    }

    public boolean isReadOnly() throws UserStoreException {
        return true;
    }

    public String[] getHybridRoles() throws UserStoreException {
        return this.hybridRoleManager.getHybridRoles();
    }

    /**
     * Check whether the basic requirements to initialize and function this
     * UserStoreManager
     * is fulfilled.
     *
     * @throws UserStoreException
     */
    private void checkInitialData() throws UserStoreException {
        if (!isExistingUser(realmConfig.getAdminUserName())) {
            log.error("Carbon cannot function without an Admin Username");
            throw new UserStoreException("Carbon cannot function without an Admin Username");
        }

        if (!isExistingRole(realmConfig.getAdminRoleName())) {
            this.addRole(realmConfig.getAdminRoleName(),
                         new String[]{realmConfig.getAdminUserName()}, null);
        }

        if (!isExistingRole(realmConfig.getEveryOneRoleName())) {
            String[] users = new String[]{realmConfig.getAdminUserName()};
            this.addRole(realmConfig.getEveryOneRoleName(), users, null);
        }

        // anonymous user and role
        if (!isExistingRole(CarbonConstants.REGISTRY_ANONNYMOUS_ROLE_NAME)) {
            this.addRole(CarbonConstants.REGISTRY_ANONNYMOUS_ROLE_NAME,
                         new String[]{CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME}, null);
        }
    }

    protected String getNameInSpaceForUserName(String userName) throws UserStoreException {
        StringBuffer searchFilter =
                new StringBuffer(
                        realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_LIST_FILTER));
        String userNameProperty =
                realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_ATTRIBUTE);
        StringBuffer buff = new StringBuffer();
        buff.append("(&").append(searchFilter).append("(").append(userNameProperty).append("=")
                .append(userName).append("))");

        if (log.isDebugEnabled()) {
            log.debug("Searching for " + buff.toString());
        }
        DirContext dirContext = this.connectionSource.getContext();
        NamingEnumeration<SearchResult> answer = null;
        try {
            String name = null;
            answer = this.searchForUser(buff.toString(), null, dirContext);
            int count = 0;
            SearchResult userObj = null;
            while (answer.hasMoreElements()) {
                SearchResult sr = (SearchResult) answer.next();
                if (count > 0) {
                    log.error("More than one user exist for the same name");
                }
                count++;
                userObj = sr;
            }
            if (userObj != null) {
                name = userObj.getNameInNamespace();
            }

            return name;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new UserStoreException(e.getMessage(), e);
        } finally {
            JNDIUtil.closeNamingEnumeration(answer);
            JNDIUtil.closeContext(dirContext);
        }

    }

    // ****************************************************

    private List<String> getAttributeListOfOneElement(String searchBase, String searchFilter,
                                                      SearchControls searchCtls)
            throws UserStoreException {
        List<String> list = new ArrayList<String>();
        DirContext dirContext = null;
        NamingEnumeration<SearchResult> answer = null;
        try {
            dirContext = connectionSource.getContext();
            answer = dirContext.search(searchBase, searchFilter, searchCtls);
            int count = 0;
            while (answer.hasMore()) {
                if (count > 0) {
                    log.error("More than element user exist with name");
                    throw new UserStoreException("More than element user exist with name");
                }
                SearchResult sr = (SearchResult) answer.next();
                count++;
                Attributes attrs = sr.getAttributes();
                if (attrs != null) {
                    try {
                        NamingEnumeration ae = null;
                        for (ae = attrs.getAll(); ae.hasMore();) {
                            Attribute attr = (Attribute) ae.next();
                            NamingEnumeration e = null;
                            for (e = attr.getAll(); e.hasMore();) {
                                String value = e.next().toString();
                                int begin = value.indexOf('=') + 1;
                                int end = value.indexOf(',');
                                if (begin > -1 && end > -1) {
                                    value = value.substring(begin, end);
                                }
                                list.add(value);
                            }
                            JNDIUtil.closeNamingEnumeration(e);
                        }
                        JNDIUtil.closeNamingEnumeration(ae);
                    } catch (NamingException e) {
                        log.error(e.getMessage(), e);
                    }
                }
            }
        } catch (NamingException e) {
            log.error(e.getMessage(), e);
            throw new UserStoreException(e.getMessage(), e);
        } finally {
            JNDIUtil.closeNamingEnumeration(answer);
            JNDIUtil.closeContext(dirContext);
        }
        return list;
    }

    private List<String> getListOfNames(String searchBase, String searchFilter,
                                        SearchControls searchCtls, String property)
            throws UserStoreException {
        List<String> names = new ArrayList<String>();
        DirContext dirContext = null;
        NamingEnumeration<SearchResult> answer = null;
        try {
            dirContext = connectionSource.getContext();
            answer = dirContext.search(searchBase, searchFilter, searchCtls);
            while (answer.hasMoreElements()) {
                SearchResult sr = (SearchResult) answer.next();
                if (sr.getAttributes() != null) {
                    Attribute attr = sr.getAttributes().get(property);
                    if (attr != null) {
                        String name = (String) attr.get();
                        names.add(name);
                    }
                }
            }
            return names;
        } catch (NamingException e) {
            log.error(e.getMessage(), e);
            throw new UserStoreException(e.getMessage(), e);
        } finally {
            JNDIUtil.closeNamingEnumeration(answer);
            JNDIUtil.closeContext(dirContext);
        }
    }

    public Map<String, String> getProperties(org.wso2.carbon.user.api.Tenant tenant)
            throws org.wso2.carbon.user.api.UserStoreException {
        return getProperties((Tenant) tenant);
    }

    public void addRole(String roleName, String[] userList,
                        org.wso2.carbon.user.api.Permission[] permissions)
            throws org.wso2.carbon.user.api.UserStoreException {
        addRole(roleName, userList, (Permission[]) permissions);

    }

    public int getTenantId() throws UserStoreException {
        return this.tenantID;
    }

    public String[] getUserListFromProperties(String property, String value, String profileName)
            throws UserStoreException {

        List<String> values = new ArrayList<String>();
        String searchFilter = realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_LIST_FILTER);
        String userPropertyName =
                realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_ATTRIBUTE);

        searchFilter = "(&" + searchFilter + "(" + property + "=" + value + "))";

        DirContext dirContext = this.connectionSource.getContext();
        NamingEnumeration<?> answer = null;
        NamingEnumeration<?> attrs = null;
        try {
            answer =
                    this.searchForUser(searchFilter, new String[]{userPropertyName}, dirContext);
            while (answer.hasMoreElements()) {
                SearchResult sr = (SearchResult) answer.next();
                Attributes attributes = sr.getAttributes();
                if (attributes != null) {
                    Attribute attribute = attributes.get(userPropertyName);
                    if (attribute != null) {
                        StringBuffer attrBuffer = new StringBuffer();
                        for (attrs = attribute.getAll(); attrs.hasMore();) {
                            String attr = (String) attrs.next();
                            if (attr != null && attr.trim().length() > 0) {
                                attrBuffer.append(attr + ",");
                            }
                        }
                        String propertyValue = attrBuffer.toString();
                        // Length needs to be more than one for a valid
                        // attribute, since we
                        // attach ",".
                        if (propertyValue != null && propertyValue.trim().length() > 1) {
                            propertyValue = propertyValue.substring(0, propertyValue.length() - 1);
                            values.add(propertyValue);
                        }
                    }
                }
            }

        } catch (NamingException e) {
            log.error(e.getMessage(), e);
            throw new UserStoreException(e.getMessage(), e);
        } finally {
            // close the naming enumeration and free up resources
            JNDIUtil.closeNamingEnumeration(attrs);
            JNDIUtil.closeNamingEnumeration(answer);
            // close directory context
            JNDIUtil.closeContext(dirContext);
        }

        return values.toArray(new String[values.size()]);
    }

    // ************** NOT GOING TO IMPLEMENT ***************

    public Date getPasswordExpirationTime(String username) throws UserStoreException {
        return null;
    }

    public int getTenantId(String username) throws UserStoreException {
        throw new UserStoreException("Invalid operation");
    }

    public int getUserId(String username) throws UserStoreException {
        throw new UserStoreException("Invalid operation");
    }

    public void doDeleteUserClaimValue(String userName, String claimURI, String profileName)
            throws UserStoreException {
        throw new UserStoreException(
                "User store is operating in read only mode. Cannot write into the user store.");

    }

    public void doDeleteUserClaimValues(String userName, String[] claims, String profileName)
            throws UserStoreException {
        throw new UserStoreException(
                "User store is operating in read only mode. Cannot write into the user store.");

    }

    public void doAddUser(String userName, Object credential, String[] roleList,
                          Map<String, String> claims, String profileName)
            throws UserStoreException {
        throw new UserStoreException(
                "User store is operating in read only mode. Cannot write into the user store.");
    }

    public void doAddUser(String userName, Object credential, String[] roleList,
                          Map<String, String> claims, String profileName,
                          boolean requirePasswordChange) throws UserStoreException {
        throw new UserStoreException(
                "User store is operating in read only mode. Cannot write into the user store.");
    }

    public void doDeleteUser(String userName) throws UserStoreException {
        throw new UserStoreException(
                "User store is operating in read only mode. Cannot write into the user store.");
    }

    public void doSetUserClaimValue(String userName, String claimURI, String claimValue,
                                    String profileName) throws UserStoreException {
        throw new UserStoreException(
                "User store is operating in read only mode. Cannot write into the user store.");
    }

    public void doSetUserClaimValues(String userName, Map<String, String> claims,
                                     String profileName)
            throws UserStoreException {
        throw new UserStoreException(
                "User store is operating in read only mode. Cannot write into the user store.");

    }

    public void doUpdateCredential(String userName, Object newCredential, Object oldCredential)
            throws UserStoreException {
        throw new UserStoreException(
                "User store is operating in read only mode. Cannot write into the user store.");
    }

    public void doUpdateCredentialByAdmin(String userName, Object newCredential)
            throws UserStoreException {
        updateCredential(userName, newCredential, null);

    }

    /*
      * ****************Unsupported methods list
      * over***********************************************
      */

    public void doUpdateRoleListOfUser(String userName, String[] deletedRoles, String[] newRoles)
            throws UserStoreException {
        this.hybridRoleManager.updateHybridRoleListOfUser(userName, deletedRoles, newRoles);
        /*
           * once the role list of user is changed, need to update the
           * userRolesCache next time, hence clearing the cache.
           */
        clearUserRolesCacheByTenant(this.tenantID);
    }

    public void doUpdateUserListOfRole(String roleName, String[] deletedUsers, String[] newUsers)
            throws UserStoreException {
        this.hybridRoleManager.updateUserListOfHybridRole(roleName, deletedUsers, newUsers);
        /*
           * once the user list of a role is changed, need to update the
           * userRolesCache, hence clearing the cache.
           */
        clearUserRolesCacheByTenant(this.tenantID);
    }

    public Map<String, String> getProperties(Tenant tenant) throws UserStoreException {
        return this.realmConfig.getUserStoreProperties();
    }

    public void addRememberMe(String userName, String token)
            throws org.wso2.carbon.user.api.UserStoreException {
        JDBCUserStoreManager jdbcUserStore =
                new JDBCUserStoreManager(dataSource, realmConfig,
                                         realmConfig.getTenantId(),
                                         false);
        jdbcUserStore.addRememberMe(userName, token);
    }

    public boolean isValidRememberMeToken(String userName, String token)
            throws org.wso2.carbon.user.api.UserStoreException {
        try {
            if (this.isExistingUser(userName)) {
                JDBCUserStoreManager jdbcUserStore =
                        new JDBCUserStoreManager(
                                dataSource,
                                realmConfig,
                                realmConfig.getTenantId(),
                                false);
                return jdbcUserStore.isExistingRememberMeToken(userName, token);
            }
        } catch (Exception e) {
            log.error("Validating remember me token failed for" + userName);
            /*
                * not throwing exception.
                * because we need to seamlessly direct them to login uis
                */
		}
		return false;
	}

}
