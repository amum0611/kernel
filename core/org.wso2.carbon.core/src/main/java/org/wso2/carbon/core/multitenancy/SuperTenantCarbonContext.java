/*
 *  Copyright (c) 2005-2009, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.wso2.carbon.core.multitenancy;

import net.sf.jsr107cache.Cache;
import net.sf.jsr107cache.CacheManager;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.context.RegistryType;
import org.wso2.carbon.core.internal.CarbonCoreDataHolder;
import org.wso2.carbon.utils.ThriftSession;
import org.wso2.carbon.registry.api.Registry;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.api.TenantManager;
import org.wso2.carbon.user.api.UserRealm;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.carbon.utils.CarbonUtils;
import org.wso2.carbon.utils.multitenancy.CarbonContextHolder;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;

import javax.servlet.http.HttpSession;

/**
 * This provides the API for super-tenant programming around
 * <a href="http://wso2.com/products/carbon">WSO2 Carbon</a> and
 * <a href="http://wso2.com/cloud/stratos">WSO2 Stratos</a>.
 * <p/>
 * The SuperTenantCarbon context extends the behaviour of the {@link CarbonContext} and can be used
 * in place of the {@link CarbonContext}. In addition to that the super-tenant (or the system) can
 * make use of it to perform an operation on behalf of a sub-tenant. Each CarbonContext will utilize
 * an underlying {@link CarbonContextHolder} instance, which will store the actual data.
 */
@SuppressWarnings("unused")
public final class SuperTenantCarbonContext extends CarbonContext {

    private static CarbonCoreDataHolder dataHolder = CarbonCoreDataHolder.getInstance();
    private static final Log log = LogFactory.getLog(SuperTenantCarbonContext.class);

    // Private constructor accepting a CarbonContext holder.
    private SuperTenantCarbonContext(CarbonContextHolder carbonContextHolder) {
        super(carbonContextHolder);
    }

    /**
     * Starts a tenant flow. This will stack the current CarbonContext and begin a new nested flow
     * which can have an entirely different context. This is ideal for scenarios where multiple
     * super-tenant and sub-tenant phases are required within as a single block of execution.
     *
     * @see CarbonContextHolder#startTenantFlow()
     */
    public static void startTenantFlow() {
        getCurrentContext().getCarbonContextHolder().startTenantFlow();
    }

    /**
     * This will end the tenant flow and restore the previous CarbonContext.
     *
     * @see CarbonContextHolder#endTenantFlow()
     */
    public static void endTenantFlow() {
        getCurrentContext().getCarbonContextHolder().endTenantFlow();
    }

    /**
     * Obtains the CarbonContext instance stored on the CarbonContext holder.
     *
     * @return the CarbonContext instance.
     */
    public static SuperTenantCarbonContext getCurrentContext() {
        return new SuperTenantCarbonContext(null);
    }

    /**
     * Obtains the CarbonContext instance stored on the CarbonContext holder in the given Message
     * Context. If an instance does not exist, it will first be added to the Message Context.
     *
     * @param messageContext The Message Context on which the CarbonContext is found.
     *
     * @return the CarbonContext instance.
     */
    public static SuperTenantCarbonContext getCurrentContext(MessageContext messageContext) {
        return new SuperTenantCarbonContext(
                CarbonContextHolder.getCurrentCarbonContextHolder(messageContext));
    }

    /**
     * Obtains the CarbonContext instance stored on the CarbonContext holder in the given Axis2
     * Configuration Context. If an instance does not exist, it will first be added to the
     * Axis2 Configuration Context.
     *
     * @param configContext The Axis2 Configuration Context on which the CarbonContext is found.
     *
     * @return the CarbonContext instance.
     */
    public static SuperTenantCarbonContext getCurrentContext(ConfigurationContext configContext) {
        return new SuperTenantCarbonContext(
                CarbonContextHolder.getCurrentCarbonContextHolder(configContext));
    }

    /**
     * Obtains the CarbonContext instance stored on the CarbonContext holder in the given Axis2
     * Configuration. If an instance does not exist, it will first be added to the Axis2
     * Configuration.
     *
     * @param axisConfiguration The Axis2 Configuration on which the CarbonContext is found.
     *
     * @return the CarbonContext instance.
     */
    public static SuperTenantCarbonContext getCurrentContext(AxisConfiguration axisConfiguration) {
        return new SuperTenantCarbonContext(
                CarbonContextHolder.getCurrentCarbonContextHolder(axisConfiguration));
    }

    /**
     * Obtains the CarbonContext instance stored on the CarbonContext holder attached to the given
     * Axis2 Service. If an instance does not exist, it will first be attached to the Axis2 Service.
     *
     * @param axisService The Axis2 Service on which the CarbonContext is attached to.
     *
     * @return the CarbonContext instance.
     */
    public static SuperTenantCarbonContext getCurrentContext(AxisService axisService) {
        AxisConfiguration axisConfiguration = axisService.getAxisConfiguration();
        return (axisConfiguration != null) ? getCurrentContext(axisConfiguration) :
                getCurrentContext();
    }

