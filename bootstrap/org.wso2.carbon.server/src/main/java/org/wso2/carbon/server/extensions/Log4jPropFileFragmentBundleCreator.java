/*
*  Copyright (c) 2005-2012, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.carbon.server.extensions;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.server.CarbonLaunchExtension;
import org.wso2.carbon.server.LauncherConstants;
import org.wso2.carbon.server.util.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public class Log4jPropFileFragmentBundleCreator implements CarbonLaunchExtension {
    private static Log log = LogFactory.getLog(Log4jPropFileFragmentBundleCreator.class);

    private static String LOG4J_PROP_FILE_NAME = "log4j.properties";
    private static String FRAGMENT_BUNDLE_NAME = "org.wso2.carbon.logging.propfile";
    private static String FRAGMENT_BUNDLE_VERSION = "1.0.0";
    private static String FRAGMENT_HOST_BUNDLE_NAME = "org.wso2.carbon.kernel";



    public void perform() {

        //Get the log4j.properties file path.
        //Calculate the target fragment bundle file name with required manifest headers.
        //org.wso2.carbon.logging.propfile_1.0.0.jar

        try {

            Manifest mf = new Manifest();
            Attributes attribs = mf.getMainAttributes();
            attribs.putValue(LauncherConstants.MANIFEST_VERSION, "1.0");
            attribs.putValue(LauncherConstants.BUNDLE_MANIFEST_VERSION, "2");
            attribs.putValue(LauncherConstants.BUNDLE_NAME, FRAGMENT_BUNDLE_NAME);
            attribs.putValue(LauncherConstants.BUNDLE_SYMBOLIC_NAME, FRAGMENT_BUNDLE_NAME);
            attribs.putValue(LauncherConstants.BUNDLE_VERSION, FRAGMENT_BUNDLE_VERSION);
            attribs.putValue(LauncherConstants.FRAGMENT_HOST, FRAGMENT_HOST_BUNDLE_NAME);
            attribs.putValue(LauncherConstants.BUNDLE_CLASSPATH, ".");

            File confFolder = new File(Utils.getCarbonComponentRepo(), "../conf");
            String loggingPropFilePath = confFolder.getAbsolutePath() + File.separator +
                    LOG4J_PROP_FILE_NAME;

            File dropinsFolder = new File(Utils.getCarbonComponentRepo(), "dropins");
            String targetFilePath = dropinsFolder.getAbsolutePath() + File.separator +
                    FRAGMENT_BUNDLE_NAME + "_" + FRAGMENT_BUNDLE_VERSION + ".jar";


            String tempDirPath = Utils.JAR_TO_BUNDLE_DIR + File.separator +
                    System.currentTimeMillis() + Math.random();

            FileOutputStream mfos = null;
            try {

                Utils.copyFileToDir(new File(loggingPropFilePath), new File(tempDirPath));
                String metaInfPath = tempDirPath + File.separator + "META-INF";
                if (!new File(metaInfPath).mkdirs()) {
                    throw new IOException("Failed to create the directory: " + metaInfPath);
                }
                mfos = new FileOutputStream(metaInfPath + File.separator + "MANIFEST.MF");
                mf.write(mfos);

                Utils.archiveDir(targetFilePath, tempDirPath);
                Utils.deleteDir(new File(tempDirPath));
            } finally {
                try {
                    if (mfos != null) {
                        mfos.close();
                    }
                } catch (IOException e) {
                    log.error("Unable to close the OutputStream " + e.getMessage(), e);
                }
            }

        }catch(IOException e){
            log.error("Error occured while creating the log4j prop fragment bundle.", e);
        }

        //Utils.createBundle(log4jPropFile, targetFragmentBundle, mf);
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
