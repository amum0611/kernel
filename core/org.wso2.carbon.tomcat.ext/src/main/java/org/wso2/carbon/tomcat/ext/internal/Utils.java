/*
 *  Copyright (c) 2005-2012, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.carbon.tomcat.ext.internal;

import org.apache.catalina.connector.Request;
import org.apache.catalina.core.StandardContext;
import org.wso2.carbon.context.ApplicationContext;
import org.wso2.carbon.utils.CarbonUtils;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * A collection of useful utility methods
 */
public class Utils {

	public static String getTenantDomain(HttpServletRequest request) {
		String requestURI = request.getRequestURI();
		String domain = MultitenantConstants.SUPER_TENANT_DOMAIN_NAME;
		if (!requestURI.contains("/t/")) {
			// check for admin services - tenant admin services are deployed in
			// super tenant flow
			HttpSession session = request.getSession(false);
			if (session != null && session.getAttribute(MultitenantConstants.TENANT_DOMAIN) != null) {
				domain = (String) session.getAttribute(MultitenantConstants.TENANT_DOMAIN);
			}
		} else {
			String temp = requestURI.substring(requestURI.indexOf("/t/") + 3);
			if (temp.indexOf('/') != -1) {
				temp = temp.substring(0, temp.indexOf('/'));
				domain = temp;
			}
		}
		return domain;
	}

	public static String getServiceName(HttpServletRequest request) {
		String requestURI = request.getRequestURI();
		String serviceName = "";
		if (requestURI.contains("/services/")) {
			String temp = requestURI.substring(requestURI.indexOf("/services/") + 9);
			if (temp.indexOf('/') != -1) {
				temp = temp.substring(0, temp.length());
				serviceName = temp;
			}
			if (serviceName.contains("/t/")) {
				String temp2[] = serviceName.split("/");
				if (temp2.length > 3) {
					serviceName = temp2[3];
				}
			}
			if (serviceName.contains(".")) {
				serviceName = serviceName.substring(0, serviceName.indexOf('.'));
			} else if (serviceName.contains("?")) {
				serviceName = serviceName.substring(0, serviceName.indexOf('?'));
			}
		}
		serviceName = serviceName.replace("/", "");
		return serviceName;
	}
    
    public static String getAppNameFromRequest(Request request) {
        String appName = null;
        String defaultHost = CarbonTomcatServiceHolder.getCarbonTomcatService().getTomcat().getEngine().getDefaultHost();
        StandardContext standardContext = (StandardContext) request.getContext();
        String appPath = standardContext.getDocBase();
        if (request.getRequestURI().contains("/services/")) {
            //setting the application id for services
            return Utils.getServiceName(request);
        } else if(!request.getContext().getName().equals("/")) {
            //checking for ST in order to get original doc base
            if(!appPath.contains(CarbonUtils.getCarbonTenantsDirPath())) {
                appPath = standardContext.getOriginalDocBase();
            }
            appName = ApplicationContext.getCurrentApplicationContext().getApplicationNameFromRequest(appPath);
        } else if(!defaultHost.equalsIgnoreCase(request.getHost().getName())) {
            //setting application id for the webapps which has deployed in a virtual host
            appPath = request.getHost().getAppBase() + appPath;
            appName = ApplicationContext.getCurrentApplicationContext().getApplicationNameFromRequest(appPath);

        }
        return appName;
    }
}