    /**
     * Obtains the CarbonContext instance stored on the CarbonContext holder in the given HTTP
     * Session. If an instance does not exist, it will first be added to the HTTP Session
     * Configuration.
     *
     * @param httpSession The HTTP Session on which the CarbonContext is found.
     *
     * @return the CarbonContext instance.
     */
    public static SuperTenantCarbonContext getCurrentContext(HttpSession httpSession) {
        return new SuperTenantCarbonContext(
                CarbonContextHolder.getCurrentCarbonContextHolder(httpSession));
    }

    /**
     * Obtains the CarbonContext instance stored on the CarbonContext holder in the given Thrift
     * Session. If an instance does not exist, it will first be added to the Thrift Session.
     *
     * @param thriftSession The HTTP Session on which the CarbonContext is found.
     *
     * @return the CarbonContext instance.
     */
    public static SuperTenantCarbonContext getCurrentContext(ThriftSession thriftSession){
        return new SuperTenantCarbonContext(
                CarbonContextHolder.getCurrentCarbonContextHolder(thriftSession));
    }

    /**
     * Method to set the tenant id on this CarbonContext instance. This method will not
     * automatically calculate the tenant domain based on the tenant id.
     *
     * @param tenantId the tenant id.
     */
    public void setTenantId(int tenantId) {
        setTenantId(tenantId, false);
    }

    /**
     * Method to set the tenant id on this CarbonContext instance.
     *
     * @param tenantId            the tenant id.
     * @param resolveTenantDomain whether the tenant domain should be calculated based on this
     *                            tenant id.
     */
    public void setTenantId(int tenantId, boolean resolveTenantDomain) {
        getCarbonContextHolder().setTenantId(tenantId);
        if (!resolveTenantDomain || tenantId == MultitenantConstants.SUPER_TENANT_ID) {
            return;
        }
        resolveTenantDomain(tenantId);
    }

    /**
     * Method to set the username on this CarbonContext instance.
     *
     * @param username the username.
     */
    public void setUsername(String username) {
        getCarbonContextHolder().setUsername(username);
    }

    /**
     * Method to set the tenant domain on this CarbonContext instance. This method will not
     * automatically calculate the tenant id based on the tenant domain.
     *
     * @param tenantDomain the tenant domain.
     */
    public void setTenantDomain(String tenantDomain) {
        setTenantDomain(tenantDomain, false);
    }

    /**
     * Method to set the tenant domain on this CarbonContext instance.
     *
     * @param tenantDomain    the tenant domain.
     * @param resolveTenantId whether the tenant id should be calculated based on this tenant
     *                        domain.
     */
    public void setTenantDomain(String tenantDomain, boolean resolveTenantId) {
        CarbonUtils.checkSecurity();
        getCarbonContextHolder().setTenantDomain(tenantDomain);
        if (!resolveTenantId) {
            return;
        }
        resolveTenantId(tenantDomain);
    }

    /**
     * Method to obtain the tenant domain on this CarbonContext instance. This method can optionally
     * resolve the tenant domain using the tenant id that is already posses.
     *
     * @param resolve whether the tenant domain should be calculated based on the tenant id that is
     *                already known.
     *
     * @return the tenant domain.
     */
    public String getTenantDomain(boolean resolve) {
        if (resolve && getTenantDomain() == null && getTenantId() > 0) {
            resolveTenantDomain(getTenantId());
        }
        return getTenantDomain();
    }

    /**
     * Method to obtain the tenant id on this CarbonContext instance. This method can optionally
     * resolve the tenant id using the tenant domain that is already posses.
     *
     * @param resolve whether the tenant id should be calculated based on the tenant domain that is
     *                already known.
     *
     * @return the tenant id.
     */
    public int getTenantId(boolean resolve) {
        if (resolve && getTenantId() < 0 && getTenantDomain() != null) {
            resolveTenantId(getTenantDomain());
        }
        return getTenantId();
    }

    /**
     * Resolve the tenant domain using the tenant id.
     *
     * @param tenantId the tenant id.
     */
    private void resolveTenantDomain(int tenantId) {
        TenantManager tenantManager = getTenantManager();
        if (tenantManager != null) {
            try {
                log.debug("Resolving tenant domain from tenant id");
                setTenantDomain(tenantManager.getDomain(tenantId));
            } catch (UserStoreException ignored) {
                // Exceptions in here, are due to issues with DB Connections. The UM Kernel takes
                // care of logging these exceptions. For us, this is of no importance. This is
                // because we are only attempting to resolve the tenant domain, which might not
                // always be possible.
            }
        }
    }

