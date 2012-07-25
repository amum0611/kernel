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

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.user.api.RealmConfiguration;
import org.wso2.carbon.user.core.AuthorizationManager;
import org.wso2.carbon.user.core.UserCoreConstants;
import org.wso2.carbon.user.core.UserRealm;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.UserStoreManager;
import org.wso2.carbon.user.core.claim.ClaimManager;
import org.wso2.carbon.user.core.claim.ClaimMapping;
import org.wso2.carbon.user.core.claim.DefaultClaimManager;
import org.wso2.carbon.user.core.claim.builder.ClaimBuilder;
import org.wso2.carbon.user.core.claim.builder.ClaimBuilderException;
import org.wso2.carbon.user.core.claim.dao.ClaimDAO;
import org.wso2.carbon.user.core.config.RealmConfigXMLProcessor;
import org.wso2.carbon.user.core.profile.DefaultProfileConfigurationManager;
import org.wso2.carbon.user.core.profile.ProfileConfiguration;
import org.wso2.carbon.user.core.profile.ProfileConfigurationManager;
import org.wso2.carbon.user.core.profile.builder.ProfileBuilderException;
import org.wso2.carbon.user.core.profile.builder.ProfileConfigurationBuilder;
import org.wso2.carbon.user.core.profile.dao.ProfileConfigDAO;
import org.wso2.carbon.user.core.util.DatabaseUtil;

public class DefaultRealm implements UserRealm {

    private static Log log = LogFactory.getLog(DefaultRealm.class);

    private ClaimManager claimMan = null;
    private ProfileConfigurationManager profileMan = null;
    private DataSource dataSource = null;
    private RealmConfiguration realmConfig = null;
    private int tenantId;

    private UserStoreManager userStoreManager = null;
    private AuthorizationManager authzManager = null;
    private Map<String, Object> properties = null;

    /**
     * Usage of this method is found on tests.
     * @param configBean - Configuration details of the realm
     * @param claimMappings
     * @param profileConfigs
     * @param tenantId
     * @throws UserStoreException
     */
    public void init(RealmConfiguration configBean, Map<String, ClaimMapping> claimMappings,
                     Map<String, ProfileConfiguration> profileConfigs,
                     int tenantId) throws UserStoreException {

        if (claimMappings == null) {
            claimMappings = loadDefaultClaimMapping();
        }

        if (profileConfigs == null) {
            profileConfigs = loadDefaultProfileConfiguration();
        }

        realmConfig = configBean;
        properties = new Hashtable<String, Object>();
        this.tenantId = tenantId;
        dataSource = DatabaseUtil.getRealmDataSource(realmConfig);
        properties.put(UserCoreConstants.DATA_SOURCE, dataSource);
        claimMan = new DefaultClaimManager(claimMappings, dataSource, tenantId);
        profileMan = new DefaultProfileConfigurationManager(profileConfigs, dataSource, tenantId);
        initializeObjects();
    }

    public void init(RealmConfiguration configBean, Map<String, Object> propertiesMap, int tenantId)
            throws UserStoreException {

        if (configBean == null) {
            configBean = loadDefaultRealmConfigs();
        }

        realmConfig = configBean;
        properties = new Hashtable<String, Object>();
        this.tenantId = tenantId;
        properties = propertiesMap;
        dataSource = (DataSource)properties.get(UserCoreConstants.DATA_SOURCE);

        Map<String, ClaimMapping> claimMappings = new HashMap<String, ClaimMapping>();
        Map<String, ProfileConfiguration> profileConfigs = new HashMap<String, ProfileConfiguration>();
        populateProfileAndClaimMaps(claimMappings, profileConfigs);

        claimMan = new DefaultClaimManager(claimMappings, dataSource, tenantId);
        profileMan = new DefaultProfileConfigurationManager(profileConfigs, dataSource, tenantId);
        initializeObjects();
    }

    public UserStoreManager getUserStoreManager() throws UserStoreException {
        return userStoreManager;
    }

    public AuthorizationManager getAuthorizationManager() throws UserStoreException {
        return authzManager;
    }

    public ClaimManager getClaimManager() throws UserStoreException {
        return claimMan;
    }

    public ProfileConfigurationManager getProfileConfigurationManager() throws UserStoreException {
        return profileMan;
    }

    public void cleanUp() throws UserStoreException {
        // TODO Auto-generated method stub
    }

    public RealmConfiguration getRealmConfiguration() throws UserStoreException {
        return realmConfig;
    }
    
