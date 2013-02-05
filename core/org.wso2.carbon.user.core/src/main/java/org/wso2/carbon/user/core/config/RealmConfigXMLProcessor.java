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
package org.wso2.carbon.user.core.config;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.wso2.carbon.CarbonConstants;
import org.wso2.carbon.CarbonException;
import org.wso2.carbon.user.api.RealmConfiguration;
import org.wso2.carbon.user.core.UserCoreConstants;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.claim.builder.ClaimBuilder;
import org.wso2.carbon.user.core.jdbc.JDBCRealmConstants;
import org.wso2.carbon.utils.CarbonUtils;
import org.wso2.securevault.SecretResolver;
import org.wso2.securevault.SecretResolverFactory;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import java.io.*;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

public class RealmConfigXMLProcessor {

    private static final Log log = LogFactory.getLog(RealmConfigXMLProcessor.class);

    public static final String REALM_CONFIG_FILE = "user-mgt.xml";

    private SecretResolver secretResolver;

    private static BundleContext bundleContext;
    InputStream inStream = null;

    public static void setBundleContext(BundleContext bundleContext) {
        RealmConfigXMLProcessor.bundleContext = bundleContext;
    }

    public RealmConfiguration buildRealmConfigurationFromFile() throws UserStoreException {
        OMElement realmElement;
        try {
            realmElement = getRealmElement();

            RealmConfiguration realmConfig = buildRealmConfiguration(realmElement);

            if (inStream != null) {
                inStream.close();
            }
            return realmConfig;
        } catch (Exception e) {
            String message = "Error while reading realm configuration from file";
            log.error(message, e);
            throw new UserStoreException(message, e);
        }

    }
    
    public RealmConfiguration buildRealmConfiguration(InputStream inStream) throws UserStoreException {
        OMElement realmElement;
        try {
            inStream = CarbonUtils.replaceSystemVariablesInXml(inStream);
            StAXOMBuilder builder = new StAXOMBuilder(inStream);
            OMElement documentElement = builder.getDocumentElement();

            realmElement = documentElement.getFirstChildWithName(new QName(
                    UserCoreConstants.RealmConfig.LOCAL_NAME_REALM));

            RealmConfiguration realmConfig = buildRealmConfiguration(realmElement);

            if (inStream != null) {
                inStream.close();
            }
            return realmConfig;
        } catch (RuntimeException e) {
            String message = "An unexpected error occurred while building the realm configuration.";
            log.error(message, e);
            throw new UserStoreException(message, e);
        } catch (Exception e) {
            String message = "Error while reading realm configuration from file";
            log.error(message, e);
            throw new UserStoreException(message, e);
        }

    }

