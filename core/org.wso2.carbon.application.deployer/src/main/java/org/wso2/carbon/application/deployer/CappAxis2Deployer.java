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
package org.wso2.carbon.application.deployer;

import org.apache.axis2.deployment.AbstractDeployer;
import org.apache.axis2.deployment.DeploymentException;
import org.apache.axis2.deployment.repository.util.DeploymentFileData;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.application.deployer.internal.ApplicationManager;
import org.wso2.carbon.application.deployer.persistence.CarbonAppPersistenceManager;

public class CappAxis2Deployer extends AbstractDeployer {

    private static final Log log = LogFactory.getLog(CappAxis2Deployer.class);

    private AxisConfiguration axisConfig;

    public void init(ConfigurationContext configurationContext) {
        if (log.isDebugEnabled()) {
            log.debug("Initializing Capp Axis2 Deployer..");
        }
        this.axisConfig = configurationContext.getAxisConfiguration();
        // load the existing Carbon apps from tenant registry space
        try {
            // create a persistence manager for particular tenant
            CarbonAppPersistenceManager capm = ApplicationManager.getInstance()
                    .getPersistenceManager(axisConfig);
            // load persisted cApps for this tenant
            capm.loadApps();
        } catch (Exception e) {
            log.error("Error while trying to load persisted cApps from registry", e);
        }
    }

    /**
     * Axis2 deployment engine will call this method when a .car archive is deployed. So
     * we only have to call the applicationManager to deploy it using the absolute path of
     * the deployed .car file.
     *
     * @param deploymentFileData - info about the deployed file
     * @throws DeploymentException - error while deploying cApp
     */
    public void deploy(DeploymentFileData deploymentFileData) throws DeploymentException {
        String artifactPath = deploymentFileData.getAbsolutePath();
        try {
            ApplicationManager.getInstance().deployCarbonApp(artifactPath, axisConfig);
        } catch (Exception e) {
            log.error("Error while deploying carbon application " + artifactPath, e);
        }
        super.deploy(deploymentFileData);
    }

    public void setDirectory(String s) {

    }

    public void setExtension(String s) {

    }

    /**
     * Undeploys the cApp from system when the .car file is deleted from the repository. Find
     * the relevant cApp using the file path and call the undeploy method on applicationManager.
     *
     * @param filePath - deleted .car file path
     * @throws DeploymentException
     */
    public void undeploy(String filePath) throws DeploymentException {
        String tenantId = AppDeployerUtils.getTenantIdString(axisConfig);
        String artifactPath = AppDeployerUtils.formatPath(filePath);
        CarbonApplication existingApp = null;
        for (CarbonApplication carbonApp : ApplicationManager
                .getInstance().getCarbonApps(tenantId)) {
            if (artifactPath.equals(carbonApp.getAppFilePath())) {
                existingApp = carbonApp;
                break;
            }
        }
        if (existingApp != null) {
            ApplicationManager.getInstance().undeployCarbonApp(existingApp, axisConfig);
        }
        super.undeploy(filePath);
    }

    public void cleanup() throws DeploymentException {
        // do nothing        
    }

}
