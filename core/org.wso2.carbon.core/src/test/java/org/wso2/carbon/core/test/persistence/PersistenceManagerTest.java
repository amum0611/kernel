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

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMNode;
import org.apache.axis2.description.AxisModule;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.description.AxisServiceGroup;
import org.apache.axis2.description.Parameter;
import org.apache.axis2.description.Version;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.neethi.Policy;
import org.apache.neethi.PolicyEngine;
import org.wso2.carbon.context.RegistryType;
import org.wso2.carbon.core.Resources;
import org.wso2.carbon.core.multitenancy.SuperTenantCarbonContext;
import org.wso2.carbon.core.persistence.PersistenceFactory;
import org.wso2.carbon.core.persistence.PersistenceUtils;
import org.wso2.carbon.core.persistence.file.ServiceGroupFilePersistenceManager;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.internal.RegistryCoreServiceComponent;
import org.wso2.carbon.registry.core.jdbc.InMemoryEmbeddedRegistryService;
import org.wso2.carbon.utils.WSO2Constants;

import javax.xml.namespace.QName;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.List;

public class PersistenceManagerTest extends BaseTestCase {

    protected static InMemoryEmbeddedRegistryService embeddedRegistryService = null;
    protected static Registry configRegistry = null;
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

            RegistryCoreServiceComponent component = new RegistryCoreServiceComponent() {
                {
                    setRealmService(embeddedRegistryService.getRealmService());
                }
            };
            component.registerBuiltInHandlers(embeddedRegistryService);

