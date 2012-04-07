/*
*  Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.carbon.ui.tracker;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;
import org.wso2.carbon.ui.CarbonUIAuthenticator;

import java.util.Arrays;

public class AuthenticatorRegistry {

    private static Log log = LogFactory.getLog(AuthenticatorRegistry.class);
    private static ServiceTracker authTracker;

    public static final String AUTHENTICATOR_TYPE = "authenticator.type";
    
    public static void init(BundleContext bc) throws Exception {
        try {
            authTracker = new ServiceTracker(bc, CarbonUIAuthenticator.class.getName(), null);
            authTracker.open();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw e;
        }
    }

    public static CarbonUIAuthenticator getCarbonAuthenticator(Object object) {
        CarbonUIAuthenticator authenticator = null;
        Object[] objects = authTracker.getServices();
        // cast each object - cannot cast object array
        CarbonUIAuthenticator[] authenticators = new CarbonUIAuthenticator[objects.length];
        int i = 0;
        for (Object obj : objects) {
            authenticators[i] = (CarbonUIAuthenticator) obj;
            i++;
        }
        Arrays.sort(authenticators, new AuthenticatorComparator());
        for (CarbonUIAuthenticator auth : authenticators) {
            if (!auth.isDisabled() && auth.isHandle(object)) {
                authenticator = auth;
                break;
            }
        }
        return authenticator;
    }
}
