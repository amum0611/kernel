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
package org.wso2.carbon.core.services.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.context.RegistryType;
import org.wso2.carbon.core.multitenancy.SuperTenantCarbonContext;
import org.wso2.carbon.core.services.authentication.stats.LoginAttempt;
import org.wso2.carbon.core.services.authentication.stats.LoginStatDatabase;
import org.wso2.carbon.core.services.callback.LoginSubscriptionManagerServiceImpl;
import org.wso2.carbon.core.services.internal.CarbonServicesServiceComponent;
import org.wso2.carbon.utils.ThriftSession;
import org.wso2.carbon.registry.core.RegistryConstants;
import org.wso2.carbon.registry.core.service.RegistryService;
import org.wso2.carbon.registry.core.session.UserRegistry;
import org.wso2.carbon.utils.ServerConstants;

import javax.servlet.http.HttpSession;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class CarbonAuthenticationUtil {

    private static final Log log = LogFactory.getLog(CarbonAuthenticationUtil.class);

    public static void onFailedAdminLogin(HttpSession httpSess, String username, int tenantId,
            String remoteAddress, String reason) throws Exception {
        onFailedAdminLogin(httpSess, username, tenantId, null, remoteAddress, reason);
    }
    
    public static void onFailedAdminLogin(HttpSession httpSess, String username, int tenantId,
            String tenantDomain, String remoteAddress, String reason) throws Exception {

        if (httpSess != null) {
            SuperTenantCarbonContext carbonContext = SuperTenantCarbonContext.getCurrentContext(httpSess);
            carbonContext.setUsername(username);
            carbonContext.setTenantId(tenantId);
            carbonContext.setTenantDomain(tenantDomain);
        }

        Date currentTime = Calendar.getInstance().getTime();
        SimpleDateFormat date = new SimpleDateFormat("'['yyyy-MM-dd HH:mm:ss,SSSS']'");

        log.warn("Failed Administrator login attempt \'" + username + "[" + tenantId + "]\' at "
                + date.format(currentTime) + " from IP address " + remoteAddress);

        if (httpSess != null) {
            httpSess.invalidate();
        }
        LoginAttempt loginAttempt = new LoginAttempt(username, tenantId, remoteAddress, new Date(),
                false, reason);
        LoginStatDatabase.recordLoginAttempt(loginAttempt);
    }

    public static void onSuccessAdminLogin(HttpSession httpSess, String username, int tenantId,
            String tenantDomain, String remoteAddress) throws Exception {

        RegistryService registryService = CarbonServicesServiceComponent.getRegistryService();
        UserRegistry userRegistry = registryService.getConfigUserRegistry(username, tenantId);
        UserRegistry governanceUserRegistry =
                registryService.getGovernanceUserRegistry(username, tenantId);
        UserRegistry systemRegistry = registryService.getConfigSystemRegistry(tenantId);
        UserRegistry governanceRegistry = registryService.getGovernanceSystemRegistry(tenantId);
        if (httpSess != null) {
            httpSess.setAttribute(ServerConstants.USER_LOGGED_IN, username);
            httpSess.setAttribute(RegistryConstants.ROOT_REGISTRY_INSTANCE, registryService
                    .getRegistry(username, tenantId));

            SuperTenantCarbonContext carbonContext = SuperTenantCarbonContext.getCurrentContext(httpSess);
            carbonContext.setUsername(username);
            carbonContext.setTenantDomain(tenantDomain);
            carbonContext.setTenantId(tenantId);
            carbonContext.setRegistry(RegistryType.SYSTEM_CONFIGURATION, systemRegistry);
            carbonContext.setRegistry(RegistryType.SYSTEM_GOVERNANCE, governanceRegistry);
            carbonContext.setRegistry(RegistryType.USER_CONFIGURATION, userRegistry);
            carbonContext.setRegistry(RegistryType.USER_GOVERNANCE, governanceUserRegistry);
            carbonContext.setUserRealm(governanceUserRegistry.getUserRealm());
        }
        Date currentTime = Calendar.getInstance().getTime();
        SimpleDateFormat date = new SimpleDateFormat("'['yyyy-MM-dd HH:mm:ss,SSSS']'");

        if(tenantDomain == null){
            log.info("\'" + username + "\' logged in at " +
                    date.format(currentTime) + " from IP address " + remoteAddress);
        } else {
            log.info("\'" + username + "@" + tenantDomain +" [" + tenantId + "]\' logged in at " +
                    date.format(currentTime) + " from IP address " + remoteAddress);
        }

        // trigger the callbacks subscribe to the login event
        LoginSubscriptionManagerServiceImpl loginSubscriptionManagerServiceImpl = CarbonServicesServiceComponent
                .getLoginSubscriptionManagerServiceImpl();
        UserRegistry configRegistry = CarbonServicesServiceComponent.getRegistryService()
                .getConfigSystemRegistry(tenantId);
        loginSubscriptionManagerServiceImpl.triggerEvent(configRegistry, username, tenantId);

        if (log.isDebugEnabled()) {
            log.debug("User Registry instance is set in the session for user " + username);
        }
        LoginAttempt loginAttempt = new LoginAttempt(username, tenantId, remoteAddress, new Date(),
                true, null);
        LoginStatDatabase.recordLoginAttempt(loginAttempt);
    }

    /**
     * Duplicate of the above method since the above method is coupled with HTTPSession.
     * This method populates the carbon context for the logged in user and stores it in Thrift Session.
     * Subsequent calls using the same session, will obtain the carbon context from the authenticated
     * Thrift Session.
     * @param thriftSession
     * @param username
     * @param tenantId
     * @param tenantDomain
     * @param remoteAddress
     * @throws Exception
     */
    public static void onSuccessAdminLogin(ThriftSession thriftSession, String username, int tenantId,
            String tenantDomain, String remoteAddress) throws Exception {

        RegistryService registryService = CarbonServicesServiceComponent.getRegistryService();
        UserRegistry userRegistry = registryService.getConfigUserRegistry(username, tenantId);
        UserRegistry governanceUserRegistry =
                registryService.getGovernanceUserRegistry(username, tenantId);
        UserRegistry systemRegistry = registryService.getConfigSystemRegistry(tenantId);
        UserRegistry governanceRegistry = registryService.getGovernanceSystemRegistry(tenantId);
        if (thriftSession != null) {
            thriftSession.setAttribute(ServerConstants.USER_LOGGED_IN, username);
            thriftSession.setAttribute(RegistryConstants.ROOT_REGISTRY_INSTANCE, registryService
                    .getRegistry(username, tenantId));

            SuperTenantCarbonContext carbonContext = SuperTenantCarbonContext.getCurrentContext(thriftSession);
            carbonContext.setUsername(username);
            carbonContext.setTenantDomain(tenantDomain);
            carbonContext.setTenantId(tenantId);
            carbonContext.setRegistry(RegistryType.SYSTEM_CONFIGURATION, systemRegistry);
            carbonContext.setRegistry(RegistryType.SYSTEM_GOVERNANCE, governanceRegistry);
            carbonContext.setRegistry(RegistryType.USER_CONFIGURATION, userRegistry);
            carbonContext.setRegistry(RegistryType.USER_GOVERNANCE, governanceUserRegistry);
            carbonContext.setUserRealm(governanceUserRegistry.getUserRealm());
        }

        Date currentTime = Calendar.getInstance().getTime();
        SimpleDateFormat date = new SimpleDateFormat("'['yyyy-MM-dd HH:mm:ss,SSSS']'");

        if(tenantDomain == null){
            log.info("\'" + username + "\' logged in at " +
                    date.format(currentTime) + " from IP address " + remoteAddress);
        } else {
            log.info("\'" + username + "@" + tenantDomain +" [" + tenantId + "]\' logged in at " +
                    date.format(currentTime) + " from IP address " + remoteAddress);
        }

        // trigger the callbacks subscribe to the login event
        LoginSubscriptionManagerServiceImpl loginSubscriptionManagerServiceImpl = CarbonServicesServiceComponent
                .getLoginSubscriptionManagerServiceImpl();
        UserRegistry configRegistry = CarbonServicesServiceComponent.getRegistryService()
                .getConfigSystemRegistry(tenantId);
        loginSubscriptionManagerServiceImpl.triggerEvent(configRegistry, username, tenantId);

        if (log.isDebugEnabled()) {
            log.debug("User Registry instance is set in the session for user " + username);
        }
        LoginAttempt loginAttempt = new LoginAttempt(username, tenantId, remoteAddress, new Date(),
                true, null);
        LoginStatDatabase.recordLoginAttempt(loginAttempt);
    }

}