            configRegistry = embeddedRegistryService.getConfigSystemRegistry();
            governanceRegistry = embeddedRegistryService.getGovernanceSystemRegistry();
        } catch (RegistryException e) {
            fail("Failed to initialize the registry. Caused by: " + e.getMessage());
        }

        try {
            ac = new AxisConfiguration();
            String fs = File.separator;
            String repoPath = new File(".").getAbsolutePath() + fs + "target" + fs + "axis2-repo" + (Math.random());
            new File(repoPath).mkdir();
            ac.setRepository(new URL("file://" + repoPath));
            SuperTenantCarbonContext.getCurrentContext(ac).setRegistry(
                    RegistryType.SYSTEM_CONFIGURATION, configRegistry);
            SuperTenantCarbonContext.getCurrentContext(ac).setRegistry(
                    RegistryType.SYSTEM_GOVERNANCE, governanceRegistry);

//            The following line of code is kept for backward compatibility. Remove this once we
//            are certain that this is not required. -- Senaka.
            ac.addParameter(WSO2Constants.CONFIG_SYSTEM_REGISTRY_INSTANCE, configRegistry);
            pf = PersistenceFactory.getInstance(ac);
        } catch (Exception e) {
            fail("Fail to add Parameter to registry. Caused by:" + e.getMessage());
        }
    }

    public void tearDown() throws Exception {
        super.tearDown();
    }

    public void testNewServiceGroupAddition() throws Exception {
        AxisServiceGroup asvGroup = new AxisServiceGroup(ac);
        asvGroup.setServiceGroupName("test");
        AxisService asv = new AxisService("testService");
        asvGroup.addService(asv);
        pf.getServiceGroupPM().handleNewServiceGroupAddition(asvGroup);
        OMElement re = pf.getServiceGroupPM().getServiceGroup("test");
        assertNotNull(re);
    }

    public void testSetServiceGroupProperty() throws Exception {
        AxisServiceGroup asvGroup = new AxisServiceGroup(ac);
        asvGroup.setServiceGroupName("testGP");
        AxisService asv = new AxisService("testServiceGP");
        asvGroup.addService(asv);
        pf.getServiceGroupPM().handleNewServiceGroupAddition(asvGroup);

        pf.getServiceGroupPM().setServiceGroupProperty(asvGroup, "name", "test");
        OMElement re = pf.getServiceGroupPM().getServiceGroup("testGP");
        String value = re.getAttributeValue(new QName("name"));
        assertEquals("test", value);
    }

    public void testNewServiceAddition() throws Exception {
        AxisServiceGroup asvGroup = new AxisServiceGroup(ac);
        asvGroup.setServiceGroupName("testServiceGroup");
        AxisService asv = new AxisService("testServiceAdd");
        asvGroup.addService(asv);
        String policyXML = "<wsp:Policy\n" +
                "   xmlns:sp=\"http://schemas.xmlsoap.org/ws/2005/07/securitypolicy\"\n" +
                "   xmlns:wsp=\"http://schemas.xmlsoap.org/ws/2004/09/policy\"\n" +
                "   xmlns:wsu=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\"\n" +
                "   wsu:Id=\"SecureMessagePolicy\" >\n" +
                "  <sp:SignedParts>\n" +
                "    <sp:Body />\n" +
                "  </sp:SignedParts>\n" +
                "  <sp:EncryptedParts>\n" +
                "    <sp:Body />\n" +
                "  </sp:EncryptedParts>\n" +
                "</wsp:Policy>";
        ByteArrayInputStream steram = new ByteArrayInputStream(policyXML.getBytes());
        Policy policy = PolicyEngine.getPolicy(steram);
        asv.getPolicySubject().attachPolicy(policy);
        pf.getServiceGroupPM().handleNewServiceGroupAddition(asvGroup);
        pf.getServicePM().handleNewServiceAddition(asv);

        String serviceElementPath = PersistenceUtils.getResourcePath(asv);

        OMNode node = pf.getServiceGroupFilePM().get(asvGroup.getServiceGroupName(),
                serviceElementPath);
        assertTrue(node instanceof OMElement);

        OMElement el = (OMElement) node;
        String s = el.getFirstChildWithName(new QName(Resources.ServiceProperties.POLICY_UUID)).getText();
        assertTrue("SecureMessagePolicy".equals(s));

        //deleting service(group) after the above process
        deleteServiceAsserts(asv);
        deleteServiceGroupAsserts(asvGroup);
    }

    /*
    //todo current tests does not cover service intialization with existing persitence data.
    public void testExistingServiceInit() throws Exception {
        AxisServiceGroup asvGroup = new AxisServiceGroup(ac);
        String serviceName = "testServiceAddEx";
        String serviceGroupName = "testServiceGroupEx";
        
        asvGroup.setServiceGroupName(serviceGroupName);
        AxisService asv = new AxisService(serviceName);
        asvGroup.addService(asv);
        asv.addParameter("param1", "value1");
        asv.addParameter("param2", "value2");

//        asv.addModuleref("wso2throttle");
        String policyXML = "<wsp:Policy\n" +
                "   xmlns:sp=\"http://schemas.xmlsoap.org/ws/2005/07/securitypolicy\"\n" +
                "   xmlns:wsp=\"http://schemas.xmlsoap.org/ws/2004/09/policy\"\n" +
                "   xmlns:wsu=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\"\n" +
                "   wsu:Id=\"SecureMessagePolicy\" >\n" +
                "  <sp:SignedParts>\n" +
                "    <sp:Body />\n" +
                "  </sp:SignedParts>\n" +
                "  <sp:EncryptedParts>\n" +
                "    <sp:Body />\n" +
                "  </sp:EncryptedParts>\n" +
                "</wsp:Policy>";
        ByteArrayInputStream steram = new ByteArrayInputStream(policyXML.getBytes());
        Policy policy = PolicyEngine.getPolicy(steram);
        asv.getPolicySubject().attachPolicy(policy);
        pf.getServiceGroupPM().handleNewServiceGroupAddition(asvGroup);
        pf.getServicePM().handleNewServiceAddition(asv);

        ac.removeService(serviceName);
        ac.removeServiceGroup(serviceGroupName);
        
        String serviceElementPath = PersistenceUtils.getResourcePath(asv);
        OMElement serviceElement = (OMElement) PersistenceFactory.getInstance(ac).getServiceGroupFilePM().get(
                asvGroup.getServiceGroupName(), serviceElementPath);

//        ac.removeServiceGroup("testServiceGroupEx");

        AxisServiceGroup asvGroupExisting = new AxisServiceGroup(ac);
        asvGroupExisting.setServiceGroupName(serviceGroupName);
        AxisService asvExisting = new AxisService(serviceName);
        asvGroupExisting.addService(asvExisting);
        pf.getServicePM().handleExistingServiceInit(asvExisting);

        assertEquals("value1",asvExisting.getParameterValue("param1"));
        assertEquals("value2",asvExisting.getParameterValue("param2"));

        assertTrue("wso2throttle should be engaged ",asvExisting.getModules().contains("wso2throttle"));

        String s = serviceElement.getFirstChildWithName(new QName(Resources.ServiceProperties.POLICY_UUID)).getText();
        assertTrue("SecureMessagePolicy".equals(s));
    }
    */

    public void testServiceProperty() throws Exception {
        AxisServiceGroup asvGroup = new AxisServiceGroup(ac);
        asvGroup.setServiceGroupName("testP");
        AxisService asv = new AxisService("testServiceP");
        asvGroup.addService(asv);
        pf.getServiceGroupPM().handleNewServiceGroupAddition(asvGroup);
        pf.getServicePM().handleNewServiceAddition(asv);

        pf.getServicePM().setServiceProperty(asv, "key", "value");
        OMElement re = pf.getServicePM().getService(asv);
        String prop = re.getAttributeValue(new QName("key"));
        assertEquals("value", prop);

        //deleting service(group) after the above process
        deleteServiceAsserts(asv);
        deleteServiceGroupAsserts(asvGroup);
    }

    public void testUpdateServiceGroupParameter() throws Exception {
        AxisServiceGroup asvGroup = new AxisServiceGroup(ac);
        asvGroup.setServiceGroupName("testGUp");
        AxisService asv = new AxisService("testServiceGUp");
        asvGroup.addService(asv);
        pf.getServiceGroupPM().handleNewServiceGroupAddition(asvGroup);

        Parameter para = new Parameter();
        para.setName("testGParam");
        para.setValue("testGValue");
        para.setLocked(true);
        pf.getServiceGroupPM().updateServiceGroupParameter(asvGroup, para);

        OMElement paramElement = (OMElement) pf.getServiceGroupFilePM().get(
                asvGroup.getServiceGroupName(), Resources.ServiceGroupProperties.ROOT_XPATH +
                "/" + Resources.ParameterProperties.PARAMETER); //there's only one parameter element

        assertEquals("testGParam", paramElement.getAttributeValue(new QName(Resources.NAME)));
        assertEquals("true", paramElement.getAttributeValue(new QName("locked")));
        assertEquals("testGValue", paramElement.getText());
    }

    public void testUpdateServiceParameter() throws Exception {
        AxisServiceGroup asvGroup = new AxisServiceGroup(ac);
        asvGroup.setServiceGroupName("testUp");
        AxisService asv = new AxisService("testServiceUp");
        asvGroup.addService(asv);
        pf.getServiceGroupPM().handleNewServiceGroupAddition(asvGroup);
        pf.getServicePM().handleNewServiceAddition(asv);

        Parameter param = new Parameter();
        param.setName("testParam");
        param.setValue("testValue");
        param.setParameterType(5);
        pf.getServicePM().updateServiceParameter(asv, param);

        String serviceElementPath = PersistenceUtils.getResourcePath(asv);
        String serviceParamElementPath = serviceElementPath + "/" + Resources.ParameterProperties.PARAMETER +
                PersistenceUtils.getXPathAttrPredicate(Resources.NAME, param.getName());

        OMElement el = (OMElement) pf.getServiceGroupFilePM().get(asvGroup.getServiceGroupName(),
                serviceParamElementPath);
        String s1 = el.getAttributeValue(new QName(Resources.NAME));
        String s2 = el.getAttributeValue(new QName(Resources.ParameterProperties.TYPE)); //this fails most probably

        assertTrue("testParam".equals(s1));
        assertTrue("5".equals(s2));

        pf.getServiceGroupFilePM().beginTransaction("testUp");

        OMElement delElement = OMAbstractFactory.getOMFactory().createOMElement("omelement", null);
                
        pf.getServiceGroupFilePM().put("testUp", delElement, serviceElementPath);
        pf.getServiceGroupFilePM().put("testUp", delElement.cloneOMElement(), serviceElementPath);

        List list = pf.getServiceGroupFilePM().getAll("testUp", serviceElementPath+"/omelement");
        assertEquals(2, list.size());

        boolean deleteAll = pf.getServiceGroupFilePM().deleteAll("testUp", serviceElementPath+"/omelement");
        assertTrue(deleteAll);

        boolean deleteAllFalse = pf.getServiceGroupFilePM().deleteAll("testUp", serviceElementPath+"/notExist");
        assertFalse(deleteAllFalse);

        pf.getServiceGroupFilePM().commitTransaction("testUp");
        
        //deleting service(group) after the above process
        deleteServiceAsserts(asv);
        deleteServiceGroupAsserts(asvGroup);
    }

    public void testNewModuleAddition() throws Exception {
        AxisModule am = new AxisModule();
        am.setName("newModule");
        am.setVersion(new Version("1.0"));
        pf.getModulePM().handleNewModuleAddition(am, "newModule", "1.0");
        OMElement re = pf.getModulePM().getModule("newModule", "1.0");
        assertNotNull(re);

        String modulePath = Resources.ModuleProperties.VERSION_XPATH + PersistenceUtils.
                getXPathAttrPredicate(Resources.ModuleProperties.VERSION_ID, "1.0");
        String modulePathPU = PersistenceUtils.getResourcePath(am);
        assertEquals(modulePath, modulePathPU);

        String s1 = pf.getModuleFilePM().get("newModule").getAttributeValue(new QName(Resources.NAME));
        String s2 = ((OMElement) pf.getModuleFilePM().get("newModule", modulePath)).
                getAttributeValue(new QName(Resources.ModuleProperties.VERSION_ID));

        assertTrue(s1.equals("newModule"));
        assertTrue(s2.equals("1.0"));
    }

    public void testNewModuleNullVersionAddition() throws Exception {
        AxisModule am = new AxisModule();
        am.setName("newModuleNV");
//        am.setVersion(new Version("1.0"));
        pf.getModulePM().handleNewModuleAddition(am, "newModule", Resources.ModuleProperties.UNDEFINED); //todo or should this be null?
        OMElement re = pf.getModulePM().getModule("newModule", Resources.ModuleProperties.UNDEFINED);
        assertNotNull(re);

        String modulePath = Resources.ModuleProperties.VERSION_XPATH + PersistenceUtils.
                getXPathAttrPredicate(Resources.ModuleProperties.VERSION_ID, Resources.ModuleProperties.UNDEFINED);
        String modulePathPU = PersistenceUtils.getResourcePath(am);
        assertEquals(modulePath, modulePathPU);

        String s1 = pf.getModuleFilePM().get("newModule").getAttributeValue(new QName(Resources.NAME));
        String s2 = ((OMElement) pf.getModuleFilePM().get("newModule", modulePath)).
                getAttributeValue(new QName(Resources.ModuleProperties.VERSION_ID));

        assertTrue(s1.equals("newModule"));
        assertTrue(s2.equals(Resources.ModuleProperties.UNDEFINED));
    }

    public void testModuleParameterUpdate() throws Exception {
        AxisModule am = new AxisModule();
        am.setName("Module1");
        Version v = new Version("1.0");
        am.setVersion(v);
        pf.getModulePM().handleNewModuleAddition(am, "Module1", "1.0");

        Parameter param = new Parameter();
        param.setName("TestParam");
        param.setValue("TestValue");

        OMFactory factory = OMAbstractFactory.getOMFactory();
        OMElement Ome = factory.createOMElement("parameter", null);
        Ome.addAttribute("name", "TestParam", null);
        Ome.setText("TestValue");
        param.setParameterElement(Ome);

        pf.getModulePM().updateModuleParameter(am, param);

        String moduleParamPath = PersistenceUtils.getResourcePath(am) +
                "/" + Resources.ParameterProperties.PARAMETER +
                PersistenceUtils.getXPathAttrPredicate(Resources.NAME, param.getName());

        OMElement modParam = (OMElement) pf.getModuleFilePM().get("Module1", moduleParamPath);
        assertNotNull("The parameter added to module should not be null", modParam);
        String s1 = modParam.getAttributeValue(new QName(Resources.NAME));
        assertTrue(s1.equals("TestParam"));

        List modVerList = pf.getModuleFilePM().getAll("Module1", PersistenceUtils.getResourcePath(am));
        assertTrue("There can be only one OMElement for a given module name and version", modVerList.size() == 1);
    }

    public void testDeleteService() throws Exception {
        AxisServiceGroup asvGroup = new AxisServiceGroup(ac);
        asvGroup.setServiceGroupName("ExistingSvGroup1");
        AxisService asv = new AxisService("ExistingSv1");
        asvGroup.addService(asv);

        pf.getServiceGroupPM().handleNewServiceGroupAddition(asvGroup);
        pf.getServicePM().handleNewServiceAddition(asv);

        pf.getServicePM().deleteService(asv);
        OMElement el = pf.getServicePM().getService(asv);
        assertNull(el);

        //deleting servicegroup after the above process
        deleteServiceGroupAsserts(asvGroup);
    }

    public void testRemoveServiceParam() throws Exception {
        AxisServiceGroup asvGroup = new AxisServiceGroup(ac);
        asvGroup.setServiceGroupName("testGD");
        AxisService asv = new AxisService("testServiceD");
        asvGroup.addService(asv);
        pf.getServiceGroupPM().handleNewServiceGroupAddition(asvGroup);
        pf.getServicePM().handleNewServiceAddition(asv);

        Parameter param = new Parameter();
        param.setName("testParam1");
        param.setValue("testValue1");
        pf.getServicePM().updateServiceParameter(asv, param);

        String serviceElementPath = PersistenceUtils.getResourcePath(asv);

        String serviceParamResourcePath = serviceElementPath + "/" + Resources.ParameterProperties.PARAMETER
                + PersistenceUtils.getXPathAttrPredicate(Resources.NAME, param.getName());

        pf.getServicePM().removeServiceParameter(asv, param);
        assertFalse(pf.getServiceGroupFilePM().
                elementExists(asvGroup.getServiceGroupName(), serviceParamResourcePath));

        //deleting service(group) after the above process
        deleteServiceAsserts(asv);
        deleteServiceGroupAsserts(asvGroup);

    }

    public void testDeleteServiceGroup() throws Exception {
        AxisServiceGroup asvGroup = new AxisServiceGroup(ac);
        asvGroup.setServiceGroupName("testSVG");
        AxisService asv = new AxisService("testSvgService");
        asvGroup.addService(asv);
        pf.getServiceGroupPM().handleNewServiceGroupAddition(asvGroup);

        pf.getServiceGroupPM().deleteServiceGroup(asvGroup);
        assertFalse("either file should not exist or content should be null",
                pf.getServiceGroupFilePM().fileExists(asvGroup.getServiceGroupName()));
    }

    public void testRemoveModule() throws Exception {
        AxisModule am = new AxisModule();
        am.setName("DModule");
        am.setVersion(new Version("1.0"));
        pf.getModulePM().handleNewModuleAddition(am, "DModule", "1.0");

        pf.getModulePM().removeModule(am);
        String modulePath = PersistenceUtils.getResourcePath(am);
        assertFalse(pf.getModuleFilePM().elementExists(am.getName(), modulePath));
    }


    public void testengageModuleForService() throws Exception {
        AxisServiceGroup asvGroup = new AxisServiceGroup(ac);
        asvGroup.setServiceGroupName("testAsvG");
        AxisService asv = new AxisService("testAsv");
        asvGroup.addService(asv);
        AxisModule am = new AxisModule();
        am.setName("Module2");
        Version v = new Version("1.0");
        am.setVersion(v);
        pf.getServiceGroupPM().handleNewServiceGroupAddition(asvGroup);
        pf.getServicePM().handleNewServiceAddition(asv);
        pf.getModulePM().handleNewModuleAddition(am, "Module2", "1.0");
        asv.engageModule(am);
        pf.getServicePM().engageModuleForService(am, asv);

        String serviceElementPath = PersistenceUtils.getResourcePath(asv);

        String s1 = "";
        String s2 = "";
        //gettting association for modules is different from other assocs
        List as = pf.getServiceGroupFilePM().getAll(asvGroup.getServiceGroupName(),
                serviceElementPath + "/" + Resources.ModuleProperties.MODULE_XML_TAG);

        for (Object a : as) {
            OMElement mod = (OMElement) a;
            s1 = mod.getAttributeValue(new QName(Resources.NAME));
            s2 = mod.getAttributeValue(new QName(Resources.VERSION));
        }
        assertTrue(s1.equals("Module2"));
        assertTrue(s2.equals("1.0"));
    }

    private void deleteServiceAsserts (AxisService asv) throws Exception {
        pf.getServicePM().deleteService(asv);
        OMElement el = pf.getServicePM().getService(asv);
        assertNull(el);
    }

    private void deleteServiceGroupAsserts (AxisServiceGroup asvGroup) throws Exception {
        pf.getServiceGroupPM().deleteServiceGroup(asvGroup);
        assertFalse("either file should not exist or content should be null",
                pf.getServiceGroupFilePM().fileExists(asvGroup.getServiceGroupName()));
    }

    /**
     * public void testFilePersistenceTransactions() throws Exception {
     * <p/>
     * //        ServiceGroupFilePersistenceManager sfpm = pf.getServiceGroupFilePM();
     * AxisServiceGroup asvGroup = new AxisServiceGroup(ac);
     * asvGroup.setServiceGroupName("testFPTGroup");
     * AxisService asv = new AxisService("testFPTService");
     * asvGroup.addService(asv);
     * pf.getServiceGroupPM().handleNewServiceGroupAddition(asvGroup);
     * pf.getServicePM().handleNewServiceAddition(asv);
     * int increment = 0;
     * <p/>
     * for (int i = 0; i < 1; i++) {
     * new Thread(
     * new ConcurrentTransactionsTest()).start();
     * <p/>
     * Parameter param = new Parameter();
     * param.setName("testParam"+ increment++);
     * param.setValue("testValue"+ increment++);
     * param.setParameterType(5);
     * pf.getServicePM().updateServiceParameter(asv, param);
     * }
     * <p/>
     * String serviceElementPath = PersistenceUtils.getResourcePath(asv);
     * String serviceParamElementPath = serviceElementPath+"/"+Resources.ParameterProperties.PARAMETER+
     * PersistenceUtils.getXPathAttrPredicate(Resources.NAME, "testParam0");
     * <p/>
     * OMElement el = (OMElement) pf.getServiceGroupFilePM().get(asvGroup.getServiceGroupName(),
     * serviceParamElementPath);
     * String s1 = el.getAttributeValue(new QName(Resources.NAME));
     * String s2 = el.getAttributeValue(new QName(Resources.ParameterProperties.TYPE)); //this fails most probably
     * <p/>
     * assertTrue("testParam".equals(s1));
     * assertTrue("5".equals(s2));
     * }
     */

    static class ConcurrentTransactionsTest implements Runnable {
        static int x = 100;

        public void run() {
            try {
                PersistenceFactory pfNew = PersistenceFactory.getInstance(ac);
                ServiceGroupFilePersistenceManager sfpmNew = pfNew.getServiceGroupFilePM();
                Parameter param = new Parameter();
                param.setName("testParam" + x++);
                param.setValue("testValue" + x++);
                param.setParameterType(5);

                pfNew.getServicePM().updateServiceParameter(ac.getService("testFPTService"), param);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void setValues() {

        }

    }

}
