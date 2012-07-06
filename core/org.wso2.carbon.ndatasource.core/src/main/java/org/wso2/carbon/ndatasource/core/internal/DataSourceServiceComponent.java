/**
 *  Copyright (c) 2012, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.wso2.carbon.ndatasource.core.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.wso2.carbon.base.api.ServerConfigurationService;
import org.wso2.carbon.coordination.core.services.CoordinationService;
import org.wso2.carbon.ndatasource.core.DataSourceAxis2ConfigurationContextObserver;
import org.wso2.carbon.ndatasource.core.DataSourceManager;
import org.wso2.carbon.ndatasource.core.DataSourceService;
import org.wso2.carbon.ndatasource.core.RegistryDSAvailabilityManager;
import org.wso2.carbon.registry.core.service.RegistryService;
import org.wso2.carbon.securevault.SecretCallbackHandlerService;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.carbon.utils.Axis2ConfigurationContextObserver;

/**
* @scr.component name="org.wso2.carbon.ndatasource" immediate="true"
* @scr.reference name="registry.service"
* interface="org.wso2.carbon.registry.core.service.RegistryService" cardinality="0..1"
* policy="dynamic" bind="setRegistryService" unbind="unsetRegistryService"
* @scr.reference name="secret.callback.handler.service"
* interface="org.wso2.carbon.securevault.SecretCallbackHandlerService"
* cardinality="1..1" policy="dynamic"
* bind="setSecretCallbackHandlerService" unbind="unsetSecretCallbackHandlerService"
* @scr.reference name="user.realmservice.default"
* interface="org.wso2.carbon.user.core.service.RealmService" cardinality="0..1" policy="dynamic"
* bind="setRealmService" unbind="unsetRealmService"
* @scr.reference name="coordination.service" interface="org.wso2.carbon.coordination.core.services.CoordinationService"
* cardinality="1..1" policy="dynamic"  bind="setCoordinationService" unbind="unsetCoordinationService"
* @scr.reference name="server.configuration.service" interface="org.wso2.carbon.base.api.ServerConfigurationService"
* cardinality="0..1" policy="dynamic"  bind="setServerConfigurationService" unbind="unsetServerConfigurationService"
*/
public class DataSourceServiceComponent {
	
	private static final Log log = LogFactory.getLog(DataSourceServiceComponent.class);
	
	private static RegistryService registryService;
	
	private static RealmService realmService;
	
	private static CoordinationService coodinationService;
	
	private static SecretCallbackHandlerService secretCallbackHandlerService;
	
	private static ServerConfigurationService serverConfigurationService;
		
	private DataSourceService dataSourceService;
	
	private ComponentContext ctx;
	
	private boolean tenantUserDataSourcesInitialized;
	
	private static RegistryDSAvailabilityManager dsAvailabilityManager;
	
	protected synchronized void activate(ComponentContext ctx) {
		this.ctx = ctx;
		if (log.isDebugEnabled()) {
			log.debug("DataSourceServiceComponent activated");
		}
		/* if the user data sources are already initialized before setting the ctx,
		 * that means the services aren't registered yet, so we should do it now */
		if (this.tenantUserDataSourcesInitialized) {
			this.registerServices();
		}
	}
	
	protected synchronized void deactivate(ComponentContext ctx) {
		this.ctx = null;
		this.tenantUserDataSourcesInitialized = false;
		if (log.isDebugEnabled()) {
			log.debug("DataSourceServiceComponent deactivated");
		}
	}
	
	/**
	 * This method getting called implement some important functionality to make sure that,
	 * components which depend on the DataSourceService will always get it after the component
	 * is fully initialized.
	 */
	private void registerServices() {
		if (this.getDataSourceService() == null) {
			this.dataSourceService = new DataSourceService();
		}
		BundleContext bundleContext = this.ctx.getBundleContext();
		bundleContext.registerService(DataSourceService.class.getName(), 
				this.getDataSourceService(), null);
		bundleContext.registerService(Axis2ConfigurationContextObserver.class.getName(),
                new DataSourceAxis2ConfigurationContextObserver(), null);
	}
	
	public DataSourceService getDataSourceService() {
		return dataSourceService;
	}
	
    protected void setRealmService(RealmService realmService) {
    	if (log.isDebugEnabled()) {
    		log.debug("RealmService acquired");
    	}
    	DataSourceServiceComponent.realmService = realmService;
    	this.checkInitTenantUserDataSources();
    }
    
