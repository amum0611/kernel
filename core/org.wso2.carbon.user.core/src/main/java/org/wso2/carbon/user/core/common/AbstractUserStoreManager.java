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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.CarbonConstants;
import org.wso2.carbon.user.api.RealmConfiguration;
import org.wso2.carbon.user.core.Permission;
import org.wso2.carbon.user.core.UserCoreConstants;
import org.wso2.carbon.user.core.UserRealm;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.UserStoreManager;
import org.wso2.carbon.user.core.authorization.AuthorizationCache;
import org.wso2.carbon.user.core.claim.Claim;
import org.wso2.carbon.user.core.claim.ClaimManager;
import org.wso2.carbon.user.core.claim.ClaimMapping;
import org.wso2.carbon.user.core.dto.RoleDTO;
import org.wso2.carbon.user.core.hybrid.HybridRoleManager;
import org.wso2.carbon.user.core.internal.UMListenerServiceComponent;
import org.wso2.carbon.user.core.listener.UserOperationEventListener;
import org.wso2.carbon.user.core.listener.UserStoreManagerListener;
import org.wso2.carbon.user.core.profile.ProfileConfiguration;
import org.wso2.carbon.user.core.profile.ProfileConfigurationManager;
import org.wso2.carbon.user.core.util.UserCoreUtil;

public abstract class AbstractUserStoreManager implements UserStoreManager {

	private static Log log = LogFactory.getLog(AbstractUserStoreManager.class);
	protected DataSource dataSource = null;
	protected RealmConfiguration realmConfig = null;
	protected ClaimManager claimManager = null;
	protected ProfileConfigurationManager profileManager = null;
	protected UserRealm userRealm = null;
	protected HybridRoleManager hybridRoleManager = null;
	// user roles cache
	protected UserRolesCache userRolesCache = null;
	UserStoreManager secondaryUserStoreManager;

	private boolean userRolesCacheEnabled = true;

	private String cacheIdentifier;

	private boolean replaceEscapeCharactersAtUserLogin = true;

	private Map<String, UserStoreManager> userStoreManagerHolder = new HashMap<String, UserStoreManager>();

	/**
     * 
     */
	public String getUserClaimValue(String userName, String claim, String profileName)
			throws UserStoreException {
		
		// Check whether we have a secondary UserStoreManager setup.
		if (this.getSecondaryUserStoreManager() != null) {
			int index;
			if ((index = userName.indexOf("/")) > 0) {
				// Using the short-circuit. User name comes with the domain name.
				String domain = userName.substring(0, index);
				UserStoreManager secManager = getSecondaryUserStoreManager(domain);
				if (secManager != null) {
					// We have a secondary UserStoreManager registered for this domain.
					userName = userName.substring(index + 1);
					return secManager.getUserClaimValue(userName, claim, profileName);
				}
			}
		}

		String profileConfiguration = UserCoreConstants.DEFAULT_PROFILE_CONFIGURATION;

		String property = null;
		try {
			property = claimManager.getAttributeName(claim);
		} catch (org.wso2.carbon.user.api.UserStoreException e) {
			throw new UserStoreException(e);
		}

		ProfileConfiguration profileConfig;
		try {
			profileConfig = (ProfileConfiguration) profileManager
					.getProfileConfig(profileConfiguration);
		} catch (org.wso2.carbon.user.api.UserStoreException e) {
			throw new UserStoreException(e);
		}
		if (profileConfig != null) {
			List<String> hidden = getMappingAttributeList(profileConfig.getHiddenClaims());
			if (hidden.contains(property)) {
				return null;
			}
		}

		String value = this.getUserPropertyValues(userName, new String[] { property }, profileName)
				.get(property);
		if ((value == null || value.trim().length() == 0)
				&& !UserCoreConstants.DEFAULT_PROFILE.equals(profileName) && profileConfig != null) {
			List<String> overridden = getMappingAttributeList(profileConfig.getOverriddenClaims());
			if (!overridden.contains(property)) {
				value = this.getUserPropertyValues(userName, new String[] { property },
						UserCoreConstants.DEFAULT_PROFILE).get(property);
			}
		}
		return value;

	}