    public RealmConfiguration buildRealmConfiguration(OMElement realmElem) {
        RealmConfiguration realmConfig = null;
        String userStoreClass = null;
        String authorizationManagerClass = null;
        String adminRoleName = null;
        String adminUserName = null;
        String adminPassword = null;
        String everyOneRoleName = null;
        String realmClass = null;
        Map<String, String> userStoreProperties = null;
        Map<String, String> authzProperties = null;
        Map<String, String> realmProperties = null;   
        boolean passwordsExternallyManaged = false;

		realmClass = (String) realmElem.getAttributeValue(new QName(
				UserCoreConstants.RealmConfig.ATTR_NAME_CLASS));
        
        OMElement mainConfig = realmElem.getFirstChildWithName(new QName(
                UserCoreConstants.RealmConfig.LOCAL_NAME_CONFIGURATION));
        realmProperties = getChildPropertyElements(mainConfig, secretResolver);
        String dbUrl = constructDatabaseURL(realmProperties.get(JDBCRealmConstants.URL));
        realmProperties.put(JDBCRealmConstants.URL, dbUrl);
        
		OMElement adminUser = mainConfig.getFirstChildWithName(new QName(
				UserCoreConstants.RealmConfig.LOCAL_NAME_ADMIN_USER));
		adminUserName = adminUser.getFirstChildWithName(
				new QName(UserCoreConstants.RealmConfig.LOCAL_NAME_USER_NAME)).getText();
		adminPassword = adminUser.getFirstChildWithName(
				new QName(UserCoreConstants.RealmConfig.LOCAL_NAME_PASSWORD)).getText();
		if (secretResolver != null && secretResolver.isInitialized()
				&& secretResolver.isTokenProtected("UserManager.AdminUser.Password")) {
			adminPassword = secretResolver.resolve("UserManager.AdminUser.Password");
		}
		adminRoleName = mainConfig.getFirstChildWithName(
				new QName(UserCoreConstants.RealmConfig.LOCAL_NAME_ADMIN_ROLE)).getText();
		everyOneRoleName = mainConfig.getFirstChildWithName(
				new QName(UserCoreConstants.RealmConfig.LOCAL_NAME_EVERYONE_ROLE)).getText();

		OMElement authzConfig = realmElem.getFirstChildWithName(new QName(
				UserCoreConstants.RealmConfig.LOCAL_NAME_ATHZ_MANAGER));
		authorizationManagerClass = authzConfig.getAttributeValue(new QName(
				UserCoreConstants.RealmConfig.ATTR_NAME_CLASS));
		authzProperties = getChildPropertyElements(authzConfig, null);

		Iterator<OMElement> iterator = realmElem.getChildrenWithName(new QName(
				UserCoreConstants.RealmConfig.LOCAL_NAME_USER_STORE_MANAGER));
		
		RealmConfiguration primaryConfig = null;
		RealmConfiguration tmpConfig = null;


		for (;iterator.hasNext();) {
			OMElement usaConfig = iterator.next();
			userStoreClass = usaConfig.getAttributeValue(new QName(
					UserCoreConstants.RealmConfig.ATTR_NAME_CLASS));
			userStoreProperties = getChildPropertyElements(usaConfig, secretResolver);

			String sIsPasswordExternallyManaged = userStoreProperties
					.get(UserCoreConstants.RealmConfig.LOCAL_PASSWORDS_EXTERNALLY_MANAGED);

            Map<String, String> multipleCredentialsProperties = getMultipleCredentialsProperties(usaConfig);

            if (null != sIsPasswordExternallyManaged
					&& !sIsPasswordExternallyManaged.trim().equals("")) {
				passwordsExternallyManaged = Boolean.parseBoolean(sIsPasswordExternallyManaged);
			} else {
				if (log.isDebugEnabled()) {
					log.debug("External password management is disabled.");
				}
			}

			realmConfig = new RealmConfiguration();
			realmConfig.setRealmClassName(realmClass);
			realmConfig.setUserStoreClass(userStoreClass);
			realmConfig.setAuthorizationManagerClass(authorizationManagerClass);
			realmConfig.setAdminRoleName(adminRoleName);
			realmConfig.setAdminUserName(adminUserName);
			realmConfig.setAdminPassword(adminPassword);
			realmConfig.setEveryOneRoleName(everyOneRoleName);
			realmConfig.setUserStoreProperties(userStoreProperties);
			realmConfig.setAuthzProperties(authzProperties);
			realmConfig.setRealmProperties(realmProperties);
			realmConfig.setPasswordsExternallyManaged(passwordsExternallyManaged);
            realmConfig.addMultipleCredentialProperties(userStoreClass, multipleCredentialsProperties);

			if (realmConfig
					.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_MAX_USER_LIST) == null) {
				realmConfig.getUserStoreProperties().put(
						UserCoreConstants.RealmConfig.PROPERTY_MAX_USER_LIST,
						UserCoreConstants.RealmConfig.PROPERTY_VALUE_DEFAULT_MAX_COUNT);
			}

			if (realmConfig.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_READ_ONLY) == null) {
				realmConfig.getUserStoreProperties().put(
						UserCoreConstants.RealmConfig.PROPERTY_READ_ONLY,
						UserCoreConstants.RealmConfig.PROPERTY_VALUE_DEFAULT_READ_ONLY);
			}
			
			if (primaryConfig == null) {
				primaryConfig = realmConfig;
			} else {
				tmpConfig.setSecondaryRealmConfig(realmConfig);
			}
			
