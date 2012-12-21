package org.wso2.carbon.user.core.tenant;

//import org.wso2.carbon.user.core.BaseCache;
import org.wso2.carbon.caching.core.BaseCache;

public class TenantCache extends BaseCache {
    
    public static final String TENANT_CACHE = "TENANT_CACHE";

    private static TenantCache tenantCache = null;

    private TenantCache() {
        super(TENANT_CACHE);
    }

    /**
     * Gets a new instance of TenantCache.
     *
     * @return A new instance of TenantCache.
     */
    public synchronized static TenantCache getInstance() {
        if (tenantCache == null) {
            tenantCache = new TenantCache();
        }
        return tenantCache;
    }

}
