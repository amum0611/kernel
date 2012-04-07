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

package org.wso2.carbon.core.multitenancy.persistence;

import org.wso2.carbon.user.core.tenant.Tenant;

/**
 * The tenant persisting code must implement this interface.
 * An OSGI service is registered for this interface.
 * Any bundle can get hold of this OSGI service and persist
 * tenants. 
 *
 */
public interface TenantPersistor {

    /**
     * A method to persist tenant.
     * 
     * @param tenant The tenant to persist.
     * @return The tenant Id
     * @throws Exception
     */
    public int persistTenant(Tenant tenant) throws Exception;

    /**
     * 
     * @param tenant Tenant to persist
     * @param checkDomainValidation Check domain validation
     * @param successKey The success key of domain validation
     * @param originatedService The originated service
     * @return The tenant Id
     * @throws Exception
     */
    public int persistTenant(Tenant tenant, boolean checkDomainValidation, String successKey,
                             String originatedService) throws Exception;
    
}
