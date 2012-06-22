/*
 *  Copyright (c) 2005-2008, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package org.wso2.carbon.server.admin.privilegedaction.extension.core;

import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axis2.context.MessageContext;

public interface PrivilegedActionExtension {
	
	/**
	 * Executes the extension. The logic must be implemented here. 
	 * 
	 * @param inEnvelope 
	 * @param outEnvelope 
	 * @return outEnvelope
	 */
	public SOAPEnvelope execute(SOAPEnvelope inEnvelope, SOAPEnvelope outEnvelope) throws PrivilegedActionExtensionException;

	/**
	 * Return the priority of the extension in the framework
	 * 
	 * @return
	 */
	public int getPriority();

	/**
	 * Checks if this extension handles THIS actions
	 * 
	 * @param msgContext
	 * @return
	 */
	public boolean isHandle(MessageContext msgContext);

	/**
	 * 
	 * @return
	 */
	public boolean isDisabled();
	
	/**
	 * Returns the local name of the SOAP action handled by this extension
	 * @return
	 */
	public String getAction();
	
	/**
	 * Returns the extension name
	 * @return
	 */
	public String getExtensionName();

    public boolean skipServiceInvocation();

    public boolean skipLowerPriorityExtensions();

}