    protected void unsetRealmService(RealmService realmService) {
    	DataSourceServiceComponent.realmService = null;
    }
	
    public static RealmService getRealmService() {
    	return DataSourceServiceComponent.realmService;
    }
	
    protected void setRegistryService(RegistryService registryService) {
    	if (log.isDebugEnabled()) {
    		log.debug("RegistryService acquired");
    	}
    	DataSourceServiceComponent.registryService = registryService;
    	this.checkInitTenantUserDataSources();
    }

    protected void unsetRegistryService(RegistryService registryService) {
        registryService = null;
    }

    public static RegistryService getRegistryService() {
        return DataSourceServiceComponent.registryService;
    }
	
    public static SecretCallbackHandlerService getSecretCallbackHandlerService() {
    	return DataSourceServiceComponent.secretCallbackHandlerService;
    }
    
    protected void setSecretCallbackHandlerService(
            SecretCallbackHandlerService secretCallbackHandlerService) {
    	if (log.isDebugEnabled()) {
    		log.debug("SecretCallbackHandlerService acquired");
    	}
    	DataSourceServiceComponent.secretCallbackHandlerService = secretCallbackHandlerService;
    	this.initSystemDataSources();
    	this.checkInitTenantUserDataSources();
    }

    protected void unsetSecretCallbackHandlerService(
            SecretCallbackHandlerService secretCallbackHandlerService) {
    	DataSourceServiceComponent.secretCallbackHandlerService = null;
    }
    
    private void initSystemDataSources() {
    	if (log.isDebugEnabled()) {
    		log.debug("Initializing system data sources...");
    	}
    	try {
    	    DataSourceManager.getInstance().initSystemDataSources();
    	    if (log.isDebugEnabled()) {
    	    	log.debug("System data sources successfully initialized");
    	    }
    	} catch (Exception e) {
			log.error("Error in intializing system data sources: " + e.getMessage(), e);
		}    	
    }
    
    private synchronized void checkInitTenantUserDataSources() {
    	if (DataSourceServiceComponent.getRealmService() != null && 
    			DataSourceServiceComponent.getRegistryService() != null &&
    			DataSourceServiceComponent.getCoordinationService() != null &&
    			DataSourceServiceComponent.getSecretCallbackHandlerService() != null && 
    			DataSourceServiceComponent.getServerConfigurationService() != null) {
    		this.initAllTenantUserDataSources();
    	}
    }
    
    private synchronized void initAllTenantUserDataSources() {
    	try {
    		dsAvailabilityManager = new RegistryDSAvailabilityManager();
    		if (log.isDebugEnabled()) {
        		log.debug("Initializing tenant user data sources...");
        	}
    		DataSourceManager.getInstance().initAllTenants();
    	    if (log.isDebugEnabled()) {
    	    	log.debug("Tenant user data sources successfully initialized");
    	    }
    	    this.tenantUserDataSourcesInitialized = true;
    	    if (this.ctx != null) {
    	        this.registerServices();
    	    }
    	} catch (Exception e) {
			log.error("Error in intializing system data sources: " + e.getMessage(), e);
		} 
    }
    
    public boolean isTenantUserDataSourcesInitialized() {
		return tenantUserDataSourcesInitialized;
	}

	public static RegistryDSAvailabilityManager getDsAvailabilityManager() {
		return dsAvailabilityManager;
	}

	protected void setCoordinationService(CoordinationService coordinationService) {
    	if (log.isDebugEnabled()) {
    		log.debug("CoordinationService acquired");
    	}
    	DataSourceServiceComponent.coodinationService = coordinationService;
    	this.checkInitTenantUserDataSources();
    }
    
    protected void unsetCoordinationService(CoordinationService coordinationService) {
    	DataSourceServiceComponent.coodinationService = null;
    }
    
    public static CoordinationService getCoordinationService() {
    	return DataSourceServiceComponent.coodinationService;
    }
    
    public static ServerConfigurationService getServerConfigurationService() {
    	return DataSourceServiceComponent.serverConfigurationService;
    }

    protected void unsetServerConfigurationService(ServerConfigurationService serverConfigurationService) {
        DataSourceServiceComponent.serverConfigurationService = null;
    }
    
    protected void setServerConfigurationService(ServerConfigurationService serverConfigurationService) {
    	if (log.isDebugEnabled()) {
    		log.debug("ServerConfigurationService acquired");
    	}
    	DataSourceServiceComponent.serverConfigurationService = serverConfigurationService;
    	this.checkInitTenantUserDataSources();
    }
	
}
