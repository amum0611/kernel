/*
 *  Copyright (c) 2005-2009, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.wso2.carbon.registry.core.jdbc.dao;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.registry.core.LogEntry;
import org.wso2.carbon.registry.core.RegistryConstants;
import org.wso2.carbon.registry.core.config.RegistryContext;
import org.wso2.carbon.registry.core.dao.LogsDAO;
import org.wso2.carbon.registry.core.dataaccess.DataAccessManager;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.jdbc.DatabaseConstants;
import org.wso2.carbon.registry.core.jdbc.dataaccess.JDBCDataAccessManager;
import org.wso2.carbon.registry.core.jdbc.dataaccess.JDBCDatabaseTransaction;
import org.wso2.carbon.registry.core.session.CurrentSession;
import org.wso2.carbon.registry.core.utils.LogRecord;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * An implementation of the {@link LogsDAO} to store logs on a JDBC-based database.
 */
public class JDBCLogsDAO implements LogsDAO {

    private static final Log log = LogFactory.getLog(JDBCLogsDAO.class);

    public void saveLogBatch(LogRecord[] logRecords) throws RegistryException {
        PreparedStatement s = null;
        Connection conn = null;
        try {
            conn = getDBConnection();

            String sql = "INSERT INTO REG_LOG (REG_PATH, REG_USER_ID, REG_LOGGED_TIME, "
                    + "REG_ACTION, REG_ACTION_DATA, REG_TENANT_ID) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";

            s = conn.prepareStatement(sql);
            for (LogRecord logRecord : logRecords) {
                s.clearParameters();
                s.setString(1, logRecord.getResourcePath());
                s.setString(2, logRecord.getUserName());
                s.setTimestamp(3, new Timestamp(logRecord.getTimestamp().getTime()));
                s.setInt(4, logRecord.getAction());
                s.setString(5, logRecord.getActionData());
                s.setInt(6, logRecord.getTenantId());
                s.addBatch();
            }
            int[] status = s.executeBatch();
            if (log.isDebugEnabled()) {
                log.debug("Successfully added " + status.length + " log records.");
            }
            conn.commit();

        } catch (SQLException e) {
            try {
                if (conn != null) {
                    conn.rollback();
                }
            } catch (SQLException e1) {
                log.error("Failed to rollback log insertion.", e);
            }
            String msg = "Failed to update log batch records " + ". "
                    + e.getMessage();
            log.error(msg, e);
            throw new RegistryException(msg, e);
        } finally {
            try {
                if (s != null) {
                    s.close();
                }
                if (conn != null && !(conn.isClosed())) {
                    conn.close();
                }
            } catch (SQLException ex) {
                String msg = RegistryConstants.RESULT_SET_PREPARED_STATEMENT_CLOSE_ERROR;
                log.error(msg, ex);
            }
        }
    }

    // Obtains direct connection to database by passing the connection set on thread local object.
    private Connection getDBConnection() throws SQLException, RegistryException {
        DataAccessManager dataAccessManager;
        if (CurrentSession.getUserRegistry() != null
                && CurrentSession.getUserRegistry().getRegistryContext() != null) {
            dataAccessManager = CurrentSession.getUserRegistry().getRegistryContext()
                    .getDataAccessManager();
        } else {
            dataAccessManager = RegistryContext.getBaseInstance().getDataAccessManager();
        }
        if (!(dataAccessManager instanceof JDBCDataAccessManager)) {
            String msg = "Failed to get logs. Invalid data access manager.";
            log.error(msg);
            throw new RegistryException(msg);
        }
        DataSource dataSource = ((JDBCDataAccessManager)dataAccessManager).getDataSource();
        Connection conn = dataSource.getConnection();
        if (conn.getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED) {
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        }
        conn.setAutoCommit(false);
        return conn;
    }

