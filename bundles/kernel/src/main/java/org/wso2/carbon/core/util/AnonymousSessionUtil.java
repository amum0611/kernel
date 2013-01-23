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
package org.wso2.carbon.core.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.CarbonException;
import org.wso2.carbon.registry.api.Registry;
import org.wso2.carbon.registry.api.RegistryException;
import org.wso2.carbon.registry.api.RegistryService;
import org.wso2.carbon.user.api.RealmService;
import org.wso2.carbon.user.api.UserRealm;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;

/**
 * When there is no authenticated session please use this calss to get the
 * registry and realm.
 * 
 * Usage : Password Callback Handler
 */
public class AnonymousSessionUtil {

    private static Log log = LogFactory.getLog(AnonymousSessionUtil.class);
    
    /**
     * @deprecated 
     */
    public static Registry getSystemRegistryByUserName(RegistryService registryService,
            RealmService realmService, String userName) throws CarbonException {
        try {
            String tenantDomain = MultitenantUtils.getTenantDomain(userName);
            return AnonymousSessionUtil.getSystemRegistryByDomainName(registryService, realmService,
                    tenantDomain);
        } catch (Exception e) {
            throw new CarbonException(e.getMessage(), e);
        }
    }
    
    /**
     * @deprecated 
     */
    public static Registry getUserRegistryByUserName(RegistryService registryService,
            RealmService realmService, String userName) throws CarbonException {
        throw new RuntimeException("kernel-merge unable to get user-registry");
        /*try {
            return registryService.getUserRegistry(userName);
        } catch (RegistryException e) {
            log.error(e.getMessage(), e);
            throw new CarbonException(e.getMessage(), e); // TODO : kernel-merge
        }*/
    
    }

    /**
     * @deprecated 
     */
    public static Registry getSystemRegistryByDomainName(RegistryService registryService,
            RealmService realmService, String domainName) throws CarbonException {
        try {
            int tenantId = realmService.getTenantManager().getTenantId(domainName);
            if (tenantId == MultitenantConstants.INVALID_TENANT_ID) {
                throw new CarbonException("Invalid domain name");
            }
            if (!realmService.getTenantManager().isTenantActive(tenantId)) {
                throw new CarbonException("Inactive tenant");
            }
            return registryService.getConfigSystemRegistry(tenantId);
        } catch (UserStoreException e) {
            log.error(e.getMessage(), e);
            throw new CarbonException(e.getMessage(), e);
        } catch (RegistryException e) {
            log.error(e.getMessage(), e);
            throw new CarbonException(e.getMessage(), e);
        }
    }

    /**
     * @deprecated 
     */
    public static Registry getUserRegistry(RegistryService registryService, String userName)
            throws CarbonException {
        throw new RuntimeException("kernel-merge unable to get user-registry");
        /*try {
            return registryService.getUserRegistry(userName);
        } catch (RegistryException e) {
            log.error(e.getMessage(), e);
            throw new CarbonException(e.getMessage(), e); //TODO kernel-merge
        }*/
    }

    public static UserRealm getRealmByUserName(RegistryService registryService,
            RealmService realmService, String userName) throws CarbonException {
        try {
            String tenantDomain = MultitenantUtils.getTenantDomain(userName);
            return AnonymousSessionUtil.getRealmByTenantDomain(registryService, realmService,
                    tenantDomain);
        } catch (Exception e) {
            throw new CarbonException(e.getMessage(), e);
        }
    }

    public static UserRealm getRealmByTenantDomain(RegistryService registryService,
            RealmService realmService, String tenantDomain) throws CarbonException {
        try {
        	if (MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
        		return realmService.getBootstrapRealm();
        	}
            int tenantId = realmService.getTenantManager().getTenantId(tenantDomain);
            if (tenantId == MultitenantConstants.INVALID_TENANT_ID) {
                log.warn("Failed to retrieve Realm for the Invalid Domain : " + tenantDomain);
                return null;
            }
            if (!realmService.getTenantManager().isTenantActive(tenantId)) {
                log.warn("Failed to retrieve Realm for the inactive tenant : " + tenantDomain);
                return null;
            }
            throw new RuntimeException("kernel-merge unable to get user-registry");
        } catch (UserStoreException e) {
            log.error(e.getMessage(), e);
            throw new CarbonException(e.getMessage(), e);
        } /*catch (RegistryException e) {
            log.error(e.getMessage(), e);
            throw new CarbonException(e.getMessage(), e);     //TODO : kernel-merge
        }*/
    }

}
