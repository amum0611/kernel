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

package org.wso2.carbon.caching.core.authorization;

import java.io.Serializable;
/**
 * Date: Oct 7, 2010 Time: 11:13:54 AM
 */

/**
 * A key class which wraps a cache key used by Authorization manager.
 */
@SuppressWarnings({"UnusedDeclaration"})
public class AuthorizationKey implements Serializable {

    private static final long serialVersionUID = 926710669453381695L;

    private String userName;

    private String resourceId;

    private String action;

    private int tenantId;

    public AuthorizationKey(int tenantId, String userName, String resourceId, String action) {
        this.userName = userName;
        this.resourceId = resourceId;
        this.action = action;
        this.tenantId = tenantId;

    }

    @Override
    public boolean equals(Object otherObject) {

        if (!(otherObject instanceof AuthorizationKey)) {
            return false;
        }

        AuthorizationKey secondObject = (AuthorizationKey)otherObject;

        // Only user name and role name can be null. We assume other parameters are not null.
        return checkAttributesAreEqual(this.tenantId, this.userName, this.resourceId, this.action, secondObject);
    }

    @Override
    public int hashCode() {

        return getHashCodeForAttributes(this.tenantId, this.userName, this.resourceId, this.action);
    }

    public String getUserName() {
        return userName;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getAction() {
        return action;
    }

    public int getTenantId() {
        return tenantId;
    }

    public void setTenantId(int tenantId) {
        this.tenantId = tenantId;
    }

    private int getHashCodeForAttributes(int tenantId, String userName, String resourceId,
                                         String action) {

        if ((tenantId != -1) && userName != null) {
            return tenantId + userName.hashCode() * 5 + resourceId.hashCode() * 7 +
                   action.hashCode() * 11;
        } else {
            return resourceId.hashCode() * 7 + action.hashCode() * 11;
        }

    }

    private boolean checkAttributesAreEqual(int tenantId, String userName, String resourceIdentifier,
                                            String actionName,
                                            AuthorizationKey authorizationKey) {
        return (tenantId==(authorizationKey.getTenantId())) && userName.equals(authorizationKey.getUserName())
               && resourceIdentifier.equals(authorizationKey.getResourceId()) &&
               actionName.equals(authorizationKey.getAction());
    }
}