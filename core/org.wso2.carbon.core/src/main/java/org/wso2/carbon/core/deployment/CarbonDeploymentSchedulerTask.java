/*
*  Copyright (c) 2005-2011, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.carbon.core.deployment;

import org.apache.axis2.clustering.ClusteringAgent;
import org.apache.axis2.clustering.ClusteringFault;
import org.apache.axis2.deployment.RepositoryListener;
import org.apache.axis2.deployment.scheduler.SchedulerTask;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.wso2.carbon.core.internal.CarbonCoreDataHolder;
import org.wso2.carbon.core.multitenancy.SuperTenantCarbonContext;

/**
 * This task takes care of deployment in WSO2 Carbon servers.
 * <p/>
 * It will do a deployment synchronization, followed by hot deployment
 */
public class CarbonDeploymentSchedulerTask extends SchedulerTask {

    private static final Log log = LogFactory.getLog(CarbonDeploymentSchedulerTask.class);
    private int tenantId;
    private String tenantDomain;
    private AxisConfiguration axisConfig;
    private boolean isInitialUpdateDone;
    private boolean isRepoUpdateFailed;

    public CarbonDeploymentSchedulerTask(RepositoryListener listener,
                                         AxisConfiguration axisConfig,
                                         int tenantId,
                                         String tenantDomain) {
        super(listener, axisConfig);
        this.tenantId = tenantId;
        this.tenantDomain = tenantDomain;
        this.axisConfig = axisConfig;
    }

    public void runAxisDeployment() {
        synchronized (this) {
            super.run();
        }
    }

    @Override
    public void run() {
        try {
            SuperTenantCarbonContext.startTenantFlow();
            SuperTenantCarbonContext.getCurrentContext().setTenantId(tenantId);
            SuperTenantCarbonContext.getCurrentContext().setTenantDomain(tenantDomain);

            deploymentSyncUpdate();
            synchronized (this) {
                super.run();  // artifact meta files which need to be committed may be generated during this super.run() call
            }
            boolean isRepoChanged = deploymentSyncCommit();

            if (isRepoChanged) {
                sendRepositorySyncMessage();
            }
        } finally {
            SuperTenantCarbonContext.endTenantFlow();
        }
    }

    private void deploymentSyncUpdate() {
        if (log.isDebugEnabled()) {
            log.debug("Running deployment synchronizer update...");
        }
        BundleContext bundleContext = CarbonCoreDataHolder.getInstance().getBundleContext();
        ServiceReference reference = bundleContext.getServiceReference(DeploymentSynchronizer.class.getName());
        if (reference != null) {
            ServiceTracker serviceTracker = new ServiceTracker(bundleContext,
                    DeploymentSynchronizer.class.getName(),
                    null);
            try {
                serviceTracker.open();
                for (Object obj : serviceTracker.getServices()) {
                    DeploymentSynchronizer depsync = (DeploymentSynchronizer) obj;
                    if (!isInitialUpdateDone || isRepoUpdateFailed) {
                        depsync.update(tenantId);
                        isInitialUpdateDone = true;
                        isRepoUpdateFailed = false;
                    }
                }
            } catch (Exception e) {
                log.error("Deployment synchronization update for tenant " + tenantId + " failed", e);
            } finally {
                serviceTracker.close();
            }
        }
    }


    private boolean deploymentSyncCommit() {
        if (log.isDebugEnabled()) {
            log.debug("Running deployment synchronizer commit...");
        }
        boolean isFilesCommitted = false;
        BundleContext bundleContext = CarbonCoreDataHolder.getInstance().getBundleContext();
        ServiceReference reference = bundleContext.getServiceReference(DeploymentSynchronizer.class.getName());
        if (reference != null) {
            ServiceTracker serviceTracker = new ServiceTracker(bundleContext,
                    DeploymentSynchronizer.class.getName(),
                    null);
            try {
                serviceTracker.open();
                for (Object obj : serviceTracker.getServices()) {
                    DeploymentSynchronizer depsync = (DeploymentSynchronizer) obj;
                    isFilesCommitted = depsync.commit(tenantId);
                    if (isFilesCommitted) {
                        break;
                    }
                }
            } catch (Exception e) {
                log.error("Deployment synchronization commit for tenant " + tenantId + " failed", e);
            } finally {
                serviceTracker.close();
            }
        }
        return isFilesCommitted;
    }

    private void sendRepositorySyncMessage() {
        // For sending clustering messages we need to use the super-tenant's AxisConfig (Main Server
        // AxisConfiguration) because we are using the clustering facility offered by the ST in the
        // tenants
        ClusteringAgent clusteringAgent =
                CarbonCoreDataHolder.getInstance().getMainServerConfigContext().
                        getAxisConfiguration().getClusteringAgent();
        if (clusteringAgent != null) {
            int numberOfRetries = 0;
            while (numberOfRetries < 60) {
                try {
                    clusteringAgent.sendMessage(new SynchronizeRepositoryRequest(tenantId), true);
                    break;
                } catch (ClusteringFault e) {
                    numberOfRetries++;
                    if (numberOfRetries < 60) {
                        log.warn("Could not send SynchronizeRepositoryRequest for tenant " +
                                tenantId + ". Retry will be attempted in 2s.", e);
                    } else {
                        log.error("Could not send SynchronizeRepositoryRequest for tenant " +
                                tenantId + ". Several retries failed", e);
                    }
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }
    }

    public void setRepoUpdateFailed() {
        this.isRepoUpdateFailed = true;
    }
}
