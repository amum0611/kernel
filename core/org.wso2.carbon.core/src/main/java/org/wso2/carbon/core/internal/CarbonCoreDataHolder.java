/*                                                                             
 * Copyright 2004,2005 The Apache Software Foundation.                         
 *                                                                             
 * Licensed under the Apache License, Version 2.0 (the "License");             
 * you may not use this file except in compliance with the License.            
 * You may obtain a copy of the License at                                     
 *                                                                             
 *      http://www.apache.org/licenses/LICENSE-2.0                             
 *                                                                             
 * Unless required by applicable law or agreed to in writing, software         
 * distributed under the License is distributed on an "AS IS" BASIS,           
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.    
 * See the License for the specific language governing permissions and         
 * limitations under the License.                                              
 */
package org.wso2.carbon.core.internal;

import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.engine.ListenerManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.osgi.service.http.HttpService;
import org.wso2.carbon.base.api.ServerConfigurationService;
import org.wso2.carbon.registry.core.service.RegistryService;
import org.wso2.carbon.user.core.service.RealmService;

/**
 * This singleton data holder contains all the data required by the Carbon core OSGi bundle
 */
public class CarbonCoreDataHolder {
    private static CarbonCoreDataHolder instance = new CarbonCoreDataHolder();
    private static Log log = LogFactory.getLog(CarbonCoreDataHolder.class);

    private static BundleContext bundleContext;
    private static RealmService realmService;
    private static RegistryService registryService;
    private static HttpService httpService;
    private static ListenerManager listenerManager;
    private static ConfigurationContext mainServerConfigContext;
    private static ServerConfigurationService serverConfigurationService;

    public static CarbonCoreDataHolder getInstance() {
        return instance;
    }

    private CarbonCoreDataHolder() {
    }

    public static BundleContext getBundleContext() {
        return bundleContext;
    }

    public static void setBundleContext(BundleContext bundleContext) {
        CarbonCoreDataHolder.bundleContext = bundleContext;
    }

    public static void setRealmService(RealmService realmService) {
        CarbonCoreDataHolder.realmService = realmService;
    }

    public static void setRegistryService(RegistryService registryService) {
        CarbonCoreDataHolder.registryService = registryService;
    }

    public static void setServerConfigurationService(ServerConfigurationService serverConfigurationService) {
        CarbonCoreDataHolder.serverConfigurationService = serverConfigurationService;
    }

    public static void setHttpService(HttpService httpService) {
        CarbonCoreDataHolder.httpService = httpService;
    }

    public static HttpService getHttpService() throws Exception {
        if (httpService == null) {
            String msg = "Before activating Carbon Core bundle, an instance of "
                    + HttpService.class.getName() + " should be in existance";
            log.error(msg);
            throw new Exception(msg);
        }
        return httpService;
    }

    public static RealmService getRealmService() throws Exception {
        if (realmService == null) {
            String msg = "Before activating Carbon Core bundle, an instance of "
                    + "UserRealm service should be in existance";
            log.error(msg);
            throw new Exception(msg);
        }
        return realmService;
    }

    public static RegistryService getRegistryService() throws Exception {
        if (registryService == null) {
            String msg = "Before activating Carbon Core bundle, an instance of "
                    + "RegistryService should be in existance";
            log.error(msg);
            throw new Exception(msg);
        }
        return registryService;
    }

    public static ServerConfigurationService getServerConfigurationService() {
        if (CarbonCoreDataHolder.serverConfigurationService == null) {
            String msg = "Before activating Carbon Core bundle, an instance of "
                    + "ServerConfigurationService should be in existance";
            log.error(msg);
        }
        return CarbonCoreDataHolder.serverConfigurationService;
    }

    public static ListenerManager getListenerManager() {
        return listenerManager;
    }

    public static void setListenerManager(ListenerManager listenerManager) {
        CarbonCoreDataHolder.listenerManager = listenerManager;
    }


    public static void setMainServerConfigContext(ConfigurationContext mainServerConfigContext) {
        CarbonCoreDataHolder.mainServerConfigContext = mainServerConfigContext;
    }

    public static ConfigurationContext getMainServerConfigContext() {
        return mainServerConfigContext;
    }
}