			tmpConfig = realmConfig;			
		}
                    
        return primaryConfig;
    }

    private String constructDatabaseURL(String url) {
        String path;
        if (url != null && url.contains(CarbonConstants.CARBON_HOME_PARAMETER)) {
            File carbonHomeDir;
            carbonHomeDir = new File(CarbonUtils.getCarbonHome());
            path = carbonHomeDir.getPath();
            path = path.replaceAll(Pattern.quote("\\"), "/");
            if (carbonHomeDir.exists() && carbonHomeDir.isDirectory()) {
                url = url.replaceAll(Pattern.quote(CarbonConstants.CARBON_HOME_PARAMETER), path);
            } else {
                log.warn("carbon home invalid");
                String[] tempStrings1 = url.split(Pattern
                        .quote(CarbonConstants.CARBON_HOME_PARAMETER));
                String dbUrl = tempStrings1[1];
                String[] tempStrings2 = dbUrl.split("/");
                for (int i = 0; i < tempStrings2.length - 1; i++) {
                    url = tempStrings1[0] + tempStrings2[i] + "/";
                }
                url = url + tempStrings2[tempStrings2.length - 1];
            }
        }
        return url;
    }

    private Map<String, String> getChildPropertyElements(OMElement omElement,
						SecretResolver secretResolver) {
        Map<String, String> map = new HashMap<String, String>();
        Iterator<?> ite = omElement.getChildrenWithName(new QName(
                UserCoreConstants.RealmConfig.LOCAL_NAME_PROPERTY));
        while (ite.hasNext()) {
            OMElement propElem = (OMElement) ite.next();
            String propName = propElem.getAttributeValue(new QName(
                    UserCoreConstants.RealmConfig.ATTR_NAME_PROP_NAME));
            String propValue = propElem.getText();
            if (secretResolver != null && secretResolver.isInitialized()) {
                if(secretResolver.isTokenProtected("UserManager.Configuration.Property." + propName)) {
                    propValue = secretResolver.resolve("UserManager.Configuration.Property." + propName);
                }
                if(secretResolver.isTokenProtected("UserStoreManager.Property." + propName)) {
                    propValue = secretResolver.resolve("UserStoreManager.Property." + propName);
                }
            }
            map.put(propName, propValue);
        }
        return map;
    }

    private Map<String, String> getMultipleCredentialsProperties(OMElement omElement) {
        Map<String, String> map = new HashMap<String, String>();
        OMElement multipleCredentialsEl = omElement.getFirstChildWithName(new QName(UserCoreConstants.RealmConfig.LOCAL_NAME_MULTIPLE_CREDENTIALS));
        if (multipleCredentialsEl != null) {
            Iterator<?> ite = multipleCredentialsEl.
                    getChildrenWithLocalName(UserCoreConstants.RealmConfig.LOCAL_NAME_CREDENTIAL);
            while (ite.hasNext()) {

                Object OMObj = ite.next();
                if (!(OMObj instanceof OMElement)) {
                    continue;
                }
                OMElement credsElem = (OMElement) OMObj;
                String credsType = credsElem.getAttributeValue(new QName(
                        UserCoreConstants.RealmConfig.ATTR_NAME_TYPE));
                String credsClassName = credsElem.getText();
                map.put(credsType, credsClassName);
            }
        }
        return map;
    }

    // TODO get a factory or a stream writer - add more props
    public static OMElement serialize(RealmConfiguration realmConfig) {
        OMFactory factory = OMAbstractFactory.getOMFactory();
        OMElement rootElement = factory.createOMElement(new QName(
                UserCoreConstants.RealmConfig.LOCAL_NAME_USER_MANAGER));
        OMElement realmElement = factory.createOMElement(new QName(
                UserCoreConstants.RealmConfig.LOCAL_NAME_REALM));
        String realmName = realmConfig.getRealmClassName();
        
        OMAttribute propAttr = factory.createOMAttribute(
                UserCoreConstants.RealmConfig.ATTR_NAME_PROP_NAME, null, realmName);
        realmElement.addAttribute(propAttr);
        
        rootElement.addChild(realmElement);

        OMElement mainConfig = factory.createOMElement(new QName(
                UserCoreConstants.RealmConfig.LOCAL_NAME_CONFIGURATION));
        realmElement.addChild(mainConfig);

        OMElement adminUser = factory.createOMElement(new QName(
                UserCoreConstants.RealmConfig.LOCAL_NAME_ADMIN_USER));
        OMElement adminUserNameElem = factory.createOMElement(new QName(
                UserCoreConstants.RealmConfig.LOCAL_NAME_USER_NAME));
        adminUserNameElem.setText(realmConfig.getAdminUserName());
        OMElement adminPasswordElem = factory.createOMElement(new QName(
                UserCoreConstants.RealmConfig.LOCAL_NAME_PASSWORD));
        adminPasswordElem.setText(realmConfig.getAdminPassword());
        adminUser.addChild(adminUserNameElem);
        adminUser.addChild(adminPasswordElem);
        mainConfig.addChild(adminUser);

        OMElement adminRoleNameElem = factory.createOMElement(new QName(
                UserCoreConstants.RealmConfig.LOCAL_NAME_ADMIN_ROLE));
        adminRoleNameElem.setText(realmConfig.getAdminRoleName());
        mainConfig.addChild(adminRoleNameElem);

        OMElement systemUserNameElem = factory.createOMElement(new QName(
                UserCoreConstants.RealmConfig.LOCAL_NAME_SYSTEM_USER_NAME));
        mainConfig.addChild(systemUserNameElem);

        // adding the anonymous user
        OMElement anonymousUserEle = factory.createOMElement(new QName(
                UserCoreConstants.RealmConfig.LOCAL_NAME_ANONYMOUS_USER));
        OMElement anonymousUserNameElem = factory.createOMElement(new QName(
                UserCoreConstants.RealmConfig.LOCAL_NAME_USER_NAME));
        OMElement anonymousPasswordElem = factory.createOMElement(new QName(
                UserCoreConstants.RealmConfig.LOCAL_NAME_PASSWORD));
        anonymousUserEle.addChild(anonymousUserNameElem);
        anonymousUserEle.addChild(anonymousPasswordElem);
        mainConfig.addChild(anonymousUserEle);

        // adding the everyone role
        OMElement everyoneRoleNameElem = factory.createOMElement(new QName(
                UserCoreConstants.RealmConfig.LOCAL_NAME_EVERYONE_ROLE));
        everyoneRoleNameElem.setText(realmConfig.getEveryOneRoleName());
        mainConfig.addChild(everyoneRoleNameElem);
        
        // add the main config properties
        addPropertyElements(factory, mainConfig,
                null, realmConfig.getRealmProperties());
        // add the user store manager properties
        OMElement userStoreManagerElement = factory.createOMElement(new QName(
                UserCoreConstants.RealmConfig.LOCAL_NAME_USER_STORE_MANAGER));
        realmElement.addChild(userStoreManagerElement);
        addPropertyElements(factory, userStoreManagerElement,
                realmConfig.getUserStoreClass(),
                realmConfig.getUserStoreProperties());
        // add the user authorizer properties
        OMElement authorizerManagerElement = factory.createOMElement(new QName(
                UserCoreConstants.RealmConfig.LOCAL_NAME_ATHZ_MANAGER));
        realmElement.addChild(authorizerManagerElement);
        addPropertyElements(factory, authorizerManagerElement,
                realmConfig.getAuthorizationManagerClass(),
                realmConfig.getAuthzProperties());

        return rootElement;
    }

    private static void addPropertyElements(OMFactory factory,
                                                            OMElement parent,
                                                            String className,
                                                            Map<String, String> properties) {
        if (className != null) {
            parent.addAttribute(UserCoreConstants.RealmConfig.ATTR_NAME_CLASS, className, null);
        }
        Iterator<Map.Entry<String, String>> ite = properties.entrySet().iterator();
        while (ite.hasNext()) {
            Map.Entry<String, String> entry = ite.next();
            String name = entry.getKey();
            String value = entry.getValue();
            OMElement propElem = factory.createOMElement(new QName(
                    UserCoreConstants.RealmConfig.LOCAL_NAME_PROPERTY));
            OMAttribute propAttr = factory.createOMAttribute(
                    UserCoreConstants.RealmConfig.ATTR_NAME_PROP_NAME, null, name);
            propElem.addAttribute(propAttr);
            propElem.setText(value);
            parent.addChild(propElem);
        }
    }

    private OMElement getRealmElement() throws XMLStreamException, IOException, UserStoreException {
        String carbonHome = CarbonUtils.getCarbonHome();
        StAXOMBuilder builder = null;

        if (carbonHome != null) {
            File profileConfigXml = new File(CarbonUtils.getCarbonConfigDirPath(),
                    REALM_CONFIG_FILE);
            if (profileConfigXml.exists()) {
                inStream = new FileInputStream(profileConfigXml);
            }
        } else {
            inStream = RealmConfigXMLProcessor.class.getResourceAsStream(REALM_CONFIG_FILE);
        }

        String warningMessage = "";
        if (inStream == null) {
            URL url;
            if (bundleContext != null) {
                if ((url = bundleContext.getBundle().getResource(REALM_CONFIG_FILE)) != null){
                    inStream = url.openStream();
                } else {
                    warningMessage = "Bundle context could not find resource " + REALM_CONFIG_FILE +
                        " or user does not have sufficient permission to access the resource.";
                }
            } else {
                if ((url = ClaimBuilder.class.getResource(REALM_CONFIG_FILE)) != null){
                    inStream = url.openStream();
                    log.error("Using the internal realm configuration. Strictly for non-production purposes.");
                } else {
                    warningMessage = "ClaimBuilder could not find resource " + REALM_CONFIG_FILE +
                        " or user does not have sufficient permission to access the resource.";
                }
            }
        }

        if (inStream == null) {
            String message = "Profile configuration not found. Cause - " + warningMessage;
            if (log.isDebugEnabled()) {
                log.debug(message);
            }
            throw new FileNotFoundException(message);
        }
        
        try {
            inStream = CarbonUtils.replaceSystemVariablesInXml(inStream);
        } catch (CarbonException e) {
            throw new UserStoreException(e.getMessage(), e);
        }
        builder = new StAXOMBuilder(inStream);
        OMElement documentElement = builder.getDocumentElement();

        setSecretResolver(documentElement);

        OMElement realmElement = documentElement.getFirstChildWithName(new QName(
                UserCoreConstants.RealmConfig.LOCAL_NAME_REALM));

        return realmElement;
    }

    public void setSecretResolver(OMElement rootElement) {
        secretResolver = SecretResolverFactory.create(rootElement, true);
    }
}
