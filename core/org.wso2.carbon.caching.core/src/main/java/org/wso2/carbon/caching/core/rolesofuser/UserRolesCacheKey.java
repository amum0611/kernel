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
package org.wso2.carbon.caching.core.rolesofuser;

import org.wso2.carbon.base.MultitenantConstants;
import org.wso2.carbon.caching.core.CacheKey;

import java.io.Serializable;

public class UserRolesCacheKey extends CacheKey implements Serializable {

    private static final long serialVersionUID = 987045632165409867L;

    private String userName;
    private int tenantId;

    public UserRolesCacheKey(int tenantId, String userName) {
        this.tenantId = tenantId;
        this.userName = userName;
    }

    @Override
    public boolean equals(Object otherObject) {
        if (!(otherObject instanceof UserRolesCacheKey)) {
            return false;
        }
        UserRolesCacheKey userRolesCacheKey = (UserRolesCacheKey) otherObject;
        return checkKeyAttributesEqual(userRolesCacheKey.getTenantId(), userRolesCacheKey.getUserName());
    }

    @Override
    public int hashCode() {
        return getAttributeHashCode();
    }

    public boolean checkKeyAttributesEqual(int tenantId, String userName) {
        return ((this.tenantId == tenantId) && (this.userName.equals(userName)));
    }

    public int getAttributeHashCode() {
        return ((this.tenantId == MultitenantConstants.SUPER_TENANT_ID ? 0 : tenantId)
        		+ this.userName.hashCode() * 7);
    }

    public int getTenantId() {
        return tenantId;
    }

    public String getUserName() {
        return userName;
    }
}
