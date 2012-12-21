/*
 * Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import javax.naming.Name;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InvalidAttributeIdentifierException;
import javax.naming.directory.InvalidAttributeValueException;
import javax.naming.directory.NoSuchAttributeException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
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
import org.wso2.carbon.user.core.claim.ClaimMapping;
import org.wso2.carbon.user.core.hybrid.HybridRoleManager;
import org.wso2.carbon.user.core.profile.ProfileConfigurationManager;
import org.wso2.carbon.user.core.tenant.Tenant;
import org.wso2.carbon.user.core.util.DatabaseUtil;
import org.wso2.carbon.user.core.util.JNDIUtil;
import org.wso2.carbon.user.core.util.UserCoreUtil;

/**
 * This class is capable of get connected to an external or internal LDAP based user store in
 * read/write mode. Create, Update, Delete users and groups are supported.
 */
@SuppressWarnings({})
public class ReadWriteLDAPUserStoreManager extends ReadOnlyLDAPUserStoreManager {

    private static Log logger = LogFactory.getLog(ReadWriteLDAPUserStoreManager.class);
    public static final String PASSWORD_HASH_METHOD = "passwordHashMethod";
    public static final String PASSWORD_HASH_METHOD_SHA = "SHA";
    public static final String PASSWORD_HASH_METHOD_MD5 = "MD5";
    public static final String ATTR_NAME_CN = "cn";
    public static final String ATTR_NAME_SN = "sn";

    protected static final String KRB5_PRINCIPAL_NAME_ATTRIBUTE = "krb5PrincipalName";
    protected static final String KRB5_KEY_VERSION_NUMBER_ATTRIBUTE = "krb5KeyVersionNumber";

    /* To track whether this is the first time startup of the server. */
    private static boolean isFirstStartup = true;
    private static boolean isAdminAdded = false;

    private static Log log = LogFactory.getLog(ReadWriteLDAPUserStoreManager.class);

    protected static final String EMPTY_ATTRIBUTE_STRING = "";

    protected Random random = new Random();

    protected boolean kdcEnabled = false;

    public ReadWriteLDAPUserStoreManager(RealmConfiguration realmConfig,
            Map<String, Object> properties, ClaimManager claimManager,
            ProfileConfigurationManager profileManager, UserRealm realm, Integer tenantId)
            throws UserStoreException {

        if (log.isDebugEnabled()) {
            log.debug("Read-Write UserStoreManager initialization started "
                    + System.currentTimeMillis());
        }

        this.realmConfig = realmConfig;
        this.claimManager = claimManager;
        this.profileManager = profileManager;
        this.userRealm = realm;
        this.tenantID = tenantId;
        this.kdcEnabled = UserCoreUtil.isKdcEnabled(realmConfig);

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

        ReadWriteLDAPUserStoreManager.isFirstStartup = (Boolean) properties
                .get(UserCoreConstants.FIRST_STARTUP_CHECK);

        // hybrid role manager used if only users needs to be read-written.
        hybridRoleManager = new HybridRoleManager(dataSource, tenantId, realmConfig, userRealm);

        // obtain the ldap connection source that was created in
        // DefaultRealmService.
        this.connectionSource = (LDAPConnectionContext) properties
                .get(UserCoreConstants.LDAP_CONNECTION_SOURCE);

        if (connectionSource == null) {
            connectionSource = new LDAPConnectionContext(realmConfig);
        }

        try {
            connectionSource.getContext();
            log.info("LDAP connection created successfully in read-write mode");
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new UserStoreException("Cannot create connection to LDAP server. Error message "
                    + e.getMessage());
        }
        this.realm = realm;

        this.addInitialData();

        this.adminUserName = realmConfig.getAdminUserName();
        /*
         * Initialize user roles cache as implemented in AbstractUserStoreManager
         */
        initUserRolesCache();

        if (log.isDebugEnabled()) {
            log.debug("Read-Write UserStoreManager initialization ended "
                    + System.currentTimeMillis());
        }
    }

