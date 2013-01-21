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

/**
 * Copy all the patches found in the patches directory to the plugins directory in a recursive manner.
 */
public class PatchInstaller implements CarbonLaunchExtension{
    private static Log log = LogFactory.getLog(PatchInstaller.class);


    public void perform() {
        File carbonComponentDir = Utils.getCarbonComponentRepo();
        File plugins = new File(carbonComponentDir, "plugins");
        File patchesDir = new File(carbonComponentDir, "patches");
        //copying resources inside patches folder to the work area.
        String applyPatches = System.getProperty(LauncherConstants.APPLY_PATCHES);
        if (applyPatches != null) {
            try {
                Utils.applyPatches(patchesDir, plugins);
            } catch (Exception e) {
                log.error("Error occurred while applying patches", e);
            }
        }
    }
}
