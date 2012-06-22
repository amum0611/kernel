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

import java.io.UnsupportedEncodingException;
import java.util.Map;

import javax.naming.Name;
import javax.naming.NameParser;
import javax.naming.NamingException;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.user.api.RealmConfiguration;
import org.wso2.carbon.user.core.UserCoreConstants;
import org.wso2.carbon.user.core.UserRealm;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.claim.ClaimManager;
import org.wso2.carbon.user.core.claim.ClaimMapping;
import org.wso2.carbon.user.core.profile.ProfileConfigurationManager;
import org.wso2.carbon.user.core.util.JNDIUtil;

/**
 * This class is responsible for manipulating Microsoft Active Directory(AD)and
 * Active Directory Light Directory Service (AD LDS)data. This class provides
 * facility to add/delete/modify/view user info in a directory server.
 */
public class ActiveDirectoryUserStoreManager extends ReadWriteLDAPUserStoreManager {

	private static Log logger = LogFactory.getLog(ActiveDirectoryUserStoreManager.class);
	private boolean isADLDSRole = false;
	private boolean isSSLConnection = false;

	public ActiveDirectoryUserStoreManager(RealmConfiguration realmConfig,
	                                       Map<String, Object> properties,
	                                       ClaimManager claimManager,
	                                       ProfileConfigurationManager profileManager,
	                                       UserRealm realm, Integer tenantId)
	                                                                         throws UserStoreException {

		super(realmConfig, properties, claimManager, profileManager, realm, tenantId);
		checkRequiredUserStoreConfigurations();
	}

	public ActiveDirectoryUserStoreManager(RealmConfiguration realmConfig,
	                                       ClaimManager claimManager,
	                                       ProfileConfigurationManager profileManager)
	                                                                                  throws UserStoreException {
		super(realmConfig, claimManager, profileManager);
		checkRequiredUserStoreConfigurations();
	}

	public void addUser(String userName, Object credential, String[] roleList,
	                    Map<String, String> claims, String profileName) throws UserStoreException {
		this.addUser(userName, credential, roleList, claims, profileName, false);
	}

	public void addUser(String userName, Object credential, String[] roleList,
	                    Map<String, String> claims, String profileName,
	                    boolean requirePasswordChange) throws UserStoreException {

		/* validity checks */
		doAddUserValidityChecks(userName, credential);

		/* getting search base directory context */
		DirContext dirContext = getSearchBaseDirectoryContext();

		/* getting add user basic attributes */
		BasicAttributes basicAttributes = getAddUserBasicAttributes(userName);

		if (!isADLDSRole) {
			// creating a disabled user account in AD DS 
			BasicAttribute userAccountControl = new BasicAttribute("userAccountControl");
			userAccountControl.add("514");
			basicAttributes.put(userAccountControl);
		}

		/* setting claims */
		setUserClaims(claims, basicAttributes);

		try {
			NameParser ldapParser = dirContext.getNameParser("");
			Name compoundName =
			                    ldapParser.parse(realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_ATTRIBUTE) +
			                                     "=" + userName);

			/* bind the user. A disabled user account with no password */
			dirContext.bind(compoundName, null, basicAttributes);
			logger.info("User " + userName + "added successfully");

			/* update the user roles */
			updateUserRoles(userName, roleList);

			/* reset the password and enable the account */
			if (!isSSLConnection) {
				logger.warn("Unsecured connection is being used. Enabling user account operation will fail");
			}

			ModificationItem[] mods = new ModificationItem[2];
			mods[0] =
			          new ModificationItem(
			                               DirContext.REPLACE_ATTRIBUTE,
			                               new BasicAttribute(
			                                                  LDAPConstants.ACTIVE_DIRECTORY_UNICODE_PASSWORD_ATTRIBUTE,
			                                                  createUnicodePassword((String) credential)));
			if (isADLDSRole) {
				mods[1] =
				          new ModificationItem(
				                               DirContext.REPLACE_ATTRIBUTE,
				                               new BasicAttribute(
				                                                  LDAPConstants.ACTIVE_DIRECTORY_MSDS_USER_ACCOUNT_DISSABLED,
				                                                  "FALSE"));
			} else {
				mods[1] =
				          new ModificationItem(
				                               DirContext.REPLACE_ATTRIBUTE,
				                               new BasicAttribute(
				                                                  LDAPConstants.ACTIVE_DIRECTORY_USER_ACCOUNT_CONTROL,
				                                                  LDAPConstants.ACTIVE_DIRECTORY_ENABLED_NORMAL_ACCOUNT));
			}
			dirContext.modifyAttributes(compoundName, mods);

		} catch (NamingException e) {
			String errorMessage =
			                      "Can not access the directory context or"
			                              + " user already exists in the system";
			logger.error(errorMessage, e);
			throw new UserStoreException(errorMessage, e);
		}

