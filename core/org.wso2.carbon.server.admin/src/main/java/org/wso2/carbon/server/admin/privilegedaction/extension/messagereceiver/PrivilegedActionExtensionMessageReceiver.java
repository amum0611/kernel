/*
 * Copyright (c) 2005-2008, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 * 
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.server.admin.privilegedaction.extension.messagereceiver;

import java.util.ArrayList;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.soap.SOAP11Constants;
import org.apache.axiom.soap.SOAP12Constants;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axis2.AxisFault;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.rpc.receivers.RPCMessageReceiver;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.server.admin.privilegedaction.extension.core.PrivilegedActionExtension;
import org.wso2.carbon.server.admin.privilegedaction.extension.core.PrivilegedActionExtensionException;
import org.wso2.carbon.server.admin.privilegedaction.extension.core.PrivilegedActionExtensionRegistry;

public class PrivilegedActionExtensionMessageReceiver extends RPCMessageReceiver {

	private static final Log log =
	                               LogFactory.getLog(PrivilegedActionExtensionMessageReceiver.class);
	private static final boolean SKIP_SERVICE_INVOCATION = false;
	private static final boolean SKIP_LOWER_PRIORITY_EXTENSIONS = false;

	@Override
	public void invokeBusinessLogic(MessageContext inMsgCtx, MessageContext outMsgCtx)
	                                                                                  throws AxisFault {

		ArrayList<PrivilegedActionExtension> extensions;
		boolean skipLowerPriorityExtensions = false;
		boolean skipServiceInvocation = false;
		int skipPriority = -1;

		extensions = PrivilegedActionExtensionRegistry.getPrivilegedActionExtensions(inMsgCtx);

		if (extensions != null && !extensions.isEmpty()) {

			SOAPEnvelope inEnvelope = inMsgCtx.getEnvelope();
			SOAPEnvelope outEnvelope = getOutEnvelope(inMsgCtx);

			/* Executing in the priority order */
			for (PrivilegedActionExtension ext : extensions) {
				if (!skipLowerPriorityExtensions || ext.getPriority() == skipPriority) {
					try {
						outEnvelope = ext.execute(inEnvelope, outEnvelope);
						if (SKIP_LOWER_PRIORITY_EXTENSIONS) {
							if (ext.skipLowerPriorityExtensions()) {
								skipLowerPriorityExtensions = true;
								skipPriority = ext.getPriority();
							}
						}
						if (SKIP_SERVICE_INVOCATION) {
							if (ext.skipServiceInvocation()) {
								skipServiceInvocation = true;
							}
						}
					} catch (PrivilegedActionExtensionException e) {
						throw new AxisFault(
						                    "Error while executing the privileged action extension " +
						                            ext.getExtensionName(), e);
					}
				}
			}

			/* Sending back the response */
			outMsgCtx.setEnvelope(outEnvelope);

			if (!skipServiceInvocation) {
				super.invokeBusinessLogic(inMsgCtx, outMsgCtx);
			}

		} else {
			super.invokeBusinessLogic(inMsgCtx, outMsgCtx);
		}
	}

	/* Creating a soap response according the the soap namespce uri */
	private SOAPEnvelope getOutEnvelope(MessageContext inMsgCtx) {

		String soapNamespace = inMsgCtx.getEnvelope().getNamespace().getNamespaceURI();
		SOAPFactory soapFactory = null;

		if (soapNamespace.equals(SOAP11Constants.SOAP_ENVELOPE_NAMESPACE_URI)) {
			soapFactory = OMAbstractFactory.getSOAP11Factory();
		} else if (soapNamespace.equals(SOAP12Constants.SOAP_ENVELOPE_NAMESPACE_URI)) {
			soapFactory = OMAbstractFactory.getSOAP12Factory();
		} else {
			System.out.println("Unknow soap message");
		}

		return soapFactory.getDefaultEnvelope();
	}

}
