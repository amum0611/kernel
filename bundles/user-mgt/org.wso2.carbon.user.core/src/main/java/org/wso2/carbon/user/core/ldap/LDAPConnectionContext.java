/*
 * Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.carbon.user.core.ldap;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.user.api.CarbonConstants;
import org.wso2.carbon.user.api.RealmConfiguration;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.utils.CarbonUtils;

public class LDAPConnectionContext {

    @SuppressWarnings("rawtypes")
    private Hashtable environment;

    private static Log log = LogFactory.getLog(LDAPConnectionContext.class);

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public LDAPConnectionContext(RealmConfiguration realmConfig) {
        String rawConnectionURL = realmConfig.getUserStoreProperty(LDAPConstants.CONNECTION_URL);
        String portInfo = rawConnectionURL.split(":")[2];

        String connectionURL = null;
        String port = null;

        // if the port contains a template string that refers to carbon.xml
        if ((portInfo.contains("${")) && (portInfo.contains("}"))) {
            port = Integer.toString(CarbonUtils.getPortFromServerConfig(portInfo));
        }

        if (port != null) {
            connectionURL = rawConnectionURL.replace(portInfo, port);
        } else {
            // if embedded-ldap is not enabled,
            connectionURL = realmConfig.getUserStoreProperty(LDAPConstants.CONNECTION_URL);
        }

        String connectionName = realmConfig.getUserStoreProperty(LDAPConstants.CONNECTION_NAME);
        String connectionPassword = realmConfig
                .getUserStoreProperty(LDAPConstants.CONNECTION_PASSWORD);

        if (log.isDebugEnabled()) {
            log.debug("Connection Name :: " + connectionName + "," + "Connection Password :: "
                    + connectionPassword + "," + "Connection URL :: " + connectionURL);
        }

        environment = new Hashtable();

        environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        environment.put(Context.SECURITY_AUTHENTICATION, "simple");

        /**
         * In carbon JNDI context we need to by pass specific tenant context and we need the base
         * context for LDAP operations.
         */
        environment.put(CarbonConstants.REQUEST_BASE_CONTEXT, "true");

        if (connectionName != null) {
            environment.put(Context.SECURITY_PRINCIPAL, connectionName);
        }

        if (connectionPassword != null) {
            environment.put(Context.SECURITY_CREDENTIALS, connectionPassword);
        }

        if (connectionURL != null) {
            environment.put(Context.PROVIDER_URL, connectionURL);
        }

        // Enable connection pooling
        environment.put("com.sun.jndi.ldap.connect.pool", "true");

        // set referral status if provided in configuration.
        if (realmConfig.getUserStoreProperty(LDAPConstants.PROPERTY_REFERRAL) != null) {
            environment.put("java.naming.referral",
                    realmConfig.getUserStoreProperty(LDAPConstants.PROPERTY_REFERRAL));
        }
        
        String binaryAttribute = realmConfig.getUserStoreProperty(LDAPConstants.LDAP_ATTRIBUTES_BINARY);
        
        if (binaryAttribute != null) {
            environment.put(LDAPConstants.LDAP_ATTRIBUTES_BINARY, binaryAttribute);
        }


    }

    public DirContext getContext() throws UserStoreException {
        DirContext context = null;
        try {
            context = new InitialDirContext(environment);
        } catch (NamingException e) {
            log.error("Error obtaining connection. " + e.getMessage(), e);
            log.error("Trying again to get connection.");

            try {
                context = new InitialDirContext(environment);
            } catch (Exception e1) {
                log.error("Error obtaining connection for the second time" + e.getMessage(), e);
                throw new UserStoreException("Error obtaining connection. " + e.getMessage(), e);
            }

        }
        return (context);

    }

    @SuppressWarnings("unchecked")
    public void updateCredential(String connectionPassword) {
        /*
         * update the password otherwise it is not possible to connect again if admin password
         * changed
         */
        this.environment.put(Context.SECURITY_CREDENTIALS, connectionPassword);
    }
}
