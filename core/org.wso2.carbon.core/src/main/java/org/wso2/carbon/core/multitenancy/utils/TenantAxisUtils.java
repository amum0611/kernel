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
package org.wso2.carbon.core.multitenancy.utils;

import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.ConfigurationContextFactory;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.description.Parameter;
import org.apache.axis2.description.TransportOutDescription;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.axis2.transport.TransportListener;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;
import org.wso2.carbon.core.internal.CarbonCoreDataHolder;
import org.wso2.carbon.core.multitenancy.SuperTenantCarbonContext;
import org.wso2.carbon.core.multitenancy.TenantAxisConfigurator;
import org.wso2.carbon.core.multitenancy.transports.DummyTransportListener;
import org.wso2.carbon.core.multitenancy.transports.TenantTransportInDescription;
import org.wso2.carbon.core.multitenancy.transports.TenantTransportSender;
import org.wso2.carbon.core.transports.TransportPersistenceManager;
import org.wso2.carbon.registry.core.session.UserRegistry;
import org.wso2.carbon.user.core.tenant.Tenant;
import org.wso2.carbon.user.core.tenant.TenantManager;
import org.wso2.carbon.utils.Axis2ConfigurationContextObserver;
import org.wso2.carbon.utils.CarbonUtils;
import org.wso2.carbon.utils.ServerConstants;
import org.wso2.carbon.utils.multitenancy.CarbonContextHolder;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility methods for Tenant Operations at Axis2-level.
 */
@SuppressWarnings("unused")
public final class TenantAxisUtils {

    private static final Log log = LogFactory.getLog(TenantAxisUtils.class);
    private static final String TENANT_CONFIGURATION_CONTEXTS = "tenant.config.contexts";
    private static final String TENANT_CONFIGURATION_CONTEXTS_CREATED = "tenant.config.contexts.created";
    private static CarbonCoreDataHolder dataHolder = CarbonCoreDataHolder.getInstance();

    private TenantAxisUtils() {
    }

    /**
     * Get tenant ID from config context
     *
     * @param configCtx The config context
     * @return The tenant ID
     * @deprecated use {@link MultitenantUtils#getTenantId(ConfigurationContext)}
     */
    public static int getTenantId(ConfigurationContext configCtx) {
        return MultitenantUtils.getTenantId(configCtx);
    }

    public static AxisConfiguration getTenantAxisConfiguration(String tenant,
                                                               ConfigurationContext mainConfigCtx) {
        ConfigurationContext tenantConfigCtx = getTenantConfigurationContext(tenant, mainConfigCtx);
        if (tenantConfigCtx != null) {
            return tenantConfigCtx.getAxisConfiguration();
        }
        return null;
    }

    public static ConfigurationContext
    getTenantConfigurationContextFromUrl(String url, ConfigurationContext mainConfigCtx) {
        String tenantDomain = MultitenantUtils.getTenantDomainFromUrl(url);
        return getTenantConfigurationContext(tenantDomain, mainConfigCtx);
    }

    public static ConfigurationContext
    getTenantConfigurationContext(String tenantDomain, ConfigurationContext mainConfigCtx) {
        synchronized (tenantDomain.intern()) { // lock based on tenant domain
            Map<String, ConfigurationContext> tenantConfigContexts = getTenantConfigurationContexts(mainConfigCtx);
            ConfigurationContext tenantConfigCtx = tenantConfigContexts.get(tenantDomain);
            if (tenantConfigCtx == null) {
                try {
                    tenantConfigCtx = createTenantConfigurationContext(mainConfigCtx, tenantDomain);
                    tenantConfigContexts.put(tenantDomain, tenantConfigCtx);
                } catch (Exception e) {
                    throw new RuntimeException("Cannot create tenant ConfigurationContext for tenant " +
                                               tenantDomain, e);
                }
            }
            try {
                tenantConfigCtx.getAxisConfiguration().
                        addParameter(MultitenantConstants.LAST_ACCESSED, System.currentTimeMillis());
            } catch (AxisFault e) {
                log.error("Cannot set last accessed time for tenant " + tenantDomain, e);
            }
            return tenantConfigCtx;
        }
    }