    /**
     * This constructor is not used. So not applying the changes done to above constructor.
     * 
     * @param realmConfig
     * @param claimManager
     * @param profileManager
     * @throws UserStoreException
     */
    public ReadWriteLDAPUserStoreManager(RealmConfiguration realmConfig, ClaimManager claimManager,
            ProfileConfigurationManager profileManager) throws UserStoreException {
        super(realmConfig, claimManager, profileManager);
        // checkRequiredUserStoreConfiguration();
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    protected String getRealmName() {

        // First check whether realm name is defined in the configuration
        String defaultRealmName = this.realmConfig
                .getUserStoreProperty(UserCoreConstants.RealmConfig.DEFAULT_REALM_NAME);

        if (defaultRealmName != null) {
            return defaultRealmName;
        }

        // If not build the realm name from the search base.
        // Here the realm name will be a concatenation of dc components in the
        // search base.
        String searchBase = this.realmConfig.getUserStoreProperty(LDAPConstants.USER_SEARCH_BASE);

        String[] domainComponents = searchBase.split("dc=");

        StringBuilder builder = new StringBuilder();

        for (String dc : domainComponents) {
            if (!dc.contains("=")) {
                String trimmedDc = dc.trim();
                if (trimmedDc.endsWith(",")) {
                    builder.append(trimmedDc.replace(',', '.'));
                } else {
                    builder.append(trimmedDc);
                }
            }
        }

        return builder.toString().toUpperCase(Locale.ENGLISH);
    }

    @Override
    public void doAddUser(String userName, Object credential, String[] roleList,
            Map<String, String> claims, String profileName) throws UserStoreException {
        this.doAddUser(userName, credential, roleList, claims, profileName, false);
    }

    @Override
    public void doAddUser(String userName, Object credential, String[] roleList,
            Map<String, String> claims, String profileName, boolean requirePasswordChange)
            throws UserStoreException {

        /* validity checks */
        doAddUserValidityChecks(userName, credential);

        /* getting search base directory context */
        DirContext dirContext = getSearchBaseDirectoryContext();

        /* getting add user basic attributes */
        BasicAttributes basicAttributes = getAddUserBasicAttributes(userName);

        BasicAttribute userPassword = new BasicAttribute("userPassword");
        userPassword.add(UserCoreUtil.getPasswordToStore((String) credential,
                this.realmConfig.getUserStoreProperty(PASSWORD_HASH_METHOD), kdcEnabled));
        basicAttributes.put(userPassword);

        /* setting claims */
        setUserClaims(claims, basicAttributes, userName);

        try {

            NameParser ldapParser = dirContext.getNameParser("");
            Name compoundName = ldapParser.parse(realmConfig
                    .getUserStoreProperty(LDAPConstants.USER_NAME_ATTRIBUTE) + "=" + userName);

            dirContext.bind(compoundName, null, basicAttributes);

            /* update the user roles */
            updateUserRoles(userName, roleList);

        } catch (NamingException e) {
            String errorMessage = "Can not access the directory context or "
                    + "user already exists in the system";
            logger.error(errorMessage, e);
            throw new UserStoreException(errorMessage, e);
        } catch (org.wso2.carbon.user.api.UserStoreException e) {
            String errorMessage = "Error in obtaining claim mapping.";
            logger.error(errorMessage, e);
            throw new UserStoreException(errorMessage, e);
        } finally {
            JNDIUtil.closeContext(dirContext);
        }
    }

    /**
     * Does required checks before adding the user
     * 
     * @param userName
     * @param credential
     * @throws UserStoreException
     */
    protected void doAddUserValidityChecks(String userName, Object credential)
            throws UserStoreException {

        if (!checkUserNameValid(userName)) {
            throw new UserStoreException(
                    "User name not valid. User name must be a non null string with following format, "
                            + realmConfig
                                    .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_USER_NAME_JAVA_REG_EX));
        }
        if (!checkUserPasswordValid(credential)) {
            throw new UserStoreException(
                    "Credential not valid. Credential must be a non null string with following format, "
                            + realmConfig
                                    .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_JAVA_REG_EX));
        }
        if (isExistingUser(userName)) {
            throw new UserStoreException("User " + userName + " already exist in the LDAP");
        }
    }

    /**
     * Returns the directory context for the user search base
     * 
     * @return
     * @throws NamingException
     * @throws UserStoreException
     */
    protected DirContext getSearchBaseDirectoryContext() throws UserStoreException {
        DirContext mainDirContext = this.connectionSource.getContext();
        String searchBase = realmConfig.getUserStoreProperty(LDAPConstants.USER_SEARCH_BASE);
        try {
            return (DirContext) mainDirContext.lookup(searchBase);
        } catch (NamingException e) {
            String errorMessage = "Can not access the directory context or"
                    + "user already exists in the system";
            logger.error(errorMessage, e);
            throw new UserStoreException(errorMessage, e);
        } finally {
            JNDIUtil.closeContext(mainDirContext);
        }
    }

    /**
     * Returns a BasicAttributes object with basic required attributes
     * 
     * @param userName
     * @return
     */
    protected BasicAttributes getAddUserBasicAttributes(String userName) {
        BasicAttributes basicAttributes = new BasicAttributes(true);
        String userObjectClass = realmConfig
                .getUserStoreProperty(LDAPConstants.USER_ENTRY_OBJECT_CLASS);
        BasicAttribute objectClass = new BasicAttribute(LDAPConstants.OBJECT_CLASS_NAME);
        objectClass.add(userObjectClass);

        // If KDC is enabled we have to set KDC specific object classes also
        if (kdcEnabled) {
            // Add Kerberos specific object classes
            objectClass.add("krb5principal");
            objectClass.add("krb5kdcentry");
            objectClass.add("subschema");
        }

        basicAttributes.put(objectClass);

        BasicAttribute userNameAttribute = new BasicAttribute(
                realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_ATTRIBUTE));
        userNameAttribute.add(userName);
        basicAttributes.put(userNameAttribute);

        if (kdcEnabled) {
            String principal = userName + "@" + this.getRealmName();

            BasicAttribute principalAttribute = new BasicAttribute(KRB5_PRINCIPAL_NAME_ATTRIBUTE);
            principalAttribute.add(principal);
            basicAttributes.put(principalAttribute);

            BasicAttribute versionNumberAttribute = new BasicAttribute(
                    KRB5_KEY_VERSION_NUMBER_ATTRIBUTE);
            versionNumberAttribute.add("0");
            basicAttributes.put(versionNumberAttribute);
        }
        return basicAttributes;
    }

    /**
     * Sets the set of claims provided at adding users
     * 
     * @param claims
     * @param basicAttributes
     * @throws UserStoreException
     */
    protected void setUserClaims(Map<String, String> claims, BasicAttributes basicAttributes,
            String userName) throws UserStoreException {
        BasicAttribute claim;

        /*
         * we keep boolean values to know whether compulsory attributes 'sn' and 'cn' are set during
         * setting claims.
         */
        boolean isSNExists = false;
        boolean isCNExists = false;

        if (claims != null) {
            for (Map.Entry<String, String> entry : claims.entrySet()) {
                /*
                 * LDAP does not allow for empty values. If an attribute has a value it’s stored
                 * with the entry, otherwise it is not. Hence needs to check for empty values before
                 * storing the attribute.
                 */
                if (EMPTY_ATTRIBUTE_STRING.equals(entry.getValue())) {
                    continue;
                }
                // needs to get attribute name from claim mapping
                String claimURI = entry.getKey();
                ClaimMapping claimMapping = null;
                try {
                    claimMapping = (ClaimMapping) claimManager.getClaimMapping(claimURI);
                } catch (org.wso2.carbon.user.api.UserStoreException e) {
                    String errorMessage = "Error in obtaining claim mapping.";
                    logger.error(errorMessage, e);
                    throw new UserStoreException(errorMessage, e);
                }

                String attributeName;
                if (claimMapping != null) {
                    attributeName = claimMapping.getMappedAttribute();
                    if (ATTR_NAME_CN.equals(attributeName)) {
                        isCNExists = true;
                    } else if (ATTR_NAME_SN.equals(attributeName)) {
                        isSNExists = true;
                    }
                } else {
                    attributeName = claimURI;
                }
                claim = new BasicAttribute(attributeName);
                claim.add(claims.get(entry.getKey()));
                basicAttributes.put(claim);
            }
        }

        // If required attributes cn, sn are not set during claim mapping,
        // set them as user names

        if (!isCNExists) {
            BasicAttribute cn = new BasicAttribute("cn");
            cn.add(userName);
            basicAttributes.put(cn);
        }

        if (!isSNExists) {
            BasicAttribute sn = new BasicAttribute("sn");
            sn.add(userName);
            basicAttributes.put(sn);
        }
    }

    /**
     * Updates user roles when adding a user
     * 
     * @param userName
     * @param roleList
     * @throws UserStoreException
     */
    protected void updateUserRoles(String userName, String[] roleList) throws UserStoreException {
        if (roleList != null && roleList.length != 0) {
            for (String role : roleList) {
                if (readLDAPUserGroups && writeLDAPUserGroups) {
                    if (!isInternalRole(role)) {
                        this.updateRoleListOfUser(userName, new String[] {}, new String[] { role });
                    } else if (isInternalRole(role)) {
                        this.hybridRoleManager.addHybridRole(role, new String[] { userName });
                        clearUserRolesCacheByTenant(this.tenantID);
                    }
                }
                if (!readLDAPUserGroups && !writeLDAPUserGroups || readLDAPUserGroups
                        && !writeLDAPUserGroups) {
                    this.hybridRoleManager.addHybridRole(role, new String[] { userName });
                    clearUserRolesCacheByTenant(this.tenantID);
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void doDeleteUser(String userName) throws UserStoreException {

        if (!readLDAPUserGroups && !writeLDAPUserGroups || readLDAPUserGroups
                && !writeLDAPUserGroups) {
            if (this.hybridRoleManager != null) {
                this.hybridRoleManager.deleteUser(userName);
            }
        }
        // delete user from LDAP group if read-write enabled.
        String userNameAttribute = realmConfig
                .getUserStoreProperty(LDAPConstants.USER_NAME_ATTRIBUTE);
        String searchFilter = realmConfig
                .getUserStoreProperty(LDAPConstants.USER_NAME_SEARCH_FILTER);
        searchFilter = searchFilter.replace("?", userName);
        String[] returningUserAttributes = new String[] { userNameAttribute };

        DirContext mainDirContext = this.connectionSource.getContext();

        NamingEnumeration<SearchResult> userResults = searchInUserBase(searchFilter,
                returningUserAttributes, SearchControls.SUBTREE_SCOPE, mainDirContext);
        NamingEnumeration<SearchResult> groupResults = null;

        DirContext subDirContext = null;
        try {
            SearchResult userResult = null;
            String userDN = null;
            // here we assume only one user
            // TODO: what to do if there are more than one user
            while (userResults.hasMore()) {
                userResult = userResults.next();
                userDN = userResult.getName();
            }

            if (readLDAPUserGroups && writeLDAPUserGroups) {
                String[] rolesOfUser = getRoleListOfUser(userName);

                if (rolesOfUser.length != 0) {
                    String roleNameSearchFilter = realmConfig
                            .getUserStoreProperty(LDAPConstants.ROLE_NAME_FILTER);
                    String[] returningGroupAttributes = new String[] { realmConfig
                            .getUserStoreProperty(LDAPConstants.MEMBERSHIP_ATTRIBUTE) };
                    for (String role : rolesOfUser) {
                        if (!isInternalRole(role)) {
                            String groupSearchFilter = roleNameSearchFilter.replace("?", role);
                            groupResults = searchInGroupBase(groupSearchFilter,
                                    returningGroupAttributes, SearchControls.SUBTREE_SCOPE,
                                    mainDirContext);
                            SearchResult groupResult = null;
                            while (groupResults.hasMore()) {
                                groupResult = groupResults.next();
                            }
                            if (isOnlyUserInRole(userDN, groupResult) && !emptyRolesAllowed) {
                                String errorMessage = "User: " + userName + " is the only user "
                                        + "in " + role + "." + "There should be at "
                                        + "least one user" + " in the role. Hence can"
                                        + " not delete the user.";
                                logger.error(errorMessage);
                                throw new UserStoreException(errorMessage);
                            }

                        }
                    }
                    for (String role : rolesOfUser) {
                        // no need to update entries related to internal roles
                        // when deleting user.
                        if (!isInternalRole(role)) {
                            updateRoleListOfUser(userName, new String[] { role }, new String[] {});
                        } else if (isInternalRole(role)) {
                            hybridRoleManager.deleteUser(userName);
                        }
                    }
                }
            }
            // delete user entry
            if (userResult.getAttributes().get(userNameAttribute).get().equals(userName)) {
                subDirContext = (DirContext) mainDirContext.lookup(userSearchBase);
                subDirContext.destroySubcontext(userDN);
            }
            // needs to clear authz cache of user
            this.userRealm.getAuthorizationManager().clearUserAuthorization(userName);
            // needs to clear roles cache upon deletion of a user
            clearUserRolesCacheByTenant(this.tenantID);

        } catch (NamingException e) {
            String errorMessage = "Error occurred while deleting the user. ";
            logger.error(errorMessage, e);
            throw new UserStoreException(errorMessage, e);
        } finally {
            JNDIUtil.closeNamingEnumeration(groupResults);
            JNDIUtil.closeNamingEnumeration(userResults);

            JNDIUtil.closeContext(subDirContext);
            JNDIUtil.closeContext(mainDirContext);
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void doUpdateCredential(String userName, Object newCredential, Object oldCredential)
            throws UserStoreException {

        /* validity checks */
        doUpdateCredentialsValidityChecks(userName, newCredential);

        DirContext dirContext = this.connectionSource.getContext();
        DirContext subDirContext = null;
        // first search the existing user entry.
        String searchBase = realmConfig.getUserStoreProperty(LDAPConstants.USER_SEARCH_BASE);
        String searchFilter = realmConfig
                .getUserStoreProperty(LDAPConstants.USER_NAME_SEARCH_FILTER);
        searchFilter = searchFilter.replace("?", userName);

        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        searchControls.setReturningAttributes(new String[] { "userPassword" });

        NamingEnumeration<SearchResult> namingEnumeration = null;
        NamingEnumeration passwords = null;

        try {
            namingEnumeration = dirContext.search(searchBase, searchFilter, searchControls);
            // here we assume only one user
            // TODO: what to do if there are more than one user
            SearchResult searchResult = null;
            while (namingEnumeration.hasMore()) {
                searchResult = namingEnumeration.next();
                Attributes attributes = searchResult.getAttributes();
                Attribute userPassword = attributes.get("userPassword");

                String passwordHashMethod = realmConfig.getUserStoreProperty(PASSWORD_HASH_METHOD);
                // when admin changes other user passwords he do not have to
                // provide
                // the old password.
                if (oldCredential != null) {
                    // here it is only possible to have one password, if there
                    // are more
                    // every one should match with the given old password
                    passwords = userPassword.getAll();
                    if (passwords.hasMore()) {
                        byte[] byteArray = (byte[]) passwords.next();
                        String password = new String(byteArray);

                        if (password.startsWith("{")) {
                            passwordHashMethod = password.substring(password.indexOf('{') + 1,
                                    password.indexOf('}'));
                        }

                        if (!password.equals(UserCoreUtil.getPasswordToStore(
                                (String) oldCredential, passwordHashMethod, kdcEnabled))) {
                            throw new UserStoreException("Old password does not match");
                        }
                    }
                }

                String dnName = searchResult.getName();
                subDirContext = (DirContext) dirContext.lookup(searchBase);

                Attribute passwordAttribute = new BasicAttribute("userPassword");
                passwordAttribute.add(UserCoreUtil.getPasswordToStore((String) newCredential,
                        passwordHashMethod, kdcEnabled));
                BasicAttributes basicAttributes = new BasicAttributes(true);
                basicAttributes.put(passwordAttribute);
                subDirContext.modifyAttributes(dnName, DirContext.REPLACE_ATTRIBUTE,
                        basicAttributes);
            }
            // we check whether both carbon admin entry and ldap connection
            // entry are the same
            if (searchResult.getNameInNamespace().equals(
                    realmConfig.getUserStoreProperty(LDAPConstants.CONNECTION_NAME))) {
                this.connectionSource.updateCredential((String) newCredential);
            }

        } catch (NamingException e) {
            throw new UserStoreException("Can not access the directory service", e);
        } finally {
            JNDIUtil.closeNamingEnumeration(passwords);
            JNDIUtil.closeNamingEnumeration(namingEnumeration);

            JNDIUtil.closeContext(subDirContext);
            JNDIUtil.closeContext(dirContext);
        }
    }

    protected void doUpdateCredentialsValidityChecks(String userName, Object newCredential)
            throws UserStoreException {
        if (!isExistingUser(userName)) {
            throw new UserStoreException("User " + userName + " does not exisit in the user store");
        }

        if (!checkUserPasswordValid(newCredential)) {
            throw new UserStoreException(
                    "Credential not valid. Credential must be a non null string with following format, "
                            + realmConfig
                                    .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_JAVA_REG_EX));
        }
    }

    @Override
    public Map<String, String> getProperties(Tenant tenant) throws UserStoreException {
        Map<String, String> existingProperties = this.realmConfig.getUserStoreProperties();
        String tenantSufix = getTenantSuffix(tenant.getDomain());
        String propertyName = null;
        Map<String, String> newProperties = new HashMap<String, String>();
        for (Map.Entry<String, String> iter : existingProperties.entrySet()) {
            propertyName = iter.getKey();
            if (propertyName.equals(LDAPConstants.USER_SEARCH_BASE)) {
                newProperties.put(propertyName, tenantSufix);
            } else {
                newProperties.put(propertyName, iter.getValue());
            }
        }
        return newProperties;
    }

    private String getTenantSuffix(String domain) {
        // here we use a simple algorithum by splitting the domain with .
        String[] domainParts = domain.split("\\.");
        StringBuffer suffixName = new StringBuffer();
        for (String domainPart : domainParts) {
            suffixName.append(",dc=").append(domainPart);
        }
        return suffixName.toString().replaceFirst(",", "");
    }

    /**
     * This method overwrites the method in LDAPUserStoreManager. This implements the functionality
     * of updating user's profile information in LDAP user store.
     * 
     * @param userName
     * @param claims
     * @param profileName
     * @throws UserStoreException
     */
    @Override
    public void doSetUserClaimValues(String userName, Map<String, String> claims, String profileName)
            throws UserStoreException {

        // get the LDAP Directory context
        DirContext dirContext = this.connectionSource.getContext();
        DirContext subDirContext = null;
        // search the relevant user entry by user name
        String userSearchBase = realmConfig.getUserStoreProperty(LDAPConstants.USER_SEARCH_BASE);
        String userSearchFilter = realmConfig
                .getUserStoreProperty(LDAPConstants.USER_NAME_SEARCH_FILTER);
        userSearchFilter = userSearchFilter.replace("?", userName);

        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        searchControls.setReturningAttributes(null);

        NamingEnumeration<SearchResult> returnedResultList = null;
        String returnedUserEntry = null;

        try {

            returnedResultList = dirContext
                    .search(userSearchBase, userSearchFilter, searchControls);
            // assume only one user is returned from the search
            // TODO:what if more than one user is returned
            returnedUserEntry = returnedResultList.next().getName();

        } catch (NamingException e) {

            throw new UserStoreException("Results could not be retrieved from the directory "
                    + "context", e);
        } finally {
            JNDIUtil.closeNamingEnumeration(returnedResultList);
        }

        if (profileName == null) {

            profileName = UserCoreConstants.DEFAULT_PROFILE;
        }

        if (claims.get(UserCoreConstants.PROFILE_CONFIGURATION) == null) {

            claims.put(UserCoreConstants.PROFILE_CONFIGURATION,
                    UserCoreConstants.DEFAULT_PROFILE_CONFIGURATION);
        }
        try {
            Attributes updatedAttributes = new BasicAttributes(true);
            for (Map.Entry<String, String> claimEntry : claims.entrySet()) {
                String claimURI = claimEntry.getKey();
                // if there is no attribute for profile configuration in LDAP,
                // skip updating it.
                if (claimURI.equals(UserCoreConstants.PROFILE_CONFIGURATION)) {
                    continue;
                }
                // get the claimMapping related to this claimURI
                ClaimMapping claimMapping = (ClaimMapping) claimManager.getClaimMapping(claimURI);
                String attributeName = null;

                if (claimMapping != null) {
                    attributeName = claimMapping.getMappedAttribute();
                } else {
                    attributeName = claimURI;
                }
                Attribute currentUpdatedAttribute = new BasicAttribute(attributeName);
                /* if updated attribute value is null, remove its values. */
                if (EMPTY_ATTRIBUTE_STRING.equals(claimEntry.getValue())) {
                    currentUpdatedAttribute.clear();
                } else {
                    if (claimEntry.getValue() != null && claimEntry.getValue().contains(",")) {
                        String[] values = claimEntry.getValue().split(",");
                        for (String newValue : values) {
                            if (newValue != null && newValue.trim().length() > 0) {
                                currentUpdatedAttribute.add(newValue.trim());
                            }
                        }
                    } else {
                        currentUpdatedAttribute.add(claimEntry.getValue());
                    }
                }
                updatedAttributes.put(currentUpdatedAttribute);
            }
            // update the attributes in the relevant entry of the directory
            // store

            subDirContext = (DirContext) dirContext.lookup(userSearchBase);
            subDirContext.modifyAttributes(returnedUserEntry, DirContext.REPLACE_ATTRIBUTE,
                    updatedAttributes);

        } catch (InvalidAttributeValueException e) {
            String errorMessage = "One or more attribute values provided are incompatible. "
                    + "Please check and try again.";
            logger.error(errorMessage, e);
            throw new UserStoreException(errorMessage, e);
        } catch (InvalidAttributeIdentifierException e) {
            String errorMessage = "One or more attributes you are trying to add/update are not "
                    + "supported by underlying LDAP.";
            logger.error(errorMessage, e);
            throw new UserStoreException(errorMessage, e);

        } catch (NoSuchAttributeException e) {
            String errorMessage = "One or more attributes you are trying to add/update are not "
                    + "supported by underlying LDAP.";
            logger.error(errorMessage, e);
            throw new UserStoreException(errorMessage, e);

        } catch (NamingException e) {
            String errorMessage = "Profile information could not be updated in ApacheDS "
                    + "LDAP user store";
            logger.error(errorMessage, e);
            throw new UserStoreException(errorMessage, e);

        } catch (org.wso2.carbon.user.api.UserStoreException e) {
            String errorMessage = "Error in obtaining claim mapping.";
            logger.error(errorMessage, e);
            throw new UserStoreException(errorMessage, e);
        } finally {
            JNDIUtil.closeContext(subDirContext);
            JNDIUtil.closeContext(dirContext);
        }

    }

    @Override
    public void doSetUserClaimValue(String userName, String claimURI, String value,
            String profileName) throws UserStoreException {

        // get the LDAP Directory context
        DirContext dirContext = this.connectionSource.getContext();
        DirContext subDirContext = null;
        // search the relevant user entry by user name
        String userSearchBase = realmConfig.getUserStoreProperty(LDAPConstants.USER_SEARCH_BASE);
        String userSearchFilter = realmConfig
                .getUserStoreProperty(LDAPConstants.USER_NAME_SEARCH_FILTER);
        userSearchFilter = userSearchFilter.replace("?", userName);

        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        searchControls.setReturningAttributes(null);

        NamingEnumeration<SearchResult> returnedResultList = null;
        String returnedUserEntry = null;

        try {

            returnedResultList = dirContext
                    .search(userSearchBase, userSearchFilter, searchControls);
            // assume only one user is returned from the search
            // TODO:what if more than one user is returned
            returnedUserEntry = returnedResultList.next().getName();

        } catch (NamingException e) {

            throw new UserStoreException("Results could not be retrieved from the directory "
                    + "context", e);
        } finally {
            JNDIUtil.closeNamingEnumeration(returnedResultList);
        }

        try {
            Attributes updatedAttributes = new BasicAttributes(true);
            // if there is no attribute for profile configuration in LDAP, skip
            // updating it.
            // get the claimMapping related to this claimURI
            ClaimMapping claimMapping = (ClaimMapping) claimManager.getClaimMapping(claimURI);
            String attributeName = null;

            if (claimMapping != null) {
                attributeName = claimMapping.getMappedAttribute();
            } else {
                attributeName = claimURI;
            }
            Attribute currentUpdatedAttribute = new BasicAttribute(attributeName);
            /* if updated attribute value is null, remove its values. */
            if (EMPTY_ATTRIBUTE_STRING.equals(value)) {
                currentUpdatedAttribute.clear();
            } else {
                if (value.contains(",")) {
                    String[] values = value.split(",");
                    for (String newValue : values) {
                        if (newValue != null && newValue.trim().length() > 0) {
                            currentUpdatedAttribute.add(newValue.trim());
                        }
                    }
                } else {
                    currentUpdatedAttribute.add(value);
                }
            }
            updatedAttributes.put(currentUpdatedAttribute);

            // update the attributes in the relevant entry of the directory
            // store

            subDirContext = (DirContext) dirContext.lookup(userSearchBase);
            subDirContext.modifyAttributes(returnedUserEntry, DirContext.REPLACE_ATTRIBUTE,
                    updatedAttributes);

        } catch (InvalidAttributeValueException e) {
            String errorMessage = "One or more attribute values provided are incompatible. "
                    + "Please check and try again.";
            logger.error(errorMessage, e);
            throw new UserStoreException(errorMessage, e);
        } catch (InvalidAttributeIdentifierException e) {
            String errorMessage = "One or more attributes you are trying to add/update are not "
                    + "supported by underlying LDAP.";
            logger.error(errorMessage, e);
            throw new UserStoreException(errorMessage, e);

        } catch (NoSuchAttributeException e) {
            String errorMessage = "One or more attributes you are trying to add/update are not "
                    + "supported by underlying LDAP.";
            logger.error(errorMessage, e);
            throw new UserStoreException(errorMessage, e);

        } catch (NamingException e) {
            String errorMessage = "Profile information could not be updated in ApacheDS "
                    + "LDAP user store";
            logger.error(errorMessage, e);
            throw new UserStoreException(errorMessage, e);

        } catch (org.wso2.carbon.user.api.UserStoreException e) {
            String errorMessage = "Error in obtaining claim mapping.";
            logger.error(errorMessage, e);
            throw new UserStoreException(errorMessage, e);
        } finally {
            JNDIUtil.closeContext(subDirContext);
            JNDIUtil.closeContext(dirContext);
        }

    }

    /**
     * Add roles by writing groups to LDAP.
     * 
     * @param roleName
     * @param userList
     * @param permissions
     * @throws UserStoreException
     */
    @Override
    public void doAddRole(String roleName, String[] userList, Permission[] permissions)
            throws UserStoreException {

        // internal roles are managed in UM_DB or code.
        if (isInternalRole(roleName) || !readLDAPUserGroups && !writeLDAPUserGroups) {
            // hybridRoleManager.addHybridRole(roleName, userList);
            super.doAddRole(roleName, userList, permissions);
            return;
        }
        // check group read-write settings and execute super method as
        // appropriate.
        if (readLDAPUserGroups && !writeLDAPUserGroups) {
            super.doAddRole(roleName, userList, permissions);
            return;
        }
        if (readLDAPUserGroups && writeLDAPUserGroups) {
            // check whether role name is valid.
            if (!roleNameValid(roleName)) {
                String roleNameRegEx = realmConfig
                        .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_ROLE_NAME_JAVA_REG_EX);
                String errorMsg = "Role name not valid. Role name must be a non null string "
                        + "with following format, " + roleNameRegEx;

                logger.error(errorMsg);
                throw new UserStoreException(errorMsg);
            }
            if (isExistingRole(roleName)) {
                String errorMessage = "Can not add the role. Role name: " + roleName
                        + " already exists.";
                logger.error(errorMessage);
                throw new UserStoreException(errorMessage);
            } else {
                /*
                 * there should be at least one member for an LDAP group if emptyRolesAllowed is
                 * false.
                 */
                if ((userList == null || userList.length == 0) && !emptyRolesAllowed) {
                    String errorMessage = "Can not create empty role. There should be at least "
                            + "one user for the role.";
                    logger.error(errorMessage);
                    throw new UserStoreException(errorMessage);

                } else if (userList == null && emptyRolesAllowed || userList != null
                        && userList.length > 0 && !emptyRolesAllowed || emptyRolesAllowed) {
                    // if (userList.length > 0) {
                    DirContext mainDirContext = this.connectionSource.getContext();
                    DirContext groupContext = null;
                    NamingEnumeration<SearchResult> results = null;

                    try {
                        // create the attribute set for group entry
                        Attributes groupAttributes = new BasicAttributes(true);

                        // create group entry's object class attribute
                        Attribute objectClassAttribute = new BasicAttribute(
                                LDAPConstants.OBJECT_CLASS_NAME);
                        objectClassAttribute.add(realmConfig
                                .getUserStoreProperty(LDAPConstants.GROUP_ENTRY_OBJECT_CLASS));
                        groupAttributes.put(objectClassAttribute);

                        // create cn attribute
                        String groupNameAttributeName = realmConfig.getUserStoreProperties().get(
                                LDAPConstants.GROUP_NAME_ATTRIBUTE);
                        Attribute cnAttribute = new BasicAttribute(groupNameAttributeName);
                        cnAttribute.add(roleName);
                        groupAttributes.put(cnAttribute);
                        // following check is for if emptyRolesAllowed made this
                        // code executed.
                        if (userList != null && userList.length > 0) {
                            String memberAttributeName = realmConfig
                                    .getUserStoreProperty(LDAPConstants.MEMBERSHIP_ATTRIBUTE);
                            Attribute memberAttribute = new BasicAttribute(memberAttributeName);
                            for (String userName : userList) {
                                // search the user in user search base
                                String searchFilter = realmConfig
                                        .getUserStoreProperty(LDAPConstants.USER_NAME_SEARCH_FILTER);
                                searchFilter = searchFilter.replace("?", userName);
                                results = searchInUserBase(searchFilter, new String[] {},
                                        SearchControls.SUBTREE_SCOPE, mainDirContext);
                                // we assume only one user with the given user
                                // name under user search base.
                                SearchResult userResult = null;
                                if (results.hasMore()) {
                                    userResult = results.next();
                                } else {
                                    String errorMsg = "There is no user with the user name: "
                                            + userName + " to be added to this role.";
                                    logger.error(errorMsg);
                                    throw new UserStoreException(errorMsg);
                                }
                                // get his DN
                                String userEntryDN = userResult.getNameInNamespace();
                                // put it as member-attribute value
                                memberAttribute.add(userEntryDN);
                            }
                            groupAttributes.put(memberAttribute);
                        }

                        groupContext = (DirContext) mainDirContext.lookup(groupSearchBase);

                        NameParser ldapParser = groupContext.getNameParser("");
                        Name compoundGroupName = ldapParser.parse(groupNameAttributeName + "="
                                + roleName);

                        groupContext.bind(compoundGroupName, null, groupAttributes);

                        // if existing users are added to role, need to update
                        // user role cache
                        if (userList != null && userList.length > 0) {
                            clearUserRolesCacheByTenant(this.tenantID);
                        }

                        // if permissions are assigned to role, add permission.
                        if (permissions != null) {
                            for (Permission permission : permissions) {
                                String resourceId = permission.getResourceId();
                                String action = permission.getAction();
                                userRealm.getAuthorizationManager().authorizeRole(roleName,
                                        resourceId, action);
                            }
                        }

                    } catch (NamingException e) {
                        String errorMsg = "Role: " + roleName + "could not be added.";
                        logger.error(errorMsg, e);
                        throw new UserStoreException(errorMsg, e);

                    } catch (Exception e) {
                        String errorMsg = "Role: " + roleName + "could not be added.";
                        logger.error(errorMsg, e);
                        throw new UserStoreException(errorMsg, e);
                    } finally {
                        JNDIUtil.closeNamingEnumeration(results);
                        JNDIUtil.closeContext(groupContext);
                        JNDIUtil.closeContext(mainDirContext);
                    }

                }

            }

        }
    }

    /**
     * Update role list of user by writting to LDAP.
     * 
     * @param userName
     * @param deletedRoles
     * @param newRoles
     * @throws UserStoreException
     */
    @SuppressWarnings("deprecation")
    @Override
    public void doUpdateRoleListOfUser(String userName, String[] deletedRoles, String[] newRoles)
            throws UserStoreException {

        if (!readLDAPUserGroups && !writeLDAPUserGroups || readLDAPUserGroups
                && !writeLDAPUserGroups) {
            // hybridRoleManager.updateHybridRoleListOfUser(userName,
            // deletedRoles, newRoles);
            super.updateRoleListOfUser(userName, deletedRoles, newRoles);
        }
        if (readLDAPUserGroups && writeLDAPUserGroups) {
            // check whether everyone role is trying to be deleted,
            if (deletedRoles != null) {
                Arrays.sort(deletedRoles);
                if (Arrays.binarySearch(deletedRoles, realmConfig.getEveryOneRoleName()) > -1) {
                    log.error("An attempt to remove " + userName + " user from Everyone role ");
                    throw new UserStoreException("Everyone role is not updatable");
                }
            }
            // check whether admin role is trying to be deleted from admin user.
            if (deletedRoles != null) {
                Arrays.sort(deletedRoles);
                if (realmConfig.getAdminUserName().equals(userName)
                        && Arrays.binarySearch(deletedRoles, realmConfig.getAdminRoleName()) > -1) {
                    log.error("An attempt to remove Admin user from Admin role ");
                    throw new UserStoreException("Cannot remove Admin user from Admin role");
                }
            }
            // get the DN of the user entry
            String userNameDN = this.getNameInSpaceForUserName(userName);
            String membershipAttribute = realmConfig
                    .getUserStoreProperty(LDAPConstants.MEMBERSHIP_ATTRIBUTE);

            /*
             * check deleted roles and delete member entries from relevant groups.
             */
            String errorMessage = null;
            String searchFilter = realmConfig.getUserStoreProperty(LDAPConstants.ROLE_NAME_FILTER);
            String roleSearchFilter = null;

            DirContext mainDirContext = this.connectionSource.getContext();

            try {
                if (deletedRoles != null && deletedRoles.length != 0) {
                    // perform validation for empty role occurrences before
                    // updating in LDAP
                    for (String deletedRole : deletedRoles) {
                        roleSearchFilter = searchFilter.replace("?", deletedRole);
                        String[] returningAttributes = new String[] { membershipAttribute };

                        NamingEnumeration<SearchResult> groupResults = searchInGroupBase(
                                roleSearchFilter, returningAttributes,
                                SearchControls.SUBTREE_SCOPE, mainDirContext);
                        SearchResult resultedGroup = null;
                        if (groupResults.hasMore()) {
                            resultedGroup = groupResults.next();
                        }
                        if (resultedGroup != null && isOnlyUserInRole(userNameDN, resultedGroup)
                                && !emptyRolesAllowed) {
                            errorMessage = userName + " is the only user in the role: "
                                    + deletedRole + ". Hence can not delete user from role.";
                            logger.error(errorMessage);
                            throw new UserStoreException(errorMessage);
                        }

                        JNDIUtil.closeNamingEnumeration(groupResults);
                    }
                    // if empty role violation does not happen, continue
                    // updating the LDAP.
                    for (String deletedRole : deletedRoles) {
                        if (isExistingRole(deletedRole)) {
                            roleSearchFilter = searchFilter.replace("?", deletedRole);
                            String[] returningAttributes = new String[] { membershipAttribute };

                            NamingEnumeration<SearchResult> groupResults = searchInGroupBase(
                                    roleSearchFilter, returningAttributes,
                                    SearchControls.SUBTREE_SCOPE, mainDirContext);
                            SearchResult resultedGroup = null;
                            String groupDN = null;
                            if (groupResults.hasMore()) {
                                resultedGroup = groupResults.next();
                                groupDN = resultedGroup.getName();
                            }
                            this.modifyUserInRole(userNameDN, groupDN, DirContext.REMOVE_ATTRIBUTE);

                            JNDIUtil.closeNamingEnumeration(groupResults);

                            // need to update authz cache of user since roles
                            // are deleted
                            userRealm.getAuthorizationManager().clearUserAuthorization(userName);

                        } else {
                            errorMessage = "The role: " + deletedRole + " does not exist.";
                            logger.error(errorMessage);
                            throw new UserStoreException(errorMessage);
                        }
                    }
                }
                if (newRoles.length != 0) {
                    for (String newRole : newRoles) {
                        if (isExistingRole(newRole)) {
                            roleSearchFilter = searchFilter.replace("?", newRole);
                            String[] returningAttributes = new String[] { membershipAttribute };

                            NamingEnumeration<SearchResult> groupResults = searchInGroupBase(
                                    roleSearchFilter, returningAttributes,
                                    SearchControls.SUBTREE_SCOPE, mainDirContext);
                            SearchResult resultedGroup = null;
                            // assume only one group with given group name
                            String groupDN = null;
                            if (groupResults.hasMore()) {
                                resultedGroup = groupResults.next();
                                groupDN = resultedGroup.getName();
                            }
                            if (resultedGroup != null && !isUserInRole(userNameDN, resultedGroup)) {
                                modifyUserInRole(userNameDN, groupDN, DirContext.ADD_ATTRIBUTE);
                            } else {
                                errorMessage = "User: " + userName + " already belongs to role: "
                                        + groupDN;
                                logger.error(errorMessage);
                                throw new UserStoreException(errorMessage);
                            }

                            JNDIUtil.closeNamingEnumeration(groupResults);

                        } else {
                            errorMessage = "The role: " + newRole + " does not exist.";
                            logger.error(errorMessage);
                            throw new UserStoreException(errorMessage);
                        }
                    }
                }

            } catch (NamingException e) {
                errorMessage = "Error occurred while modifying the role list of user: " + userName;
                logger.error(errorMessage);
                throw new UserStoreException(errorMessage);
            } finally {
                JNDIUtil.closeContext(mainDirContext);
            }
        }
        // need to clear roles cache upon role update
        clearUserRolesCacheByTenant(this.tenantID);

    }

    /**
     * Update the set of users belong to a LDAP role.
     * 
     * @param roleName
     * @param deletedUsers
     * @param newUsers
     */
    @SuppressWarnings("deprecation")
    @Override
    public void doUpdateUserListOfRole(String roleName, String[] deletedUsers, String[] newUsers)
            throws UserStoreException {
        if (!readLDAPUserGroups && !writeLDAPUserGroups || readLDAPUserGroups
                && !writeLDAPUserGroups) {
            // hybridRoleManager.updateUserListOfHybridRole(roleName,
            // deletedUsers, newUsers);
            super.updateUserListOfRole(roleName, deletedUsers, newUsers);
        }
        if (readLDAPUserGroups && writeLDAPUserGroups) {

            if (deletedUsers != null) {
                Arrays.sort(deletedUsers);
                if (realmConfig.getAdminRoleName().equals(roleName)
                        && Arrays.binarySearch(deletedUsers, realmConfig.getAdminUserName()) > -1) {
                    log.error("An attempt to remove Admin user from Admin role ");
                    throw new UserStoreException("Cannot remove Admin user from Admin role");
                }
            }
            String errorMessage = null;
            NamingEnumeration<SearchResult> groupSearchResults = null;

            if (isExistingRole(roleName)) {

                DirContext mainDirContext = this.connectionSource.getContext();

                try {
                    String searchFilter = realmConfig
                            .getUserStoreProperty(LDAPConstants.ROLE_NAME_FILTER);
                    searchFilter = searchFilter.replace("?", roleName);
                    String membershipAttributeName = realmConfig
                            .getUserStoreProperty(LDAPConstants.MEMBERSHIP_ATTRIBUTE);
                    String[] returningAttributes = new String[] { membershipAttributeName };

                    groupSearchResults = searchInGroupBase(searchFilter, returningAttributes,
                            SearchControls.SUBTREE_SCOPE, mainDirContext);
                    SearchResult resultedGroup = null;
                    String groupName = null;
                    while (groupSearchResults.hasMoreElements()) {
                        resultedGroup = groupSearchResults.next();
                        groupName = resultedGroup.getName();
                    }
                    // check whether update operations are going to violate non
                    // empty role
                    // restriction specified in user-mgt.xml by
                    // checking whether all users are trying to be deleted
                    // before updating LDAP.
                    Attribute returnedMemberAttribute = resultedGroup.getAttributes().get(
                            membershipAttributeName);
                    if (!emptyRolesAllowed
                            && newUsers.length - deletedUsers.length
                                    + returnedMemberAttribute.size() == 0) {
                        errorMessage = "There should be at least one member in the role. "
                                + "Hence can not delete all the members.";
                        logger.error(errorMessage);
                        throw new UserStoreException(errorMessage);

                    } else {
                        if (newUsers.length != 0) {
                            for (String newUser : newUsers) {
                                String userNameDN = getNameInSpaceForUserName(newUser);
                                if (!isUserInRole(userNameDN, resultedGroup)) {
                                    modifyUserInRole(userNameDN, groupName,
                                            DirContext.ADD_ATTRIBUTE);
                                } else {
                                    errorMessage = "User: " + newUser
                                            + " already belongs to role: " + roleName;
                                    logger.error(errorMessage);
                                    throw new UserStoreException(errorMessage);
                                }
                            }
                        }

                        if (deletedUsers != null && deletedUsers.length != 0) {
                            for (String deletedUser : deletedUsers) {
                                String userNameDN = getNameInSpaceForUserName(deletedUser);
                                modifyUserInRole(userNameDN, groupName, DirContext.REMOVE_ATTRIBUTE);
                                // needs to clear authz cache for deleted users
                                userRealm.getAuthorizationManager().clearUserAuthorization(
                                        deletedUser);
                            }
                        }
                    }
                } catch (NamingException e) {

                    errorMessage = "Error occurred while modifying the user list of role: "
                            + roleName;
                    logger.error(errorMessage);
                    throw new UserStoreException(errorMessage);
                } finally {
                    JNDIUtil.closeNamingEnumeration(groupSearchResults);
                    JNDIUtil.closeContext(mainDirContext);
                }
            } else {
                errorMessage = "The role: " + roleName + " does not exist.";
                logger.error(errorMessage);
                throw new UserStoreException(errorMessage);
            }
        }
        // need to clear user roles cache upon roles update
        clearUserRolesCacheByTenant(this.tenantID);
    }

    /**
     * @param roleName
     * @return
     * @throws UserStoreException
     */
    @Override
    public boolean isExistingRole(String roleName) throws UserStoreException {
        boolean isExistingRole = false;
        // internal roles are managed in UM_DB or code.
        if (isInternalRole(roleName) || !readLDAPUserGroups && !writeLDAPUserGroups) {
            return hybridRoleManager.isExistingRole(roleName);
        }
        // check group read-write settings and execute super method as
        // appropriate.
        if (readLDAPUserGroups && !writeLDAPUserGroups || readLDAPUserGroups && writeLDAPUserGroups) {
            return super.isExistingRole(roleName);
        }
        return isExistingRole;
    }

    /**
     * Either delete or add user from/to group.
     * 
     * @param userNameDN : distinguish name of user entry.
     * @param groupRDN : relative distinguish name of group entry
     * @param modifyType : modify attribute type in DirCOntext.
     * @throws UserStoreException
     */
    protected void modifyUserInRole(String userNameDN, String groupRDN, int modifyType)
            throws UserStoreException {
        DirContext mainDirContext = null;
        DirContext groupContext = null;
        try {
            mainDirContext = this.connectionSource.getContext();
            groupContext = (DirContext) mainDirContext.lookup(groupSearchBase);
            String memberAttributeName = realmConfig
                    .getUserStoreProperty(LDAPConstants.MEMBERSHIP_ATTRIBUTE);
            Attributes modifyingAttributes = new BasicAttributes(true);
            Attribute memberAttribute = new BasicAttribute(memberAttributeName);
            memberAttribute.add(userNameDN);
            modifyingAttributes.put(memberAttribute);
            groupContext.modifyAttributes(groupRDN, modifyType, modifyingAttributes);
            if (log.isDebugEnabled()) {
                logger.debug("Member attribute value: " + userNameDN + " was successfully "
                        + "modified in LDAP group: " + groupRDN);
            }
        } catch (NamingException e) {
            String errorMessage = "Error occurred while modifying user entry: " + userNameDN
                    + " in LDAP role: " + groupRDN;
            logger.error(errorMessage, e);
            throw new UserStoreException(errorMessage, e);
        } finally {
            JNDIUtil.closeContext(groupContext);
            JNDIUtil.closeContext(mainDirContext);
        }
    }

    /**
     * Check whether user is in the group by searching through its member attributes.
     * 
     * @param userDN
     * @param groupEntry
     * @return
     * @throws UserStoreException
     */
    @SuppressWarnings("rawtypes")
    protected boolean isUserInRole(String userDN, SearchResult groupEntry)
            throws UserStoreException {
        boolean isUserInRole = false;
        try {
            Attributes groupAttributes = groupEntry.getAttributes();
            if (groupAttributes != null) {
                // get group's returned attributes
                NamingEnumeration attributes = groupAttributes.getAll();
                // loop through attributes
                while (attributes.hasMoreElements()) {
                    Attribute memberAttribute = (Attribute) attributes.next();
                    String memberAttributeName = realmConfig
                            .getUserStoreProperty(LDAPConstants.MEMBERSHIP_ATTRIBUTE);
                    if (memberAttributeName.equals(memberAttribute.getID())) {
                        // loop through attribute values
                        for (int i = 0; i < memberAttribute.size(); i++) {
                            if (userDN.equals(memberAttribute.get(i))) {
                                return true;
                            }
                        }
                    }

                }

                attributes.close();
            }
        } catch (NamingException e) {
            String errorMessage = "Error occurred while looping through attributes set of group: "
                    + groupEntry.getNameInNamespace();
            logger.error(errorMessage, e);
            throw new UserStoreException(errorMessage, e);
        }
        return isUserInRole;
    }

    /**
     * Check whether this is the last/only user in this group.
     * 
     * @param userDN
     * @param groupEntry
     * @return
     */
    @SuppressWarnings("rawtypes")
    protected boolean isOnlyUserInRole(String userDN, SearchResult groupEntry)
            throws UserStoreException {
        boolean isOnlyUserInRole = false;
        try {
            Attributes groupAttributes = groupEntry.getAttributes();
            if (groupAttributes != null) {
                NamingEnumeration attributes = groupAttributes.getAll();
                while (attributes.hasMoreElements()) {
                    Attribute memberAttribute = (Attribute) attributes.next();
                    String memberAttributeName = realmConfig
                            .getUserStoreProperty(LDAPConstants.MEMBERSHIP_ATTRIBUTE);
                    String attributeID = memberAttribute.getID();
                    if (memberAttributeName.equals(attributeID)) {
                        if (memberAttribute.size() == 1 && userDN.equals(memberAttribute.get())) {
                            return true;
                        }
                    }

                }

                attributes.close();

            }
        } catch (NamingException e) {
            String errorMessage = "Error occurred while looping through attributes set of group: "
                    + groupEntry.getNameInNamespace();
            logger.error(errorMessage, e);
            throw new UserStoreException(errorMessage, e);
        }
        return isOnlyUserInRole;
    }

    @Override
    public void doUpdateRoleName(String roleName, String newRoleName) throws UserStoreException {
        if (!readLDAPUserGroups && !writeLDAPUserGroups || readLDAPUserGroups
                && !writeLDAPUserGroups) {
            // hybridRoleManager.updateHybridRoleName(roleName, newRoleName);
            super.doUpdateRoleName(roleName, newRoleName);
        }
        if (readLDAPUserGroups && writeLDAPUserGroups) {
            // admin role and everyone role are not editable.
            if (realmConfig.getAdminRoleName().equals(roleName)) {
                throw new UserStoreException("Cannot rename admin role");
            }
            if (realmConfig.getEveryOneRoleName().equals(roleName)) {
                throw new UserStoreException("Cannot rename everyone role");
            }
            if (isExistingRole(newRoleName)) {
                throw new UserStoreException(
                        "Duplicate role name in the system. Please pick another name");
            } else if (!isExistingRole(newRoleName)) {
                DirContext mainContext = this.connectionSource.getContext();
                DirContext groupContext = null;
                NamingEnumeration<SearchResult> groupSearchResults = null;

                try {
                    String groupSearchFilter = realmConfig
                            .getUserStoreProperty(LDAPConstants.ROLE_NAME_FILTER);
                    groupSearchFilter = groupSearchFilter.replace("?", roleName);
                    String roleNameAttributeName = realmConfig
                            .getUserStoreProperty(LDAPConstants.GROUP_NAME_ATTRIBUTE);
                    String[] returningAttributes = { roleNameAttributeName };

                    // DirContext mainDirContext =
                    // this.connectionSource.getContext();

                    groupSearchResults = searchInGroupBase(groupSearchFilter, returningAttributes,
                            SearchControls.SUBTREE_SCOPE, mainContext);
                    SearchResult resultedGroup = null;
                    while (groupSearchResults.hasMoreElements()) {
                        resultedGroup = groupSearchResults.next();
                    }

                    if (resultedGroup == null) {
                        throw new UserStoreException("Could not find user role " + roleName
                                + " in LDAP server.");
                    }

                    String groupNameRDN = resultedGroup.getName();
                    String newGroupNameRDN = roleNameAttributeName + "=" + newRoleName;

                    groupContext = (DirContext) mainContext.lookup(groupSearchBase);
                    groupContext.rename(groupNameRDN, newGroupNameRDN);
                    this.userRealm.getAuthorizationManager().resetPermissionOnUpdateRole(roleName,
                            newRoleName);
                } catch (NamingException e) {
                    String errorMessage = "Error occurred while modifying the name of role: "
                            + roleName;
                    logger.error(errorMessage, e);
                    throw new UserStoreException(errorMessage, e);
                } finally {
                    JNDIUtil.closeNamingEnumeration(groupSearchResults);
                    JNDIUtil.closeContext(groupContext);
                    JNDIUtil.closeContext(mainContext);
                }
            }
        }
        // need to update user role cache upon update of role names
        clearUserRolesCacheByTenant(this.tenantID);
    }

    /**
     * Delete LDAP group corresponding to the role.
     * 
     * @param roleName The role to delete.
     * @throws UserStoreException In case if an error occurred while deleting a role.
     */
    @Override
    public void doDeleteRole(String roleName) throws UserStoreException {
        if (!readLDAPUserGroups && !writeLDAPUserGroups || readLDAPUserGroups
                && !writeLDAPUserGroups) {
            // hybridRoleManager.deleteHybridRole(roleName);
            super.doDeleteRole(roleName);
        }
        if (readLDAPUserGroups && writeLDAPUserGroups) {
            // admin role and everyone role are not editable.
            if (realmConfig.getAdminRoleName().equals(roleName)) {
                throw new UserStoreException("Cannot delete admin role.");
            }
            if (realmConfig.getEveryOneRoleName().equals(roleName)) {
                throw new UserStoreException("Cannot delete everyone role.");
            }
            if (isExistingRole(roleName)) {
                DirContext mainDirContext = null;
                DirContext groupContext = null;
                NamingEnumeration<SearchResult> groupSearchResults = null;

                try {
                    String groupSearchFilter = realmConfig
                            .getUserStoreProperty(LDAPConstants.ROLE_NAME_FILTER);
                    groupSearchFilter = groupSearchFilter.replace("?", roleName);
                    String[] returningAttributes = { realmConfig
                            .getUserStoreProperty(LDAPConstants.GROUP_NAME_ATTRIBUTE) };

                    mainDirContext = this.connectionSource.getContext();

                    groupSearchResults = searchInGroupBase(groupSearchFilter, returningAttributes,
                            SearchControls.SUBTREE_SCOPE, mainDirContext);
                    SearchResult resultedGroup = null;
                    while (groupSearchResults.hasMoreElements()) {
                        resultedGroup = groupSearchResults.next();
                    }

                    if (resultedGroup == null) {
                        throw new UserStoreException("Could not find specified group/role - "
                                + roleName);
                    }

                    String groupName = resultedGroup.getName();

                    groupContext = (DirContext) mainDirContext.lookup(groupSearchBase);
                    String groupNameAttributeValue = (String) resultedGroup
                            .getAttributes()
                            .get(realmConfig
                                    .getUserStoreProperty(LDAPConstants.GROUP_NAME_ATTRIBUTE))
                            .get();
                    if (groupNameAttributeValue.equals(roleName)) {
                        groupContext.destroySubcontext(groupName);
                    }
                    this.userRealm.getAuthorizationManager().clearRoleAuthorization(roleName);

                } catch (NamingException e) {
                    String errorMessage = "Error occurred while deleting the role: " + roleName;
                    logger.error(errorMessage, e);
                    throw new UserStoreException(errorMessage, e);
                } finally {
                    JNDIUtil.closeNamingEnumeration(groupSearchResults);
                    JNDIUtil.closeContext(groupContext);
                    JNDIUtil.closeContext(mainDirContext);
                }

            } else {
                String errorMessage = "Role name: " + roleName + " does not exist in group base.";
                logger.error(errorMessage);
                throw new UserStoreException(errorMessage);
            }
        }

        // need to clear user role cache upon delete of roles
        clearUserRolesCacheByTenant(this.tenantID);
    }

    /**
     * Reused methods to search users with various filters
     * 
     * @param searchFilter
     * @param returningAttributes
     * @param searchScope
     * @return
     */
    private NamingEnumeration<SearchResult> searchInUserBase(String searchFilter,
            String[] returningAttributes, int searchScope, DirContext rootContext)
            throws UserStoreException {
        String userBase = realmConfig.getUserStoreProperty(LDAPConstants.USER_SEARCH_BASE);
        SearchControls userSearchControl = new SearchControls();
        userSearchControl.setReturningAttributes(returningAttributes);
        userSearchControl.setSearchScope(searchScope);
        NamingEnumeration<SearchResult> userSearchResults = null;

        try {
            userSearchResults = rootContext.search(userBase, searchFilter, userSearchControl);
        } catch (NamingException e) {
            String errorMessage = "Error occurred while searching in user base.";
            logger.error(errorMessage, e);
            throw new UserStoreException(errorMessage, e);
        }

        return userSearchResults;

    }

    /**
     * Reused method to search groups with various filters.
     * 
     * @param searchFilter
     * @param returningAttributes
     * @param searchScope
     * @return
     */
    private NamingEnumeration<SearchResult> searchInGroupBase(String searchFilter,
            String[] returningAttributes, int searchScope, DirContext rootContext)
            throws UserStoreException {
        String userBase = realmConfig.getUserStoreProperty(LDAPConstants.GROUP_SEARCH_BASE);
        SearchControls userSearchControl = new SearchControls();
        userSearchControl.setReturningAttributes(returningAttributes);
        userSearchControl.setSearchScope(searchScope);
        NamingEnumeration<SearchResult> groupSearchResults = null;
        try {
            groupSearchResults = rootContext.search(userBase, searchFilter, userSearchControl);
        } catch (NamingException e) {
            String errorMessage = "Error occurred while searching in group base.";
            logger.error(errorMessage, e);
            throw new UserStoreException(errorMessage, e);
        }

        return groupSearchResults;
    }

    /**
     * This is to read and validate the required user store configuration for this user store
     * manager to take decisions.
     * 
     * @throws UserStoreException
     */
    @Override
    protected void checkRequiredUserStoreConfigurations() throws UserStoreException {

        super.checkRequiredUserStoreConfigurations();

        String userObjectClass = realmConfig
                .getUserStoreProperty(LDAPConstants.USER_ENTRY_OBJECT_CLASS);
        if (userObjectClass == null || userObjectClass.equals("")) {
            throw new UserStoreException(
                    "Required UserEntryObjectClass property is not set at the LDAP configurations");
        }
        String userNameSearchFilter = realmConfig
                .getUserStoreProperty(LDAPConstants.USER_NAME_SEARCH_FILTER);
        if (userNameSearchFilter == null || userNameSearchFilter.equals("")) {
            throw new UserStoreException(
                    "Required  UserNameSearchFilter property is not set at the LDAP configurations");
        }

        /*
         * String usernameJSRegEx = realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.
         * PROPERTY_USER_NAME_JS_REG_EX); if (usernameJSRegEx == null || usernameJSRegEx.equals(""))
         * { throw new UserStoreException(
         * "Required UserNameJavaScriptRegEx property is not set at the LDAP configurations" ); }
         */

        // checking password configurations
        /*
         * String passwordJSRegEx = realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.
         * PROPERTY_JS_REG_EX); if (passwordJSRegEx == null || passwordJSRegEx.equals("")) { throw
         * new UserStoreException(
         * "Required PasswordJavaScriptRegEx property is not set at the LDAP configurations" ); }
         */

        // checking role configurations
        /*
         * String roleNameJavaRegEx =
         * realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.
         * PROPERTY_ROLE_NAME_JAVA_REG_EX); if (roleNameJavaRegEx == null ||
         * roleNameJavaRegEx.equals("")) { throw new UserStoreException(
         * "Required RoleNameJavaRegEx property is not set at the LDAP configurations" ); } String
         * roleNameJSRegEx = realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.
         * PROPERTY_ROLE_NAME_JS_REG_EX); if (roleNameJSRegEx == null || roleNameJSRegEx.equals(""))
         * { throw new UserStoreException(
         * "Required RoleNameJavaScriptRegEx property is not set at the LDAP configurations" ); }
         */
        writeLDAPUserGroups = Boolean.parseBoolean(realmConfig
                .getUserStoreProperty(LDAPConstants.WRITE_EXTERNAL_ROLES));

        if (!readLDAPUserGroups && writeLDAPUserGroups) {
            throw new UserStoreException("Wrong combination for LDAP groups "
                    + "read-write properties.");
        }

        emptyRolesAllowed = Boolean.parseBoolean(realmConfig
                .getUserStoreProperty(LDAPConstants.EMPTY_ROLES_ALLOWED));

        String groupEntryObjectClass = realmConfig
                .getUserStoreProperty(LDAPConstants.GROUP_ENTRY_OBJECT_CLASS);
        if (groupEntryObjectClass == null || groupEntryObjectClass.equals("")) {
            throw new UserStoreException(
                    "Required GroupEntryObjectClass property is not set at the LDAP configurations");
        }

        String groupNameSearchFilter = realmConfig
                .getUserStoreProperty(LDAPConstants.ROLE_NAME_FILTER);
        if (groupNameSearchFilter == null || groupNameSearchFilter.equals("")) {
            throw new UserStoreException(
                    "Required GroupNameSearchFilter property is not set at the LDAP configurations");
        }

        userSearchBase = realmConfig.getUserStoreProperty(LDAPConstants.USER_SEARCH_BASE);
        groupSearchBase = realmConfig.getUserStoreProperty(LDAPConstants.GROUP_SEARCH_BASE);

    }

    /**
     * This is to validate whether a particular role is an internal role of carbon.
     * 
     * @param roleName
     * @return
     */
    protected boolean isInternalRole(String roleName) {
        return roleName.equals(realmConfig.getEveryOneRoleName());
    }

    /**
     * Check and add the initial data to the user store for user manager to start properly.
     */
    private void addInitialData() throws UserStoreException {

        if (realmConfig.getAdminUserName() == null || realmConfig.getAdminRoleName() == null) {
            String error = "Admin user name or role name is not valid. Please provide valid values.";
            log.error(error);
            throw new UserStoreException(error);
        }

        // check whether this is the fist start up of the server
        if (isFirstStartup) {
        	if(!isAdminAdded){
			// TODO : what if the admin username/role changed ?
        	// how to remove permissions from the user/role ?
        	
			String adminUserName = realmConfig.getAdminUserName();
			String adminRoleName = realmConfig.getAdminRoleName();

			// add admin user if not already added - if it is the first start up
			// only. this will not affect MT environment. since TenantManager
			// creates the tenant admin, before initializing UserStoreManager
			// for the tenant.
			if (!isExistingUser(adminUserName)) {
				if (log.isDebugEnabled()) {
					log.debug("Admin user does not exist in the LDAP. Hence creating the user "
					          + "entry in LDAP.");
				}
				this.doAddUser(adminUserName, realmConfig.getAdminPassword(), null, null, null);
			}
            
            // add admin role, if not already added.
            if (!isExistingRole(adminRoleName)) {
                if (log.isDebugEnabled()) {
                    log.debug("Admin role does not exist in the LDAP. Hence creating the "
                            + "group entry in LDAP.");
                }
                this.addRole(realmConfig.getAdminRoleName(),
                        new String[] { realmConfig.getAdminUserName() }, null);
                
            } else if (!isUserHasTheRole(adminUserName, adminRoleName)) { 
            	// this is when the admin role is changed in the user-mgt.xml
				if (log.isDebugEnabled()) {
					log.debug("Admin user is not in the Admin role. Adding the Admin user"
					          + " to the Admin role");
				}
				String[] roles = { adminRoleName };
				this.updateRoleListOfUser(realmConfig.getAdminUserName(), null, roles);
            }

            // add anonymous user and role - carbon specific entries.
            if (!isExistingUser(CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME)) {
                this.doAddUser(CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME,
                        UserCoreUtil.getPolicyFriendlyRandomPassword(CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME), null, null, null);

            }
            // if the realm is read only for groups, the role will be hybrid
            if (!isExistingRole(CarbonConstants.REGISTRY_ANONNYMOUS_ROLE_NAME)) {
                if (writeLDAPUserGroups) {
                    this.addRole(CarbonConstants.REGISTRY_ANONNYMOUS_ROLE_NAME,
                            new String[] { CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME }, null);
                } else {
                    super.addRole(CarbonConstants.REGISTRY_ANONNYMOUS_ROLE_NAME,
                            new String[] { CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME }, null);
                }
            }
            isAdminAdded=true;
        	}
        	
        } else {
            // if this is not the first start up, check admin user
            if (!isExistingUser(realmConfig.getAdminUserName())) {
                String error = "Initial data has been changed after first start up. AdminUser does"
                        + "not exist.";
                log.error(error);
                throw new UserStoreException(error);
            }

            // if this is not the first start up, check admin role
            if (!isExistingRole(realmConfig.getAdminRoleName())) {
                String error = "Initial data has been changed after first start up. AdminRole does"
                        + " not exist.";
                log.error(error);
                throw new UserStoreException(error);
            }

            // if this is not the first start up, check registry anonymous user.
            if (!isExistingUser(CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME)) {
                String error = "Initial data has been changed after first start up. Registry "
                        + "Anonymous User does not exist.";
                log.error(error);
                throw new UserStoreException(error);
            }
            // if this is not the first start up, check registry anonymous role.
            if (!isExistingRole(CarbonConstants.REGISTRY_ANONNYMOUS_ROLE_NAME)) {
                String error = "Initial data has been changed after first start up. Registry "
                        + "Anonymous Role does not exist.";
                log.error(error);
                throw new UserStoreException(error);
            }

        }

    }

}
