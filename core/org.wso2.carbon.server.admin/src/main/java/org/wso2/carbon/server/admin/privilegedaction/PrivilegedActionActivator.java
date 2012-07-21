/*
 *  Copyright (c) 2005-2012, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.carbon.server.admin.privilegedaction;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.service.component.ComponentContext;

import java.util.Collections;

/**
 * In order to write a PrivilegedActionExtension this class needs to be extended and the <code>@scr.component</code>
   annotation should be included at the top of the class as follows
   <code>
        &#047;&#042;&#042;
        &nbsp;&#042;&nbsp;&#064;scr.component name= immediate=true
        &nbsp;&#042;&#047;
   </code>
 */
public abstract class PrivilegedActionActivator {

    private static final Log log = LogFactory.getLog(PrivilegedActionActivator.class);

    private PrivilegedAction privilegedAction = null;

    protected void activate (ComponentContext ctxt) {
        privilegedAction = this.getServiceInstance();
        PrivilegedActionMessageReceiver.privilegedActions.add(privilegedAction);
        Collections.sort(PrivilegedActionMessageReceiver.privilegedActions,new PrivilegedActionComparator());
        if(log.isDebugEnabled()){
            String msg = privilegedAction.getExtensionName() + "activated";
            log.debug(msg);
        }
    }

    /**
         * Should return an instance of the PrivilegedActionExtension that implements <code>PrivilegedAction</code>
         *
         * @return instance of PrivilegedAction
         */
    public abstract PrivilegedAction getServiceInstance();


}
