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
package org.wso2.carbon.core.test.persistence;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.axis2.description.AxisModule;
import org.apache.axis2.description.AxisOperation;
import org.apache.axis2.description.AxisOperationFactory;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.description.AxisServiceGroup;
import org.apache.axis2.description.Version;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.axis2.wsdl.WSDLConstants;
import org.wso2.carbon.context.RegistryType;
import org.wso2.carbon.core.RegistryResources;
import org.wso2.carbon.core.Resources;
import org.wso2.carbon.core.multitenancy.SuperTenantCarbonContext;
import org.wso2.carbon.core.persistence.PersistenceFactory;
import org.wso2.carbon.core.persistence.PersistenceUtils;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.jdbc.EmbeddedRegistryService;
import org.wso2.carbon.registry.core.jdbc.InMemoryEmbeddedRegistryService;
import org.wso2.carbon.utils.WSO2Constants;

import java.io.File;
import java.io.InputStream;
import java.net.URL;

public class PersistenceUtilsTest extends BaseTestCase {

    protected static EmbeddedRegistryService embeddedRegistryService = null;
    protected static Registry registry = null;
    protected static Registry governanceRegistry = null;

    private static PersistenceFactory pf;
    private static AxisConfiguration ac;

    public void setUp() {
        super.setUp();

        if (embeddedRegistryService != null) {
            return;
        }

        try {
            InputStream regConfigStream = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream("registry.xml");
            embeddedRegistryService = new InMemoryEmbeddedRegistryService(regConfigStream);
            registry = embeddedRegistryService.getConfigSystemRegistry();
            governanceRegistry = embeddedRegistryService.getGovernanceSystemRegistry();
        } catch (RegistryException e) {
            fail("Failed to initialize the registry. Caused by: " + e.getMessage());
        }

        try {
            ac = new AxisConfiguration();
            String fs = File.separator;
            String repoPath = new File(".").getAbsolutePath()+fs+"target"+fs+"axis2-repo"+(Math.random());
            new File(repoPath).mkdir();
            ac.setRepository(new URL("file://"+repoPath));

            SuperTenantCarbonContext.getCurrentContext(ac).setRegistry(
                    RegistryType.SYSTEM_CONFIGURATION, registry);
            SuperTenantCarbonContext.getCurrentContext(ac).setRegistry(
                    RegistryType.SYSTEM_CONFIGURATION, governanceRegistry);

            // The following line of code is kept for backward compatibility. Remove this once we
            // are certain that this is not required. -- Senaka.
            ac.addParameter(WSO2Constants.CONFIG_SYSTEM_REGISTRY_INSTANCE, registry);
            pf = new PersistenceFactory(ac);
        } catch (Exception e) {
            fail("Fail to add Parameter to registry. Caused by:" + e.getMessage());
        }
    }


    public void tearDown() throws Exception {
        super.tearDown();
    }

//    public void testGetResourcePath() throws Exception {
//        AxisServiceGroup asvGroup = new AxisServiceGroup(ac);
//        asvGroup.setServiceGroupName("testServiceGroup1");
//        AxisService asv = new AxisService("testService1");
//        asvGroup.addService(asv);
//        pf.getServiceGroupPM().handleNewServiceGroupAddition(asvGroup);
//        String path = PersistenceUtils.getResourcePath(asvGroup);
//        assertTrue(path.equals(RegistryResources.SERVICE_GROUPS + "testServiceGroup1"));
//    }

    public void testGetResourcePath1() throws Exception {
        AxisServiceGroup asvGroup = new AxisServiceGroup(ac);
        asvGroup.setServiceGroupName("testServiceGroup2");
        AxisService asv = new AxisService("testService2");
        asvGroup.addService(asv);
        pf.getServiceGroupPM().handleNewServiceGroupAddition(asvGroup);
        pf.getServicePM().handleNewServiceAddition(asv);
        String path = PersistenceUtils.getResourcePath(asv);
        assertTrue(path.equals(Resources.ServiceProperties.ROOT_XPATH+PersistenceUtils.
                getXPathAttrPredicate(Resources.NAME, asv.getName())));
    }

    public void testGetResourcePath2() throws Exception {
        AxisServiceGroup asvGroup = new AxisServiceGroup(ac);
        asvGroup.setServiceGroupName("testServiceGroup3");
        AxisService asv = new AxisService("testService3");
        AxisOperation operation = AxisOperationFactory
                .getAxisOperation(WSDLConstants.MEP_CONSTANT_IN_OUT);
        asvGroup.addService(asv);
        asv.addOperation(operation);
        String path = PersistenceUtils.getResourcePath(operation);
        assertNotNull(path);
    }

    public void testGetResourcePath3() throws Exception {
        AxisModule am = new AxisModule();
        Version v = new Version("1.0");
        am.setVersion(v);
        am.setName("ModulePU1");
        pf.getModulePM().handleNewModuleAddition(am, "ModulePU1", "1.0");
        String modulePath = Resources.ModuleProperties.VERSION_XPATH+PersistenceUtils.
                getXPathAttrPredicate(Resources.ModuleProperties.VERSION_ID, "1.0");
        String path = PersistenceUtils.getResourcePath(am);
        assertTrue(path.equals(modulePath));
    }

    public void testGetResourcePath4() throws Exception {
        //This is already checked in PersistenceManagerTest.
    }

    public static Test suite() {
        return new TestSuite(PersistenceUtilsTest.class);
    }
}
