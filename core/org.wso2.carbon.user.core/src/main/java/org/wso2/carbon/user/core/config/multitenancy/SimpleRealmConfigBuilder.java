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

import java.util.Map;

import org.apache.axiom.om.util.UUIDGenerator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.CarbonConstants;
import org.wso2.carbon.user.api.RealmConfiguration;
import org.wso2.carbon.user.api.TenantMgtConfiguration;
import org.wso2.carbon.user.core.UserCoreConstants;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.jdbc.JDBCRealmConstants;
import org.wso2.carbon.user.core.tenant.Tenant;

public class SimpleRealmConfigBuilder implements MultiTenantRealmConfigBuilder {

    private static Log log = LogFactory.getLog(SimpleRealmConfigBuilder.class);
    
    public RealmConfiguration getRealmConfigForTenantToCreateRealm(
            RealmConfiguration bootStrapConfig, RealmConfiguration persistedConfig, int tenantId)
            throws UserStoreException {
        return persistedConfig;
    }

    public RealmConfiguration getRealmConfigForTenantToCreateRealmOnTenantCreation(
            RealmConfiguration bootStrapConfig, RealmConfiguration persistedConfig, int tenantId)
            throws UserStoreException {
        return persistedConfig;
    }

    public RealmConfiguration getRealmConfigForTenantToPersist(RealmConfiguration bootStrapConfig,
                                                               TenantMgtConfiguration
                                                                       tenantMgtConfiguration,
                                                               Tenant tenantInfo, int tenantId)
            throws UserStoreException {
        try {
            RealmConfiguration realmConfig = (RealmConfiguration)bootStrapConfig.cloneRealmConfiguration();
            // TODO :: Random password generation
            realmConfig.setAdminPassword(UUIDGenerator.getUUID());
            realmConfig.setAdminUserName(tenantInfo.getAdminName());
            realmConfig.setTenantId(tenantId);
            Map<String, String> authz = realmConfig.getAuthzProperties();
            authz.put(UserCoreConstants.RealmConfig.PROPERTY_ADMINROLE_AUTHORIZATION,
                    CarbonConstants.UI_ADMIN_PERMISSION_COLLECTION);
            realmConfig.getRealmProperties().remove(JDBCRealmConstants.DRIVER_NAME);
            realmConfig.getRealmProperties().remove(JDBCRealmConstants.URL);
            realmConfig.getRealmProperties().remove(JDBCRealmConstants.USER_NAME);
            realmConfig.getRealmProperties().remove(JDBCRealmConstants.PASSWORD);
            realmConfig.getRealmProperties().remove(JDBCRealmConstants.MAX_ACTIVE);
            realmConfig.getRealmProperties().remove(JDBCRealmConstants.MIN_IDLE);
            realmConfig.getRealmProperties().remove(JDBCRealmConstants.MAX_WAIT);
            
            return realmConfig;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new UserStoreException(e.getMessage(), e);
        }
    }

}