		finally {
			JNDIUtil.closeContext(dirContext);
		}
	}

	/**
	 * Sets the set of claims provided at adding users
	 * 
	 * @param claims
	 * @param basicAttributes
	 * @throws UserStoreException
	 */
	protected void setUserClaims(Map<String, String> claims, BasicAttributes basicAttributes)
	                                                                                         throws UserStoreException {
		BasicAttribute claim;

		for (Map.Entry<String, String> entry : claims.entrySet()) {
			// avoid attributes with empty values
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
			// skipping profile configuration attribute
			if (claimURI.equals(UserCoreConstants.PROFILE_CONFIGURATION)) {
				continue;
			}
			String attributeName;
			if (claimMapping != null) {
				attributeName = claimMapping.getMappedAttribute();
			} else {
				attributeName = claimURI;
			}
			claim = new BasicAttribute(attributeName);
			claim.add(claims.get(entry.getKey()));
			basicAttributes.put(claim);
		}
	}

	public void updateCredential(String userName, Object newCredential, Object oldCredential)
	                                                                                         throws UserStoreException {
		/* validity checks */
		doUpdateCredentialsValidityChecks(userName, newCredential);

		DirContext dirContext = this.connectionSource.getContext();
		String searchBase = realmConfig.getUserStoreProperty(LDAPConstants.USER_SEARCH_BASE);
		String usernameAttribute =
		                           realmConfig.getUserStoreProperty(LDAPConstants.USER_NAME_ATTRIBUTE);

		DirContext subDirContext = null;
		try {
			subDirContext = (DirContext) dirContext.lookup(searchBase);
			ModificationItem[] mods = null;

			// The user tries to change his own password
			if (oldCredential != null && newCredential != null) {
				mods = new ModificationItem[2];
				byte[] oldUnicodePassword = createUnicodePassword((String) oldCredential);
				byte[] newUnicodePassword = createUnicodePassword((String) newCredential);
				mods[0] =
				          new ModificationItem(
				                               DirContext.REMOVE_ATTRIBUTE,
				                               new BasicAttribute(
				                                                  LDAPConstants.ACTIVE_DIRECTORY_UNICODE_PASSWORD_ATTRIBUTE,
				                                                  oldUnicodePassword));
				mods[1] =
				          new ModificationItem(
				                               DirContext.ADD_ATTRIBUTE,
				                               new BasicAttribute(
				                                                  LDAPConstants.ACTIVE_DIRECTORY_UNICODE_PASSWORD_ATTRIBUTE,
				                                                  newUnicodePassword));
			} else { // Admin is changing the password
				mods = new ModificationItem[1];
				mods[0] =
				          new ModificationItem(
				                               DirContext.REPLACE_ATTRIBUTE,
				                               new BasicAttribute(
				                                                  LDAPConstants.ACTIVE_DIRECTORY_UNICODE_PASSWORD_ATTRIBUTE,
				                                                  createUnicodePassword((String) newCredential)));
			}
			subDirContext.modifyAttributes(usernameAttribute + "=" + userName, mods);

		} catch (NamingException e) {
			String error = "Can not access the directory service";
			logger.error(error, e);
			throw new UserStoreException(error, e);
		} finally {
			JNDIUtil.closeContext(subDirContext);
			JNDIUtil.closeContext(dirContext);
		}

	}

	protected void doUpdateCredentialsValidityChecks(String userName, Object newCredential)
	                                                                                       throws UserStoreException {
		super.doUpdateCredentialsValidityChecks(userName, newCredential);
		if (!isSSLConnection) {
			logger.warn("Unsecured connection is being used. Password operations will fail");
		}
	}

	/**
	 * This is to read and validate the required user store configuration for
	 * this user store manager to take decisions.
	 * 
	 * @throws UserStoreException
	 */
	protected void checkRequiredUserStoreConfigurations() throws UserStoreException {

		super.checkRequiredUserStoreConfigurations();

		String is_ADLDSRole =
		                      realmConfig.getUserStoreProperty(LDAPConstants.ACTIVE_DIRECTORY_LDS_ROLE);
		if (is_ADLDSRole == null || is_ADLDSRole.equals("")) {
			throw new UserStoreException(
			                             "Required PasswordHashMethod property is not set at the LDAP configurations");
		}
		isADLDSRole = Boolean.parseBoolean(is_ADLDSRole);

		String connectionURL = realmConfig.getUserStoreProperty(LDAPConstants.CONNECTION_URL);
		String[] array = connectionURL.split(":");
		if (!array[0].equals("ldaps")) {
			logger.warn("Connection to the Active Directory is not secure. Passowrd involved operations such as update credentials and adduser operations will fail");
		} else {
			this.isSSLConnection = true;
		}
	}

	private byte[] createUnicodePassword(String password) {
		String newQuotedPassword = "\"" + password + "\"";
		byte[] encodedPwd = null;
		try {
			encodedPwd = newQuotedPassword.getBytes("UTF-16LE");
		} catch (UnsupportedEncodingException e) {
			logger.error("Error while encoding the given password", e);
		}
		return encodedPwd;
	}

}
