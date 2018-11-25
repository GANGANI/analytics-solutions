/*
 *  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.extension.siddhi.execution.geovelocity.internal.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.extension.siddhi.execution.geovelocity.api.GeoVelocityData;
import org.wso2.extension.siddhi.execution.geovelocity.internal.exception.GeoVelocityException;
import org.wso2.extension.siddhi.execution.geovelocity.internal.utils.DatabaseUtils;
import org.wso2.siddhi.core.util.config.ConfigReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This is the implementation class that provides the RDBMS based approach to get geovelocity related data.
 */
public class RDBMSGeoVelocityDataResolver {
    private static final Log log = LogFactory.getLog(RDBMSGeoVelocityDataResolver.class);
    private static final RDBMSGeoVelocityDataResolver instance = new RDBMSGeoVelocityDataResolver();

    private static final String CONFIG_KEY_ISPERSIST_IN_DATABASE = "isPersistInDatabase";
    private static final String CONFIG_KEY_DATASOURCE = "datasource";
    private static final String DEFAULT_DATASOURCE_NAME = "IS_ANALYTICS_DB";

    private AtomicBoolean isInitialized = new AtomicBoolean(false);
    private DatabaseUtils dbUtils;
    private boolean isPersistInDatabase;

    private static final String SQL_SELECT_LASTLOGINTIME_FROM_GEOVELOCITY_INFO = "SELECT logintime FROM " +
            "Geovelocity_info WHERE username = ? AND city = ? AND authenticationsuccess = 1";
    private static final String SQL_SELECT_ID_FROM_TRAVELRESTRICTEDAREAS = "SELECT count(ID) FROM " +
            "travelrestrictedareas WHERE currentlocation = ? AND lastlocation = ?";

    public static RDBMSGeoVelocityDataResolver getInstance() {
        return instance;
    }

    public void init(ConfigReader configReader) throws GeoVelocityException {
        if (isInitialized.get()) {
            return;
        }
        isPersistInDatabase = Boolean.parseBoolean(configReader.readConfig(CONFIG_KEY_ISPERSIST_IN_DATABASE, "true"));

        dbUtils = DatabaseUtils.getInstance();
        dbUtils.initialize(configReader.readConfig(CONFIG_KEY_DATASOURCE, DEFAULT_DATASOURCE_NAME));
        isInitialized.set(true);
    }

    GeoVelocityData getGeoVelocityData(String username, String city) {
        GeoVelocityData geoVelocityData = null;
        Connection connection = null;
        try {
            connection = dbUtils.getConnection();
            geoVelocityData = loadGeoVelocityData(username, city, connection);
        } catch (SQLException e) {
            log.error("Cannot retrieve the login time of the last " +
                    "successful login from the give locationfrom database ", e);
        } finally {
            dbUtils.closeAllConnections(null, connection, null);
        }
        return geoVelocityData;
    }

    GeoVelocityData getLoginData(String toCountry, String fromCountry) {
        GeoVelocityData geoVelocityData = null;
        Connection connection = null;
        try {
            connection = dbUtils.getConnection();
            geoVelocityData = loadLoginData(toCountry, fromCountry, connection);
        } catch (SQLException e) {
            log.error("Cannot retrieve the restricted location combinations from database", e);
        } finally {
            dbUtils.closeAllConnections(null, connection, null);
        }
        return geoVelocityData;
    }

    /**
     * Calls external system or database database to find the geovelocity data.
     * Can be used by an extended class.
     * @param username username
     * @param city city
     * @param connection the Db connection to be used. Do not close this connection within this method.
     * @return geoVelocityData with last login time
     */
    private GeoVelocityData loadGeoVelocityData(String username, String city,
                                                Connection connection) throws SQLException {
        GeoVelocityData geoVelocityData = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            if (isPersistInDatabase) {
                statement = connection.prepareStatement(SQL_SELECT_LASTLOGINTIME_FROM_GEOVELOCITY_INFO);
                statement.setString(1, username);
                statement.setString(2, city);
                resultSet = statement.executeQuery();
            }
            if (resultSet != null && resultSet.next()) {
                geoVelocityData = new GeoVelocityData(resultSet.getLong(1), 0);
            }
        } finally {
            dbUtils.closeAllConnections(statement, null, resultSet);
        }
        return geoVelocityData;
    }

    /**
     * Calls external system or database database to find restricted area based data.
     * Can be used by an extended class.
     * @param currentLocation current location of login
     * @param lastLocation last location of login
     * @param connection the Db connection to be used. Do not close this connection within this method.
     * @return geoVelocityData with last login time
     */
    private GeoVelocityData loadLoginData(String currentLocation, String lastLocation,
                                          Connection connection) throws SQLException {
        GeoVelocityData geoVelocityData = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            if (isPersistInDatabase) {
                statement = connection.prepareStatement(SQL_SELECT_ID_FROM_TRAVELRESTRICTEDAREAS);
                statement.setString(1, currentLocation);
                statement.setString(2, lastLocation);
                resultSet = statement.executeQuery();
            }
            if (resultSet != null && resultSet.next()) {
                geoVelocityData = new GeoVelocityData(0L, resultSet.getInt(1));
            }
        } finally {
            dbUtils.closeAllConnections(statement, null, resultSet);
        }
        return geoVelocityData;
    }
}