    public static long getLastAccessed(String tenantDomain, ConfigurationContext mainConfigCtx) {
        Map<String, ConfigurationContext> tenantConfigContexts = getTenantConfigurationContexts(mainConfigCtx);
        ConfigurationContext tenantConfigCtx = tenantConfigContexts.get(tenantDomain);
        synchronized (tenantDomain.intern()) {
            if (tenantConfigCtx != null) {
                Parameter lastAccessedParam =
                        tenantConfigCtx.getAxisConfiguration().
                                getParameter(MultitenantConstants.LAST_ACCESSED);
                return lastAccessedParam == null ? -1 : (Long) lastAccessedParam.getValue();
            }
        }
        return -1;
    }

    public static void setTenantAccessed(String tenantDomain, ConfigurationContext mainConfigCtx) {
        synchronized (tenantDomain.intern()) {
            getTenantConfigurationContext(tenantDomain, mainConfigCtx);
        }
    }

    /**
     * @param url               will have pattern <some-string>/t/<tenant>/<service>?<some-params>
     * @param mainConfigContext The main ConfigurationContext from the server
     * @return The tenant's AxisService
     * @throws org.apache.axis2.AxisFault If an error occurs while retrieving the AxisService
     */
    public static AxisService getAxisService(String url, ConfigurationContext mainConfigContext)
            throws AxisFault {
        String[] strings = url.split("/");
        boolean foundTenantDelimiter = false;
        String tenant = null;
        String service = null;
        for (String str : strings) {
            if (!foundTenantDelimiter && str.equals("t")) {
                foundTenantDelimiter = true;
                continue;
            }
            if (foundTenantDelimiter & tenant == null) {
                tenant = str;
                continue;
            }
            if (tenant != null) {
                if (service == null) {
                    service = str;
                } else {
                    service += "/" + str;
                }
            }
        }
        if (service != null) {
            service = service.split("\\?")[0];
            AxisConfiguration tenantAxisConfig =
                    getTenantAxisConfiguration(tenant, mainConfigContext);
            if (tenantAxisConfig != null) {
                return tenantAxisConfig.getServiceForActivation(service);
            }
        }
        return null;
    }

    /**
     * Get all the tenant ConfigurationContexts
     *
     * @param mainConfigCtx Super-tenant Axis2 ConfigurationContext
     * @return the tenant ConfigurationContexts as a Map of "tenant domain -> ConfigurationContext"
     */
    @SuppressWarnings("unchecked")
    public static Map<String, ConfigurationContext>
    getTenantConfigurationContexts(ConfigurationContext mainConfigCtx) {
        Map<String, ConfigurationContext> tenantConfigContexts = (Map<String, ConfigurationContext>)
                mainConfigCtx.getProperty(TENANT_CONFIGURATION_CONTEXTS);
        if (tenantConfigContexts == null) {
            tenantConfigContexts = new ConcurrentHashMap<String, ConfigurationContext>();
            mainConfigCtx.setProperty(TENANT_CONFIGURATION_CONTEXTS, tenantConfigContexts);
        }
        return tenantConfigContexts;
    }

    /**
     * Set the transports for the tenants
     *
     * @param mainConfigCtx The main config context
     * @throws AxisFault If an error occurs while initializing tenant transports
     */
    @SuppressWarnings("unchecked")
    public static void initializeTenantTransports(ConfigurationContext mainConfigCtx)
            throws AxisFault {
        AxisConfiguration mainAxisConfig = mainConfigCtx.getAxisConfiguration();
        Map<String, ConfigurationContext> tenantConfigContexts =
                getTenantConfigurationContexts(mainConfigCtx);
        if (tenantConfigContexts != null) {
            for (Map.Entry<String, ConfigurationContext> entry : tenantConfigContexts.entrySet()) {
                String tenantDomain = entry.getKey();
                ConfigurationContext tenantConfigCtx = entry.getValue();
                AxisConfiguration tenantAxisConfig = tenantConfigCtx.getAxisConfiguration();
                // Add the transports that are made available in the main axis2.xml file
                setTenantTransports(mainAxisConfig, tenantDomain, tenantAxisConfig);
            }
        }
    }

