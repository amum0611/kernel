/*
 *  Copyright (c) WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.carbon.user.core.listener;

import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.UserStoreManager;

import java.util.Map;

/**
 * This allows an extension point to implement various additional operations before and after
 * actual user operation is done.
 */
public interface UserOperationEventListener {

    /**
     * Get the execution order identifier for this listener.
     * @return The execution order identifier integer value.
     */
    int getExecutionOrderId();

    /**
     * Define any additional actions before actual authentication is happen
     *
     * @param userName   User name of User
     * @param credential Credential/password of the user
     * @param userStoreManager The underlying UserStoreManager
     * @return Whether execution of this method of the underlying UserStoreManager must happen.
     * @throws UserStoreException  Thrown by the underlying UserStoreManager
     */
    public boolean doPreAuthenticate(String userName, Object credential,
                                     UserStoreManager userStoreManager) throws UserStoreException;

    /**
     * Define any additional actions after actual authentication is happen
     *
     * @param userName   User name of User
     * @param authenticated where user is authenticated or not
     * @param userStoreManager The underlying UserStoreManager
     * @return Whether execution of this method of the underlying UserStoreManager must happen.
     * @throws UserStoreException  Thrown by the underlying UserStoreManager
     */
    public boolean doPostAuthenticate(String userName, boolean authenticated,
                                      UserStoreManager userStoreManager) throws UserStoreException;

    /**
     * Define any additional actions before user is added.
     *
     * @param userName  User name of User
     * @param credential Credential/password of the user
     * @param roleList role list of user
     * @param claims Properties of the user
     * @param profile profile name of user
     * @param userStoreManager The underlying UserStoreManager
     * @return Whether execution of this method of the underlying UserStoreManager must happen.
     * @throws UserStoreException  Thrown by the underlying UserStoreManager
     */
    public boolean doPreAddUser(String userName, Object credential, String[] roleList,
            Map<String, String> claims, String profile, UserStoreManager userStoreManager)
                                                                        throws UserStoreException;

    /**
     * Define any additional actions after user is added.
     *
     * @param userName User name of User
     * @param userStoreManager The underlying UserStoreManager
     * @return Whether execution of this method of the underlying UserStoreManager must happen.
     * @throws UserStoreException  Thrown by the underlying UserStoreManager
     */
    public boolean doPostAddUser(String userName, UserStoreManager userStoreManager)
                                                                        throws UserStoreException;

    /**
     * Define any additional actions before credential is updated by user
     *
     * @param userName  User name of User
     * @param newCredential  new credential/password of the user
     * @param oldCredential  Old credential/password of the user
     * @param userStoreManager The underlying UserStoreManager
     * @return Whether execution of this method of the underlying UserStoreManager must happen.
     * @throws UserStoreException  Thrown by the underlying UserStoreManager
     */
    public boolean doPreUpdateCredential(String userName, Object newCredential, Object oldCredential,
                                    UserStoreManager userStoreManager) throws UserStoreException;

    /**
     * Define any additional actions after credential is updated by user
     *
     * @param userName  User name of User
     * @param userStoreManager The underlying UserStoreManager
     * @return Whether execution of this method of the underlying UserStoreManager must happen.
     * @throws UserStoreException  Thrown by the underlying UserStoreManager
     */
    public boolean doPostUpdateCredential(String userName, UserStoreManager userStoreManager)
                                                                        throws UserStoreException;

    /**
     * Define any additional actions before credential is updated by Admin
     *
     * @param userName  User name of User
     * @param newCredential  new credential/password of the user
     * @param userStoreManager The underlying UserStoreManager
     * @return Whether execution of this method of the underlying UserStoreManager must happen.
     * @throws UserStoreException  Thrown by the underlying UserStoreManager
     */
    public boolean doPreUpdateCredentialByAdmin(String userName, Object newCredential,
                                    UserStoreManager userStoreManager) throws UserStoreException;

    /**
     * Define any additional actions after credential is updated by Admin
     *
     * @param userName  User name of User
     * @param userStoreManager The underlying UserStoreManager
     * @return Whether execution of this method of the underlying UserStoreManager must happen.
     * @throws UserStoreException  Thrown by the underlying UserStoreManager
     */

    public boolean doPostUpdateCredentialByAdmin(String userName, UserStoreManager userStoreManager)
                                                                        throws UserStoreException;

    /**
     * Define any additional actions before user is deleted by Admin
     *
     * @param userName User name of User
     * @param userStoreManager The underlying UserStoreManager
     * @return Whether execution of this method of the underlying UserStoreManager must happen.
     * @throws UserStoreException  Thrown by the underlying UserStoreManager
     */
    public boolean doPreDeleteUser(String userName, UserStoreManager userStoreManager)
                                                                        throws UserStoreException;