    /**
     * Resolve the tenant id using the tenant domain.
     *
     * @param tenantDomain the tenant domain.
     */
    private void resolveTenantId(String tenantDomain) {
        TenantManager tenantManager = getTenantManager();
        if (tenantManager != null) {
            try {
                log.debug("Resolving tenant id from tenant domain");
                setTenantId(tenantManager.getTenantId(tenantDomain));
            } catch (UserStoreException ignored) {
                // Exceptions in here, are due to issues with DB Connections. The UM Kernel takes
                // care of logging these exceptions. For us, this is of no importance. This is
                // because we are only attempting to resolve the tenant id, which might not always
                // be possible.
            }
        }
    }

    /**
     * Utility method to obtain the tenant manager from the realm service. This will only work in an
     * OSGi environment.
     *
     * @return tenant manager.
     */
    private TenantManager getTenantManager() {
        try {
            RealmService realmService = dataHolder.getRealmService();
            if (realmService != null) {
                return realmService.getTenantManager();
            }
        } catch (Exception ignored) {
            // We don't mind any exception occurring here. Our intention is provide a tenant manager
            // here. It is perfectly valid to not have a tenant manager in some situations.
        }
        return null;
    }

    /**
     * Method to set an instance of a registry on this CarbonContext instance.
     *
     * @param type     the type of registry to set.
     * @param registry the registry instance.
     */
    public void setRegistry(RegistryType type, Registry registry) {
        if (registry != null) {
            switch (type) {
                case USER_CONFIGURATION:
                    log.trace("Setting config user registry instance.");
                    getCarbonContextHolder().setProperty(
                            CarbonContextHolder.CONFIG_USER_REGISTRY_INSTANCE, registry);
                    break;

                case SYSTEM_CONFIGURATION:
                    log.trace("Setting config system registry instance.");
                    getCarbonContextHolder().setProperty(
                            CarbonContextHolder.CONFIG_SYSTEM_REGISTRY_INSTANCE, registry);
                    break;

                case USER_GOVERNANCE:
                    log.trace("Setting governance user registry instance.");
                    getCarbonContextHolder().setProperty(
                            CarbonContextHolder.GOVERNANCE_USER_REGISTRY_INSTANCE, registry);
                    break;

                case SYSTEM_GOVERNANCE:
                    log.trace("Setting governance system registry instance.");
                    getCarbonContextHolder().setProperty(
                            CarbonContextHolder.GOVERNANCE_SYSTEM_REGISTRY_INSTANCE, registry);
                    break;

                case LOCAL_REPOSITORY:
                    log.trace("Setting local repository instance.");
                    getCarbonContextHolder().setProperty(
                            CarbonContextHolder.LOCAL_REPOSITORY_INSTANCE, registry);
                    break;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public Registry getRegistry(RegistryType type) {
        Registry registry = super.getRegistry(type);
        if (registry != null) {
            return registry;
        }
        switch (type) {
            case SYSTEM_CONFIGURATION:
                CarbonUtils.checkSecurity();
                try {
                    int tenantId = getTenantId();
                    if (tenantId != -1) {
                        registry =
                                dataHolder.getRegistryService().getConfigSystemRegistry(tenantId);
                        setRegistry(RegistryType.SYSTEM_CONFIGURATION, registry);
                        return registry;
                    }
                } catch (Exception ignored) {
                    // If we can't obtain an instance of the registry, we'll simply return null. The
                    // errors that lead to this situation will be logged by the Registry Kernel.
                }
                return null;

            case SYSTEM_GOVERNANCE:
                CarbonUtils.checkSecurity();
                try {
                    int tenantId = getTenantId();
                    if (tenantId != -1) {
                        registry =
                                dataHolder.getRegistryService().getGovernanceSystemRegistry(
                                        tenantId);
                        setRegistry(RegistryType.SYSTEM_GOVERNANCE, registry);
                        return registry;
                    }
                } catch (Exception ignored) {
                    // If we can't obtain an instance of the registry, we'll simply return null. The
                    // errors that lead to this situation will be logged by the Registry Kernel.
                }
                return null;

            default:
                return null;
        }
    }

    /**
     * Method to obtain a named cache instance. Please note that it is highly probable that a named
     * cache may not be pre-initialized like the default cache. When in a cluster, it might take
     * sometime for the cache to join the other members. This would result in cache misses in the
     * meantime.
     *
     * @param name the name of the cache instance.
     *
     * @return the cache instance.
     */
    public Cache getCache(String name) {
        return CacheManager.getInstance().getCache(name);
    }

    public void setUserRealm(UserRealm userRealm) {
        getCarbonContextHolder().setProperty(CarbonContextHolder.USER_REALM, userRealm);
    }
}
