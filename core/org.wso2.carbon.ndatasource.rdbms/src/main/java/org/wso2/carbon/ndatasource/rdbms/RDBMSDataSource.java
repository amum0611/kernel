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
package org.wso2.carbon.ndatasource.rdbms;

import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.wso2.carbon.ndatasource.common.DataSourceException;
import org.wso2.carbon.ndatasource.rdbms.utils.RDBMSDataSourceUtils;

/**
 * RDBMS data source implementation.
 */
public class RDBMSDataSource {

	private DataSource dataSource;
	
	public RDBMSDataSource(RDBMSConfiguration config) throws DataSourceException {
		PoolConfiguration poolProperties = RDBMSDataSourceUtils.createPoolConfiguration(config);
		this.dataSource = new DataSource(poolProperties);
	}
	
	public javax.sql.DataSource getDataSource() {
		return dataSource;
	}
	
}
