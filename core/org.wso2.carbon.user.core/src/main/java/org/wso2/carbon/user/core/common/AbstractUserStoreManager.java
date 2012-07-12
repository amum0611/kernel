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
package org.wso2.carbon.user.core.common;

import org.apache.axis2.context.MessageContext;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.CarbonConstants;
import org.wso2.carbon.caching.core.authorization.AuthorizationCache;
import org.wso2.carbon.caching.core.rolesofuser.UserRolesCache;
import org.wso2.carbon.user.api.RealmConfiguration;
import org.wso2.carbon.user.core.UserCoreConstants;
import org.wso2.carbon.user.core.UserRealm;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.UserStoreManager;
import org.wso2.carbon.user.core.claim.Claim;
import org.wso2.carbon.user.core.claim.ClaimManager;
import org.wso2.carbon.user.core.claim.ClaimMapping;
import org.wso2.carbon.user.core.hybrid.HybridRoleManager;
import org.wso2.carbon.user.core.internal.UMListenerServiceComponent;
import org.wso2.carbon.user.core.listener.UserOperationEventListener;
import org.wso2.carbon.user.core.listener.UserStoreManagerListener;
import org.wso2.carbon.user.core.profile.ProfileConfiguration;
import org.wso2.carbon.user.core.profile.ProfileConfigurationManager;
import org.wso2.carbon.utils.ServerConstants;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.sql.DataSource;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractUserStoreManager implements UserStoreManager {

    private static Log log = LogFactory.getLog(AbstractUserStoreManager.class);
    protected DataSource dataSource = null;
    protected RealmConfiguration realmConfig = null;
    protected ClaimManager claimManager = null;
    protected ProfileConfigurationManager profileManager = null;
    protected UserRealm userRealm = null;
    protected HybridRoleManager hybridRoleManager = null;
    //user roles cache
    protected UserRolesCache userRolesCache = null;

    private boolean userRolesCacheEnabled = true;

    private boolean replaceEscapeCharactersAtUserLogin = true;

    public String getUserClaimValue(String userName, String claim, String profileName)
            throws UserStoreException {

        String profileConfiguration = UserCoreConstants.DEFAULT_PROFILE_CONFIGURATION;

        String property = null;
        try {
            property = claimManager.getAttributeName(claim);
        } catch (org.wso2.carbon.user.api.UserStoreException e) {
            throw new UserStoreException(e);
        }

        ProfileConfiguration profileConfig;
        try {
            profileConfig =
                    (ProfileConfiguration) profileManager.getProfileConfig(profileConfiguration);
        } catch (org.wso2.carbon.user.api.UserStoreException e) {
            throw new UserStoreException(e);
        }
        if (profileConfig != null) {
            List<String> hidden = getMappingAttributeList(profileConfig.getHiddenClaims());
            if (hidden.contains(property)) {
                return null;
            }
        }

        String value = this.getUserPropertyValues(userName, new String[]{property}, profileName)
                .get(property);
        if ((value == null || value.trim().length() == 0)
            && !UserCoreConstants.DEFAULT_PROFILE.equals(profileName) && profileConfig != null) {
            List<String> overridden = getMappingAttributeList(profileConfig.getOverriddenClaims());
            if (!overridden.contains(property)) {
                value = this.getUserPropertyValues(userName, new String[]{property},
                                                   UserCoreConstants.DEFAULT_PROFILE).get(property);
            }
        }
        return value;

    }

    public Claim[] getUserClaimValues(String userName, String profileName)
            throws UserStoreException {
        if (profileName == null) {
            profileName = UserCoreConstants.DEFAULT_PROFILE;
        }

        String[] claims;
        try {
            claims = claimManager.getAllClaimUris();
        } catch (org.wso2.carbon.user.api.UserStoreException e) {
            throw new UserStoreException(e);
        }
        Map<String, String> values = this.getUserClaimValues(userName, claims, profileName);
        Claim[] finalValues = new Claim[values.size()];
        int i = 0;
        for (Iterator<Map.Entry<String, String>> ite = values.entrySet().iterator(); ite.hasNext();) {
            Map.Entry<String, String> entry = ite.next();
            Claim claim = new Claim();
            claim.setValue(entry.getValue());
            claim.setClaimUri(entry.getKey());
            String displayTag;
            try {
                displayTag = claimManager.getClaim(entry.getKey()).getDisplayTag();
            } catch (org.wso2.carbon.user.api.UserStoreException e) {
                throw new UserStoreException(e);
            }
            claim.setDisplayTag(displayTag);
            finalValues[i] = claim;
            i++;
        }
        return finalValues;
    }

    public Map<String, String> getUserClaimValues(String userName, String[] claims,
                                                  String profileName) throws UserStoreException {

        if (profileName == null) {
            profileName = UserCoreConstants.DEFAULT_PROFILE;
        }

        String profileConfigurationName = null;
        boolean isPofileCnfigurationRequested = false;

        Set<String> propertySet = new HashSet<String>();
        for (String claim : claims) {
            ClaimMapping mapping = null;
            try {
                mapping = (ClaimMapping) claimManager.getClaimMapping(claim);
            } catch (org.wso2.carbon.user.api.UserStoreException e) {
                throw new UserStoreException(e);
            }
            // There can be cases some claim values being requested for claims
            // we don't have.
            if (mapping != null) {
                propertySet.add(mapping.getMappedAttribute());
            }

            if (UserCoreConstants.PROFILE_CONFIGURATION.equals(claim)) {
                isPofileCnfigurationRequested = true;
            }
        }

        propertySet.add(UserCoreConstants.PROFILE_CONFIGURATION);

        String[] properties = propertySet.toArray(new String[propertySet.size()]);
        Map<String, String> uerProperties = this.getUserPropertyValues(userName, properties,
                                                                       profileName);
        profileConfigurationName = uerProperties.get(UserCoreConstants.PROFILE_CONFIGURATION);
        if (profileConfigurationName == null) {
            profileConfigurationName = UserCoreConstants.DEFAULT_PROFILE_CONFIGURATION;
        }

        ProfileConfiguration profileConfig;
        try {
            profileConfig = (ProfileConfiguration) profileManager.getProfileConfig(profileConfigurationName);
        } catch (org.wso2.carbon.user.api.UserStoreException e) {
            throw new UserStoreException(e);
        }

        List<String> hidden = null;
        if (profileConfig != null) {
            hidden = getMappingAttributeList(profileConfig.getHiddenClaims());
        }

        List<String> overridden = null;
        if (profileConfig != null) {
            overridden = getMappingAttributeList(profileConfig.getOverriddenClaims());
        }

        List<String> getAgain = new ArrayList<String>();
        Map<String, String> finalValues = new HashMap<String, String>();

        if (isPofileCnfigurationRequested) {
            finalValues.put(UserCoreConstants.PROFILE_CONFIGURATION, profileConfigurationName);
        }

        for (String claim : claims) {
            ClaimMapping mapping;
            try {
                mapping = (ClaimMapping) claimManager.getClaimMapping(claim);
            } catch (org.wso2.carbon.user.api.UserStoreException e) {
                throw new UserStoreException(e);
            }
            if (mapping != null) {
                String property = mapping.getMappedAttribute();
                String value = uerProperties.get(property);
                if (hidden != null && hidden.contains(property)) {
                    continue;
                }

                if (profileName.equals(UserCoreConstants.DEFAULT_PROFILE)) {
                    // Check whether we have a value for the requested attribute
                    if (value != null && value.trim().length() > 0) {
                        finalValues.put(claim, value);
                    }
                } else if (profileConfig != null && (value == null || value.equals(""))) {
                    if (overridden != null && overridden.contains(property)) {
                        finalValues.put(claim, value);
                    } else {
                        getAgain.add(claim);
                    }
                } else {
                    if (value != null && value.trim().length() > 0) {
                        finalValues.put(claim, value);
                    }
                }
            }
        }

        if (getAgain.size() > 0) {
            // oh the beautiful recursion
            Map<String, String> mapClaimValues = this.getUserClaimValues(userName,
                                                                         (String[]) getAgain.toArray(new String[getAgain.size()]),
                                                                         UserCoreConstants.DEFAULT_PROFILE);

            Iterator<Map.Entry<String, String>> ite3 = mapClaimValues.entrySet().iterator();
            while (ite3.hasNext()) {
                Map.Entry<String, String> entry = ite3.next();
                if (entry.getValue() != null) {
                    finalValues.put(entry.getKey(), entry.getValue());
                }
            }
        }

        return finalValues;
    }

    protected List<String> getMappingAttributeList(List<String> claimList)
            throws UserStoreException {
        ArrayList<String> attributeList = null;
        Iterator<String> claimIter = null;

        attributeList = new ArrayList<String>();
        if (claimList == null) {
            return attributeList;
        }
        claimIter = claimList.iterator();
        while (claimIter.hasNext()) {
            try {
                attributeList.add(claimManager.getAttributeName(claimIter.next()));
            } catch (org.wso2.carbon.user.api.UserStoreException e) {
                throw new UserStoreException(e);
            }
        }
        return attributeList;
    }

//    public String[] getHybridRoles() throws UserStoreException {
//        return hybridRoleManager.getHybridRoles();
//    }
//
//    public String[] getUserListOfHybridRole(String roleName) throws UserStoreException {
//        return hybridRoleManager.getUserListOfHybridRole(roleName);
//    }
//
//    public void updateUserListOfHybridRole(String roleName, String[] deletedUsers, String[] addUsers)
//            throws UserStoreException {
//        hybridRoleManager.updateUserListOfHybridRole(roleName, deletedUsers, addUsers);
//    }
//
//    public String[] getHybridRoleListOfUser(String user) throws UserStoreException {
//        return hybridRoleManager.getHybridRoleListOfUser(user);
//    }
//
//    public void updateHybridRolesOfUser(String user, String[] deletedRoles, String[] addRoles)
//            throws UserStoreException {
//        hybridRoleManager.updateHybridRolesOfUser(user, deletedRoles, addRoles);
//    }

    /**
     * This method is used by the support system to read properties
     */
    public abstract Map<String, String> getUserPropertyValues(String userName,
                                                              String[] propertyNames,
                                                              String profileName)
            throws UserStoreException;

    protected boolean checkUserPasswordValid(Object credential)
            throws UserStoreException {

        if (credential == null) {
            return false;
        }

        if (!(credential instanceof String)) {
            throw new UserStoreException("Can handle only string type credentials");
        }

        String password = ((String) credential).trim();

        if (password.length() < 1) {
            return false;
        }

        String regularExpression = realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.
                PROPERTY_JAVA_REG_EX);
        return regularExpression == null || isFormatCorrect(regularExpression, password);
    }


    protected boolean checkUserNameValid(String userName)
            throws UserStoreException {

        if (userName == null || CarbonConstants.REGISTRY_SYSTEM_USERNAME.equals(userName)) {
            return false;
        }

        userName = userName.trim();

        if (userName.length() < 1) {
            return false;
        }

        String regularExpression = realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.
                PROPERTY_USER_NAME_JAVA_REG_EX);
        return regularExpression == null || isFormatCorrect(regularExpression, userName);

    }

    protected boolean roleNameValid(String roleName) {
        if (roleName == null) {
            return false;
        }

        if (roleName.length() < 1) {
            return false;
        }

        String regularExpression =
                realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_ROLE_NAME_JAVA_REG_EX);
        if (regularExpression != null) {
            if (!isFormatCorrect(regularExpression, roleName)) {
                return false;
            }
        }

        return true;
    }

    protected String[] getRoleListOfUserFromCache(int tenantID, String userName) {
        if (userRolesCache != null) {

            return userRolesCache.getRolesListOfUser(tenantID, userName);
        }
        return null;
    }

    protected void clearUserRolesCacheByTenant(int tenantID) {
        if (userRolesCache != null) {
            userRolesCache.clearCacheByTenant(tenantID);
            AuthorizationCache authorizationCache = AuthorizationCache.getInstance();
            authorizationCache.clearCacheByTenant(tenantID);
        }
    }

    protected void addToUserRolesCache(int tenantID, String userName, String[] roleList) {
        if (userRolesCache != null) {
            userRolesCache.addToCache(tenantID, userName, roleList);
            AuthorizationCache authorizationCache = AuthorizationCache.getInstance();
            authorizationCache.clearCacheByUser(tenantID, userName);
        }
    }

    protected void initUserRolesCache() {
        String userRolesCacheEnabledString = (realmConfig.getUserStoreProperty(
                UserCoreConstants.RealmConfig.PROPERTY_ROLES_CACHE_ENABLED));

        if (userRolesCacheEnabledString != null && userRolesCacheEnabledString.equals("")) {
            userRolesCacheEnabled = Boolean.parseBoolean(userRolesCacheEnabledString);
            if (log.isDebugEnabled()) {
                log.debug("User Roles Cache is configured to:" + userRolesCacheEnabledString);
            }
        } else {
            if (log.isDebugEnabled()) {
                log.info("User Roles Cache is not configured. Default value: " +
                         userRolesCacheEnabled + " is taken.");
            }
        }

        if (userRolesCacheEnabled) {
            userRolesCache = UserRolesCache.getUserRolesCacheInstance();
        }

    }


    private boolean isFormatCorrect(String regularExpression, String attribute) {

        Pattern p = Pattern.compile(regularExpression);
        Matcher m = p.matcher(attribute);
        return m.matches();

    }

    /**
     * This is to replace escape characters in user name at user login if replace escape
     * characters enabled in user-mgt.xml. Some User Stores like ApacheDS stores user names
     * by replacing escape characters. In that case, we have to parse the username accordingly.
     *
     * @param userName
     */
    protected String replaceEscapeCharacters(String userName) {
        String replaceEscapeCharactersAtUserLoginString = realmConfig.getUserStoreProperty(
                UserCoreConstants.RealmConfig.PROPERTY_REPLACE_ESCAPE_CHARACTERS_AT_USER_LOGIN);

        if (replaceEscapeCharactersAtUserLoginString != null) {
            replaceEscapeCharactersAtUserLogin = Boolean.parseBoolean(
                    replaceEscapeCharactersAtUserLoginString);
            if (log.isDebugEnabled()) {
                log.debug("Replace escape characters at userlogin is condifured to: " +
                          replaceEscapeCharactersAtUserLoginString);
            }
            if(replaceEscapeCharactersAtUserLogin){
                //currently only '\' & '\\' are identified as escape characters that needs to be replaced.
                return userName.replaceAll("\\\\", "\\\\\\\\");
            }
        }
        return userName;
    }

    @Override
    public String[] getUserList(String claim, String claimValue, String profileName)
                                                                    throws UserStoreException {
        String property;
        try {
            property = claimManager.getAttributeName(claim);
        } catch (org.wso2.carbon.user.api.UserStoreException e) {
            throw new UserStoreException(e);
        }

        return getUserListFromProperties(property, claimValue, profileName);
    }

    @Override
    public void updateCredential(String userName, Object newCredential, Object oldCredential)
                                                                        throws UserStoreException {

        for (UserStoreManagerListener listener : UMListenerServiceComponent
                .getUserStoreManagerListeners()) {
            if (!listener.updateCredential(userName, newCredential, oldCredential, this)) {
                return;
            }
        }

        for (UserOperationEventListener listener : UMListenerServiceComponent
                .getUserOperationEventListeners()) {
            if (!listener.doPreUpdateCredential(userName, newCredential, oldCredential, this)) {
                return;
            }
        }

        doUpdateCredential(userName, newCredential, oldCredential);

        for (UserOperationEventListener listener : UMListenerServiceComponent
                .getUserOperationEventListeners()) {
            if (!listener.doPostUpdateCredential(userName, this)) {
                return;
            }
        }

    }

    @Override
    public void updateCredentialByAdmin(String userName, Object newCredential)
                                                                        throws UserStoreException {
        
        for (UserStoreManagerListener listener : UMListenerServiceComponent
                .getUserStoreManagerListeners()) {
            if (!listener.updateCredentialByAdmin(userName, newCredential, this)) {
                return;
            }
        }

        for (UserOperationEventListener listener : UMListenerServiceComponent
                .getUserOperationEventListeners()) {
            if (!listener.doPreUpdateCredentialByAdmin(userName, newCredential, this)) {
                return;
            }
        }

        doUpdateCredentialByAdmin(userName, newCredential);

        for (UserOperationEventListener listener : UMListenerServiceComponent
                .getUserOperationEventListeners()) {
            if (!listener.doPostUpdateCredentialByAdmin(userName, this)) {
                return;
            }
        }
    }

    @Override
    public void deleteUser(String userName) throws UserStoreException {

        for (UserStoreManagerListener listener : UMListenerServiceComponent
                .getUserStoreManagerListeners()) {
            if (!listener.deleteUser(userName, this)) {
                return;
            }
        }

        for (UserOperationEventListener listener : UMListenerServiceComponent
                .getUserOperationEventListeners()) {
            if (!listener.doPreDeleteUser(userName, this)) {
                return;
            }
        }

		if (realmConfig.getAdminUserName().equals(userName)) {
			throw new UserStoreException("Cannot delete admin user");
		}

		if (CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME.equals(userName)) {
			throw new UserStoreException("Cannot delete anonymous user");
		}

        doDeleteUser(userName);

        for (UserOperationEventListener listener : UMListenerServiceComponent
                .getUserOperationEventListeners()) {
            if (!listener.doPostDeleteUser(userName, this)) {
                return;
            }
        }
    }


    @Override
    public void setUserClaimValue(String userName, String claimURI, String claimValue,
                                                    String profileName) throws UserStoreException {

        for (UserOperationEventListener listener : UMListenerServiceComponent
                .getUserOperationEventListeners()) {
            if (!listener.doPreSetUserClaimValue(userName, claimURI, claimValue, profileName, this)) {
                return;
            }
        }

        doSetUserClaimValue(userName, claimURI, claimValue, profileName);
        
        for (UserOperationEventListener listener : UMListenerServiceComponent
                .getUserOperationEventListeners()) {
            if (!listener.doPostSetUserClaimValue(userName, this)) {
                return;
            }
        }

    }

    @Override
    public void setUserClaimValues(String userName, Map<String, String> claims, String profileName)
                                                                        throws UserStoreException {

        for (UserOperationEventListener listener : UMListenerServiceComponent
                .getUserOperationEventListeners()) {
            if (!listener.doPreSetUserClaimValues(userName, claims, profileName, this)) {
                return;
            }
        }

        doSetUserClaimValues(userName, claims, profileName);

        for (UserOperationEventListener listener : UMListenerServiceComponent
                .getUserOperationEventListeners()) {
            if (!listener.doPostSetUserClaimValues(userName, this)) {
                return;
            }
        }
    }

    @Override
    public void deleteUserClaimValue(String userName, String claimURI, String profileName)
                                                                        throws UserStoreException {

        for (UserOperationEventListener listener : UMListenerServiceComponent
                .getUserOperationEventListeners()) {
            if (!listener.doPreDeleteUserClaimValue(userName, claimURI, profileName, this)) {
                return;
            }
        }

        doDeleteUserClaimValue(userName, claimURI, profileName);

        for (UserOperationEventListener listener : UMListenerServiceComponent
                .getUserOperationEventListeners()) {
            if (!listener.doPostDeleteUserClaimValue(userName, this)) {
                return;
            }
        }
    }

    @Override
    public void deleteUserClaimValues(String userName, String[] claims, String profileName)
                                                                        throws UserStoreException {

        for (UserOperationEventListener listener : UMListenerServiceComponent
                .getUserOperationEventListeners()) {
            if (!listener.doPreDeleteUserClaimValues(userName, claims, profileName, this)) {
                return;
            }
        }

        doDeleteUserClaimValues(userName, claims, profileName);

        for (UserOperationEventListener listener : UMListenerServiceComponent
                .getUserOperationEventListeners()) {
            if (!listener.doPostDeleteUserClaimValues(userName, this)) {
                return;
            }
        }
    }

    @Override
    public boolean authenticate(String userName, Object credential) throws UserStoreException {

        for (UserStoreManagerListener listener : UMListenerServiceComponent
                .getUserStoreManagerListeners()) {
            if (!listener.authenticate(userName, credential, this)) {
                return true;
            }
        }
        
        for (UserOperationEventListener listener : UMListenerServiceComponent
                .getUserOperationEventListeners()) {
            if (!listener.doPreAuthenticate(userName, credential, this)) {
                return false;
            }
        }

        boolean authenticated = doAuthenticate(userName, credential);

        for (UserOperationEventListener listener : UMListenerServiceComponent
                .getUserOperationEventListeners()) {
            authenticated = listener.doPostAuthenticate(userName, authenticated, this);
            if (!authenticated) {
                return false;
            }
        }

        if(log.isDebugEnabled()){
            if(!authenticated) {
                log.debug("Authentication failure. Wrong username or password is provided.");                         
            }
        }

        return authenticated;
    }

    @Override
    public void addUser(String userName, Object credential, String[] roleList, Map<String, String> claims,
                    String profileName, boolean requirePasswordChange) throws UserStoreException {

        for (UserStoreManagerListener listener : UMListenerServiceComponent
                .getUserStoreManagerListeners()) {
            if (!listener.addUser(userName, credential, roleList, claims, profileName, this)) {
                return;
            }
        }        

        for (UserOperationEventListener listener : UMListenerServiceComponent
                .getUserOperationEventListeners()) {
            if (!listener.doPreAddUser(userName, credential, roleList, claims, profileName, this)) {
                return;
            }
        }

        doAddUser(userName, credential, roleList, claims, profileName, requirePasswordChange);

        for (UserOperationEventListener listener : UMListenerServiceComponent
                .getUserOperationEventListeners()) {
            if (!listener.doPostAddUser(userName, this)) {
                return;
            }
        }
    }

    @Override
    public void addUser(String userName, Object credential, String[] roleList, Map<String, String> claims,
                                                    String profileName) throws UserStoreException {

        for (UserStoreManagerListener listener : UMListenerServiceComponent
                .getUserStoreManagerListeners()) {
            if (!listener.addUser(userName, credential, roleList, claims, profileName, this)) {
                return;
            }
        }
        
        for (UserOperationEventListener listener : UMListenerServiceComponent
                .getUserOperationEventListeners()) {
            if (!listener.doPreAddUser(userName, credential, roleList, claims, profileName, this)) {
                return;
            }
        }
        
        doAddUser(userName, credential, roleList, claims, profileName);

        for (UserOperationEventListener listener : UMListenerServiceComponent
                .getUserOperationEventListeners()) {
            if (!listener.doPostAddUser(userName, this)) {
                return;
            }
        }
    }


    @Override
    public void updateUserListOfRole(String roleName, String[] deletedUsers, String[] newUsers)
                                                                        throws UserStoreException {

        if (realmConfig.getEveryOneRoleName().equals(roleName)) {
            throw new UserStoreException("Everyone role can not be updated");
        }

        if (deletedUsers != null) {
            Arrays.sort(deletedUsers);
            if (realmConfig.getAdminRoleName().equals(roleName)
                    && Arrays.binarySearch(deletedUsers, realmConfig.getAdminUserName()) > -1) {
                log.error("An attempt to remove Admin user from Admin role ");
                throw new UserStoreException("Cannot remove Admin user from Admin role");
            }
        }
        
        doUpdateUserListOfRole(roleName, deletedUsers, newUsers);
    }

    @Override
    public void updateRoleListOfUser(String userName, String[] deletedRoles, String[] newRoles)
                                                                        throws UserStoreException {
        if (deletedRoles != null) {
            Arrays.sort(deletedRoles);
            if(Arrays.binarySearch(deletedRoles, realmConfig.getEveryOneRoleName()) > -1){
                log.error("An attempt to remove "+userName+" user from Everyone role ");
                throw new UserStoreException("Everyone role can not be updated");
            }

            if (realmConfig.getAdminUserName().equals(userName)
                    && Arrays.binarySearch(deletedRoles, realmConfig.getAdminRoleName()) > -1) {
                log.error("An attempt to remove Admin user from Admin role ");
                throw new UserStoreException("Cannot remove Admin user from Admin role");
            }
        }

        doUpdateRoleListOfUser(userName, deletedRoles, newRoles);

    }

    @Override
    public void updateRoleName(String roleName, String newRoleName) throws UserStoreException {
       
    }

    /**
     * Retrieves a list of user names for given user's property in user profile
     *
     * @param property
     *              user property in user profile
     * @param value
     *                  value of property
     * @param profileName
     *              profile name, can be null. If null the default profile is considered.
     * @return      An array of user names
     * @throws UserStoreException
     *              if the operation failed
     */
    public abstract String[] getUserListFromProperties(String property, String value, String profileName)
                    throws UserStoreException ;

    /**
     * Given the user name and a credential object, the implementation code must
     * validate whether the user is authenticated.
     *
     * @param userName
     *            The user name
     * @param credential
     *            The credential of a user
     * @return If the value is true the provided credential match with the user
     *         name. False is returned for invalid credential, invalid user name
     *         and mismatching credential with user name.
     * @throws UserStoreException
     *             An unexpected exception has occurred
     */
    public abstract boolean doAuthenticate(String userName, Object credential) throws UserStoreException;

    /**
     * Add a user to the user store.
     *
     * @param userName
     *            User name of the user
     * @param credential
     *            The credential/password of the user
     * @param roleList
     *            The roles that user belongs
     * @param claims
     *            Properties of the user
     * @param profileName
     *            profile name, can be null. If null the default profile is considered.
     * @throws UserStoreException
     *              An unexpected exception has occurred
     */
    public abstract void doAddUser(String userName, Object credential, String[] roleList,
                       Map<String, String> claims, String profileName) throws UserStoreException;


    public abstract void doAddUser(String userName, Object credential, String[] roleList,
                                    Map<String, String> claims, String profileName,
                                    boolean requirePasswordChange) throws UserStoreException;

    /**
     * Update the credential/password of the user
     *
     * @param userName
     *            The user name
     * @param newCredential
     *            The new credential/password
     * @param oldCredential
     *            The old credential/password
     * @throws UserStoreException
     *              An unexpected exception has occurred
     */
    public abstract void doUpdateCredential(String userName, Object newCredential, Object oldCredential)
                                                                        throws UserStoreException;
    /**
     * Update credential/password by the admin of another user
     *
     * @param userName
     *            The user name
     * @param newCredential
     *            The new credential
     * @throws UserStoreException
     *          An unexpected exception has occurred
     */
    public abstract void doUpdateCredentialByAdmin(String userName, Object newCredential)
                                                                        throws UserStoreException;
    /**
     * Delete the user with the given user name
     *
     * @param userName
     *            The user name
     * @throws UserStoreException
     *              An unexpected exception has occurred
     */
    public abstract void doDeleteUser(String userName) throws UserStoreException;

    /**
     * Set a single user claim value
     *
     * @param userName
     *            The user name
     * @param claimURI
     *            The claim URI
     * @param claimValue
     *            The value
     * @param profileName
     *            The profile name, can be null. If null the default profile is
     *            considered.
     * @throws UserStoreException
     *              An unexpected exception has occurred
     */
    public abstract void doSetUserClaimValue(String userName, String claimURI, String claimValue,
                                                    String profileName) throws UserStoreException;

    /**
     * Set many user claim values
     *
     * @param userName
     *            The user name
     * @param claims
     *            Map of claim URIs against values
     * @param profileName
     *            The profile name, can be null. If null the default profile is
     *            considered.
     * @throws UserStoreException
     *              An unexpected exception has occurred
     */
    public abstract void doSetUserClaimValues(String userName, Map<String, String> claims,
                                              String profileName) throws UserStoreException;

    /**
     * Delete a single user claim value
     *
     * @param userName
     *            The user name
     * @param claimURI
     *            Name of the claim
     * @param profileName
     *            The profile name, can be null. If null the default profile is
     *            considered.
     * @throws UserStoreException
     *              An unexpected exception has occurred
     */
    public abstract void doDeleteUserClaimValue(String userName, String claimURI, String profileName)
                                                                        throws UserStoreException;

    /**
     * Delete many user claim values.
     *
     * @param userName
     *            The user name
     * @param claims
     *            URIs of the claims to be deleted.
     * @param profileName
     *            The profile name, can be null. If null the default profile is
     *            considered.
     * @throws UserStoreException
     *              An unexpected exception has occurred
     */
    public abstract void doDeleteUserClaimValues(String userName, String[] claims, String profileName)
                                                                        throws UserStoreException;

    /**
     * Update user list of a particular role
     *
     * @param roleName  The role name
     *
     * @param deletedUsers  Array of user names,  that is going to be removed from the role
     *
     * @param newUsers Array of user names,  that is going to be added to the role
     *
     * @throws UserStoreException  An unexpected exception has occurred
     */
    public abstract void doUpdateUserListOfRole(String roleName, String[] deletedUsers,
                                                String[] newUsers)  throws UserStoreException;

    /**
     * Update role list of a particular user
     *
     * @param userName   The user name
     *
     * @param deletedRoles  Array of role names,  that is going to be removed from the user
     *
     * @param newRoles  Array of role names,  that is going to be added to the user
     *
     * @throws UserStoreException  An unexpected exception has occurred
     */
    public abstract void doUpdateRoleListOfUser(String userName, String[] deletedRoles,
                                                String[] newRoles) throws UserStoreException;

}