    public static void setTenantTransports(AxisConfiguration mainAxisConfig, String tenantDomain,
                                           AxisConfiguration tenantAxisConfig) throws AxisFault {
        for (String transport : mainAxisConfig.getTransportsIn().keySet()) {
            TenantTransportInDescription tenantTransportIn =
                    new TenantTransportInDescription(transport);
            tenantTransportIn
                    .setMainTransportInDescription(mainAxisConfig.getTransportsIn().get(transport));
            TransportListener mainTransportListener =
                    mainAxisConfig.getTransportIn(transport).getReceiver();
            tenantTransportIn.setReceiver(new DummyTransportListener(mainTransportListener,
                                                                     tenantDomain));
            tenantAxisConfig.addTransportIn(tenantTransportIn);
        }
    }

    /**
     * Create Tenant Axis2 ConfigurationContexts & add them to the main Axis2 ConfigurationContext
     *
     * @param mainConfigCtx Super-tenant Axis2 ConfigurationContext
     * @param tenantDomain  Tenant domain (e.g. foo.com)
     * @return The newly created Tenant ConfigurationContext
     * @throws Exception If an error occurs while creating tenant ConfigurationContext
     */
    private static ConfigurationContext
    createTenantConfigurationContext(ConfigurationContext mainConfigCtx,
                                     String tenantDomain) throws Exception {
        int tenantId = getTenantId(tenantDomain);
        if (tenantId == 0 || tenantId == -1) {
            throw new Exception("Tenant " + tenantDomain + " does not exist");
        }
        Map<String, ConfigurationContext> tenantConfigContexts = getTenantConfigurationContexts(mainConfigCtx);
        ConfigurationContext tenantConfigCtx = tenantConfigContexts.get(tenantDomain);
        if (tenantConfigCtx != null) {
            return tenantConfigCtx;
        }

        AxisConfiguration mainAxisConfig = mainConfigCtx.getAxisConfiguration();

        try {
            UserRegistry tenantConfigRegistry =
                    dataHolder.getRegistryService().getConfigSystemRegistry(tenantId);
            UserRegistry tenantLocalUserRegistry =
                    dataHolder.getRegistryService().getLocalRepository(tenantId);
            TenantAxisConfigurator tenantAxisConfigurator =
                    new TenantAxisConfigurator(mainAxisConfig, tenantDomain, tenantId,
                                               tenantConfigRegistry, tenantLocalUserRegistry);
            doPreConfigContextCreation(tenantId);
            tenantConfigCtx =
                    ConfigurationContextFactory.createConfigurationContext(tenantAxisConfigurator);

            AxisConfiguration tenantAxisConfig = tenantConfigCtx.getAxisConfiguration();

            tenantConfigCtx.setServicePath(CarbonUtils.getAxis2ServicesDir(tenantAxisConfig));
            tenantConfigCtx.setContextRoot("local:/");

            TenantTransportSender transportSender = new TenantTransportSender(mainConfigCtx);
            //adding new transport outs
            // adding the two tenant specific transport senders
            TransportOutDescription httpOutDescription = new TransportOutDescription(Constants.TRANSPORT_HTTP);
            httpOutDescription.setSender(transportSender);
            tenantAxisConfig.addTransportOut(httpOutDescription);

            // adding the two tenant specific transport senders
            TransportOutDescription httpsOutDescription = new TransportOutDescription(Constants.TRANSPORT_HTTPS);
            httpsOutDescription.setSender(transportSender);
            tenantAxisConfig.addTransportOut(httpsOutDescription);

            // Set the work directory
            tenantConfigCtx.setProperty(ServerConstants.WORK_DIR,
                                        mainConfigCtx.getProperty(ServerConstants.WORK_DIR));
            SuperTenantCarbonContext.getCurrentContext(tenantConfigCtx).setTenantId(tenantId);
            new TransportPersistenceManager(tenantAxisConfig).
                    updateEnabledTransports(tenantAxisConfig.getTransportsIn().values(),
                                            tenantAxisConfig.getTransportsOut().values());

            CarbonContextHolder.getThreadLocalCarbonContextHolder().setTenantId(tenantId);
            // Notify all observers
            BundleContext bundleContext = dataHolder.getBundleContext();
            if (bundleContext != null) {
                ServiceTracker tracker =
                        new ServiceTracker(bundleContext,
                                           Axis2ConfigurationContextObserver.class.getName(), null);
                tracker.open();
                Object[] services = tracker.getServices();
                if (services != null) {
                    for (Object service : services) {
                        ((Axis2ConfigurationContextObserver) service).createdConfigurationContext(tenantConfigCtx);
                    }
                }
                tracker.close();
            }
            return tenantConfigCtx;
        } catch (Exception e) {
            String msg = "Error occurred while running deployment for tenant ";
            log.error(msg + tenantDomain, e);
            throw new Exception(msg, e);
        }
    }

