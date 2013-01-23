/*                                                                             
 * Copyright 2004,2005 The Apache Software Foundation.                         
 *                                                                             
 * Licensed under the Apache License, Version 2.0 (the "License");             
 * you may not use this file except in compliance with the License.            
 * You may obtain a copy of the License at                                     
 *                                                                             
 *      http://www.apache.org/licenses/LICENSE-2.0                             
 *                                                                             
 * Unless required by applicable law or agreed to in writing, software         
 * distributed under the License is distributed on an "AS IS" BASIS,           
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.    
 * See the License for the specific language governing permissions and         
 * limitations under the License.                                              
 */
package org.wso2.carbon.core.security;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.CarbonConstants;
import org.wso2.carbon.user.api.AuthorizationManager;
import org.wso2.carbon.user.api.UserRealm;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.api.UserStoreManager;

import javax.management.remote.JMXAuthenticator;
import javax.management.remote.JMXPrincipal;
import javax.security.auth.Subject;
import java.util.Collections;

/**
 * JMX Authenticator for WSAS
 */
public class CarbonJMXAuthenticator implements JMXAuthenticator {

    private static Log log = LogFactory.getLog(CarbonJMXAuthenticator.class);
    private static UserRealm userRealm;

    private static final String JMX_USER_PERMISSION = "/permission/protected/server-admin";

    private static Log audit = CarbonConstants.AUDIT_LOG;

    public static void setUserRealm(UserRealm userRealm) {
        CarbonJMXAuthenticator.userRealm = userRealm;
    }

    public Subject authenticate(Object credentials) {
        // Verify that credentials is of type String[].
        //
        if (!(credentials instanceof String[])) {
            // Special case for null so we get a more informative message
            if (credentials == null) {
                throw new SecurityException("Credentials required");
            }
            throw new SecurityException("Credentials should be String[]");
        }

        // Verify that the array contains username/password
        //
        final String[] aCredentials = (String[]) credentials;
        if (aCredentials.length < 2) {
            throw new SecurityException("Credentials should have at least username & password");
        }

        // Perform authentication
        //
        String userName = aCredentials[0];
        String password = aCredentials[1];

        UserStoreManager authenticator;
        try {
            authenticator = userRealm.getUserStoreManager();
        } catch (UserStoreException e) {
            String msg = "Cannot get authenticator from Realm";
            log.error(msg, e);
            throw new SecurityException(msg, e);
        }

        try {
            if(authenticator.authenticate(userName, password)){

                audit.info("User " + userName + " successfully authenticated to perform JMX operations.");

                if (authorize(userName)) {

                    audit.info("User : " + userName + " successfully authorized to perform JMX operations.");

                    return new Subject(true,
                                   Collections.singleton(new JMXPrincipal(userName)),
                                   Collections.EMPTY_SET,
                                   Collections.EMPTY_SET);
                } else {
                    throw new SecurityException("User : " + userName + " not authorized to perform JMX operations.");
                }

            } else {
                throw new SecurityException("Login failed for user : " + userName + ". Invalid username or password.");
            }
        } catch (SecurityException se) {

            String msg = "Unauthorized access attempt to JMX operation. ";
            audit.warn(msg, se);
            throw new SecurityException(msg, se);

        } catch (Exception e) {

            String msg = "JMX operation failed.";
            log.error(msg, e);
            throw new SecurityException(msg, e);
        }
    }

    private boolean authorize(String userName) throws UserStoreException {

        AuthorizationManager authorizationManager = userRealm.getAuthorizationManager();

        if (authorizationManager != null) {
            return authorizationManager.isUserAuthorized(userName, JMX_USER_PERMISSION, "ui.execute");
        }

        throw new UserStoreException("Unable to retrieve Authorization manager to perform authorization");
    }
}