	/**
     * 
     */
	public Claim[] getUserClaimValues(String userName, String profileName)
			throws UserStoreException {
		
		// Check whether we have a secondary UserStoreManager setup.
		if (this.getSecondaryUserStoreManager() != null) {
			int index;
			if ((index = userName.indexOf("/")) > 0) {
				// Using the short-circuit. User name comes with the domain name.
				String domain = userName.substring(0, index);
				UserStoreManager secManager = getSecondaryUserStoreManager(domain);
				if (secManager != null) {
					// We have a secondary UserStoreManager registered for this domain.
					userName = userName.substring(index + 1);
					return secManager.getUserClaimValues(userName, profileName);
				}
			}
		}		
		
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

	/**
     * 
     */
	public Map<String, String> getUserClaimValues(String userName, String[] claims,
			String profileName) throws UserStoreException {

		// Check whether we have a secondary UserStoreManager setup.
		if (this.getSecondaryUserStoreManager() != null) {
			int index;
			if ((index = userName.indexOf("/")) > 0) {
				// Using the short-circuit. User name comes with the domain name.
				String domain = userName.substring(0, index);
				UserStoreManager secManager = getSecondaryUserStoreManager(domain);
				if (secManager != null) {
					// We have a secondary UserStoreManager registered for this domain.
					userName = userName.substring(index + 1);
					return secManager.getUserClaimValues(userName, claims, profileName);
				}
			}
		}
		
		if (profileName == null) {
			profileName = UserCoreConstants.DEFAULT_PROFILE;
		}

		String profileConfigurationName = null;
		boolean isPofileCnfigurationRequested = false;
		boolean requireRoles = false;
		boolean requireIntRoles = false;
		boolean requireExtRoles = false;
		String roleClaim = null;

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
			if (mapping != null
					&& (!UserCoreConstants.ROLE_CLAIM.equals(claim)
							|| !UserCoreConstants.INT_ROLE_CLAIM.equals(claim) || !UserCoreConstants.EXT_ROLE_CLAIM
								.equals(claim))) {
				propertySet.add(mapping.getMappedAttribute());
			}

			if (UserCoreConstants.ROLE_CLAIM.equals(claim)) {
				requireRoles = true;
				roleClaim = claim;
			} else if (UserCoreConstants.INT_ROLE_CLAIM.equals(claim)) {
				requireIntRoles = true;
				roleClaim = claim;
			} else if (UserCoreConstants.EXT_ROLE_CLAIM.equals(claim)) {
				requireExtRoles = true;
				roleClaim = claim;
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
			profileConfig = (ProfileConfiguration) profileManager
					.getProfileConfig(profileConfigurationName);
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

		// We treat roles claim in special way.
		String[] roles = null;

		if (requireRoles) {
			roles = getRoleListOfUser(userName);
		} else if (requireIntRoles) {
			roles = getInternalRoleListOfUser(userName);
		} else if (requireExtRoles) {
			List<String> roleList = getExternalRoleListOfUser(userName);
			if (roleList != null) {
				roles = roleList.toArray(new String[roleList.size()]);
			}
		}

		if (roles != null && roles.length > 0) {
			String delim = "";
			StringBuffer roleBf = new StringBuffer();
			for (String role : roles) {
				roleBf.append(delim).append(role);
				delim = ",";
			}
			finalValues.put(roleClaim, roleBf.toString());
		}

		return finalValues;
	}

	/**
	 * 
	 * @param claimList
	 * @return
	 * @throws UserStoreException
	 */
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

	/**
	 * This method is used by the support system to read properties
	 */
	public abstract Map<String, String> getUserPropertyValues(String userName,
			String[] propertyNames, String profileName) throws UserStoreException;

	/**
	 * 
	 * @param credential
	 * @return
	 * @throws UserStoreException
	 */
	protected boolean checkUserPasswordValid(Object credential) throws UserStoreException {

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

		String regularExpression = realmConfig
				.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_JAVA_REG_EX);
		return regularExpression == null || isFormatCorrect(regularExpression, password);
	}

	/**
	 * 
	 * @param userName
	 * @return
	 * @throws UserStoreException
	 */
	protected boolean checkUserNameValid(String userName) throws UserStoreException {

		if (userName == null || CarbonConstants.REGISTRY_SYSTEM_USERNAME.equals(userName)) {
			return false;
		}

		userName = userName.trim();

		if (userName.length() < 1) {
			return false;
		}

		String regularExpression = realmConfig
				.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_USER_NAME_JAVA_REG_EX);
		return regularExpression == null || regularExpression.equals("")
				|| isFormatCorrect(regularExpression, userName);

	}

	/**
	 * 
	 * @param roleName
	 * @return
	 */
	protected boolean roleNameValid(String roleName) {
		if (roleName == null) {
			return false;
		}

		if (roleName.length() < 1) {
			return false;
		}

		String regularExpression = realmConfig
				.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_ROLE_NAME_JAVA_REG_EX);
		if (regularExpression != null) {
			if (!isFormatCorrect(regularExpression, roleName)) {
				return false;
			}
		}

