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

package org.wso2.carbon.server.admin.module.handler;

import java.util.ArrayList;
import java.util.Iterator;
import org.apache.axis2.AxisFault;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.description.AxisOperation;
import org.apache.axis2.description.Parameter;
import org.apache.axis2.handlers.AbstractHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.core.services.privilegedactionextensions.PrivilegedActionException;
import org.wso2.carbon.core.services.privilegedactionextensions.PrivilegedActionExtension;
import org.wso2.carbon.server.admin.privilegedactionextensions.PrivilegedActionExtensionsRegistry;

public class PrivilegedActionExtensionHandler extends AbstractHandler {

	private static final Log log = LogFactory.getLog(AuthenticationHandler.class);

	@Override
	public InvocationResponse invoke(MessageContext msgContext) throws AxisFault {

		AxisOperation operation = msgContext.getAxisOperation();
		Parameter privilegedAction = operation.getParameter("privilagedAction");

		/* Check if the soap operation is not a privileged actions */
		if (privilegedAction == null) {
			return InvocationResponse.CONTINUE;
		}
		/* This is a privileged action, must be handled */
		handleAction(msgContext);

		return InvocationResponse.CONTINUE;
	}

	private void handleAction(MessageContext msgContext) throws AxisFault {

		ArrayList<PrivilegedActionExtension> extensions;

		try {
			extensions =
			             PrivilegedActionExtensionsRegistry.getPrivilegedActionExtensions(msgContext);
		} catch (PrivilegedActionException e) {
			log.error("No privilaged action extension found", e);
			throw new AxisFault("No privilaged action extension found");
		}

		Iterator<PrivilegedActionExtension> iterator = extensions.iterator();
		/* Execute in the priority order */
		while (iterator.hasNext()) {

			try {
				iterator.next().execute(msgContext);
			} catch (PrivilegedActionException e) {
				log.error("Error while executing the extension", e);
				throw new AxisFault("Error while executing the extension", e);
			}

		}
	}

}
