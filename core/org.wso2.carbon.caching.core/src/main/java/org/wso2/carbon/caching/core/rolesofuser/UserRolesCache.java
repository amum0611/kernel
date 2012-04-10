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

import org.wso2.carbon.caching.core.BaseCache;

import java.util.Set;

public class UserRolesCache extends BaseCache {

    private static UserRolesCache userRolesCache = null;

    private static final String USER_ROLES_CACHE = "USER_ROLES_CACHE";

    private UserRolesCache(String cacheName) {
        super(cacheName);
    }

    public synchronized static UserRolesCache getUserRolesCacheInstance() {
        if (userRolesCache == null) {
            userRolesCache = new UserRolesCache(USER_ROLES_CACHE);
        }
        return userRolesCache;
    }

    //add to cache
    public void addToCache(int tenantId, String userName, String[] userRoleList) {
        //create cache key
        UserRolesCacheKey userRolesCacheKey = new UserRolesCacheKey(tenantId, userName);
        //create cache entry
        UserRolesCacheEntry userRolesCacheEntry = new UserRolesCacheEntry(userRoleList);
        //add to cache
        super.addToCache(userRolesCacheKey, userRolesCacheEntry);

    }

    //get roles list of user
    public String[] getRolesListOfUser(int tenantId, String userName) {
        //create cache key
        UserRolesCacheKey userRolesCacheKey = new UserRolesCacheKey(tenantId, userName);
        //search cache and get cache entry
        UserRolesCacheEntry userRolesCacheEntry = (UserRolesCacheEntry) super.getValueFromCache(
                userRolesCacheKey);
        String[] roleList = userRolesCacheEntry.getUserRolesList();
        //get role list of user
        return roleList;
    }

    //clear userRolesCache by tenantId
    public void clearCacheByTenant(int tenantId) {
        Set objectSet = this.cache.keySet();
        for (Object object: objectSet) {
            UserRolesCacheKey userRolesCacheKey=(UserRolesCacheKey)object;
            if(tenantId==userRolesCacheKey.getTenantId()){
                this.cache.remove(userRolesCacheKey);
            }
        }
    }

    //clear userRolesCache by tenant and user name
    public void clearCacheEntry(int tenantId, String userName) {
        UserRolesCacheKey userRolesCacheKey=new UserRolesCacheKey(tenantId,userName);
        if(this.cache.containsKey(userRolesCacheKey)){
            this.cache.remove(userRolesCacheKey);
        }

    }

}