    private void initializeObjects() throws UserStoreException {
        try {

            String value = realmConfig.getUserStoreClass();
            if (value == null) {
                log.info("System is functioning without user store writering ability. User add/edit/delete will not work");
            } else {
                this.userStoreManager = (UserStoreManager) createObjectWithOptions(value);
            }

            value = realmConfig.getAuthorizationManagerClass();
            if (value == null) {
                String message = "System cannot continue. Authorization writer is null";
                log.error(message);
                throw new UserStoreException(message);
            }
            this.authzManager = (AuthorizationManager) createObjectWithOptions(value);

        } catch (UserStoreException e) {
            // all user store exceptions are logged at the place it is created.
            throw e;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new UserStoreException(e.getMessage(), e);
        }

    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Object createObjectWithOptions(String className) throws UserStoreException {

        Class[] initClassOpt1 = new Class[] { RealmConfiguration.class, Map.class,
                ClaimManager.class, ProfileConfigurationManager.class, UserRealm.class,
                Integer.class };
        Object[] initObjOpt1 = new Object[] { realmConfig, properties, claimMan, profileMan, this,
                tenantId };

        Class[] initClassOpt2 = new Class[] { RealmConfiguration.class, Map.class,
                ClaimManager.class, ProfileConfigurationManager.class, UserRealm.class };
        Object[] initObjOpt2 = new Object[] { realmConfig, properties, claimMan, profileMan, this };

        Class[] initClassOpt3 = new Class[] { RealmConfiguration.class, Map.class };
        Object[] initObjOpt3 = new Object[] { realmConfig, properties };

        try {
            Class clazz = Class.forName(className);
            Constructor constructor = null;
            Object newObject = null;

            if (log.isDebugEnabled()) {
                log.debug("Start initializing class with the first option");
            }

            try {
                constructor = clazz.getConstructor(initClassOpt1);
                newObject = constructor.newInstance(initObjOpt1);
                return newObject;
            } catch (NoSuchMethodException e) {
                // if not found try again.
                if (log.isDebugEnabled()) {
                    log.debug("Cannont initialize " + className + " using the option 1");
                }
            }

            if (log.isDebugEnabled()) {
                log.debug("End initializing class with the first option");
            }

            try {
                constructor = clazz.getConstructor(initClassOpt2);
                newObject = constructor.newInstance(initObjOpt2);
                return newObject;
            } catch (NoSuchMethodException e) {
                // if not found try again.
                if (log.isDebugEnabled()) {
                    log.debug("Cannont initialize " + className + " using the option 2");
                }
            }

            if (log.isDebugEnabled()) {
                log.debug("End initializing class with the second option");
            }

            try {
                constructor = clazz.getConstructor(initClassOpt3);
                newObject = constructor.newInstance(initObjOpt3);
                return newObject;
            } catch (NoSuchMethodException e) {
                // cannot initialize in any of the methods. Throw exception.
                String message = "Cannot initialize " + className + ". Error " + e.getMessage();
                log.error(message);
                throw new UserStoreException(message);
            }

        } catch (Throwable e) {
            log.error("Cannot create " + className, e);
            throw new UserStoreException(e.getMessage() + "Type " + e.getClass(), e);
        }

    }

    private RealmConfiguration loadDefaultRealmConfigs() throws UserStoreException {
        RealmConfigXMLProcessor processor = new RealmConfigXMLProcessor();
        RealmConfiguration config = processor.buildRealmConfigurationFromFile();
        return config;
    }

    private Map<String, ClaimMapping> loadDefaultClaimMapping() throws UserStoreException {
        try {
            ClaimBuilder claimBuilder = new ClaimBuilder(tenantId);
            Map<String, ClaimMapping> claimMapping = claimBuilder
                    .buildClaimMappingsFromConfigFile();
            return claimMapping;
        } catch (ClaimBuilderException e) {
            log.error(e.getMessage(), e);
            throw new UserStoreException(e.getMessage(), e);
        }
    }

    private Map<String, ProfileConfiguration> loadDefaultProfileConfiguration()
            throws UserStoreException {
        try {
            ProfileConfigurationBuilder profilBuilder = new ProfileConfigurationBuilder(tenantId);
            Map<String, ProfileConfiguration> profileConfig = profilBuilder
                    .buildProfileConfigurationFromConfigFile();
            return profileConfig;
        } catch (ProfileBuilderException e) {
            log.error(e.getMessage(), e);
            throw new UserStoreException(e.getMessage(), e);
        }
    }

    private void populateProfileAndClaimMaps(Map<String, ClaimMapping> claimMappings,
            Map<String, ProfileConfiguration> profileConfigs) throws UserStoreException {
        ClaimDAO claimDAO = new ClaimDAO(dataSource, tenantId);
        ProfileConfigDAO profileDAO = new ProfileConfigDAO(dataSource, tenantId);
        ClaimBuilder claimBuilder = new ClaimBuilder(tenantId);
        ProfileConfigurationBuilder profileBilder = new ProfileConfigurationBuilder(tenantId);

        int count = claimDAO.getDialectCount();
        if (count == 0) {
            try {
                claimMappings.putAll(claimBuilder.buildClaimMappingsFromConfigFile());
            } catch (ClaimBuilderException e) {
                String msg = "Error in building claims.";
                log.error(msg);
                throw new UserStoreException(msg, e);
            }
            claimDAO.addCliamMappings(claimMappings.values().toArray(
                    new ClaimMapping[claimMappings.size()]));
            try {
                profileConfigs.putAll(profileBilder.buildProfileConfigurationFromConfigFile());
            } catch (ProfileBuilderException e) {
                String msg = "Error in building the profile.";
                log.error(msg);
                throw new UserStoreException(msg, e);
            }
            profileDAO.addProfileConfig(profileConfigs.values().toArray(
                    new ProfileConfiguration[profileConfigs.size()]));
        } else {
            try {
                claimMappings.putAll(claimBuilder.buildClaimMappingsFromDatabase(dataSource,
                        UserCoreConstants.INTERNAL_USERSTORE));
            } catch (ClaimBuilderException e) {
                String msg = "Error in building claims.";
                log.error(msg);
                throw new UserStoreException(msg, e);
            }
            try {
                profileConfigs.putAll(profileBilder.buildProfileConfigurationFromDatabase(dataSource,
                        UserCoreConstants.INTERNAL_USERSTORE));
            } catch (ProfileBuilderException e) {
                String msg = "Error in building the profile.";
                log.error(msg);
                throw new UserStoreException(msg, e);
            }
        }
    }

}
