/*
 * Copyright (c) 2008, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.carbon.registry.core.jdbc.indexing;

import org.apache.lucene.store.jdbc.dialect.DerbyDialect;
import org.apache.lucene.store.jdbc.dialect.Dialect;
import org.apache.lucene.store.jdbc.dialect.H2Dialect;
import org.apache.lucene.store.jdbc.dialect.HSQLDialect;
import org.apache.lucene.store.jdbc.dialect.OracleDialect;
import org.apache.lucene.store.jdbc.dialect.SQLServerDialect;
import org.wso2.carbon.registry.core.exceptions.RegistryException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

public class DialectFactory {

    public static Dialect getDialect(DataSource dataSource) throws RegistryException {
        Connection conn = null;

        try {
            conn = dataSource.getConnection();
            DatabaseMetaData metaData = conn.getMetaData();

            if (metaData.getDatabaseProductName().matches("(?i).*hsql.*")) {
                return new HSQLDialect();

            } else if (metaData.getDatabaseProductName().matches("(?i).*derby.*")) {
                return new DerbyDialect();

            } else if (metaData.getDatabaseProductName().matches("(?i).*mysql.*")) {
                return new MySQLDialectDriver();

            } else if (metaData.getDatabaseProductName().matches("(?i).*oracle.*")) {
                return new OracleDialect();

            } else if (metaData.getDatabaseProductName().matches("(?i).*microsoft.*")) {
                return new SQLServerDialect();

            } else if (metaData.getDatabaseProductName().matches("(?i).*h2.*")) {
                return new H2Dialect();

            } else {
                String msg = "Unsupported database: " + metaData.getDatabaseProductName() +
                        ". Database will not be created automatically by the WSO2 Registry. " +
                        "Please create the database using appropriate database scripts for " +
                        "the database.";

                return null;
            }

        } catch (SQLException e) {

            String msg = "Failed to detect dialect " + e.getMessage();
            throw new RegistryException(msg, e);

        } finally {
            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (SQLException e) {

            }
        }
    }
}