		return true;
	}

	/**
	 * 
	 * @param tenantID
	 * @param userName
	 * @return
	 */
	protected String[] getRoleListOfUserFromCache(int tenantID, String userName) {
		if (userRolesCache != null) {
			return userRolesCache.getRolesListOfUser(cacheIdentifier, tenantID, userName);
		}
		return null;
	}

	/**
	 * 
	 * @param tenantID
	 */
	protected void clearUserRolesCacheByTenant(int tenantID) {
		if (userRolesCache != null) {
			userRolesCache.clearCacheByTenant(tenantID);
			AuthorizationCache authorizationCache = AuthorizationCache.getInstance();
			authorizationCache.clearCacheByTenant(tenantID);
		}
	}

	/**
	 * 
	 * @param tenantID
	 * @param userName
	 * @param roleList
	 */
	protected void addToUserRolesCache(int tenantID, String userName, String[] roleList) {
		if (userRolesCache != null) {
			userRolesCache.addToCache(cacheIdentifier, tenantID, userName, roleList);
			AuthorizationCache authorizationCache = AuthorizationCache.getInstance();
			authorizationCache.clearCacheByTenant(tenantID);
		}
	}

	/**
     * 
     */
	protected void initUserRolesCache() {

		String userRolesCacheEnabledString = (realmConfig
				.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_ROLES_CACHE_ENABLED));

		String userCoreCacheIdentifier = realmConfig
				.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_USER_CORE_CACHE_IDENTIFIER);

		if (userCoreCacheIdentifier != null && userCoreCacheIdentifier.trim().length() > 0) {
			cacheIdentifier = userCoreCacheIdentifier;
		}

		if (userRolesCacheEnabledString != null && !userRolesCacheEnabledString.equals("")) {
			userRolesCacheEnabled = Boolean.parseBoolean(userRolesCacheEnabledString);
			if (log.isDebugEnabled()) {
				log.debug("User Roles Cache is configured to:" + userRolesCacheEnabledString);
			}
		} else {
			if (log.isDebugEnabled()) {
				log.info("User Roles Cache is not configured. Default value: "
						+ userRolesCacheEnabled + " is taken.");
			}
		}

		if (userRolesCacheEnabled) {
			userRolesCache = UserRolesCache.getInstance();
		}

	}

	/**
	 * 
	 * @param regularExpression
	 * @param attribute
	 * @return
	 */
	private boolean isFormatCorrect(String regularExpression, String attribute) {
		Pattern p = Pattern.compile(regularExpression);
		Matcher m = p.matcher(attribute);
		return m.matches();
	}

	/**
	 * This is to replace escape characters in user name at user login if replace escape characters
	 * enabled in user-mgt.xml. Some User Stores like ApacheDS stores user names by replacing escape
	 * characters. In that case, we have to parse the username accordingly.
	 * 
	 * @param userName
	 */
	protected String replaceEscapeCharacters(String userName) {
		String replaceEscapeCharactersAtUserLoginString = realmConfig
				.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_REPLACE_ESCAPE_CHARACTERS_AT_USER_LOGIN);

		if (replaceEscapeCharactersAtUserLoginString != null) {
			replaceEscapeCharactersAtUserLogin = Boolean
					.parseBoolean(replaceEscapeCharactersAtUserLoginString);
			if (log.isDebugEnabled()) {
				log.debug("Replace escape characters at userlogin is condifured to: "
						+ replaceEscapeCharactersAtUserLoginString);
			}
			if (replaceEscapeCharactersAtUserLogin) {
				// currently only '\' & '\\' are identified as escape characters that needs to be
				// replaced.
				return userName.replaceAll("\\\\", "\\\\\\\\");
			}
		}
		return userName;
	}

	/**
	 * {@inheritDoc}
	 */
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

	/**
	 * {@inheritDoc}
	 */
	public void updateCredential(String userName, Object newCredential, Object oldCredential)
			throws UserStoreException {

		// Check whether we have a secondary UserStoreManager setup.
		if (this.getSecondaryUserStoreManager() != null) {
			int index;
			if ((index = userName.indexOf("/")) > 0) {
				// Using the short-circuit. User name comes with the domain name.
				String domain = userName.substring(0, index);
				UserStoreManager secManager = getSecondaryUserStoreManager(domain);
				if (secManager != null) {
					// We have a secondary UserStoreManager registered for this domain.
					userName = userName.substring(index + 1);
					secManager.updateCredential(userName, oldCredential, newCredential);
                    return;
				}
			}
		}        

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

	/**
	 * {@inheritDoc}
	 */
	public void updateCredentialByAdmin(String userName, Object newCredential)
			throws UserStoreException {

		// Check whether we have a secondary UserStoreManager setup.
		if (this.getSecondaryUserStoreManager() != null) {
			int index;
			if ((index = userName.indexOf("/")) > 0) {
				// Using the short-circuit. User name comes with the domain name.
				String domain = userName.substring(0, index);
				UserStoreManager secManager = getSecondaryUserStoreManager(domain);
				if (secManager != null) {
					// We have a secondary UserStoreManager registered for this domain.
					userName = userName.substring(index + 1);
					secManager.updateCredentialByAdmin(userName, newCredential);
                    return;
				}
			}
		}

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
			if (!listener.doPostUpdateCredentialByAdmin(userName, newCredential, this)) {
				return;
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void deleteUser(String userName) throws UserStoreException {
		
		// Check whether we have a secondary UserStoreManager setup.
		if (this.getSecondaryUserStoreManager() != null) {
			int index;
			if ((index = userName.indexOf("/")) > 0) {
				// Using the short-circuit. User name comes with the domain name.
				String domain = userName.substring(0, index);
				UserStoreManager secManager = getSecondaryUserStoreManager(domain);
				if (secManager != null) {
					// We have a secondary UserStoreManager registered for this domain.
					userName = userName.substring(index + 1);
					secManager.deleteUser(userName);
					return;
				}
			}
		}

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

	/**
	 * {@inheritDoc}
	 */
	public void setUserClaimValue(String userName, String claimURI, String claimValue,
			String profileName) throws UserStoreException {


		// Check whether we have a secondary UserStoreManager setup.
		if (this.getSecondaryUserStoreManager() != null) {
			int index;
			if ((index = userName.indexOf("/")) > 0) {
				// Using the short-circuit. User name comes with the domain name.
				String domain = userName.substring(0, index);
				UserStoreManager secManager = getSecondaryUserStoreManager(domain);
				if (secManager != null) {
					// We have a secondary UserStoreManager registered for this domain.
					userName = userName.substring(index + 1);
					secManager.setUserClaimValue(userName, claimURI, claimValue, profileName);
                    return;
				}
			}
		}

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

	/**
	 * {@inheritDoc}
	 */
	public void setUserClaimValues(String userName, Map<String, String> claims, String profileName)
			throws UserStoreException {

		// Check whether we have a secondary UserStoreManager setup.
		if (this.getSecondaryUserStoreManager() != null) {
			int index;
			if ((index = userName.indexOf("/")) > 0) {
				// Using the short-circuit. User name comes with the domain name.
				String domain = userName.substring(0, index);
				UserStoreManager secManager = getSecondaryUserStoreManager(domain);
				if (secManager != null) {
					// We have a secondary UserStoreManager registered for this domain.
					userName = userName.substring(index + 1);
					secManager.setUserClaimValues(userName, claims, profileName);
                    return;
				}
			}
		}
        
		for (UserOperationEventListener listener : UMListenerServiceComponent
				.getUserOperationEventListeners()) {
			if (!listener.doPreSetUserClaimValues(userName, claims, profileName, this)) {
				return;
			}
		}

		doSetUserClaimValues(userName, claims, profileName);

		for (UserOperationEventListener listener : UMListenerServiceComponent
				.getUserOperationEventListeners()) {
			if (!listener.doPostSetUserClaimValues(userName, claims, profileName, this)) {
				return;
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void deleteUserClaimValue(String userName, String claimURI, String profileName)
			throws UserStoreException {

		// Check whether we have a secondary UserStoreManager setup.
		if (this.getSecondaryUserStoreManager() != null) {
			int index;
			if ((index = userName.indexOf("/")) > 0) {
				// Using the short-circuit. User name comes with the domain name.
				String domain = userName.substring(0, index);
				UserStoreManager secManager = getSecondaryUserStoreManager(domain);
				if (secManager != null) {
					// We have a secondary UserStoreManager registered for this domain.
					userName = userName.substring(index + 1);
					secManager.deleteUserClaimValue(userName, claimURI, profileName);
                    return;
				}
			}
		}

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

	/**
	 * {@inheritDoc}
	 */
	public void deleteUserClaimValues(String userName, String[] claims, String profileName)
			throws UserStoreException {


		// Check whether we have a secondary UserStoreManager setup.
		if (this.getSecondaryUserStoreManager() != null) {
			int index;
			if ((index = userName.indexOf("/")) > 0) {
				// Using the short-circuit. User name comes with the domain name.
				String domain = userName.substring(0, index);
				UserStoreManager secManager = getSecondaryUserStoreManager(domain);
				if (secManager != null) {
					// We have a secondary UserStoreManager registered for this domain.
					userName = userName.substring(index + 1);
					secManager.deleteUserClaimValues(userName, claims, profileName);
                    return;
				}
			}
		}

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

	/**
	 * {@inheritDoc}
	 */
	public boolean authenticate(String userName, Object credential) throws UserStoreException {
		
		boolean authenticated = false;
		boolean domainMatched = false;
        int index = userName != null ? userName.indexOf("/") : -1;
        boolean domainProvided = index > 0;

        String domain = domainProvided ? userName.substring(0, index) : null;
        userName = domainProvided ? userName.substring(index + 1) : userName;

		// Check whether we have a secondary UserStoreManager setup.
		if (this.getSecondaryUserStoreManager() != null) {
            if (domainProvided) {
                // Using the short-circuit. User name comes with the domain name.
                UserStoreManager secManager = null;

                //Get the domainName of this user store.
                String myDomain = this.realmConfig.getUserStoreProperty(
                        UserCoreConstants.RealmConfig.PROPERTY_DOMAIN_NAME);
                //If the domain given by the user is not of this user store
                if(myDomain != null && !myDomain.equals(domain)){
                    //Get the secondary user store belonging to the domain.
                    secManager = getSecondaryUserStoreManager(domain);
                }

				if (secManager != null) {
					// We have a secondary UserStoreManager registered for this domain.
					domainMatched = true;
					return secManager.authenticate(userName, credential);
				}
			}
		}

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

		if (!domainMatched) {
			// We are here due to two reason. Either there is no secondary UserStoreManager or no
			// domain name provided with user name.
			// Let's authenticate with the primary UserStoreManager.
			authenticated = doAuthenticate(userName, credential);

            // If authentication fails in the previous step and if the user has not specified a domain
            // - then we need to execute chained UserStoreManagers recursively.
            if (!authenticated && !domainProvided && this.getSecondaryUserStoreManager() != null) {
				authenticated = this.getSecondaryUserStoreManager().authenticate(userName,
						credential);
			}
		}

		for (UserOperationEventListener listener : UMListenerServiceComponent
				.getUserOperationEventListeners()) {

			authenticated = listener.doPostAuthenticate(userName, authenticated, this);
			if (!authenticated) {
				return false;
			}
		}

		if (log.isDebugEnabled()) {
			if (!authenticated) {
				log.debug("Authentication failure. Wrong username or password is provided.");
			}
		}

		return authenticated;
	}

	/**
	 * {@inheritDoc}
	 */
	public void addUser(String userName, Object credential, String[] roleList,
			Map<String, String> claims, String profileName, boolean requirePasswordChange)
			throws UserStoreException {
		
		// Check whether we have a secondary UserStoreManager setup.
		if (this.getSecondaryUserStoreManager() != null) {
			int index;
			if ((index = userName.indexOf("/")) > 0) {
				// Using the short-circuit. User name comes with the domain name.
				String domain = userName.substring(0, index);
				UserStoreManager secManager = getSecondaryUserStoreManager(domain);
				if (secManager != null) {
					// We have a secondary UserStoreManager registered for this domain.
					userName = userName.substring(index + 1);
					secManager.addUser(userName, credential, roleList, claims, profileName,requirePasswordChange);
					return;
				}
			}
		}

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
			if (!listener.doPostAddUser(userName, credential, roleList, claims, profileName, this)) {
				return;
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void addUser(String userName, Object credential, String[] roleList,
			Map<String, String> claims, String profileName) throws UserStoreException {
		
		// Check whether we have a secondary UserStoreManager setup.
		if (this.getSecondaryUserStoreManager() != null) {
			int index;
			if ((index = userName.indexOf("/")) > 0) {
				// Using the short-circuit. User name comes with the domain name.
				String domain = userName.substring(0, index);
				UserStoreManager secManager = getSecondaryUserStoreManager(domain);
				if (secManager != null) {
					// We have a secondary UserStoreManager registered for this domain.
					userName = userName.substring(index + 1);
					secManager.addUser(userName, credential, roleList, claims, profileName);
					return;
				}
			}
		}

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
			if (!listener.doPostAddUser(userName, credential, roleList, claims, profileName, this)) {
				return;
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void updateUserListOfRole(String roleName, String[] deletedUsers, String[] newUsers)
			throws UserStoreException {

        // Check whether we have a secondary UserStoreManager setup.
		if (this.getSecondaryUserStoreManager() != null) {
			int index;
			if ((index = roleName.indexOf("/")) > 0) {
				// Using the short-circuit. User name comes with the domain name.
				String domain = roleName.substring(0, index);
				UserStoreManager secManager = getSecondaryUserStoreManager(domain);
				if (secManager != null) {
					// We have a secondary UserStoreManager registered for this domain.
                    secManager.updateUserListOfRole(roleName, deletedUsers, newUsers);
                    return;
				}
			}
		}

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

		for (UserOperationEventListener listener : UMListenerServiceComponent
				.getUserOperationEventListeners()) {
			if (!listener.doPreUpdateUserListOfRole(roleName, deletedUsers, newUsers, this)) {
				return;
			}
		}

		doUpdateUserListOfRole(roleName, deletedUsers, newUsers);

		for (UserOperationEventListener listener : UMListenerServiceComponent
				.getUserOperationEventListeners()) {
			if (!listener.doPostUpdateUserListOfRole(roleName, deletedUsers, newUsers, this)) {
				return;
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void updateRoleListOfUser(String userName, String[] deletedRoles, String[] newRoles)
			throws UserStoreException {

        // Check whether we have a secondary UserStoreManager setup.
		if (this.getSecondaryUserStoreManager() != null) {
			int index;
			if ((index = userName.indexOf("/")) > 0) {
				// Using the short-circuit. User name comes with the domain name.
				String domain = userName.substring(0, index);
				UserStoreManager secManager = getSecondaryUserStoreManager(domain);
				if (secManager != null) {
					// We have a secondary UserStoreManager registered for this domain.
                    userName = userName.substring(index + 1);
                    secManager.updateRoleListOfUser(userName, deletedRoles, newRoles);
                    return;
				}
			}
		}

        if (deletedRoles != null) {
			Arrays.sort(deletedRoles);
			if (Arrays.binarySearch(deletedRoles, realmConfig.getEveryOneRoleName()) > -1) {
				log.error("An attempt to remove " + userName + " user from Everyone role ");
				throw new UserStoreException("Everyone role can not be updated");
			}

			if (realmConfig.getAdminUserName().equals(userName)
					&& Arrays.binarySearch(deletedRoles, realmConfig.getAdminRoleName()) > -1) {
				log.error("An attempt to remove Admin user from Admin role ");
				throw new UserStoreException("Cannot remove Admin user from Admin role");
			}
		}

		for (UserOperationEventListener listener : UMListenerServiceComponent
				.getUserOperationEventListeners()) {
			if (!listener.doPreUpdateRoleListOfUser(userName, deletedRoles, newRoles, this)) {
				return;
			}
		}

		doUpdateRoleListOfUser(userName, deletedRoles, newRoles);

		for (UserOperationEventListener listener : UMListenerServiceComponent
				.getUserOperationEventListeners()) {
			if (!listener.doPostUpdateRoleListOfUser(userName, deletedRoles, newRoles, this)) {
				return;
			}
		}

	}

	/**
	 * {@inheritDoc}
	 */
	public void updateRoleName(String roleName, String newRoleName) throws UserStoreException {

        // Check whether we have a secondary UserStoreManager setup.
        if (this.getSecondaryUserStoreManager() != null) {
            int index;
            if ((index = roleName.indexOf("/")) > 0) {
                // Using the short-circuit. User name comes with the domain name.
                String domain = roleName.substring(0, index);
                UserStoreManager secManager = getSecondaryUserStoreManager(domain);
                if (secManager != null) {
                    // We have a secondary UserStoreManager registered for this domain.
                    secManager.updateRoleName(roleName, newRoleName);
                    return;
                }
            }
        }
        
        for (UserOperationEventListener listener : UMListenerServiceComponent
				.getUserOperationEventListeners()) {
			if (!listener.doPreUpdateRoleName(roleName, newRoleName, this)) {
				return;
			}
		}

		doUpdateRoleName(roleName, newRoleName);

		for (UserOperationEventListener listener : UMListenerServiceComponent
				.getUserOperationEventListeners()) {
			if (!listener.doPostUpdateRoleName(roleName, newRoleName, this)) {
				return;
			}
		}
	}

	/**
	 * Retrieves a list of user names for given user's property in user profile
	 * 
	 * @param property user property in user profile
	 * @param value value of property
	 * @param profileName profile name, can be null. If null the default profile is considered.
	 * @return An array of user names
	 * @throws UserStoreException if the operation failed
	 */
	public abstract String[] getUserListFromProperties(String property, String value,
			String profileName) throws UserStoreException;

	/**
	 * Given the user name and a credential object, the implementation code must validate whether
	 * the user is authenticated.
	 * 
	 * @param userName The user name
	 * @param credential The credential of a user
	 * @return If the value is true the provided credential match with the user name. False is
	 *         returned for invalid credential, invalid user name and mismatching credential with
	 *         user name.
	 * @throws UserStoreException An unexpected exception has occurred
	 */
	public abstract boolean doAuthenticate(String userName, Object credential)
			throws UserStoreException;

	/**
	 * Add a user to the user store.
	 * 
	 * @param userName User name of the user
	 * @param credential The credential/password of the user
	 * @param roleList The roles that user belongs
	 * @param claims Properties of the user
	 * @param profileName profile name, can be null. If null the default profile is considered.
	 * @throws UserStoreException An unexpected exception has occurred
	 */
	public abstract void doAddUser(String userName, Object credential, String[] roleList,
			Map<String, String> claims, String profileName) throws UserStoreException;

	public abstract void doAddUser(String userName, Object credential, String[] roleList,
			Map<String, String> claims, String profileName, boolean requirePasswordChange)
			throws UserStoreException;

	/**
	 * Update the credential/password of the user
	 * 
	 * @param userName The user name
	 * @param newCredential The new credential/password
	 * @param oldCredential The old credential/password
	 * @throws UserStoreException An unexpected exception has occurred
	 */
	public abstract void doUpdateCredential(String userName, Object newCredential,
			Object oldCredential) throws UserStoreException;

	/**
	 * Update credential/password by the admin of another user
	 * 
	 * @param userName The user name
	 * @param newCredential The new credential
	 * @throws UserStoreException An unexpected exception has occurred
	 */
	public abstract void doUpdateCredentialByAdmin(String userName, Object newCredential)
			throws UserStoreException;

	/**
	 * Delete the user with the given user name
	 * 
	 * @param userName The user name
	 * @throws UserStoreException An unexpected exception has occurred
	 */
	public abstract void doDeleteUser(String userName) throws UserStoreException;

	/**
	 * Set a single user claim value
	 * 
	 * @param userName The user name
	 * @param claimURI The claim URI
	 * @param claimValue The value
	 * @param profileName The profile name, can be null. If null the default profile is considered.
	 * @throws UserStoreException An unexpected exception has occurred
	 */
	public abstract void doSetUserClaimValue(String userName, String claimURI, String claimValue,
			String profileName) throws UserStoreException;

	/**
	 * Set many user claim values
	 * 
	 * @param userName The user name
	 * @param claims Map of claim URIs against values
	 * @param profileName The profile name, can be null. If null the default profile is considered.
	 * @throws UserStoreException An unexpected exception has occurred
	 */
	public abstract void doSetUserClaimValues(String userName, Map<String, String> claims,
			String profileName) throws UserStoreException;

	/**
	 * Delete a single user claim value
	 * 
	 * @param userName The user name
	 * @param claimURI Name of the claim
	 * @param profileName The profile name, can be null. If null the default profile is considered.
	 * @throws UserStoreException An unexpected exception has occurred
	 */
	public abstract void doDeleteUserClaimValue(String userName, String claimURI, String profileName)
			throws UserStoreException;

	/**
	 * Delete many user claim values.
	 * 
	 * @param userName The user name
	 * @param claims URIs of the claims to be deleted.
	 * @param profileName The profile name, can be null. If null the default profile is considered.
	 * @throws UserStoreException An unexpected exception has occurred
	 */
	public abstract void doDeleteUserClaimValues(String userName, String[] claims,
			String profileName) throws UserStoreException;

	/**
	 * Update user list of a particular role
	 * 
	 * @param roleName The role name
	 * @param deletedUsers Array of user names, that is going to be removed from the role
	 * @param newUsers Array of user names, that is going to be added to the role
	 * @throws UserStoreException An unexpected exception has occurred
	 */
	public abstract void doUpdateUserListOfRole(String roleName, String[] deletedUsers,
			String[] newUsers) throws UserStoreException;

	/**
	 * Update role list of a particular user
	 * 
	 * @param userName The user name
	 * @param deletedRoles Array of role names, that is going to be removed from the user
	 * @param newRoles Array of role names, that is going to be added to the user
	 * @throws UserStoreException An unexpected exception has occurred
	 */
	public abstract void doUpdateRoleListOfUser(String userName, String[] deletedRoles,
			String[] newRoles) throws UserStoreException;

	/**
	 * Only gets the internal roles of the user.
	 * 
	 * @param userName Name of the user - who we need to find roles.
	 * @return
	 * @throws UserStoreException
	 */
	public abstract String[] getInternalRoleListOfUser(String userName) throws UserStoreException;

	/**
	 * Only gets the external roles of the user.
	 * 
	 * @param userName Name of the user - who we need to find roles.
	 * @return
	 * @throws UserStoreException
	 */
	public abstract List<String> getExternalRoleListOfUser(String userName)
			throws UserStoreException;

	/**
	 * Getter method for claim manager property specifically to be used in the implementations of
	 * UserOperationEventListener implementations
	 * 
	 * @return
	 */
	public ClaimManager getClaimManager() {
		return claimManager;
	}

	/**
	 * Adds a role to the system.
	 * 
	 * @param roleName The role name.
	 * @param userList the list of the users.
	 * @param permissions The permissions of the role.
	 * @throws org.wso2.carbon.user.core.UserStoreException
	 * 
	 */
	public void addRole(String roleName, String[] userList, Permission[] permissions)
			throws UserStoreException {

        // Check whether we have a secondary UserStoreManager setup.
		if (this.getSecondaryUserStoreManager() != null) {
			int index;
			if ((index = roleName.indexOf("/")) > 0) {
				// Using the short-circuit. User name comes with the domain name.
				String domain = roleName.substring(0, index);
				UserStoreManager secManager = getSecondaryUserStoreManager(domain);
				if (secManager != null) {
					// We have a secondary UserStoreManager registered for this domain.
					secManager.addRole(roleName,userList,permissions);
                    return;
				}
			}
		}

		for (UserOperationEventListener listener : UMListenerServiceComponent
				.getUserOperationEventListeners()) {
			if (!listener.doPreAddRole(roleName, userList, permissions, this)) {
				return;
			}
		}

		doAddRole(roleName, userList, permissions);

		for (UserOperationEventListener listener : UMListenerServiceComponent
				.getUserOperationEventListeners()) {
			if (!listener.doPostAddRole(roleName, userList, permissions, this)) {
				return;
			}
		}
	}

	/**
	 * Delete the role with the given role name
	 * 
	 * @param roleName The role name
	 * @throws org.wso2.carbon.user.core.UserStoreException
	 * 
	 */
	public void deleteRole(String roleName) throws UserStoreException {

        // Check whether we have a secondary UserStoreManager setup.
		if (this.getSecondaryUserStoreManager() != null) {
			int index;
			if ((index = roleName.indexOf("/")) > 0) {
				// Using the short-circuit. User name comes with the domain name.
				String domain = roleName.substring(0, index);
				UserStoreManager secManager = getSecondaryUserStoreManager(domain);
				if (secManager != null) {
					// We have a secondary UserStoreManager registered for this domain.
					secManager.deleteRole(roleName);
                    return;
				}
			}
		}

        for (UserOperationEventListener listener : UMListenerServiceComponent
				.getUserOperationEventListeners()) {
			if (!listener.doPreDeleteRole(roleName, this)) {
				return;
			}
		}

		doDeleteRole(roleName);

		for (UserOperationEventListener listener : UMListenerServiceComponent
				.getUserOperationEventListeners()) {
			if (!listener.doPostDeleteRole(roleName, this)) {
				return;
			}
		}
	}

	/**
	 * Add role with a list of users and permissions provided.
	 * 
	 * @param roleName
	 * @param userList
	 * @param permissions
	 * @throws UserStoreException
	 */
	public abstract void doAddRole(String roleName, String[] userList, Permission[] permissions)
			throws UserStoreException;

	/**
	 * delete the role.
	 * 
	 * @param roleName
	 * @throws UserStoreException
	 */
	public abstract void doDeleteRole(String roleName) throws UserStoreException;

	/**
	 * update the role name with the new name
	 * 
	 * @param roleName
	 * @param newRoleName
	 * @throws UserStoreException
	 */
	public abstract void doUpdateRoleName(String roleName, String newRoleName)
			throws UserStoreException;

	/**
	 * 
	 * @return
	 */
	public boolean isSCIMEnabled() {
		String scimEnabled = realmConfig
				.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_SCIM_ENABLED);
		if (scimEnabled != null) {
			return Boolean.parseBoolean(scimEnabled);
		} else {
			return false;
		}
	}

	/**
	 * 
	 * @return
	 */
	public String getEveryOneRoleName() {
		return realmConfig.getEveryOneRoleName();
	}

	/**
	 * 
	 * @return
	 */
	public String getAdminRoleName() {
		return realmConfig.getAdminRoleName();
	}

	/**
     * 
     */
	public UserStoreManager getSecondaryUserStoreManager() {
		return secondaryUserStoreManager;
	}

	/**
     * 
     */
	public UserStoreManager getSecondaryUserStoreManager(String userDomain) {
		return userStoreManagerHolder.get(userDomain);
	}

	/**
	 * 
	 */
	public void setSecondaryUserStoreManager(UserStoreManager secondaryUserStoreManager) {
		this.secondaryUserStoreManager = secondaryUserStoreManager;
	}

	/**
	 * 
	 */
	public void addSecondaryUserStoreManager(String userDomain, UserStoreManager userStoreManager) {
		userStoreManagerHolder.put(userDomain, userStoreManager);
	}

	/**
	 * 
	 */
	public String[] getAllSecondaryRoles() throws UserStoreException {
		UserStoreManager secondary = this.getSecondaryUserStoreManager();
		List<String> roleList = new ArrayList<String>();
		while (secondary != null) {
			String[] roles = secondary.getRoleNames(true);
			if (roles != null && roles.length > 0) {
				Collections.addAll(roleList, roles);
			}
			secondary = secondary.getSecondaryUserStoreManager();
		}
		return roleList.toArray(new String[roleList.size()]);
	}

    public RoleDTO[] getAllSecondaryRoleDTOs() throws UserStoreException {
        UserStoreManager secondary = this.getSecondaryUserStoreManager();
        List<RoleDTO> roleList = new ArrayList<RoleDTO>();
        while (secondary != null) {
            String domain = secondary.getRealmConfiguration().getUserStoreProperty(
                    UserCoreConstants.RealmConfig.PROPERTY_DOMAIN_NAME);
            String[] roles = secondary.getRoleNames(true);
            if (roles != null && roles.length > 0) {
                Collections.addAll(roleList, UserCoreUtil.convertRoleNamesToRoleDTO(roles, domain));
            }
            secondary = secondary.getSecondaryUserStoreManager();
        }
        return roleList.toArray(new RoleDTO[roleList.size()]);
    }

//	/**
//	 *   //TODO check reason for this method?  
//	 * @param userName
//	 * @return
//	 * @throws UserStoreException
//	 */
//	public List<String> getSecondaryRoleListOfUser(String userName) throws UserStoreException {
//		UserStoreManager secondary = (UserStoreManager) this
//				.getSecondaryUserStoreManager();
//		while (secondary != null) {
//			if (secondary.isExistingUser(userName)) {
//				return Arrays.asList(secondary.getRoleListOfUser(userName));
//			}
//			secondary = (AbstractUserStoreManager) secondary.getSecondaryUserStoreManager();
//		}
//		return new ArrayList<String>();
//	}

    protected String addDomainToUserName(String userName) {
        int index;
        if ((index = userName.indexOf("/")) < 0) {
            //domain name is not already appended, and if exist in user-mgt.xml, append it..
            String domainName = realmConfig.getUserStoreProperty(
                    UserCoreConstants.RealmConfig.PROPERTY_DOMAIN_NAME);
            if (domainName != null) {
                //append domain name if exist
                domainName = domainName + "/";
                userName = domainName + userName;
            }
        }
        return userName;
    }
}