    /**
     * Defines any additional actions after user is deleted by Admin
     *
     * @param userName User name of User
     * @param userStoreManager The underlying UserStoreManager
     * @return Whether execution of this method of the underlying UserStoreManager must happen.
     * @throws UserStoreException  Thrown by the underlying UserStoreManager
     */
    public boolean doPostDeleteUser(String userName, UserStoreManager userStoreManager)
                                                                        throws UserStoreException;

    /**
     *  Defines any additional actions before user attribute is set by Admin
     *
     * @param userName User name of User
     * @param claimURI claim uri
     * @param claimValue claim value
     * @param profileName user profile name
     * @param userStoreManager The underlying UserStoreManager
     * @return Whether execution of this method of the underlying UserStoreManager must happen.
     * @throws UserStoreException  Thrown by the underlying UserStoreManager
     */
    public boolean doPreSetUserClaimValue(String userName, String claimURI, String claimValue,
                  String profileName, UserStoreManager userStoreManager) throws UserStoreException;


    /**
     * Defines any additional actions after user attribute is set by Admin
     *
     * @param userName User name of User
     * @param userStoreManager The underlying UserStoreManager
     * @return Whether execution of this method of the underlying UserStoreManager must happen.
     * @throws UserStoreException  Thrown by the underlying UserStoreManager
     */
    public boolean doPostSetUserClaimValue(String userName, UserStoreManager userStoreManager)
                                                                        throws UserStoreException;

    /**
     *  Defines any additional actions before user attributes are set by Admin
     *
     * @param userName  User name of User
     * @param claims claim uri and claim value map
     * @param profileName user profile name
     * @param userStoreManager The underlying UserStoreManager
     * @return Whether execution of this method of the underlying UserStoreManager must happen.
     * @throws UserStoreException  Thrown by the underlying UserStoreManager
     */
    public boolean doPreSetUserClaimValues(String userName, Map<String, String> claims,
                  String profileName, UserStoreManager userStoreManager) throws UserStoreException;

    /**
     * Defines any additional actions after user attributes are set by Admin
     *
     * @param userName  User name of User
     * @param userStoreManager The underlying UserStoreManager
     * @return Whether execution of this method of the underlying UserStoreManager must happen.
     * @throws UserStoreException  Thrown by the underlying UserStoreManager
     */
    public boolean doPostSetUserClaimValues(String userName, UserStoreManager userStoreManager)
                                                                        throws UserStoreException;

    /**
     * Defines any additional actions before user attributes are deleted by Admin
     *
     * @param userName   User name of User
     * @param claims claim uri and claim value map
     * @param profileName user profile name
     * @param userStoreManager The underlying UserStoreManager
     * @return Whether execution of this method of the underlying UserStoreManager must happen.
     * @throws UserStoreException  Thrown by the underlying UserStoreManager
     */
    public boolean doPreDeleteUserClaimValues(String userName, String[] claims, String profileName,
                                      UserStoreManager userStoreManager) throws UserStoreException;

    /**
     * Defines any additional actions after user attributes are deleted by Admin
     *
     * @param userName   User name of User
     * @param userStoreManager The underlying UserStoreManager
     * @return Whether execution of this method of the underlying UserStoreManager must happen.
     * @throws UserStoreException  Thrown by the underlying UserStoreManager
     */
    public boolean doPostDeleteUserClaimValues(String userName, UserStoreManager userStoreManager)
                                                                        throws UserStoreException;

    /**
     * Defines any additional actions before user attribute is deleted by Admin
     *
     * @param userName   User name of User
     * @param claimURI  claim uri
     * @param profileName user profile name
     * @param userStoreManager The underlying UserStoreManager
     * @return Whether execution of this method of the underlying UserStoreManager must happen.
     * @throws UserStoreException  Thrown by the underlying UserStoreManager
     */
    public boolean doPreDeleteUserClaimValue(String userName, String claimURI, String profileName,
                                     UserStoreManager userStoreManager) throws UserStoreException;

    /**
     * Defines any additional actions after user attribute is deleted by Admin
     *
     * @param userName  User name of User
     * @param userStoreManager The underlying UserStoreManager
     * @return Whether execution of this method of the underlying UserStoreManager must happen.
     * @throws UserStoreException  Thrown by the underlying UserStoreManagern
     */
    public boolean doPostDeleteUserClaimValue(String userName, UserStoreManager userStoreManager)
                                                                        throws UserStoreException;

}
