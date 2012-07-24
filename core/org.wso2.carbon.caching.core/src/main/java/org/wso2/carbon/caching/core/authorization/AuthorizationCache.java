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

import net.sf.jsr107cache.Cache;
import net.sf.jsr107cache.CacheManager;
import net.sf.jsr107cache.CacheStatistics;


import java.util.Set;
/**
 * Date: Oct 1, 2010 Time: 10:32:26 AM
 */

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * This class is used to cache some of authorization information.
 */
@SuppressWarnings({"UnusedDeclaration"})
public class AuthorizationCache {
    private static Log log = LogFactory.getLog(AuthorizationCache.class);

    public static final String AUTHORIZATION_CACHE = "AUTHORIZATION_CACHE";

    protected Cache cache = null;

    private static AuthorizationCache authorizationCache = new AuthorizationCache();

    private AuthorizationCache() {
        this.cache = CacheManager.getInstance().getCache(AUTHORIZATION_CACHE);
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
                log.debug("AUTHORIZATION_CACHE doesn't exist in CacheManager:\n" + traceString);
    		}
    		return true;
    	}    	
    	return false;
    }


	/**
	 * Gets a new instance of AuthorizationCache.
	 *
	 * @return A new instance of AuthorizationCache.
	 */
	public static AuthorizationCache getInstance() {
		return authorizationCache;
	}

    /**
     * Adds an entry to the cache. Says whether given user or role is authorized or not.
     * @param userName Name of the user which was authorized. If this is null roleName must not be null.
     * @param resourceId The resource on which user/role was authorized.
     * @param action The action which user/role authorized for.
     * @param isAuthorized Whether role/user was authorized or not. <code>true</code> for authorized else <code>false</code>.
     */
    public void addToCache(int tenantId, String userName, String resourceId, String action,
                           boolean isAuthorized) {
    	//check for null
    	if (isCacheNull()) {
    		return;
    	}
        AuthorizationKey key = new AuthorizationKey(tenantId, userName, resourceId, action);

        if (this.cache.containsKey(key)) {
            // Element already in the cache. Remove it first
            this.cache.remove(key);
        }

        AuthorizeCacheEntry cacheEntry = new AuthorizeCacheEntry(isAuthorized);
        this.cache.put(key, cacheEntry);
    }


    /**
     * Looks up from cache whether given user is already authorized. If an entry is not found throws an exception.
     * @param userName User name. Both user name and role name cannot be null at the same time.
     * @param resourceId The resource which we need to check.
     * @param action The action on resource.
     * @return <code>true</code> if an entry is found in cache and user/role is authorized. else <code>false</code>.
     * @throws AuthorizationCacheException  an entry is not found in the cache.
     */
    public boolean isUserAuthorized(int tenantId, String userName, String resourceId, String action)
        throws AuthorizationCacheException {
    	//check for null
    	if (isCacheNull()) {
            throw new AuthorizationCacheException("Authorization information not found in the cache.");
    	}
    	
        AuthorizationKey key = new AuthorizationKey(tenantId, userName, resourceId, action);
        if (!this.cache.containsKey(key)) {
            throw new AuthorizationCacheException("Authorization information not found in the cache.");
        }

        AuthorizeCacheEntry entry = (AuthorizeCacheEntry)this.cache.get(key);
        return entry.isUserAuthorized();
    }

    /**
     * Clears the cache.
     */
    public void clearCache() {
    	//check for null
    	if (isCacheNull()) {
    		return;
    	}
    	
        this.cache.clear();
    }

    /**
     * Clears a given cache entry.
     * @param userName User name to construct the cache key.
     * @param resourceId Resource id to construct the cache key.
     * @param action Action to construct the cache key.
     */
    public void clearCacheEntry(int tenantId, String userName, String resourceId, String action) {
    	//check for null
    	if (isCacheNull()) {
    		return;
    	}

        AuthorizationKey key = new AuthorizationKey(tenantId, userName, resourceId, action);
        if (this.cache.containsKey(key)) {
            this.cache.remove(key);
        }
    }

    /**
     * Clears the cache by user name.
     * @param userName Name of the user.
     */
    public void clearCacheByUser(int tenantId, String userName) {
    	//check for null
    	if (isCacheNull()) {
    		return;
    	}

        Set objectSect = this.cache.keySet();
        for (Object anObjectSect : objectSect) {
            AuthorizationKey key = (AuthorizationKey) anObjectSect;

            if ((key.getTenantId() == tenantId) && (key.getUserName().equals(userName))) {
                this.cache.remove(key);
            }

        }

    }

    /**
     * Method to get the cache hit rate.
     *
     * @return the cache hit rate.
     */
    public double hitRate() {
    	//check for null
    	if (isCacheNull()) {
    		return 0.0;
    	}

        CacheStatistics stats = this.cache.getCacheStatistics();
        return (double) stats.getCacheHits() /
                ((double) (stats.getCacheHits() + stats.getCacheMisses()));
    }

    /**
     * Clears the cache by tenantID to facilitate the cache clearance when role authorization
     * is cleared.
     * @param tenantId
     */
    public void clearCacheByTenant(int tenantId) {
    	//check for null
    	if (isCacheNull()) {
    		return;
    	}

        Set cacheKeySet = this.cache.keySet();
        for (Object cacheKey : cacheKeySet) {
            AuthorizationKey authzKey = (AuthorizationKey) cacheKey;
            if (tenantId == (authzKey.getTenantId())) {
                this.cache.remove(authzKey);
            }

        }
    }

    /**
     * To clear cache when resource authorization is cleared.
     * @param tenantID
     * @param resourceID
     */
    public void clearCacheByResource(int tenantID, String resourceID){
    	//check for null
    	if (isCacheNull()) {
    		return;
    	}

        Set cacheKeySet = this.cache.keySet();
        for (Object cacheKey : cacheKeySet) {
            AuthorizationKey authzKey = (AuthorizationKey) cacheKey;
            if ((tenantID == (authzKey.getTenantId())) && (resourceID.equals(
                    authzKey.getResourceId()))) {
                this.cache.remove(authzKey);
            }

        }
    }

    /**
     * Disable cache completely. Can not enable the cache again.
     */
    public void disableCache(){
        this.cache = null;                                                         
    }

}