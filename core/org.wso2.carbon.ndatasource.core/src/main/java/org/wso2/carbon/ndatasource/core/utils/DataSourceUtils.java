/**
 *  Copyright (c) 2012, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.wso2.carbon.ndatasource.core.utils;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.axiom.om.OMElement;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.wso2.carbon.core.multitenancy.SuperTenantCarbonContext;
import org.wso2.carbon.core.util.CryptoException;
import org.wso2.carbon.core.util.CryptoUtil;
import org.wso2.carbon.ndatasource.common.DataSourceConstants;
import org.wso2.carbon.ndatasource.common.DataSourceException;
import org.wso2.carbon.ndatasource.core.DataSourceMetaInfo;
import org.wso2.carbon.ndatasource.core.JNDIConfig;
import org.wso2.carbon.ndatasource.core.DataSourceMetaInfo.DataSourceDefinition;
import org.wso2.carbon.ndatasource.core.internal.DataSourceServiceComponent;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.user.api.Tenant;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;
import org.wso2.securevault.SecretResolver;
import org.wso2.securevault.SecretResolverFactory;

/**
 * Data Sources utility class.
 */
public class DataSourceUtils {
	
	private static Log log = LogFactory.getLog(DataSourceUtils.class);
	
	private static SecretResolver secretResolver;
	
	public static Registry getConfRegistryForTenant(int tenantId) throws DataSourceException {
		try {
			/* be super tenant to retrieve the registry of a given tenant id */
			SuperTenantCarbonContext.startTenantFlow();
			SuperTenantCarbonContext.getCurrentContext().setTenantId(
					MultitenantConstants.SUPER_TENANT_ID);
			return DataSourceServiceComponent.getRegistryService().getConfigSystemRegistry(
					tenantId);
		} catch (RegistryException e) {
			throw new DataSourceException("Error in retrieving conf registry instance: " + 
		            e.getMessage(), e);
		} finally {
			/* go out of being super tenant */
			SuperTenantCarbonContext.endTenantFlow();
		}
	}
	
	public static Registry getGovRegistryForTenant(int tenantId) throws DataSourceException {
		try {
			/* be super tenant to retrieve the registry of a given tenant id */
			SuperTenantCarbonContext.startTenantFlow();
			SuperTenantCarbonContext.getCurrentContext().setTenantId(
					MultitenantConstants.SUPER_TENANT_ID);
			return DataSourceServiceComponent.getRegistryService().getGovernanceSystemRegistry(
					tenantId);
		} catch (RegistryException e) {
			throw new DataSourceException("Error in retrieving gov registry instance: " + 
		            e.getMessage(), e);
		} finally {
			/* go out of being super tenant */
			SuperTenantCarbonContext.endTenantFlow();
		}
	}
	
	public static boolean nullAllowEquals(Object lhs, Object rhs) {
		if (lhs == null && rhs == null) {
			return true;
		}
		if ((lhs == null && rhs != null) || (lhs != null && rhs == null)) {
			return false;
		}
		return lhs.equals(rhs);
	}
	
	public static String elementToString(Element element) {
		try {
			if (element == null) {
				return null;
			}
		    Transformer transformer = TransformerFactory.newInstance().newTransformer();
		    StringWriter buff = new StringWriter();
		    transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		    transformer.transform(new DOMSource(element), new StreamResult(buff));
		    return buff.toString();
		} catch (Exception e) {
			log.error("Error while convering element to string: " + e.getMessage(), e);
			return null;
		}
	}
	
	public static Element stringToElement(String xml) {
		if (xml == null) {
			return null;
		}
		try {
		    DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		    return db.parse(new ByteArrayInputStream(xml.getBytes())).getDocumentElement();
		} catch (Exception e) {
			log.error("Error while convering string to element: " + e.getMessage(), e);
			return null;
		}
	}
	
	private static DataSourceMetaInfo copyDSMInfo(DataSourceMetaInfo dsmInfo) {
		DataSourceMetaInfo result = new DataSourceMetaInfo();
		result.setDescription(dsmInfo.getDescription());
		JNDIConfig jndiConfig = dsmInfo.getJndiConfig();
	    if (jndiConfig != null) {
	    	result.setJndiConfig(dsmInfo.getJndiConfig().copy());
	    }
		result.setName(dsmInfo.getName());
		result.setSystem(dsmInfo.isSystem());
		DataSourceDefinition dsDef = new DataSourceDefinition();
		dsDef.setType(dsmInfo.getDefinition().getType());
		Element confEl = (Element) dsmInfo.getDefinition().getDsXMLConfiguration();
		if (confEl != null) {
		    dsDef.setDsXMLConfiguration(DataSourceUtils.stringToElement(
		    		DataSourceUtils.elementToString(confEl)));
		}
		result.setDefinition(dsDef);
		return result;
	}
	
