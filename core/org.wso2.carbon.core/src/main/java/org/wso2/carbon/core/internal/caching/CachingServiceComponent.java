package org.wso2.carbon.core.internal.caching;

import org.osgi.service.component.ComponentContext;
import org.wso2.carbon.caching.core.CacheInvalidator;
import org.wso2.carbon.core.caching.CacheInvalidationService;
import org.wso2.carbon.core.internal.CarbonCoreDataHolder;
import org.wso2.carbon.utils.ConfigurationContextService;

/**
 * @scr.component name="org.wso2.carbon.caching"
 * immediate="true"
 * @scr.reference name="config.context.service"
 * interface="org.wso2.carbon.utils.ConfigurationContextService"
 * cardinality="1..1" policy="dynamic"  bind="setConfigurationContextService"
 * unbind="unsetConfigurationContextService"
 */
public class CachingServiceComponent {
    
    private CarbonCoreDataHolder dataHolder = CarbonCoreDataHolder.getInstance();
    
    protected void activate(ComponentContext ctxt) {
        CacheInvalidationService cacheInvalidation = new CacheInvalidationService();    
        ctxt.getBundleContext().registerService(CacheInvalidator.class.getName(), cacheInvalidation, null);
    }

    protected void setConfigurationContextService(ConfigurationContextService contextService) {
        dataHolder.setMainServerConfigContext(contextService.getServerConfigContext());
    }

    protected void unsetConfigurationContextService(ConfigurationContextService contextService) {
        dataHolder.setMainServerConfigContext(null);
    }
    
}
