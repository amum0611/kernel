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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.axis2.engine.AxisConfiguration;
import org.wso2.carbon.application.deployer.AppDeployerConstants;
import org.wso2.carbon.application.deployer.config.Artifact;
import org.wso2.carbon.application.deployer.config.CappFile;
import org.wso2.carbon.application.deployer.CarbonApplication;
import org.wso2.carbon.application.deployer.AppDeployerUtils;
import org.wso2.carbon.application.deployer.internal.AppDeployerServiceComponent;
import org.wso2.carbon.utils.CarbonUtils;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;
import org.osgi.framework.BundleException;
import org.osgi.framework.Bundle;

import java.util.List;
import java.util.Map;
import java.io.File;

/**
 * This is one of the default handlers which is registered into the ApplicationManager. This
 * class deploys AAR services, JAXWS services, Data services and libs.
 */
public class DefaultAppDeployer implements AppDeploymentHandler {

    private static final Log log = LogFactory.getLog(DefaultAppDeployer.class);

    public static final String AAR_TYPE = "service/axis2";
    public static final String JAXWS_TYPE = "service/jaxws";
    public static final String DS_TYPE = "service/dataservice";
    public static final String BUNDLE_TYPE = "bundle";

    public static final String JAXWS_DIR = "servicejars";
    public static final String DS_DIR = "dataservices";

    private Map<String, Boolean> acceptanceList = null;

    /**
     * Deploy the artifacts which can be deployed through this deployer (Axis2 services,
     * JAXWS services ..).
     *
     * @param carbonApp - find artifacts from this CarbonApplication instance
     * @param axisConfig - AxisConfiguration of the current tenant
     */
    public void deployArtifacts(CarbonApplication carbonApp, AxisConfiguration axisConfig) {
        List<Artifact.Dependency> dependencies = carbonApp.getAppConfig().getApplicationArtifact()
                .getDependencies();
        deployRecursively(dependencies, axisConfig);
    }

    /**
     * Installs an OSGi bundle into the OSGi environment through the bundle context..
     *
     * @param bundlePath - absolute path to the bundle to be installed..
     */
    private void installBundle(String bundlePath) {
        String bundlePathFormatted = AppDeployerUtils.formatPath(bundlePath);

        // prepare the URL
        if (bundlePathFormatted.startsWith("/")) {
            // on linux
        	bundlePathFormatted = "file://" + bundlePathFormatted;
        } else {
            // on windows
        	bundlePathFormatted = "file:///" + bundlePathFormatted;
        }
        
        try {
            Bundle bundle = AppDeployerServiceComponent
                    .getBundleContext().installBundle(bundlePathFormatted);
            bundle.start();
        } catch (BundleException e) {
            log.error("Error while installing bundle : " + bundlePathFormatted);
        }
    }

    /**
     * Each artifact can have it's dependencies which are also artifacts. This method searches
     * the entire tree of artifacts to deploy default types..
     *
     * @param deps - list of dependencies to be searched..
     * @param axisConfig - Axis config of the current tenant
     */
    private void deployRecursively(List<Artifact.Dependency> deps, AxisConfiguration axisConfig) {
        String artifactPath, destPath;
        String repo = axisConfig.getRepository().getPath();
        
        for (Artifact.Dependency dependency : deps) {
            Artifact artifact = dependency.getArtifact();
            if (artifact == null) {
                continue;
            }

            if (!isAccepted(artifact.getType())) {
                log.warn("Can't deploy artifact : " + artifact.getName() + " of type : " +
                        artifact.getType() + ". Required features are not installed in the system");
                continue;
            }

            if (AAR_TYPE.equals(artifact.getType())) {
                destPath = repo + File.separator + CarbonUtils.getAxis2ServicesDir(axisConfig);
            } else if (JAXWS_TYPE.equals(artifact.getType())) {
                destPath = repo + File.separator + JAXWS_DIR;
            } else if (DS_TYPE.equals(artifact.getType())) {
                destPath = repo + File.separator + DS_DIR;
            } else if (AppDeployerConstants.CARBON_APP_TYPE.equals(artifact.getType())) {
                destPath = repo + File.separator + AppDeployerConstants.CARBON_APPS;
            } else if (artifact.getType().startsWith("lib/") && AppDeployerUtils
                    .getTenantId(axisConfig) == MultitenantConstants.SUPER_TENANT_ID) {
                // library installation is only allowed for teh super tenant
                destPath = CarbonUtils.getCarbonOSGiDropinsDir();
            } else {
                continue;
            }

            List<CappFile> files = artifact.getFiles();
            if (files.size() != 1) {
                log.error(artifact.getType() + " type must have a single file to " +
                        "be deployed. But " + files.size() + " files found.");
                continue;
            }
            String fileName = artifact.getFiles().get(0).getName();
            artifactPath = artifact.getExtractedPath() + File.separator + fileName;
            AppDeployerUtils.createDir(destPath);

            String destFilePath = destPath + File.separator + fileName;
            AppDeployerUtils.copyFile(artifactPath, destFilePath);

            /**
             * if the current artifact is a lib or bundle, we have to manually install it into the
             * OSGi environment for the usage of the lib before the first restart..
             * Important : This OSGi library installation is only allowed for the super tenant
             */
            if ((artifact.getType().startsWith("lib/") || BUNDLE_TYPE.equals(artifact.getType()))
                    && AppDeployerUtils.getTenantId(axisConfig) ==
                    MultitenantConstants.SUPER_TENANT_ID) {
                installBundle(destFilePath);
                artifact.setRuntimeObjectName(fileName);
            }
            deployRecursively(artifact.getDependencies(), axisConfig);
        }
    }

    /**
     * Check whether a particular artifact type can be accepted for deployment. If the type doesn't
     * exist in the acceptance list, we assume that it doesn't require any special features to be
     * installed in the system. Therefore, that type is accepted.
     * If the type exists in the acceptance list, the acceptance value is returned.
     *
     * @param serviceType - service type to be checked
     * @return true if all features are there or entry is null. else false
     */
    private boolean isAccepted(String serviceType) {
        if (acceptanceList == null) {
            acceptanceList = AppDeployerUtils.buildAcceptanceList(AppDeployerServiceComponent
                    .getRequiredFeatures());
        }
        Boolean acceptance = acceptanceList.get(serviceType);
        return (acceptance == null || acceptance);
    }

}
