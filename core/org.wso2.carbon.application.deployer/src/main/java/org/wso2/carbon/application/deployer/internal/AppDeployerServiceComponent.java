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
package org.wso2.carbon.application.deployer.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.osgi.service.component.ComponentContext;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.wso2.carbon.application.deployer.service.ApplicationManagerService;
import org.wso2.carbon.application.deployer.handler.RegistryResourceUndeployer;
import org.wso2.carbon.application.deployer.handler.DefaultAppUndeployer;
import org.wso2.carbon.application.deployer.handler.RegistryResourceDeployer;
import org.wso2.carbon.application.deployer.handler.DefaultAppDeployer;
import org.wso2.carbon.application.deployer.AppDeployerConstants;
import org.wso2.carbon.application.deployer.Feature;
import org.wso2.carbon.application.deployer.AppDeployerUtils;
import org.wso2.carbon.registry.core.service.RegistryService;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.net.URL;

/**
 * @scr.component name="application.deployer.dscomponent" immediate="true"
 * @scr.reference name="registry.service" interface="org.wso2.carbon.registry.core.service.RegistryService"
 * cardinality="1..1" policy="dynamic" bind="setRegistryService" unbind="unsetRegistryService"
 */

public class AppDeployerServiceComponent {

    private static RegistryService registryService;

    private static BundleContext bundleContext;
    private static ServiceRegistration appManagerRegistration;
    private static Map<String, List<Feature>> requiredFeatures;

    private static final Log log = LogFactory.getLog(AppDeployerServiceComponent.class);

    protected void activate(ComponentContext ctxt) {
        try {
            bundleContext = ctxt.getBundleContext();
            ApplicationManager applicationManager = ApplicationManager.getInstance();

            // register default undeployment handlers before registering deployment handlers
            applicationManager.registerUndeploymentHandler(new RegistryResourceUndeployer());
            applicationManager.registerUndeploymentHandler(new DefaultAppUndeployer());

            // now register deployment handlers
            applicationManager.registerDeploymentHandler(new RegistryResourceDeployer());
            applicationManager.registerDeploymentHandler(new DefaultAppDeployer());

            // register ApplicationManager as a service
            appManagerRegistration = ctxt.getBundleContext().registerService(
                    ApplicationManagerService.class.getName(), applicationManager, null);

            // read required-features.xml
            URL reqFeaturesResource = bundleContext.getBundle()
                    .getResource(AppDeployerConstants.REQ_FEATURES_XML);
            if (reqFeaturesResource != null) {
                InputStream xmlStream = reqFeaturesResource.openStream();
                requiredFeatures = AppDeployerUtils
                        .readRequiredFeaturs(new StAXOMBuilder(xmlStream).getDocumentElement());
            }

            if (log.isDebugEnabled()) {
                log.debug("Carbon Application Deployer is activated..");
            }

        } catch (Throwable e) {
            log.error("Failed to activate Carbon Application Deployer", e);
        }
    }

    protected void deactivate(ComponentContext ctxt) {
        if (appManagerRegistration != null) {
            appManagerRegistration.unregister();
        }
    }

    protected void setRegistryService(RegistryService regService) {
        registryService = regService;
    }

    protected void unsetRegistryService(RegistryService regService) {
        registryService = null;
    }

    public static RegistryService getRegistryService() throws Exception {
        if (registryService == null) {
            String msg = "Before activating Carbon Application deployer bundle, an instance of "
                    + "RegistryService should be in existance";
            log.error(msg);
            throw new Exception(msg);
        }
        return registryService;
    }

    public static BundleContext getBundleContext() {
        if (bundleContext == null) {
            log.error("Application Deployer has not started. Therefore Bundle context is null");
        }
        return bundleContext;
    }

    public static Map<String, List<Feature>> getRequiredFeatures() {
        return requiredFeatures;
    }

}
