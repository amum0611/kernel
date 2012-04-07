/*
*  Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.carbon.user.core.util;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.user.api.RealmConfiguration;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.jdbc.JDBCRealmConstants;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DatabaseUtil {

    private static Log log = LogFactory.getLog(DatabaseUtil.class);
    private static long connectionsCreated ;
    private static long connectionsClosed ;
    private static ExecutorService executor = null;

    private static BasicDataSource dataSource = null;
    private static final int DEFAULT_MAX_ACTIVE = 40;
    private static final int DEFAULT_MAX_WAIT = 1000 * 60;
    private static final int DEFAULT_MIN_IDLE = 5;
    private static final int DEFAULT_MAX_IDLE = 6;
	private static final long DEFAULT_MIN_EVIC_TABLE_IDLE_TIME_MILLIS = 1000 * 60 * 30;
	private static final int DEFAULT_NUM_TESTS_PEREVICTION_RUN = 3;
	private static final int DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS = -1;
	private static final boolean DEFAULT_TEST_WHILE_IDLE = false;
    
    /**
     * Gets a database pooling connection. If a pool is not created this will create a connection pool.
     * @param realmConfig The realm configuration. This includes necessary configuration parameters needed to
     *                      create a database pool.
     *
     * NOTE : If we use this there will be a single connection for all tenants. But there might be a requirement
     * where different tenants want to connect to multiple data sources. In that case we need to create
     * a dataSource for each tenant.
     * @return A database pool.
     */
    public static synchronized DataSource getRealmDataSource(RealmConfiguration realmConfig){

        if (dataSource == null) {
            return createRealmDataSource(realmConfig);
        } else {
            return dataSource;
        }
    }

    /**
     * Close all database connections in the pool.
     */
    public static synchronized void closeDatabasePoolConnection() {

        if (dataSource != null) {
            try {                
                dataSource.close();
                dataSource = null;
            } catch (SQLException e) {
                log.error("Unable to close data source connection (Connection pool)", e);
            }
        }

    }

    private static DataSource createRealmDataSource(RealmConfiguration realmConfig) {

        dataSource = new BasicDataSource();

        dataSource.setDriverClassName(realmConfig.getRealmProperty(JDBCRealmConstants.DRIVER_NAME));
        dataSource.setUrl(realmConfig.getRealmProperty(JDBCRealmConstants.URL));
        dataSource.setUsername(realmConfig.getRealmProperty(JDBCRealmConstants.USER_NAME));
        dataSource.setPassword(realmConfig.getRealmProperty(JDBCRealmConstants.PASSWORD));

        if(realmConfig.getRealmProperty(JDBCRealmConstants.MAX_ACTIVE) != null && !realmConfig
                .getRealmProperty(JDBCRealmConstants.MAX_ACTIVE).equals("")){
            dataSource.setMaxActive(Integer.parseInt(realmConfig.
                    getRealmProperty(JDBCRealmConstants.MAX_ACTIVE)));
        } else {
            dataSource.setMaxActive(DEFAULT_MAX_ACTIVE);
        }

        if(realmConfig.getRealmProperty(JDBCRealmConstants.MIN_IDLE) != null && !realmConfig
                .getRealmProperty(JDBCRealmConstants.MIN_IDLE).equals("")){
            dataSource.setMinIdle(Integer.parseInt(realmConfig.
                    getRealmProperty(JDBCRealmConstants.MIN_IDLE)));
        } else {
            dataSource.setMinIdle(DEFAULT_MIN_IDLE);
        }
        
        if(realmConfig.getRealmProperty(JDBCRealmConstants.MAX_IDLE) != null && !realmConfig
                .getRealmProperty(JDBCRealmConstants.MAX_IDLE).equals("")){
            dataSource.setMinIdle(Integer.parseInt(realmConfig.
                    getRealmProperty(JDBCRealmConstants.MAX_IDLE)));
        } else {
            dataSource.setMinIdle(DEFAULT_MAX_IDLE);
        }

        if(realmConfig.getRealmProperty(JDBCRealmConstants.MAX_WAIT) != null && !realmConfig
                .getRealmProperty(JDBCRealmConstants.MAX_WAIT).equals("")){
            dataSource.setMaxWait(Integer.parseInt(realmConfig.
                    getRealmProperty(JDBCRealmConstants.MAX_WAIT)));
        } else {
            dataSource.setMaxWait(DEFAULT_MAX_WAIT);
        }   
		if (realmConfig
				.getRealmProperty(JDBCRealmConstants.VALIDATION_QUERYTIME_OUT) != null
				&& !realmConfig.getRealmProperty(
						JDBCRealmConstants.VALIDATION_QUERYTIME_OUT).equals("")) {
			dataSource
					.setValidationQueryTimeout(Integer.parseInt(realmConfig
							.getRealmProperty(JDBCRealmConstants.VALIDATION_QUERYTIME_OUT)));
		}		
		
				if (realmConfig.getRealmProperty(JDBCRealmConstants.TEST_WHILE_IDLE) != null
				&& !realmConfig.getRealmProperty(
						JDBCRealmConstants.TEST_WHILE_IDLE).equals("")) {
			dataSource.setTestWhileIdle(Boolean.parseBoolean(realmConfig
					.getRealmProperty(JDBCRealmConstants.TEST_WHILE_IDLE)));
		}
		
				if (realmConfig
				.getRealmProperty(JDBCRealmConstants.TIME_BETWEEN_EVICTION_RUNS_MILLIS) != null
				&& !realmConfig.getRealmProperty(
						JDBCRealmConstants.TIME_BETWEEN_EVICTION_RUNS_MILLIS)
						.equals("")) {
			dataSource
					.setTimeBetweenEvictionRunsMillis(Integer.parseInt(realmConfig
							.getRealmProperty(JDBCRealmConstants.TIME_BETWEEN_EVICTION_RUNS_MILLIS)));
		} 
		
		if (realmConfig
				.getRealmProperty(JDBCRealmConstants.MIN_EVIC_TABLE_IDLE_TIME_MILLIS) != null
				&& !realmConfig.getRealmProperty(
						JDBCRealmConstants.MIN_EVIC_TABLE_IDLE_TIME_MILLIS)
						.equals("")) {
			dataSource
					.setMinEvictableIdleTimeMillis(Integer.parseInt(realmConfig
							.getRealmProperty(JDBCRealmConstants.MIN_EVIC_TABLE_IDLE_TIME_MILLIS)));
		} 

        if (realmConfig.getRealmProperty(JDBCRealmConstants.VALIDATION_QUERY) != null) {
            dataSource.setValidationQuery(realmConfig.getRealmProperty(JDBCRealmConstants.VALIDATION_QUERY));
        }
        return dataSource;
    }

    public static String[] getStringValuesFromDatabase(Connection dbConnection, String sqlStmt, Object... params)
            throws UserStoreException {
        String[] values = new String[0];
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        try {
            prepStmt = dbConnection.prepareStatement(sqlStmt);
            if (params != null && params.length > 0) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    if (param == null) {
                        throw new UserStoreException("Null data provided.");
                    } else if (param instanceof String) {
                        prepStmt.setString(i + 1, (String)param);
                    } else if (param instanceof Integer) {
                        prepStmt.setInt(i + 1, (Integer)param);
                    }
                }
            }
            rs = prepStmt.executeQuery();
            List<String> lst = new ArrayList<String>();
            while (rs.next()) {
                String name = rs.getString(1);
                lst.add(name);
            }
            if (lst.size() > 0) {
                values = lst.toArray(new String[lst.size()]);
            }
            return values;
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            log.error("Using sql : " + sqlStmt);
            throw new UserStoreException(e.getMessage(), e);
        } finally {
            DatabaseUtil.closeAllConnections(null, rs, prepStmt);
        }
    }
    
    
    public static int getIntegerValueFromDatabase(Connection dbConnection, String sqlStmt,
            Object... params) throws UserStoreException {
        PreparedStatement prepStmt = null;
        ResultSet rs = null;
        int value = -1;
        try {
            prepStmt = dbConnection.prepareStatement(sqlStmt);
            if (params != null && params.length > 0) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    if (param == null) {
                        throw new UserStoreException("Null data provided.");
                    } else if (param instanceof String) {
                        prepStmt.setString(i + 1, (String) param);
                    } else if (param instanceof Integer) {
                        prepStmt.setInt(i + 1, (Integer) param);
                    }
                }
            }
            rs = prepStmt.executeQuery();
            if (rs.next()) {
                value = rs.getInt(1);
            }
            return value;
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            log.error("Using sql : " + sqlStmt);
            throw new UserStoreException(e.getMessage(), e);
        } finally {
            DatabaseUtil.closeAllConnections(null, rs, prepStmt);
        }
    }

    public static void udpateUserRoleMappingInBatchMode(Connection dbConnection, String sqlStmt,
            Object... params) throws UserStoreException {
        PreparedStatement prepStmt = null;
        boolean localConnection = false;
        try {
            prepStmt = dbConnection.prepareStatement(sqlStmt);
            int batchParamIndex = -1;
            if (params != null && params.length > 0) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    if (param == null) {
                        throw new UserStoreException("Null data provided.");
                    } else if (param instanceof String[]) {
                        batchParamIndex = i;
                    } else if (param instanceof String) {
                        prepStmt.setString(i + 1, (String)param);
                    } else if (param instanceof Integer) {
                        prepStmt.setInt(i + 1, (Integer)param);
                    }
                }
            }
            if (batchParamIndex != -1) {
                String[] values = (String[])params[batchParamIndex];
                for (String value : values) {
                    prepStmt.setString(batchParamIndex + 1, value);
                    prepStmt.addBatch();
                }
            }

            int[] count = prepStmt.executeBatch();
            if (log.isDebugEnabled()) {
                log.debug("Executed a batch update. Querry is : " + sqlStmt + ": and result is"
                        + Arrays.toString(count));
            }
            if (localConnection) {
                dbConnection.commit();
            }
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            log.error("Using sql : " + sqlStmt);
            throw new UserStoreException(e.getMessage(), e);
        } finally {
            if (localConnection) {
                DatabaseUtil.closeAllConnections(dbConnection);
            }
            DatabaseUtil.closeAllConnections(null, prepStmt);
        }
    }
    
    public static void updateDatabase(Connection dbConnection, String sqlStmt, Object... params)
            throws UserStoreException {
        PreparedStatement prepStmt = null;
        try {            
            prepStmt = dbConnection.prepareStatement(sqlStmt);
            if (params != null && params.length > 0) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    if (param == null) {
                        throw new UserStoreException("Null data provided.");
                    } else if (param instanceof String) {
                        prepStmt.setString(i + 1, (String)param);
                    } else if (param instanceof Integer) {
                        prepStmt.setInt(i + 1, (Integer)param);
                    } else if (param instanceof Short) {
                        prepStmt.setShort(i + 1, (Short)param);
                    } else if (param instanceof Date) {
                        Date date = (Date)param;
                        Timestamp time = new Timestamp(date.getTime());
                        prepStmt.setTimestamp(i+1, time);
                    }
                }
            }
            int count = prepStmt.executeUpdate();
            if (log.isDebugEnabled()) {
                log.debug("Executed querry is " + sqlStmt + " and number of updated rows :: "
                        + count);
            }
        } catch (SQLException e) {
            log.error("Error! " + e.getMessage(), e);
            log.error("Using sql : " + sqlStmt);
            throw new UserStoreException("Error! " + e.getMessage(), e);
        } finally {
            DatabaseUtil.closeAllConnections(null, prepStmt);
        }
    }

    public static Connection getDBConnection(DataSource dataSource) throws SQLException {
		Connection dbConnection = dataSource.getConnection();
		incrementConnectionsCreated();
		return dbConnection;
	}

    public static void closeConnection(Connection dbConnection) {     

        if (dbConnection != null) {
            try {
                dbConnection.close();
                incrementConnectionsClosed();
            } catch (SQLException e) {
                log.error("Database error. Could not close statement. Continuing with others. - " + e.getMessage(), e);
            }
        }
    }

    private static void closeResultSet(ResultSet rs) {

        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                log.error("Database error. Could not close result set  - " + e.getMessage(), e);
            }
        }

    }

    private static void closeStatement(PreparedStatement preparedStatement) {

        if (preparedStatement != null) {
            try {
                preparedStatement.close();
            } catch (SQLException e) {
                log.error("Database error. Could not close statement. Continuing with others. - " + e.getMessage(), e);
            }
        }

    }

    private static void closeStatements(PreparedStatement... prepStmts) {

        if (prepStmts != null && prepStmts.length > 0) {
            for (PreparedStatement stmt : prepStmts) {
                closeStatement(stmt);
            }
        }

    }

    public static void closeAllConnections(Connection dbConnection, PreparedStatement... prepStmts) {

        closeStatements(prepStmts);
        closeConnection(dbConnection);
    }

    public static void closeAllConnections(Connection dbConnection, ResultSet rs, PreparedStatement... prepStmts){

        closeResultSet(rs);
        closeStatements(prepStmts);
        closeConnection(dbConnection);
    }

    public static void closeAllConnections(Connection dbConnection, ResultSet rs1, ResultSet rs2,
                                           PreparedStatement... prepStmts) {
        closeResultSet(rs1);
        closeResultSet(rs1);
        closeStatements(prepStmts);
        closeConnection(dbConnection);
    }

    public static void rollBack(Connection dbConnection) {
        try {
            if (dbConnection != null) {
                dbConnection.rollback();
            }
        } catch (SQLException e1) {
            log.error("An error occurred while rolling back transactions. ", e1);
        }
    }

    public static long getConnectionsCreated() {
        return connectionsCreated;
    }

    public static long getConnectionsClosed() {
        return connectionsClosed;
    }

    public static synchronized void incrementConnectionsCreated() {
        if (connectionsCreated != Long.MAX_VALUE) {
            connectionsCreated++;
        }
    }

    public static synchronized void incrementConnectionsClosed() {
        if (connectionsClosed != Long.MAX_VALUE) {
            connectionsClosed++;
        }
    }

    public static void logDatabaseConnections() {
         executor = Executors.newCachedThreadPool();
         Runtime.getRuntime().addShutdownHook(new Thread(){
             public void run() {
                 executor.shutdownNow();
             }
         });
         final ScheduledExecutorService scheduler =
                 Executors.newScheduledThreadPool(10);
         Runtime.getRuntime().addShutdownHook(new Thread(){
             public void run() {
                 scheduler.shutdownNow();
             }
         });
         Runnable runnable = new Runnable() {
             public void run() {
                     log.debug("Total Number of Connections Created      : " +
                             getConnectionsCreated());
                     log.debug("Total Number of Connections Closed       : " +
                             getConnectionsClosed());
                 }
         };
         scheduler.scheduleAtFixedRate(runnable, 60, 60, TimeUnit.SECONDS);
     }
}
