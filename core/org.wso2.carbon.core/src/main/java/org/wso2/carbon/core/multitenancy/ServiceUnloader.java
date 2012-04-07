package org.wso2.carbon.core.multitenancy;

import org.apache.axis2.AxisFault;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.description.AxisServiceGroup;
import org.apache.axis2.description.Parameter;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.CarbonConstants;
import org.wso2.carbon.core.multitenancy.utils.TenantAxisUtils;
import org.wso2.carbon.utils.ServerConstants;
import org.wso2.carbon.utils.deployment.GhostDeployer;
import org.wso2.carbon.utils.deployment.GhostDeployerUtils;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class ServiceUnloader implements Runnable {

    private static final Log log = LogFactory.getLog(ServiceUnloader.class);

    // Maximum allowed inactive time period for services. Default is set to 10 mins
    private static final long DEFAULT_MAX_INACTIVE_INTERVAL = 10;

    private ConfigurationContext mainConfigCtx;

    public ServiceUnloader(ConfigurationContext configCtx) {
        this.mainConfigCtx = configCtx;
    }

    public void run() {
        if (mainConfigCtx == null) {
            return;
        }
        try {
            // iterate through all tenant config contexts
            Set<Map.Entry<String, ConfigurationContext>> ccEntries = TenantAxisUtils
                    .getTenantConfigurationContexts(mainConfigCtx).entrySet();
            for (Map.Entry<String, ConfigurationContext> entry : ccEntries) {
                String tenantDomain = entry.getKey();
                unloadInactiveServices(entry.getValue(), tenantDomain);
            }
            // unload from super tenant as well..
            unloadInactiveServices(mainConfigCtx, "Super Tenant");
        } catch (AxisFault axisFault) {
            log.error("Error while unloading inactive services..", axisFault);
        }
    }

    private void unloadInactiveServices(ConfigurationContext configCtx,
                                        String tenantDomain) throws AxisFault {
        AxisConfiguration axisConfig = configCtx.getAxisConfiguration();
        if (axisConfig != null) {
            // iterate through all services in the current tenant
            Collection<AxisService> services = axisConfig.getServices().values();
            for (AxisService service : services) {
                if (isSkippedServiceType(service)) {
                    continue;
                }
                // get the last usage parameter from the service
                Parameter lastUsageParam = service
                        .getParameter(CarbonConstants.SERVICE_LAST_USED_TIME);
                if (lastUsageParam != null && isInactive((Long) lastUsageParam.getValue())) {
                    // service is inactive. now we have to unload it..
                    GhostDeployer ghostDeployer = GhostDeployerUtils.getGhostDeployer(axisConfig);
                    if (ghostDeployer != null && service.getFileName() != null) {
                        AxisServiceGroup existingSG = (AxisServiceGroup) service.getParent();
                        // remove the existing actual service
                        log.info("Unloading actual Service Group : " + existingSG
                                .getServiceGroupName() + " and adding a Ghost Service Group. " +
                                "Tenant Domain: " + tenantDomain);
                        // we can't delete the configs in the registry. so keep history..
                        existingSG.addParameter(CarbonConstants.KEEP_SERVICE_HISTORY_PARAM, "true");
                        axisConfig.removeServiceGroup(existingSG.getServiceGroupName());
                        // Create the Ghost service group using the file name
                        File ghostFile = GhostDeployerUtils.getGhostFile(service.getFileName()
                                .getPath(), axisConfig);
                        AxisServiceGroup ghostServiceGroup =
                                GhostDeployerUtils.createGhostServiceGroup(axisConfig,
                                        ghostFile, service.getFileName());
                        if (ghostServiceGroup != null) {
                            // add the ghost service
                            axisConfig.addServiceGroup(ghostServiceGroup);
                        }
                    }
                }
            }
        }
    }

    private boolean isInactive(Long lastUsedTime) {
        long inactiveInterval = System.currentTimeMillis() - lastUsedTime;
        // set the default value
        long maxInactiveInterval = DEFAULT_MAX_INACTIVE_INTERVAL;
        // check the system property
        String property = System.getProperty(CarbonConstants.SERVICE_IDLE_TIME);
        if (property != null) {
            maxInactiveInterval = Long.parseLong(property);
        }
        return inactiveInterval > maxInactiveInterval * 60 * 1000;
    }

    /**
     * There are some service types that we can't unload. That is because, ghost deployer can't
     * redeploy these types later. Ex : bpel services
     *
     * @param service - AxisService instance
     * @return - true if the type of the service is a skipped one
     */
    private boolean isSkippedServiceType(AxisService service) {
        String serviceType = null;
        Parameter serviceTypeParam;
        serviceTypeParam = service.getParameter(ServerConstants.SERVICE_TYPE);
        if (serviceTypeParam != null) {
            serviceType = (String) serviceTypeParam.getValue();
        }
        // add to this check if there are more types to skip
        return serviceType != null && serviceType.equals("bpel");
    }

}
