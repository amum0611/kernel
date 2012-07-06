/**
 *  Copyright (c) 2012, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.wso2.carbon.ndatasource.core;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.ndatasource.common.DataSourceException;
import org.wso2.carbon.ndatasource.core.utils.DataSourceUtils;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;

/**
 * This class manages the setting/retrieving the information of, if a specific tenant's 
 * data source repository has any data sources registered. This information is stored in the 
 * super tenant's registry, and is used to avoid loading each tenant's registries 
 * if there aren't any data sources there to be registered.
 */
public class RegistryDSAvailabilityManager {

	private static final Log log = LogFactory.getLog(RegistryDSAvailabilityManager.class);
	
	public static final String REG_DS_AVAIL_MANAGE_BASE_PATH = "/repository/components/org.wso2.carbon.ndatasource/availability";
	
	private Registry registry;

	public RegistryDSAvailabilityManager() throws DataSourceException {
		this.registry = DataSourceUtils.getGovRegistryForTenant(
				MultitenantConstants.SUPER_TENANT_ID);
	}
	
	public Registry getRegistry() {
		return registry;
	}
	
	private String generateDSAvailablePath(int tenantId) {
		return REG_DS_AVAIL_MANAGE_BASE_PATH + "/" + tenantId;
	}
	
	public boolean checkDSAvailable(int tenantId) throws DataSourceException {
		try {
			boolean result = this.registry.resourceExists(
					this.generateDSAvailablePath(tenantId));
			if (log.isDebugEnabled()) {
				log.debug("Checking data sources available for tenant: " + tenantId +
						", result: " + result);
			}
			return result;
		} catch (RegistryException e) {
			throw new DataSourceException("Error in checking data sources availability: " +
		            e.getMessage(), e);
		}
	}
	
	public void setDSAvailable(int tenantId, boolean exists) throws DataSourceException {
		try {
			if (exists) {
			    this.getRegistry().put(this.generateDSAvailablePath(tenantId), 
					    this.getRegistry().newResource());
			} else {
				this.getRegistry().delete(this.generateDSAvailablePath(tenantId));
			}
			if (log.isDebugEnabled()) {
				log.debug("Setting data sources available for tenant: " + tenantId +
						", exists: " + exists);
			}
		} catch (Exception e) {
			throw new DataSourceException(
					"Error in setting data sources availability: " + e.getMessage(), e);
		}
	}
	
}
