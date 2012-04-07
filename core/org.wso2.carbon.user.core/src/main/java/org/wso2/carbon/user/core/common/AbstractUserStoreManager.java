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
import org.wso2.carbon.user.core.profile.ProfileConfiguration;
import org.wso2.carbon.user.core.profile.ProfileConfigurationManager;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

        String[] claims = new String[0];
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

        if (userRolesCacheEnabledString != null) {
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
}
