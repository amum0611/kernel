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
package org.wso2.carbon.user.core.common;

import net.sf.jsr107cache.Cache;
import net.sf.jsr107cache.CacheException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.caching.core.BaseCache;
import org.wso2.carbon.caching.core.CacheInvalidator;
import org.wso2.carbon.caching.core.rolesofuser.UserRolesCacheEntry;
import org.wso2.carbon.caching.core.rolesofuser.UserRolesCacheKey;
import org.wso2.carbon.user.core.internal.UMListenerServiceComponent;
import org.wso2.carbon.utils.CarbonUtils;

import java.util.Set;

public class UserRolesCache {

    private static Log log = LogFactory.getLog(UserRolesCache.class);

    private Cache cache = null;

    private static final String USER_ROLES_CACHE = "USER_ROLES_CACHE";

    private static UserRolesCache userRolesCache = new UserRolesCache();

    private UserRolesCache() {
        this.cache =  CarbonUtils.getLocalCache(USER_ROLES_CACHE);
    }

	/**
	 * Gets a new instance of UserRolesCache.
	 *
	 * @return A new instance of UserRolesCache.
	 */
	public static UserRolesCache getInstance() {
		return userRolesCache;
	}
    
    //avoiding NullPointerException when this.cache is null
    private boolean isCacheNull() {
    	if (this.cache == null) {
    		if (log.isDebugEnabled()) {
    			StackTraceElement[] elemets = Thread.currentThread().getStackTrace();
    			String traceString = "";
    			for (int i=1; i<elemets.length; ++i) {
    				traceString += elemets[i] + System.getProperty("line.separator");
    			}
                log.debug("USER_ROLES_CAHCHE doesn't exist in CacheManager:\n" + traceString);                
    		}
    		return true;
    	}    	
    	return false;
    }

    //add to cache
    public void addToCache(String serverId, int tenantId, String userName, String[] userRoleList) {
    	//check for null
    	if (isCacheNull()) {
    		return;
    	}
        //create cache key
        UserRolesCacheKey userRolesCacheKey = new UserRolesCacheKey(serverId, tenantId, userName);
        //create cache entry
        UserRolesCacheEntry userRolesCacheEntry = new UserRolesCacheEntry(userRoleList);
        //add to cache
        this.cache.put(userRolesCacheKey, userRolesCacheEntry);

    }

    //get roles list of user
    public String[] getRolesListOfUser(String serverId, int tenantId, String userName) {
    	//check for null
       	if (isCacheNull()) {
    		return new String[0];
    	}
        //create cache key
        UserRolesCacheKey userRolesCacheKey = new UserRolesCacheKey(serverId, tenantId, userName);
        //search cache and get cache entry
        UserRolesCacheEntry userRolesCacheEntry = (UserRolesCacheEntry) cache.get(
                userRolesCacheKey);
        String[] roleList = userRolesCacheEntry.getUserRolesList();
        //get role list of user
        return roleList;
    }

    //clear userRolesCache by tenantId
    public void clearCacheByTenant(int tenantId) {
    	//check for null
       	if (isCacheNull()) {
    		return;
    	}
        Set objectSet = this.cache.keySet();
        for (Object object: objectSet) {
            UserRolesCacheKey userRolesCacheKey=(UserRolesCacheKey)object;
            if(tenantId==userRolesCacheKey.getTenantId()){
                this.cache.remove(userRolesCacheKey);
            }                                                           
        }

        //sending cluster message
        CacheInvalidator invalidator = UMListenerServiceComponent.getCacheInvalidator();
        try {
            if (invalidator != null) {
                invalidator.invalidateCache(USER_ROLES_CACHE, tenantId);
                if (log.isDebugEnabled()) {
                    log.debug("Calling invalidation cache");
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Not calling invalidation cache");
                }
            }
        } catch (CacheException e) {
            // TODO
            log.error("Error while invalidating cache", e);
        }

    }

    //clear userRolesCache by serverId, tenant and user name
    public void clearCacheEntry(String serverId, int tenantId, String userName) {
    	//check for null
       	if (isCacheNull()) {
    		return;
    	}
        UserRolesCacheKey userRolesCacheKey=new UserRolesCacheKey(serverId,tenantId,userName);
        if(this.cache.containsKey(userRolesCacheKey)){
            this.cache.remove(userRolesCacheKey);
        }

    }
}
