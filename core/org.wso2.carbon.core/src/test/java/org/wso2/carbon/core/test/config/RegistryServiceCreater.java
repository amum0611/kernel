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
package org.wso2.carbon.core.test.config;

import org.apache.axis2.engine.AxisConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.wso2.carbon.core.internal.CarbonCoreServiceComponent;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.RegistryConstants;
import org.wso2.carbon.registry.core.config.RegistryConfiguration;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.internal.RegistryCoreServiceComponent;
import org.wso2.carbon.registry.core.jdbc.EmbeddedRegistryService;
import org.wso2.carbon.registry.core.jdbc.InMemoryEmbeddedRegistryService;

import java.io.File;

public class RegistryServiceCreater extends CarbonCoreServiceComponent {

    Log log = LogFactory.getLog(CarbonCoreServiceComponent.class);
    EmbeddedRegistryService embeddedRegistryService = null;
    Registry registry = null;
    AxisConfiguration ac = null;
    BundleContext bundleContext;

    public RegistryServiceCreater() {

        if (System.getProperty("carbon.home") == null) {
            File file = new File("../distribution/kernel/carbon-home");
            if (file.exists()) {
                System.setProperty("carbon.home", file.getAbsolutePath());
            }
            file = new File("../../distribution/kernel/carbon-home");
            if (file.exists()) {
                System.setProperty("carbon.home", file.getAbsolutePath());
            }
        }

        try {
            String carbonHome = System.getProperty("carbon.home");
            String carbonXMLPath = carbonHome + File.separator + "repository" + File.separator +  "conf" + File.separator
                    + "carbon.xml";
            RegistryConfiguration regConfig = new RegistryConfiguration(carbonXMLPath);
            RegistryCoreServiceComponent.setRegistryConfig(regConfig);
        } catch (RegistryException e) {
            throw new RuntimeException("Error creating RegistryConfig" + e.getMessage(), e);
        }

        if (embeddedRegistryService != null) {
            return;
        }

        try {
            embeddedRegistryService = new InMemoryEmbeddedRegistryService();
            registry = embeddedRegistryService.getUserRegistry(RegistryConstants.ADMIN_USER, RegistryConstants.ADMIN_PASSWORD);
        } catch (RegistryException e) {
            log.error("Failed to initialize the registry. Caused by: " + e.getMessage());
        }

    }

    public void setRegistryService() throws Exception {
        RegistryServiceCreater grs = new RegistryServiceCreater();
        grs.setRegistryService(embeddedRegistryService);

    }
}




