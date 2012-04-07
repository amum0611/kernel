/*
*  Copyright (c) 2005-2011, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.carbon.core.deployment;

/**
 * Interface for OSGi services which provide DeploymentSynchronization.
 * Deployment synchronization refers to synchronizing artifact deployment repositories
 * across multiple nodes in a cluster
 */
public interface DeploymentSynchronizer {

    /**
     * Do an update of the deployment repository
     *
     * @param tenantId The ID of the tenant which has to be synchronized
     * @return true if files were actually updated, false otherwise
     */
    boolean update(int tenantId);

    /**
     * Do a commit of the deployment repository
     * 
     * @param tenantId The ID of the tenant which has to be synchronized
     * @return true if file changes were committed, false otherwise
     */
    boolean commit(int tenantId);
}
