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

import javax.servlet.http.HttpServletRequest;

/**
 * A collection of useful utility methods
 */
public class Utils {

    public static String getTenantDomain(HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        if (requestURI.indexOf("/t/") == -1) {  // Super tenant?
            return null;
        }
        String temp = requestURI.substring(requestURI.indexOf("/t/") + 3);
        if (temp.indexOf('/') != -1) {
            temp = temp.substring(0, temp.indexOf('/'));
        }
        return temp;
    }
}