    /**
     * Get the list of all active tenants in the system
     *
     * @param mainConfigCtx The main super-tenant ConfigurationContext
     * @return The list of active tenants
     * @throws Exception If an error occurs while retrieving tenants
     */
    public static List<Tenant> getActiveTenants(ConfigurationContext mainConfigCtx)
            throws Exception {
        Map<String, ConfigurationContext> tenantConfigContexts =
                getTenantConfigurationContexts(mainConfigCtx);
        List<Tenant> tenants = new ArrayList<Tenant>();
        try {
            TenantManager tenantManager =
                    dataHolder.getRealmService().getTenantManager();
            for (ConfigurationContext tenantCfgCtx : tenantConfigContexts.values()) {
                Tenant tenant = (Tenant)tenantManager.getTenant(MultitenantUtils.getTenantId(tenantCfgCtx));
                tenants.add(tenant);
            }
        } catch (Exception e) {
            String msg = "Error occurred while getting active tenant list";
            log.error(msg, e);
            throw new Exception(msg, e);
        }
        return tenants;
    }

    /**
     * Get the tenantID given the domain
     *
     * @param tenantDomain The tenant domain
     * @return The tenant ID
     * @throws Exception If an error occurs while retrieving tenant ID
     */
    private static int getTenantId(String tenantDomain) throws Exception {
        return dataHolder.getRealmService().getTenantManager().getTenantId(tenantDomain);
    }

    /**
     * Traverse the list of tenants and cleanup tenants which have been idling for longer than
     * <code>tenantIdleTimeMillis</code>
     *
     * @param tenantIdleTimeMillis The maximum tenant idle time in milliseconds
     */
    public static void cleanupTenants(long tenantIdleTimeMillis) {
        ConfigurationContext mainServerConfigContext =
                CarbonCoreDataHolder.getInstance().getMainServerConfigContext();
        if (mainServerConfigContext == null) {
            return;
        }
        Map<String, ConfigurationContext> tenantConfigContexts =
                getTenantConfigurationContexts(mainServerConfigContext);
        for (Map.Entry<String, ConfigurationContext> entry : tenantConfigContexts.entrySet()) {
            String tenantDomain = entry.getKey();
            synchronized (tenantDomain.intern()) {
                ConfigurationContext tenantCfgCtx = entry.getValue();
                Parameter lastAccessed =
                        tenantCfgCtx.getAxisConfiguration().getParameter(MultitenantConstants.LAST_ACCESSED);
                if (lastAccessed == null ||
                        System.currentTimeMillis() - (Long) lastAccessed.getValue() >= tenantIdleTimeMillis) {
                    terminateTenantConfigContext(tenantCfgCtx);
                }
            }
        }
    }

