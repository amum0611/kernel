package org.wso2.carbon.user.core.multiplecredentials;

/*
*  Copyright (c) WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.UserStoreManager;

import java.util.Map;

public interface MultipleCredentialUserStoreManager extends UserStoreManager {

    public void addUser(Credential credential, String[] roleList,
                        Map<String, String> claims, String profileName) throws UserStoreException;

    public void addUser(Credential[] credential, String[] roleList,
                        Map<String, String> claims, String profileName) throws UserStoreException;

    public void deleteUser(String identifier, String credentialType) throws UserStoreException;

    public void deleteUser(Credential credential) throws UserStoreException;

    public void addCredential(String anIdentifier, String credentialType, Credential credential) throws UserStoreException;

    public void updateCredential(String identifier, String credentialType, Credential credential) throws UserStoreException;

    public void deleteCredential(String identifier, String credentialType) throws UserStoreException;

    public Credential[] getCredentials(String anIdentifier, String credentialType) throws UserStoreException;

    public Credential[] getCredentials(Credential credential) throws UserStoreException;

    public boolean authenticate(Credential credential) throws UserStoreException;

    String[] getRoleListOfUser(String identifer, String credentialType)
            throws UserStoreException;
}
