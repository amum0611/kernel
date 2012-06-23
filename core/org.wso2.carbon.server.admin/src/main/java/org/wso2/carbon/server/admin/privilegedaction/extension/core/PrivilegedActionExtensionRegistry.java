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

package org.wso2.carbon.server.admin.privilegedaction.extension.core;

import java.util.ArrayList;
import java.util.Collections;
import org.apache.axis2.context.MessageContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;
import org.wso2.carbon.server.admin.privilegedaction.extension.utills.PrivilegedActionExtensionComparator;

public class PrivilegedActionExtensionRegistry {

	private static Log log = LogFactory.getLog(PrivilegedActionExtensionRegistry.class);
	private static ServiceTracker extensionTracker;

	public static void init(BundleContext bc) throws Exception {
		try {
			extensionTracker =
			                   new ServiceTracker(bc, PrivilegedActionExtension.class.getName(),
			                                      null);
			extensionTracker.open();
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw e;
		}
	}

	public static ArrayList<PrivilegedActionExtension> getPrivilegedActionExtensions(MessageContext msgContext){

		Object[] objects = extensionTracker.getServices();

		if (objects == null) {
			return null;
		}

		ArrayList<PrivilegedActionExtension> extensions =
		                                                  new ArrayList<PrivilegedActionExtension>();
		PrivilegedActionExtension ext = null;

		for (Object obj : objects) {
			ext = (PrivilegedActionExtension) obj;
			/* Must add only the extensions that handle the current action */
			if (!ext.isDisabled() && ext.isHandle(msgContext)) {
				extensions.add(ext);
			}
		}
		/* Sorting in the priority order so can be executed in sequence */
		Collections.sort(extensions, new PrivilegedActionExtensionComparator());

		return extensions;
	}

}