    public List getLogs(String resourcePath, int action,
                        String userName, Date from, Date to, boolean descending)
            throws RegistryException {

        JDBCDatabaseTransaction.ManagedRegistryConnection conn =
                JDBCDatabaseTransaction.getConnection();

        if(conn == null) {
            log.fatal("Failed to get Logs. Communications link failure. The connection to the database could not be acquired.");
            throw new RegistryException("Failed to get Logs. Communications link failure. The connection to the database could not be acquired.");
        }

        PreparedStatement s = null;
        ResultSet results = null;

        String sql =
                "SELECT REG_PATH, REG_USER_ID, REG_LOGGED_TIME, REG_ACTION, REG_ACTION_DATA FROM " +
                        "REG_LOG";

        boolean queryStarted = false;

        sql = addWherePart(resourcePath, queryStarted, sql, userName, from, to, action);

        if (descending) {
            sql = sql + " ORDER BY REG_LOGGED_TIME DESC";
        }

        try {
            s = conn.prepareStatement(sql);

            int paramNumber = 1;

            if (resourcePath != null) {
                s.setString(paramNumber, resourcePath);
                paramNumber++;
            }

            if (userName != null) {
                s.setString(paramNumber, userName);
                paramNumber++;
            }

            if (from != null) {
                s.setTimestamp(paramNumber, new Timestamp(from.getTime()));
                paramNumber++;
            }

            if (to != null) {
                s.setTimestamp(paramNumber, new Timestamp(to.getTime()));
                paramNumber++;
            }

            if (action != -1) {
                s.setInt(paramNumber, action);
                paramNumber++;
            }
            s.setInt(paramNumber, CurrentSession.getTenantId());

            results = s.executeQuery();

            List<LogEntry> resultList = new ArrayList<LogEntry>();
            while (results.next()) {
                LogEntry logEntry = new LogEntry();
                logEntry.setResourcePath(results.getString(DatabaseConstants.PATH_FIELD));
                logEntry.setUserName(results.getString(DatabaseConstants.USER_ID_FIELD));
                logEntry.setDate(
                        new Date(results.getTimestamp(
                                DatabaseConstants.LOGGED_TIME_FIELD).getTime()));
                logEntry.setAction(results.getInt(DatabaseConstants.ACTION_FIELD));
                logEntry.setActionData(results.getString(DatabaseConstants.ACTION_DATA_FIELD));

                resultList.add(logEntry);
            }
            return resultList;

        } catch (SQLException e) {

            String msg = "Failed to get logs. " + e.getMessage();
            log.error(msg, e);
            throw new RegistryException(msg, e);
        } finally {
            try {
                try {
                    if (results != null) {
                        results.close();
                    }
                } finally {
                    if (s != null) {
                        s.close();
                    }
                }
            } catch (SQLException ex) {
                String msg = RegistryConstants.RESULT_SET_PREPARED_STATEMENT_CLOSE_ERROR;
                log.error(msg, ex);
            }
        }
    }

    // Utility method to add 'WHERE' part to an SQL query.
    private String addWherePart(String resourcePath,
                                boolean queryStarted,
                                String sql,
                                String userName,
                                Date from,
                                Date to,
                                int action) {
        if (resourcePath != null) {
            if (queryStarted) {
                sql = sql + " AND REG_PATH=?";
            } else {
                sql = sql + " WHERE REG_PATH=?";
                queryStarted = true;
            }
        }

        if (userName != null) {
            if (queryStarted) {
                sql = sql + "  AND REG_USER_ID=?";
            } else {
                sql = sql + "  WHERE REG_USER_ID=?";
                queryStarted = true;
            }
        }

        if (from != null) {
            if (queryStarted) {
                sql = sql + " AND REG_LOGGED_TIME>?";
            } else {
                sql = sql + " WHERE REG_LOGGED_TIME>?";
                queryStarted = true;
            }
        }

        if (to != null) {
            if (queryStarted) {
                sql = sql + " AND REG_LOGGED_TIME<?";
            } else {
                sql = sql + " WHERE REG_LOGGED_TIME<?";
                queryStarted = true;
            }
        }

        if (action != -1) {
            if (queryStarted) {
                sql = sql + " AND REG_ACTION=?";
            } else {
                sql = sql + " WHERE REG_ACTION=?";
                queryStarted = true;
            }
        }

        if (queryStarted) {
            sql = sql + " AND REG_TENANT_ID=?";
        } else {
            sql = sql + " WHERE REG_TENANT_ID=?";
        }
        return sql;
    }

