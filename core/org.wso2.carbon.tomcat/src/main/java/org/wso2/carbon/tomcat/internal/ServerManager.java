/*
 * Copyright (c) 2005-2012, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.tomcat.internal;


import org.apache.catalina.LifecycleException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * Configuring,initialization and stopping the carbon tomcat instance
 */
public class ServerManager {

    private static Log log = LogFactory.getLog(ServerManager.class);
    private static CarbonTomcat tomcat;
    private InputStream inputStream;
    static ClassLoader bundleCtxtClassLoader;


    /**
     * initialization code goes here.i.e : configuring tomcat instance using catalina-server.xml
     */
    public void init() {
        bundleCtxtClassLoader = Thread.currentThread().getContextClassLoader();
        String carbonHome = System.getProperty("carbon.home");
        String catalinaHome = new File(carbonHome).getAbsolutePath() + File.separator + "lib" +
                              File.separator + "tomcat";
        String catalinaXML = new File(carbonHome).getAbsolutePath() + File.separator +
                             "repository" + File.separator + "conf" + File.separator +
                             "tomcat" + File.separator + "catalina-server.xml";
        try {
            inputStream = new FileInputStream(new File(catalinaXML));
        } catch (FileNotFoundException e) {
            log.error("could not locate the file catalina-server.xml", e);
        }
         //setting catalina.base system property. tomcat configurator refers this property while tomcat instance creation.
        //you can override the property in wso2server.sh
        if (System.getProperty("catalina.base") == null) {
            System.setProperty("catalina.base", System.getProperty("carbon.home") + File.separator +
                                                "lib" + File.separator + "tomcat");
        }
        tomcat = new CarbonTomcat();
        tomcat.configure(catalinaHome, inputStream);
    }

    /**
     * starting the a tomcat instance in a new thread. Otherwise activator gets blocked.
     */
    public synchronized void start() {
        new Thread(new Runnable() {
            public void run() {
                Thread.currentThread().setContextClassLoader(bundleCtxtClassLoader);
                try {
                    tomcat.start();
                } catch (LifecycleException e) {
                    log.error("tomcat life-cycle exception", e);
                }

            }
        }).start();
    }

    /**
     * stopping the tomcat instance
     */
    public void stop() {
        try {
            tomcat.stop();
        } catch (LifecycleException e) {
            log.error("Error while stopping tomcat", e);
        }

    }

    /**
     * we are not expecting others to access this service. The only use case would be activator.
     * hence package private access modifier
     *
     * @return
     */
    CarbonTomcat getTomcatInstance() {
        return tomcat;
    }


}

