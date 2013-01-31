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
package org.wso2.carbon.user.core.config.multitenancy;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.user.api.RealmConfiguration;
import org.wso2.carbon.user.api.TenantMgtConfiguration;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.tenant.Tenant;
import org.wso2.carbon.user.core.util.UserCoreUtil;

public class SimpleRealmConfigBuilder implements MultiTenantRealmConfigBuilder {

    private static Log log = LogFactory.getLog(SimpleRealmConfigBuilder.class);
    
    public RealmConfiguration getRealmConfigForTenantToCreateRealm(
            RealmConfiguration bootStrapConfig, RealmConfiguration persistedConfig, int tenantId)
            throws UserStoreException {
    	
        RealmConfiguration realmConfig;
        try {
            realmConfig = bootStrapConfig.cloneRealmConfiguration();
            realmConfig.setAdminUserName(persistedConfig.getAdminUserName());
            realmConfig.setAdminPassword(persistedConfig.getAdminPassword());
        } catch (Exception e) {
            String errorMessage = "Error while building tenant specific realm configuration" +
                                  "when creating tenant's realm.";
            log.error(errorMessage, e);
            throw new UserStoreException(errorMessage, e);
        }
        return realmConfig;
    }

    public RealmConfiguration getRealmConfigForTenantToPersist(RealmConfiguration bootStrapConfig, TenantMgtConfiguration tenantMgtConfiguration, org.wso2.carbon.user.api.Tenant tenantInfo, int tenantId) throws org.wso2.carbon.user.api.UserStoreException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.  TODO: kernel-merge
    }

    public RealmConfiguration getRealmConfigForTenantToCreateRealmOnTenantCreation(
            RealmConfiguration bootStrapConfig, RealmConfiguration persistedConfig, int tenantId)
            throws UserStoreException {
        return persistedConfig;
    }

	public RealmConfiguration getRealmConfigForTenantToPersist(RealmConfiguration bootStrapConfig,
	                                                           TenantMgtConfiguration tenantMgtConfig,
	                                                           Tenant tenantInfo, int tenantId)
	                                                                                           throws UserStoreException {
		try {
			RealmConfiguration realmConfig = bootStrapConfig.cloneRealmConfiguration();
			removePropertiesFromTenantRealmConfig(realmConfig);
			realmConfig.setAdminUserName(tenantInfo.getAdminName());
			realmConfig.setAdminPassword(UserCoreUtil.getDummyPassword());
			return realmConfig;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new UserStoreException(e.getMessage(), e);
		}
	}

	private void removePropertiesFromTenantRealmConfig(RealmConfiguration tenantRealmConfiguration) {
		tenantRealmConfiguration.getRealmProperties().clear();
		tenantRealmConfiguration.getUserStoreProperties().clear();
		tenantRealmConfiguration.getAuthzProperties().clear();
	}

}