    public LogEntry[] getLogs(String resourcePath,
                              int action,
                              String userName,
                              Date from,
                              Date to,
                              boolean descending,
                              int start,
                              int pageLen,
                              DataAccessManager dataAccessManager)
            throws RegistryException {
        String sql =
                "SELECT REG_PATH, REG_USER_ID, REG_LOGGED_TIME, REG_ACTION, REG_ACTION_DATA FROM " +
                        "REG_LOG";
        boolean queryStarted = false;

        sql = addWherePart(resourcePath, queryStarted, sql, userName, from, to, action);

        if (descending) {
            sql = sql + " ORDER BY REG_LOGGED_TIME DESC";
        }
        Connection conn = null;
        PreparedStatement s = null;
        ResultSet results = null;
        if (!(dataAccessManager instanceof JDBCDataAccessManager)) {
            String msg = "Failed to get logs. Invalid data access manager.";
            log.error(msg);
            throw new RegistryException(msg);
        }
        DataSource dataSource = ((JDBCDataAccessManager)dataAccessManager).getDataSource();
        try {
            conn = dataSource.getConnection();
            s = conn.prepareStatement(sql);

            int paramNumber = 1;

            if (resourcePath != null) {
                s.setString(paramNumber, resourcePath);
                paramNumber++;
            }

            if (userName != null) {
                s.setString(paramNumber, userName);
                paramNumber++;
            }

            if (from != null) {
                s.setTimestamp(paramNumber, new Timestamp(from.getTime()));
                paramNumber++;
            }

            if (to != null) {
                s.setTimestamp(paramNumber, new Timestamp(to.getTime()));
                paramNumber++;
            }

            if (action != -1) {
                s.setInt(paramNumber, action);
                paramNumber++;
            }
            s.setInt(paramNumber, CurrentSession.getTenantId());

            results = s.executeQuery();

            List<LogEntry> resultList = new ArrayList<LogEntry>();
            int current = 0;
            while (results.next()) {
                if (current >= start && (pageLen == -1 || current < start + pageLen)) {
                    LogEntry logEntry = new LogEntry();
                    logEntry.setResourcePath(results.getString(DatabaseConstants.PATH_FIELD));
                    logEntry.setUserName(results.getString(DatabaseConstants.USER_ID_FIELD));
                    logEntry.setDate(
                            new Date(results.getTimestamp(
                                    DatabaseConstants.LOGGED_TIME_FIELD).getTime()));
                    logEntry.setAction(results.getInt(DatabaseConstants.ACTION_FIELD));
                    logEntry.setActionData(results.getString(DatabaseConstants.ACTION_DATA_FIELD));

                    resultList.add(logEntry);
                }
                current++;
            }
            return resultList.toArray(new LogEntry[resultList.size()]);

        } catch (SQLException e) {

            String msg = "Failed to get logs. " + e.getMessage();
            log.error(msg, e);
            throw new RegistryException(msg, e);
        } finally {
            try {
                try {
                    if (results != null) {
                        results.close();
                    }
                } finally {
                    try {
                        if (s != null) {
                            s.close();
                        }
                    } finally {
                        if (conn != null) {
                            conn.close();
                        }
                    }
                }
            } catch (SQLException ex) {
                String msg = RegistryConstants.RESULT_SET_PREPARED_STATEMENT_CLOSE_ERROR;
                log.error(msg, ex);
            }
        }
    }

