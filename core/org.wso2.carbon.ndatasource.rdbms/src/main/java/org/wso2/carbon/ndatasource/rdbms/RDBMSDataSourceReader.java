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

import java.io.ByteArrayInputStream;

import javax.xml.bind.JAXBContext;

import org.wso2.carbon.ndatasource.common.DataSourceException;
import org.wso2.carbon.ndatasource.common.spi.DataSourceReader;

/**
 * This class represents the RDBMS based data source reader implementation.
 */
public class RDBMSDataSourceReader implements DataSourceReader {

	@Override
	public String getType() {
		return RDBMSDataSourceConstants.RDBMS_DATASOURCE_TYPE;
	}

	public static RDBMSConfiguration loadConfig(String xmlConfiguration) 
			throws DataSourceException {
		try {
		    JAXBContext ctx = JAXBContext.newInstance(RDBMSConfiguration.class);
		    return (RDBMSConfiguration) ctx.createUnmarshaller().unmarshal(
		    		new ByteArrayInputStream(xmlConfiguration.getBytes()));
		} catch (Exception e) {
			throw new DataSourceException("Error in loading RDBMS configuration: " +
		            e.getMessage(), e);
		}
	}

	@Override
	public Object createDataSource(String xmlConfiguration)
			throws DataSourceException {
		return (new RDBMSDataSource(loadConfig(xmlConfiguration)).getDataSource());
	}

}
