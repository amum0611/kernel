/*
*  Copyright WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.carbon.core.caching;

import java.io.Serializable;
import java.util.Set;

import net.sf.jsr107cache.Cache;
import net.sf.jsr107cache.CacheManager;

import org.apache.axis2.clustering.ClusteringCommand;
import org.apache.axis2.clustering.ClusteringFault;
import org.apache.axis2.clustering.ClusteringMessage;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.caching.core.authorization.AuthorizationKey;
import org.wso2.carbon.caching.core.identity.IdentityCacheKey;
import org.wso2.carbon.caching.core.rolesofuser.UserRolesCacheKey;

/**
 * This is the cluster message to invalidate cache entries. 
 */
public class InvalidateCacheEntryMessage extends ClusteringMessage {

    private static final long serialVersionUID = 5223440009631274037L;

	private static Log log = LogFactory.getLog(InvalidateCacheEntryMessage.class);
	
	private String cacheName;
	private Serializable key;
	
	public InvalidateCacheEntryMessage() {
    }

	public InvalidateCacheEntryMessage(String cacheName, Serializable key) {
		this.cacheName = cacheName;
		this.key = key;
    }

	@Override
    public ClusteringCommand getResponse() {
		return null;
    }

	@Override
    public void execute(ConfigurationContext configContext) throws ClusteringFault {
        if (cacheName != null && key != null) {
            if (log.isDebugEnabled()) {
                log.debug("Got cluster message " + cacheName + ":" + key.toString());
            }
            Cache cache = CacheManager.getInstance().getCache(cacheName);
            if(key instanceof Integer){
                if(cache != null){
                    Set cacheKeySet = cache.keySet();
                    for (Object cacheKey : cacheKeySet) {
                        if(cacheKey instanceof AuthorizationKey){
                            AuthorizationKey authorizationKey = (AuthorizationKey) cacheKey;
                            if ((Integer) key == authorizationKey.getTenantId()) {
                                cache.remove(authorizationKey);
                            }
                        } else if(cacheKey instanceof UserRolesCacheKey){
                            UserRolesCacheKey userRolesCacheKey = (UserRolesCacheKey) cacheKey;
                            if ((Integer) key == userRolesCacheKey.getTenantId()) {
                                cache.remove(userRolesCacheKey);
                            }
                        } else if(cacheKey instanceof IdentityCacheKey){
                            IdentityCacheKey identityCacheKey = (IdentityCacheKey) cacheKey;
                            if((Integer) key == identityCacheKey.getTenantId()){
                                cache.remove(identityCacheKey);
                            }
                        }
                    }
                }
            } else {
                if (cache.containsKey(key)) {
                    cache.remove(key);
                }
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Got cluster message " + cacheName + ":" + key.toString());
            }
        }
    }

}
