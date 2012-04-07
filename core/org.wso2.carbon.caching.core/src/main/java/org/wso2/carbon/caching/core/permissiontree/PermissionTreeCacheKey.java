package org.wso2.carbon.caching.core.permissiontree;

import org.wso2.carbon.caching.core.CacheKey;

import java.io.Serializable;
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

public class PermissionTreeCacheKey extends CacheKey implements Serializable {

    private int cacheKeyId;
    private static final long serialVersionUID = 1281400603190208429L;

    public PermissionTreeCacheKey(int cacheKeyId) {
        this.cacheKeyId = cacheKeyId;
    }

    public int getCacheKeyId() {
        return cacheKeyId;
    }

    @Override
    public boolean equals(Object otherObject) {
        
        if (!(otherObject instanceof PermissionTreeCacheKey)) {
            return false;
        }
        PermissionTreeCacheKey secondKey = (PermissionTreeCacheKey)otherObject;
        return this.getCacheKeyId() == secondKey.getCacheKeyId();
    }

    @Override
    public int hashCode() {
        return this.getCacheKeyId();
    }
}
