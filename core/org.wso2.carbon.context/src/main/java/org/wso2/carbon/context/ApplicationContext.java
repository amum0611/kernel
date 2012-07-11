/*
 *  Copyright (c) 2005-2009, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.carbon.context;

import org.wso2.carbon.base.CarbonBaseUtils;
import org.wso2.carbon.utils.CarbonUtils;
import org.wso2.carbon.utils.multitenancy.CarbonApplicationContextHolder;

import java.io.File;

public class ApplicationContext {
    // The reason to why we decided to have a ApplicationContext is to follow the same flow of CarbonContext and
    // to keep the deployed application name and application specific information through out the whole carbon platform.

    private CarbonApplicationContextHolder carbonApplicationContextHolder = null;

    /**
     * Creates a ApplicationContext using the given ApplicationContext holder as its backing instance.
     *
     * @param carbonApplicationContextHolder the ApplicationContext holder that backs this ApplicationContext object.
     *
     * @see ApplicationContext
     */
    protected ApplicationContext(CarbonApplicationContextHolder carbonApplicationContextHolder) {
        this.carbonApplicationContextHolder = carbonApplicationContextHolder;
    }

    /**
     * Utility method to obtain the current ApplicationContext holder after an instance of a
     * ApplicationContext has been created.
     *
     * @return the current ApplicationContext holder
     */
    protected CarbonApplicationContextHolder getCarbonApplicationContextHolder() {
        if (carbonApplicationContextHolder == null) {
            return CarbonApplicationContextHolder.getCurrentCarbonAppContextHolder();
        }
        return carbonApplicationContextHolder;
    }

    /**
     * Obtains the ApplicationContext instance stored on the ApplicationContext holder.
     *
     * @return the ApplicationContext instance.
     */
    public static ApplicationContext getCurrentApplicationContext() {
        return new ApplicationContext(null);
    }

    /**
     * Method to obtain the application name on this ApplicationContext instance.
     *
     * @return the Application Name.
     */
    public String getApplicationName() {
        CarbonBaseUtils.checkSecurity();
        return getCarbonApplicationContextHolder().getApplicationName();
    }

    /**
     * Method to get the application name when a url mapping is available to a context.
     * then we'll get the / as the context, so from the actual virtual host getting
     * docbase of / context and filter out the application name from that.
     *
     * @param appPath doc base of the context
     * @return
     */
    public String getApplicationNameFromRequest(String appPath) {
        File file = new File(appPath);
        String appName = null;
        String contextName;

        if(appPath.contains(CarbonUtils.getCarbonTenantsDirPath())) {
            //removing tenant repository path
            contextName = appPath.substring(CarbonUtils.getCarbonTenantsDirPath().length(), appPath.length());
            String[] elements = null;
            elements = contextName.split("/");
            if(file.isDirectory()) {
                appName = elements[elements.length - 1];
            } else if(appPath.contains(".war")) {
                appName = elements[elements.length - 1].substring(0, elements[elements.length - 1].indexOf(".war"));
            }
        } else if(appPath.contains(CarbonUtils.getCarbonRepository())) {
            //removing carbon repository path
            contextName = appPath.substring(CarbonUtils.getCarbonRepository().length(), appPath.length());
            String[] elements = null;
            elements = contextName.split("/");
            if(file.isDirectory()) {
                appName = elements[elements.length - 1];
            } else if(appPath.contains(".war")){
                appName = elements[elements.length - 1].substring(0, elements[elements.length - 1].indexOf(".war"));
            }
        } else {

        }
        return appName;
    }

}