/*
 * Copyright (c) 2005-2012, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.carbon.tomcat.ext.realms;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.catalina.realm.GenericPrincipal;
import org.apache.catalina.realm.RealmBase;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.tomcat.ext.internal.CarbonRealmServiceHolder;
import org.wso2.carbon.user.api.UserRealmService;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.utils.multitenancy.CarbonContextHolder;

/**
 * This is a custom Tomcat realm that uses Carbon realm inside.
 * <p/>
 * It is outside the OSGi container so creates a RealmService and
 * RegistryService separately.
 * <p/>
 * Registry is needed because it is where we store the user-mgt.xml of each
 * tenant.
 * <p/>
 * A classic demonstration of Adaptor Pattern.
 */
public class CarbonTomcatRealm extends RealmBase {

    private static Log log = LogFactory.getLog(CarbonTomcatRealm.class);

    /**
     * ThreadLocal variable which keeps track of whether SaaS is enabled for the webapp which
     * is currently being served
     */
    /*private static ThreadLocal<Boolean> isSaaSEnabled = new ThreadLocal<Boolean>(){
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };*/

    /**
     * ThreadLocal variable to keep SaaS options of a webapp which is currently used.
     */
    private static ThreadLocal<HashMap> saasParamaters = new ThreadLocal<HashMap>();

    public CarbonTomcatRealm() throws Exception {
    }

    /*public void setEnableSaas(boolean value) {
        isSaaSEnabled.set(value);
    }*/

    public void setSaaSParams(Map enableSaaSParams) {
        saasParamaters.set((HashMap) enableSaaSParams);
    }

    protected String getName() {
        return getClass().getSimpleName();
    }

    protected String getPassword(String username) {
        throw new IllegalStateException("When CarbonTomcatRealm is in operation " +
                                        "this method getPassword(String) should never be called");
    }

    public Principal authenticate(String username, String response, String nonce, String nc,
                                  String cNonce, String qop, String realmName, String md5) {
        // Carbon has SHA-256 but Digested Authentication is MD5
        throw new IllegalStateException("Carbon doesn't use MD5 hashes. Can't do " +
                                        "digest authentication");
    }

    public Principal authenticate(String userName, String credential) {
        String tenantDomain = null;
        if (userName.contains("@")) {
            tenantDomain = userName.substring(userName.indexOf('@') + 1);
        }
        String tenantLessUserName;
        if(userName.lastIndexOf('@') > -1) {
            tenantLessUserName = userName.substring(0, userName.lastIndexOf('@'));
        } else {
            tenantLessUserName = userName;
        }

        // If SaaS is not enabled, do not allow users from other tenants to call this secured webapp
        if (!checkSaasAccess(tenantDomain, tenantLessUserName)) {
            String requestTenantDomain =
                    CarbonContextHolder.getCurrentCarbonContextHolder().getTenantDomain();
            if (tenantDomain != null &&
                    !tenantDomain.equals(requestTenantDomain)) {
                if (requestTenantDomain.trim().length() == 0) {
                    requestTenantDomain = "0";
                }
                log.warn("Illegal access attempt by " + userName +
                                 " to secured resource hosted by tenant " + requestTenantDomain);
                return null;
            }
        }

        try {

            UserRealmService userRealmService = CarbonRealmServiceHolder.getRealmService();
            //todo if not existent tenant was given this returns -1, handle it
            int tenantId = userRealmService.getTenantManager().getTenantId(tenantDomain);
            if (!userRealmService.getTenantUserRealm(tenantId).getUserStoreManager().
                                  authenticate(tenantLessUserName, credential)) {
                return null;
            }

            return getPrincipal(userName);
        } catch (UserStoreException e) {
            // not logging because already logged.
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * Chechk if saas mode enabled for the given tenant
     *
     * @param tenantDomain - tenant
     * @param userName - name of the user(without tenant part)
     * @return  false if saas mode denied.
     */
    private boolean checkSaasAccess(String tenantDomain, String userName) {
        HashMap saasParams = saasParamaters.get();
        Set saasParamsSet = saasParams.keySet();

        if(saasParamsSet.contains("!".concat(tenantDomain))) {
            return false;
        } else if(saasParamsSet.contains(tenantDomain)) {
            ArrayList users = (ArrayList) saasParams.get(tenantDomain);
            if (users != null && users.contains("!".concat(userName))){
                return false;
            } else if(users!= null  && !users.contains(userName)) {
                return false;
            }
        } else if(!saasParamsSet.contains("*")) {
            return false;
        }
        return true;
    }

    protected Principal getPrincipal(String userNameWithTenant) {
        return new GenericCarbonPrincipal(userNameWithTenant);
    }

    /**
     * Carbon java.security.Principal implementation
     * 
     * @see java.security.Principal
     * @see org.apache.catalina.realm.GenericPrincipal
     */
    private static class GenericCarbonPrincipal extends GenericPrincipal {
        private String tenantDomain = null;

        public GenericCarbonPrincipal(String name) {
            super(name, null);
            tenantDomain = null;
            if (name.contains("@")) {
                tenantDomain = name.substring(name.indexOf('@') + 1);
            }
        }

        // Carbon realm does not give the password out

        public String getPassword() {
            throw new IllegalStateException("When CarbonTomcatRealm is in operation " +
                                            "this method Principal.getPassword() should never be called");
        }

        public boolean hasRole(String role) {
            try {
                UserRealmService realmService = CarbonRealmServiceHolder.getRealmService();
                int tenantId = realmService.getTenantManager().getTenantId(tenantDomain);
                int indexOfAt = name.lastIndexOf('@');
                String tenantLessUserName = (indexOfAt == -1) ? name : name.substring(0, indexOfAt);
                String[] roles = 
                                 CarbonRealmServiceHolder.getRealmService().
                                                           getTenantUserRealm(tenantId).
                                                           getUserStoreManager().
                                                           getRoleListOfUser(tenantLessUserName);
                Arrays.sort(roles);
                return Arrays.binarySearch(roles, role) > -1;
            } catch (UserStoreException e) {
                log.error("Cannot check role", e);
            }
            return false;
        }
    }
}