	private static synchronized String loadFromSecureVault(String alias) {
		if (secretResolver == null) {
		    secretResolver = SecretResolverFactory.create((OMElement) null, false);
		    secretResolver.init(DataSourceServiceComponent.
		    		getSecretCallbackHandlerService().getSecretCallbackHandler());
		}
		return secretResolver.resolve(alias);
	}
	
	private static void secureLoadElement(Element element, boolean checkSecureVault) 
			throws CryptoException {
		if (checkSecureVault) {
			String secretAlias = element.getAttributeNS(DataSourceConstants.SECURE_VAULT_NS, 
					DataSourceConstants.SECRET_ALIAS_ATTR_NAME);
			if (secretAlias != null && secretAlias.length() > 0) {
				element.setTextContent(loadFromSecureVault(secretAlias));
			} 
		} else {
		    String encryptedStr = element.getAttribute(DataSourceConstants.ENCRYPTED_ATTR_NAME);
		    if (encryptedStr != null) {
			    boolean encrypted = Boolean.parseBoolean(encryptedStr);
			    if (encrypted) {
				    element.setTextContent(new String(CryptoUtil.getDefaultCryptoUtil(
				    		DataSourceServiceComponent.getServerConfigurationService(),
				    		DataSourceServiceComponent.getRegistryService()).
				    		base64DecodeAndDecrypt(element.getTextContent())));
			    }
		    }
		}
		NodeList childNodes = element.getChildNodes();
		int count = childNodes.getLength();
		Node tmpNode;
		for (int i = 0; i < count; i++) {
			tmpNode = childNodes.item(i);
			if (tmpNode instanceof Element) {
				secureLoadElement((Element) tmpNode, checkSecureVault);
			}
		}
	}
	
	private static void secureSaveElement(Element element) throws CryptoException {
		String encryptedStr = element.getAttribute(DataSourceConstants.ENCRYPTED_ATTR_NAME);
		if (encryptedStr != null) {
		    boolean encrypted = Boolean.parseBoolean(encryptedStr);
		    if (encrypted) {
			    element.setTextContent(CryptoUtil.getDefaultCryptoUtil(
			    		DataSourceServiceComponent.getServerConfigurationService(),
			    		DataSourceServiceComponent.getRegistryService()).
			    		encryptAndBase64Encode(element.getTextContent().getBytes()));
		    }
		}
		NodeList childNodes = element.getChildNodes();
		int count = childNodes.getLength();
		Node tmpNode;
		for (int i = 0; i < count; i++) {
			tmpNode = childNodes.item(i);
			if (tmpNode instanceof Element) {
				secureSaveElement((Element) tmpNode);
			}
		}
	}
	
	public static DataSourceMetaInfo secureSaveDSMInfo(DataSourceMetaInfo dsmInfo) 
			throws DataSourceException {
		DataSourceMetaInfo saveInfo = copyDSMInfo(dsmInfo);
		Element element = (Element) saveInfo.getDefinition().getDsXMLConfiguration();
		if (element != null) {
			try {
				secureSaveElement(element);
			} catch (CryptoException e) {
				throw new DataSourceException("Error in secure save of data source meta info: " +
			            e.getMessage(), e);
			}
		}
		return saveInfo;
	}
	
	public static DataSourceMetaInfo secureLoadDSMInfo(DataSourceMetaInfo dsmInfo, 
			boolean checkSecureVault) throws DataSourceException {
		DataSourceMetaInfo loadInfo = copyDSMInfo(dsmInfo);
		Element element = (Element) loadInfo.getDefinition().getDsXMLConfiguration();
		if (element != null) {
			try {
				secureLoadElement(element, checkSecureVault);
			} catch (CryptoException e) {
				throw new DataSourceException("Error in secure load of data source meta info: " +
			            e.getMessage(), e);
			}
		}
		return loadInfo;
	}
	
	public static List<Integer> getAllTenantIds() throws DataSourceException {
		try {
			Tenant[] tenants = DataSourceServiceComponent.getRealmService().getTenantManager().
					getAllTenants();
			List<Integer> tids = new ArrayList<Integer>();
			for (Tenant tenant : tenants) {
				tids.add(tenant.getId());
			}
			tids.add(MultitenantConstants.SUPER_TENANT_ID);
			return tids;
		} catch (UserStoreException e) {
			throw new DataSourceException("Error in listing all the tenants: " + 
		            e.getMessage(), e);
		}
	}
	
}
