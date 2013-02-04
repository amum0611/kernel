package org.wso2.carbon.user.mgt.multiplecredentials;

/*
 *   Copyright (c) WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *   WSO2 Inc. licenses this file to you under the Apache License,
 *   Version 2.0 (the "License"); you may not use this file except
 *   in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

import org.wso2.carbon.core.AbstractAdmin;
import org.wso2.carbon.user.core.multiplecredentials.Credential;
import org.wso2.carbon.user.mgt.common.ClaimValue;
import org.wso2.carbon.user.mgt.common.MultipleCredentialsUserAdminException;

public class MultipleCredentialsUserAdmin extends AbstractAdmin {

    public void addUser(Credential credential, String[] roleList,
                        ClaimValue[] claims, String profileName) throws
                                                                 MultipleCredentialsUserAdminException {
        MultipleCredentialsUserProxy multipleCredentialsUserProxy = new MultipleCredentialsUserProxy(super.getUserRealm());
        multipleCredentialsUserProxy.addUser(credential, roleList, claims, profileName);
    }

    public void addUsers(Credential[] credential, String[] roleList,
                        ClaimValue[] claims, String profileName) throws MultipleCredentialsUserAdminException {
        MultipleCredentialsUserProxy multipleCredentialsUserProxy = new MultipleCredentialsUserProxy(super.getUserRealm());
        multipleCredentialsUserProxy.addUser(credential, roleList, claims, profileName);
    }

    public void deleteUser(String identifier, String credentialType) throws MultipleCredentialsUserAdminException {
        MultipleCredentialsUserProxy multipleCredentialsUserProxy = new MultipleCredentialsUserProxy(super.getUserRealm());
        multipleCredentialsUserProxy.deleteUser(identifier, credentialType, super.getConfigSystemRegistry());
    }

    public void addCredential(String anIdentifier, String credentialType, Credential credential) throws MultipleCredentialsUserAdminException {
        MultipleCredentialsUserProxy multipleCredentialsUserProxy = new MultipleCredentialsUserProxy(super.getUserRealm());
        multipleCredentialsUserProxy.addCredential(anIdentifier, credentialType, credential);
    }

    public void updateCredential(String identifier, String credentialType, Credential credential) throws MultipleCredentialsUserAdminException {
        MultipleCredentialsUserProxy multipleCredentialsUserProxy = new MultipleCredentialsUserProxy(super.getUserRealm());
        multipleCredentialsUserProxy.updateCredential(identifier, credentialType, credential);
    }

//    public void updateCredentialByUser(Credential credential) throws MultipleCredentialsUserAdminException {
//        MultipleCredentialsUserProxy multipleCredentialsUserProxy = new MultipleCredentialsUserProxy(super.getUserRealm());
//        multipleCredentialsUserProxy.updateCredentialByUser(credential);
//    }

    public void deleteCredential(String identifier, String credentialType) throws MultipleCredentialsUserAdminException {
        MultipleCredentialsUserProxy multipleCredentialsUserProxy = new MultipleCredentialsUserProxy(super.getUserRealm());
        multipleCredentialsUserProxy.deleteCredential(identifier, credentialType);
    }

    public Credential[] getCredentials(String anIdentifier, String credentialType) throws MultipleCredentialsUserAdminException {
        MultipleCredentialsUserProxy multipleCredentialsUserProxy = new MultipleCredentialsUserProxy(super.getUserRealm());
        return multipleCredentialsUserProxy.getCredentials(anIdentifier, credentialType);
    }

    public boolean authenticate(Credential credential) throws
                                                       MultipleCredentialsUserAdminException {
        MultipleCredentialsUserProxy multipleCredentialsUserProxy = new MultipleCredentialsUserProxy(super.getUserRealm());
        return multipleCredentialsUserProxy.authenticate(credential);
    }
}
