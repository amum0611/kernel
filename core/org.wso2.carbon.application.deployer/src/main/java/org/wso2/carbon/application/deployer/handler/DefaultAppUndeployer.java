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
package org.wso2.carbon.application.deployer.handler;

import org.apache.axis2.engine.AxisConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.application.deployer.AppDeployerConstants;
import org.wso2.carbon.application.deployer.AppDeployerUtils;
import org.wso2.carbon.application.deployer.CarbonApplication;
import org.wso2.carbon.application.deployer.config.Artifact;
import org.wso2.carbon.application.deployer.config.CappFile;
import org.wso2.carbon.utils.CarbonUtils;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;

import java.io.File;
import java.util.List;

/**
 * This is one of the default undeployment handlers which is registered into the
 * ApplicationManager. This class handles undeployment of AAR services, JAXWS services,
 * Data services and libs.
 */
public class DefaultAppUndeployer implements AppUndeploymentHandler {

    private static final Log log = LogFactory.getLog(DefaultAppUndeployer.class);

    /**
     * Undeploys AAR, JAXWS, Data services and libs
     *
     * @param carbonApp - all information about the existing artifacts are in this instance
     * @param axisConfig - AxisConfiguration of the current tenant
     */
    public void undeployArtifacts(CarbonApplication carbonApp, AxisConfiguration axisConfig) {
        List<Artifact.Dependency> dependencies = carbonApp.getAppConfig().getApplicationArtifact()
                .getDependencies();
        undeployRecursively(dependencies, axisConfig);
    }

    /**
     * Each artifact can have it's dependencies which are also artifacts. This method searches
     * the entire tree of artifacts to undeploy default types..
     *
     * @param deps - list of deps to be searched..
     * @param axisConfig - AxisConfiguration of the current tenant
     */
    private void undeployRecursively(List<Artifact.Dependency> deps,
                                     AxisConfiguration axisConfig) {
        String artifactPath, destPath;
        String repo = axisConfig.getRepository().getPath();

        for (Artifact.Dependency dependency : deps) {
            Artifact artifact = dependency.getArtifact();
            if (artifact == null) {
                continue;
            }
            if (DefaultAppDeployer.AAR_TYPE.equals(artifact.getType())) {
                destPath = repo + File.separator + CarbonUtils.getAxis2ServicesDir(axisConfig);
            } else if (DefaultAppDeployer.DS_TYPE.equals(artifact.getType())) {
                destPath = repo + File.separator + DefaultAppDeployer.DS_DIR;
            } else if (AppDeployerConstants.CARBON_APP_TYPE.equals(artifact.getType())) {
                destPath = repo + File.separator + AppDeployerConstants.CARBON_APPS;
            } else if (artifact.getType() != null && (artifact.getType().startsWith("lib/") ||
                    DefaultAppDeployer.BUNDLE_TYPE.equals(artifact.getType())) &&
                    AppDeployerUtils.getTenantId(axisConfig) == MultitenantConstants
                            .SUPER_TENANT_ID) {
                // library un-installation is only allowed for teh super tenant
                destPath = CarbonUtils.getCarbonOSGiDropinsDir();
            } else {
                continue;
            }

            List<CappFile> files = artifact.getFiles();
            if (files.size() != 1) {
                log.error(artifact.getType() + " type must have a single file. But " +
                        files.size() + " files found.");
                continue;
            }
            String fileName = artifact.getFiles().get(0).getName();
            artifactPath = destPath + File.separator + fileName;
            File artifactFile = new File(artifactPath);
            if (artifactFile.exists() && !artifactFile.delete()) {
                log.warn("Couldn't delete App artifact file : " + artifactPath);
            }

            undeployRecursively(artifact.getDependencies(), axisConfig);
        }
    }
}
