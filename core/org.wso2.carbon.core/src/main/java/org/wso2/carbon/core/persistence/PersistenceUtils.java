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
package org.wso2.carbon.core.persistence;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMDocument;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.util.AXIOMUtil;
import org.apache.axis2.AxisFault;
import org.apache.axis2.description.*;
import org.apache.axis2.util.Utils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.neethi.Policy;
import org.wso2.carbon.Axis2ModuleNotFound;
import org.wso2.carbon.core.RegistryResources;
import org.wso2.carbon.core.Resources;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.Resource;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public final class PersistenceUtils {

    private static Log log = LogFactory.getLog(PersistenceUtils.class);

    private static OMFactory omFactory = OMAbstractFactory.getOMFactory();

    private static final String PROXY_SERVICE = "proxy";
    private static final String GLOBALLY_ENGAGED_PARAM_NAME = "globallyEngaged";
    private static final String GLOBALLY_ENGAGED_CUSTOM = "globallyEngagedCustom";

    private PersistenceUtils() {
    }

    /**
     * Returns the resource path for the specified Service group
     *
     * @param serviceGroup - AxisServiceGroup instance
     * @return - registry resource path
     */
//    public static String getResourcePath(AxisServiceGroup serviceGroup) {
//        return RegistryResources.SERVICE_GROUPS + serviceGroup.getServiceGroupName();
//    }

    /**
     * Returns the resource path for the specified AxisService
     * /serviceGroup/service[@name="xxx"]
     * Ideal for use with get* operations. Since we use xpath of the
     * _parent_ for put* operations this shouldn't be used there.
     *
     * @param service - AxisService instance
     * @return - file xpath
     */
    public static String getResourcePath(AxisService service) {
//        /service[@name="xxx"]
        return Resources.ServiceProperties.ROOT_XPATH + getXPathAttrPredicate(Resources.NAME, service.getName());
    }

    /**
     * Used for Proxy services since we write the policy to both registry and file system
     *
     * @param service - AxisService instance
     * @return - registry path
     */
    public static String getRegistryResourcePath(AxisService service) {
        return RegistryResources.SERVICE_GROUPS + service.getAxisServiceGroup()
                .getServiceGroupName() + RegistryResources.SERVICES + service.getName();
    }

    /**
     * Returns the resource path for the specified AxisOperation
     *
     * @param operation - AxisService instance
     * @return - xpath to given operation
     */
    public static String getResourcePath(AxisOperation operation) {
//        Get AxisService
        return getResourcePath(operation.getAxisService()) + "/" +
                Resources.OPERATION + getXPathAttrPredicate(Resources.NAME, operation.getName().getLocalPart());
    }

    /**
     * Returns the xpath for the given module
     * <p/>
     * The name of the module isn't needed here, because it's not needed to calculate
     * the xpath as it's one file per moduleName.
     *
     * @param module - AxisModule instance
     * @return module resource path
     */
    public static String getResourcePath(AxisModule module) {
        String version = Resources.ModuleProperties.UNDEFINED;
        if (module.getVersion() != null) {
            version = module.getVersion().toString();
        }
        //  /version[@id="xxx"]
        return Resources.ModuleProperties.VERSION_XPATH + PersistenceUtils.
                getXPathAttrPredicate(Resources.ModuleProperties.VERSION_ID, version);
    }

    /**
     * Returns the resource path for the specified AxisBindingOperation
     *
     * @param serviceXPath - service resource path
     * @param binding
     * @return - registry resource path
     */
    public static String getBindingPath(String serviceXPath, AxisBinding binding) {
//        Service.ROOT_XPATH/bindings/binding[@name="xxx"]
        return serviceXPath + "/" + Resources
                .ServiceProperties.BINDINGS + "/" + Resources
                .ServiceProperties.BINDING_XML_TAG + PersistenceUtils.
                getXPathAttrPredicate(Resources.NAME, binding.getName().getLocalPart());
    }

    /**
     * Returns the resource path for the specified AxisBindingOperation
     *
     * @param serviceXPath - service resource path
     * @param abo          - AxisBindingOperation instance
     * @return - registry resource path
     */
    public static String getBindingOperationPath(String serviceXPath, AxisBindingOperation abo) {
//        Service.ROOT_XPATH/bindings/binding[@name="xxx"]/operation[@name="yyy"]
        AxisBinding binding = abo.getAxisBinding();
        return getBindingPath(serviceXPath, abo.getAxisBinding()) + "/" +
                Resources.OPERATION + getXPathAttrPredicate(Resources.NAME, abo.getName().getLocalPart());
    }

    /**
     * todo fix this when you are at servicePM
     * Finds a policy with the given uuid in the provided list of policies
     *
     * @param policyId          - uuid to find
     * @param policyElementList - resource list - the policyWrapperElements
     * @return - policy resource if found, else null
     */
    public static OMElement getPolicyElementFromList(String policyId, List policyElementList) {
        for (Object resource : policyElementList) {
            OMElement currentElement = (OMElement) resource;
            if (policyId.equalsIgnoreCase(
                    currentElement.getFirstChildWithName(
                            new QName(Resources.ServiceProperties.POLICY_UUID)).getText())) {
                return currentElement;
            }
        }
        return null;
    }

    /**
     *
     * todo is this used anywhere?          getAxisModule
     * Finds module name and version from the given resource and retrieves the AxisModule instance
     * from the config context
     *
     * @param moduleResource - Registry resource for the module
     * @param axisConfig     - AxisConfiguration instance
     * @return - the AxisModule instance if found
     * @throws Axis2ModuleNotFound - if the module is not found
     */
//    public static AxisModule getAxisModule(Resource moduleResource, AxisConfiguration
//            axisConfig) throws Axis2ModuleNotFound {
//        String modName = moduleResource.getProperty(RegistryResources.ModuleProperties.NAME);
//        String modVersion = moduleResource
//                .getProperty(RegistryResources.ModuleProperties.VERSION);
//        AxisModule axisModule = axisConfig.getModule(modName, modVersion);
//
//        if (axisModule == null)
//            throw new Axis2ModuleNotFound("Module: " + modName + " not found", modName);
//
//        return axisModule;
//    }

    /**
     * Checks whether the given service is a proxy service
     *
     * @param service - AxisService instance
     * @return true if "proxy" param is found. else false
     */
    public static boolean isProxyService(AxisService service) {
        ArrayList axisServiceParameters = service.getParameters();
        ListIterator iter = axisServiceParameters.listIterator();
        boolean isProxyService = false;

        while (iter.hasNext()) {
            Parameter elem = (Parameter) (iter.next());
            Object value = elem.getValue();
            if (value != null && PROXY_SERVICE.equals(value.toString())) {
                isProxyService = true;
            }
        }
        return isProxyService;
    }

    /**
     * Mark this module as faulty
     *
     * @param axisModule - AxisModule instance to be marked as faulty
     */
    public static void markFaultyModule(AxisModule axisModule) {
        axisModule.getParent().getFaultyModules().put(Utils.getModuleName(axisModule.getName(),
                axisModule.getVersion().toString()), axisModule.getName());
    }

    /**
     * Set the needed global params for the module by reading from registry resource
     *
     * @param axisModule  - AxisModule instance
     * @param moduleVerEl - module version element
     * @throws AxisFault - on axis level errors
     */
    public static void handleGlobalParams(AxisModule axisModule,
                                          OMElement moduleVerEl) throws AxisFault {

        if (Boolean.parseBoolean(moduleVerEl.
                getAttributeValue(new QName(Resources.ModuleProperties.GLOBALLY_ENGAGED)))) {
            axisModule.addParameter(new Parameter(GLOBALLY_ENGAGED_PARAM_NAME,
                    Boolean.TRUE.toString()));
            axisModule.getParent().engageModule(axisModule);
        }

        if (Boolean.parseBoolean(moduleVerEl.getAttributeValue(new QName(GLOBALLY_ENGAGED_CUSTOM)))) {
            axisModule.addParameter(new Parameter(GLOBALLY_ENGAGED_PARAM_NAME,
                    Boolean.TRUE.toString()));
        }
    }

//    /**
//     *
//     * todo is this used anywhere?          handleGlobalParams
//     * Set the needed global params for the module by reading from registry resource
//     *
//     * @param axisModule     - AxisModule instance
//     * @param moduleResource - module resource
//     * @throws AxisFault - on axis level errors
//     * @deprecated
//     */
//    public static void handleGlobalParams(AxisModule axisModule,
//                                          Resource moduleResource) throws AxisFault {
//        if (Boolean.parseBoolean(moduleResource
//                .getProperty(RegistryResources.ModuleProperties.GLOBALLY_ENGAGED))) {
//            axisModule.addParameter(new Parameter(GLOBALLY_ENGAGED_PARAM_NAME,
//                    Boolean.TRUE.toString()));
//            axisModule.getParent().engageModule(axisModule);
//        }
//
//        if (Boolean.parseBoolean(moduleResource.getProperty(GLOBALLY_ENGAGED_CUSTOM))) {
//            axisModule.addParameter(new Parameter(GLOBALLY_ENGAGED_PARAM_NAME,
//                    Boolean.TRUE.toString()));
//        }
//    }

    /**
     * [@attrName="attrValue"]
     *
     * @param attrName
     * @param attrValue
     * @return
     */
    public static String getXPathAttrPredicate(String attrName, String attrValue) {
        return "[@" + attrName + "=\"" + attrValue + "\"]";
    }

    /**
     * Set null for prefixes if there aren't any.
     * returns
     * [text()=\"SecPolicy\"]" or
     * <p/>
     * [prefixes/text()=\"SecPolicy\"]"
     *
     * @param prefixes
     * @param textValue
     * @return
     */
    public static String getXPathTextPredicate(String prefixes, String textValue) {
        if (prefixes == null || "".equals(prefixes)) {
            return "[text()=\"" + textValue + "\"]";
        } else {
            return "[" + prefixes + "/text()=\"" + textValue + "\"]";
        }
    }

    /**
     * I'm thinking of making the destinationPath be either a xpath or a path to a registry.
     * Currently it's a registry path.
     *
     * @param destinationPath
     * @param type
     * @return
     */
    public static OMElement createAssociation(String destinationPath, String type) {
        OMElement association = omFactory.createOMElement(
                Resources.Associations.ASSOCIATION_XML_TAG, null);
        association.addAttribute(Resources.Associations.DESTINATION_PATH,
                destinationPath, null);
        association.addAttribute("type", type, null);

        return association;
    }

    /**
     * Creates a {@code <module> } OMElement that can be used to add to serviceGroup file.
     * <p/>
     * ex. {@code <module name="rampart" version="3.2" type=""/> } version attr not added is null.
     * <p/>
     * todo if moduleVersion == null, then the the attrib doesn't get added. How does this behavior is handled when retrieving the module version back. - kasung
     *
     * @param moduleName
     * @param moduleVersion
     * @param type
     * @return
     */
    public static OMElement createModule(String moduleName, String moduleVersion, String type) {
        OMElement module = omFactory.createOMElement(
                Resources.ModuleProperties.MODULE_XML_TAG, null);
        module.addAttribute(Resources.NAME, moduleName, null);
        if (moduleVersion != null) {
            module.addAttribute(Resources.VERSION, moduleVersion, null);
        } else {
            module.addAttribute(Resources.VERSION, Resources.ModuleProperties.UNDEFINED, null);
        }
        module.addAttribute(Resources.ModuleProperties.TYPE, type, null);

        return module;
    }

    public static OMElement createOperation(AxisDescription ad, String opName) {
        OMElement operationElement = omFactory.createOMElement(Resources.OPERATION, null);
        String doc = ad.getDocumentation();
        if (doc != null) {
            operationElement.addAttribute(Resources.ServiceProperties.DOCUMENTATION, doc, null);
        }
        operationElement.addAttribute(Resources.NAME, opName, null);
        return operationElement;

    }

    /**
     * Creates a registry Resource for a given Policy
     *
     * @param policy - Policy instance
     *               todo now policyId, and policyType is removed, adjust invocations of this method (PersistenceUtils.createPolicyElement)  - kasung
     * @return - created policy resource
     * @throws Exception - error on serialization
     */
    public static OMElement createPolicyElement(Policy policy) throws Exception {
        // String policyId, int policyType

        // Set the policy as a string in the resource
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        XMLStreamWriter writer = XMLOutputFactory.newInstance().createXMLStreamWriter(outputStream);
        policy.serialize(writer);
        writer.flush();

        OMElement policyElement = AXIOMUtil.stringToOM(outputStream.toString());

        /**
         * @see org.wso2.carbon.core.persistence.file.ModuleFilePersistenceManager#getAll(String, String) for the argument
         */
        if (policyElement.getParent() instanceof OMDocument) {
            policyElement.detach();
        }
        return policyElement;
    }

    /**
     * Creates a registry Resource for a given Policy
     *
     * @param configRegistry config registry
     * @param policy     - Policy instance
     * @param policyId   - policy uuid
     * @param policyType - policy type
     * @return - created policy resource
     * @throws Exception - error on serialization
     */
    public static Resource createPolicyResource(Registry configRegistry,
                                            Policy policy, String policyId, String policyType)
            throws RegistryException {
        try {
            Resource policyResource = configRegistry.newResource();
            policyResource.setProperty(RegistryResources.ServiceProperties.POLICY_UUID, policyId);

            // Set the policy as a string in the resource
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            XMLStreamWriter writer = XMLOutputFactory.newInstance().createXMLStreamWriter(outputStream);
            policy.serialize(writer);
            writer.flush();
            policyResource.setContent(outputStream.toString());

            policyResource.setProperty(RegistryResources.ServiceProperties.POLICY_TYPE, policyType);
            return policyResource;
        } catch (XMLStreamException e) {
            log.error("Error creating the registry resource for "+policyId, e);
            throw new RegistryException("Error creating the registry resource for "+policyId, e);
        }
    }


    /**
     * Returns the policyUUID (Resources.POLICY_UUID) from the given policyWrapperElement OMElement
     * A policyWrapperElement is a &lt;policy&gt; element that wraps &lt;wsp:policy&gt;. It includes
     * policyUUID as a child element.
     *
     * @param policyWrapperElement
     * @return
     */
    public static String getPolicyUUIDFromWrapperOM(OMElement policyWrapperElement) {
        return policyWrapperElement.getFirstChildWithName(new QName(Resources.ServiceProperties.POLICY_UUID)).getText();
    }

}