    /**
     * Calculate the tenant domain from the complete URL
     *
     * @param url - incoming URL
     * @return - Tenant domain
     */
    public static String getTenantDomain(String url) {
        String[] strings = url.split("/");
        boolean foundTenantDelimiter = false;
        String tenant = null;
        for (String str : strings) {
            if (!foundTenantDelimiter && str.equals("t")) {
                foundTenantDelimiter = true;
                continue;
            }
            if (foundTenantDelimiter) {
                tenant = str;
                break;
            }
        }
        return tenant;
    }

    /**
     * Terminate the provided Tenant ConfigurationContext
     *
     * @param tenantCfgCtx The tenant ConfigurationContext which needs to be terminated
     */
    public static void terminateTenantConfigContext(ConfigurationContext tenantCfgCtx) {
        ConfigurationContext mainServerConfigContext =
                CarbonCoreDataHolder.getInstance().getMainServerConfigContext();
        Map<String, ConfigurationContext> tenantConfigContexts =
                getTenantConfigurationContexts(mainServerConfigContext);
        String tenantDomain = SuperTenantCarbonContext.getCurrentContext(tenantCfgCtx).getTenantDomain();
        tenantCfgCtx.getAxisConfiguration().getConfigurator().cleanup();
        try {
            Thread.sleep(10000);
        } catch (InterruptedException ignored) {
        }
        try {
            doPreConfigContextTermination(tenantCfgCtx);
            tenantCfgCtx.terminate();
            doPostConfigContextTermination(tenantCfgCtx);
            tenantConfigContexts.remove(tenantDomain);
            log.info("Cleaned up tenant " + tenantDomain);
        } catch (AxisFault e) {
            log.error("Cannot cleanup ConfigurationContext of tenant " + tenantDomain, e);
        }
    }

    private static void doPreConfigContextCreation(int tenantId) {
        BundleContext bundleContext = CarbonCoreDataHolder.getInstance().getBundleContext();
        if (bundleContext != null) {
            ServiceTracker tracker =
                    new ServiceTracker(bundleContext,
                                       Axis2ConfigurationContextObserver.class.getName(), null);
            tracker.open();
            Object[] services = tracker.getServices();
            if (services != null) {
                for (Object service : services) {
                    ((Axis2ConfigurationContextObserver) service).
                            creatingConfigurationContext(tenantId);
                }
            }
            tracker.close();
        }
    }

    private static void doPreConfigContextTermination(ConfigurationContext tenantCfgCtx) {
        BundleContext bundleContext = CarbonCoreDataHolder.getInstance().getBundleContext();
        if (bundleContext != null) {
            ServiceTracker tracker =
                    new ServiceTracker(bundleContext,
                                       Axis2ConfigurationContextObserver.class.getName(), null);
            tracker.open();
            Object[] services = tracker.getServices();
            if (services != null) {
                for (Object service : services) {
                    ((Axis2ConfigurationContextObserver) service).
                            terminatingConfigurationContext(tenantCfgCtx);
                }
            }
            tracker.close();
        }
    }

    private static void doPostConfigContextTermination(ConfigurationContext tenantCfgCtx) {
        BundleContext bundleContext = CarbonCoreDataHolder.getInstance().getBundleContext();
        if (bundleContext != null) {
            ServiceTracker tracker =
                    new ServiceTracker(bundleContext,
                                       Axis2ConfigurationContextObserver.class.getName(), null);
            tracker.open();
            Object[] services = tracker.getServices();
            if (services != null) {
                for (Object service : services) {
                    ((Axis2ConfigurationContextObserver) service).
                            terminatedConfigurationContext(tenantCfgCtx);
                }
            }
            tracker.close();
        }
    }
}