    public LogEntry[] getLogs(String resourcePath,
                              int action,
                              String userName,
                              Date from,
                              Date to,
                              boolean descending,
                              DataAccessManager dataAccessManager)
            throws RegistryException {

        String sql =
                "SELECT REG_PATH, REG_USER_ID, REG_LOGGED_TIME, REG_ACTION, REG_ACTION_DATA FROM " +
                        "REG_LOG";

        boolean queryStarted = false;

        sql = addWherePart(resourcePath, queryStarted, sql, userName, from, to, action);

        if (descending) {
            sql = sql + " ORDER BY REG_LOGGED_TIME DESC";
        }

        PreparedStatement s = null;
        ResultSet results = null;
        Connection conn = null;
        if (!(dataAccessManager instanceof JDBCDataAccessManager)) {
            String msg = "Failed to get logs. Invalid data access manager.";
            log.error(msg);
            throw new RegistryException(msg);
        }
        DataSource dataSource = ((JDBCDataAccessManager)dataAccessManager).getDataSource();
        try {
            conn = dataSource.getConnection();
            s = conn.prepareStatement(sql);

            int paramNumber = 1;

            if (resourcePath != null) {
                s.setString(paramNumber, resourcePath);
                paramNumber++;
            }

            if (userName != null) {
                s.setString(paramNumber, userName);
                paramNumber++;
            }

            if (from != null) {
                s.setTimestamp(paramNumber, new Timestamp(from.getTime()));
                paramNumber++;
            }

            if (to != null) {
                s.setTimestamp(paramNumber, new Timestamp(to.getTime()));
                paramNumber++;
            }

            if (action != -1) {
                s.setInt(paramNumber, action);
                paramNumber++;
            }

            s.setInt(paramNumber, CurrentSession.getTenantId());

            results = s.executeQuery();

            List<LogEntry> resultList = new ArrayList<LogEntry>();
            while (results.next()) {
                LogEntry logEntry = new LogEntry();
                logEntry.setResourcePath(results.getString(DatabaseConstants.PATH_FIELD));
                logEntry.setUserName(results.getString(DatabaseConstants.USER_ID_FIELD));
                logEntry.setDate(
                        new Date(results.getTimestamp(
                                DatabaseConstants.LOGGED_TIME_FIELD).getTime()));
                logEntry.setAction(results.getInt(DatabaseConstants.ACTION_FIELD));
                logEntry.setActionData(results.getString(DatabaseConstants.ACTION_DATA_FIELD));

                resultList.add(logEntry);
            }
            return resultList.toArray(new LogEntry[resultList.size()]);

        } catch (SQLException e) {

            String msg = "Failed to get logs. " + e.getMessage();
            log.error(msg, e);
            throw new RegistryException(msg, e);
        } finally {
            try {
                try {
                    if (results != null) {
                        results.close();
                    }
                } finally {
                    try {
                        if (s != null) {
                            s.close();
                        }
                    } finally {
                        if (conn != null) {
                            conn.close();
                        }
                    }
                }
            } catch (SQLException ex) {
                String msg = RegistryConstants.RESULT_SET_PREPARED_STATEMENT_CLOSE_ERROR;
                log.error(msg, ex);
            }
        }
    }

    public int getLogsCount(String resourcePath,
                            int action,
                            String userName,
                            Date from,
                            Date to,
                            boolean descending)
            throws RegistryException {
        int count = 0;

        JDBCDatabaseTransaction.ManagedRegistryConnection conn =
                JDBCDatabaseTransaction.getConnection();

        String sql = "SELECT COUNT(*) AS REG_LOG_COUNT FROM REG_LOG";

        boolean queryStarted = false;

        sql = addWherePart(resourcePath, queryStarted, sql, userName, from, to, action);

        PreparedStatement s = null;
        ResultSet results = null;
        try {
            s = conn.prepareStatement(sql);

            int paramNumber = 1;

            if (resourcePath != null) {
                s.setString(paramNumber, resourcePath);
                paramNumber++;
            }

            if (userName != null) {
                s.setString(paramNumber, userName);
                paramNumber++;
            }

            if (from != null) {
                s.setTimestamp(paramNumber, new Timestamp(from.getTime()));
                paramNumber++;
            }

            if (to != null) {
                s.setTimestamp(paramNumber, new Timestamp(to.getTime()));
                paramNumber++;
            }

            if (action != -1) {
                s.setInt(paramNumber, action);
                paramNumber++;
            }

            s.setInt(paramNumber, CurrentSession.getTenantId());

            results = s.executeQuery();

            if (results.next()) {
                count = results.getInt(DatabaseConstants.LOG_COUNT_FIELD);
            }


        } catch (SQLException e) {

            String msg = "Failed to get logs. " + e.getMessage();
            log.error(msg, e);
            throw new RegistryException(msg, e);
        } finally {
            try {
                try {
                    if (results != null) {
                        results.close();
                    }
                } finally {
                    if (s != null) {
                        s.close();
                    }
                }
            } catch (SQLException ex) {
                String msg = RegistryConstants.RESULT_SET_PREPARED_STATEMENT_CLOSE_ERROR;
                log.error(msg, ex);
            }
        }
        return count;
    }
}
