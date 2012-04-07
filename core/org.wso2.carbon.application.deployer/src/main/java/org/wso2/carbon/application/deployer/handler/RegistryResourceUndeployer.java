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

import org.wso2.carbon.application.deployer.CarbonApplication;
import org.wso2.carbon.application.deployer.AppDeployerConstants;
import org.wso2.carbon.application.deployer.internal.ApplicationManager;
import org.wso2.carbon.application.deployer.persistence.CarbonAppPersistenceManager;
import org.wso2.carbon.application.deployer.config.ApplicationConfiguration;
import org.wso2.carbon.application.deployer.config.Artifact;
import org.wso2.carbon.application.deployer.config.RegistryConfig;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.axis2.engine.AxisConfiguration;

import java.util.List;
import java.util.ArrayList;

public class RegistryResourceUndeployer implements AppUndeploymentHandler {

    private static final Log log = LogFactory.getLog(RegistryResourceUndeployer.class);

    /**
     * Undeploys Registry resources of the given cApp
     *
     * @param carbonApp - all information about the existing artifacts are in this instance
     * @param axisConfig - AxisConfiguration of the current tenant
     */
    public void undeployArtifacts(CarbonApplication carbonApp, AxisConfiguration axisConfig) {
        ApplicationConfiguration appConfig = carbonApp.getAppConfig();
        List<Artifact.Dependency> deps = appConfig.getApplicationArtifact().getDependencies();
        
        List<Artifact> artifacts = new ArrayList<Artifact>();
        for (Artifact.Dependency dep : deps) {
            if (dep.getArtifact() != null) {
                artifacts.add(dep.getArtifact());
            }
        }
        CarbonAppPersistenceManager capm = ApplicationManager.getInstance()
                .getPersistenceManager(axisConfig);
        // undeploying registry resources in all dependent artifacts
        undeployRegistryArtifacts(capm, artifacts, carbonApp.getAppName());
    }

    /**
     * Uneploys registry artifacts recursively. A Registry artifact can exist as a sub artifact in
     * any type of artifact. Therefore, have to search recursively
     *
     * @param capm - CarbonAppPersistenceManager instance for current tenant
     * @param artifacts - list of artifacts to be undeployed
     * @param parentAppName - name of the parent app name
     */
    private void undeployRegistryArtifacts(CarbonAppPersistenceManager capm,
                                           List<Artifact> artifacts, String parentAppName) {
        for (Artifact artifact : artifacts) {
            if (RegistryResourceDeployer.REGISTRY_RESOURCE_TYPE.equals(artifact.getType())) {
                try {
                    RegistryConfig regConfig = artifact.getRegConfig();
                    if (regConfig == null) {
                        regConfig = capm.loadRegistryConfig(AppDeployerConstants.APPLICATIONS +
                                parentAppName + AppDeployerConstants.APP_DEPENDENCIES +
                                artifact.getName());
                    }
                    if (regConfig != null) {
                        capm.removeArtifactResources(regConfig);
                    }
                } catch (Exception e) {
                    log.error("Error while loading registry config for artifact " +
                            artifact.getName(), e);
                }
            }
            // set the parent name for all sub artifacts..
            List<Artifact> subArtifacts = artifact.getSubArtifacts();
            if (subArtifacts.size() != 0) {
                for (Artifact sub : subArtifacts) {
                    sub.setName(artifact.getName());
                }
            }
            undeployRegistryArtifacts(capm, subArtifacts, parentAppName);
        }
    }
}