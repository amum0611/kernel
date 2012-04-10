package org.wso2.carbon.user.core.tenant;

//import org.wso2.carbon.user.core.BaseCache;
import org.wso2.carbon.caching.core.BaseCache;


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

/**
 * Date: Oct 1, 2010 Time: 2:36:37 PM
 */

/**
 * This is the tenant cache.
 */
class TenantDomainCache extends BaseCache {
    
    public static final String TENANT_DOMAIN_CACHE = "TENANT_DOMAIN_CACHE";

    private static TenantDomainCache tenantDomainCache = null;

    private TenantDomainCache() {
        super(TENANT_DOMAIN_CACHE);
    }

    /**
     * Gets a new instance of TenantCache.
     *
     * @return A new instance of TenantCache.
     */
    public synchronized static TenantDomainCache getInstance() {
        if (tenantDomainCache == null) {
            tenantDomainCache = new TenantDomainCache();
        }
        return tenantDomainCache;
    }

}
