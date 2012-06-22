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

package org.wso2.carbon.server.admin.internal;

import java.util.Hashtable;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.service.component.ComponentContext;
import org.wso2.carbon.server.admin.privilegedaction.extension.core.PrivilegedActionExtensionRegistry;

/**
 * @scr.component name="org.wso2.carbon.server.admin.privilegedaction.extension" immediate="true"
 * 
 */

public class PrivilegedActionExtensionComponent {
    
    private static final Log log = LogFactory.getLog(PrivilegedActionExtensionComponent.class);

    protected void activate(ComponentContext ctxt) throws Exception {
    	PrivilegedActionExtensionRegistry registry = new PrivilegedActionExtensionRegistry();
        PrivilegedActionExtensionRegistry.init(ctxt.getBundleContext());
        Hashtable<String, String> props = new Hashtable<String, String>();
        props.put("PrivilegedActionsExtensionsRegistry", registry.getClass().getName());
        ctxt.getBundleContext().registerService(PrivilegedActionExtensionRegistry.class.getName(), registry, props);

        log.info("Privileged Actions Extension bundle activated successfuly.");

        if (log.isDebugEnabled()) {
            log.debug("Privileged Actions Extension bundle activated successfuly.");
        }
    }
    
}