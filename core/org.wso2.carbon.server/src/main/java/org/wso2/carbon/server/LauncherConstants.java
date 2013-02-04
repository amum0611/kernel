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
package org.wso2.carbon.server;

public final class LauncherConstants {
    private LauncherConstants() {
    }

    public  static final String WS_DELIM = " \t\n\r\f";

    public static final String COMMAND_HELP = "HELP";
    public static final String COMMAND_CLEAN_REGISTRY = "CLEANREGISTRY";

    public static final String ENABLE_OSGI_CONSOLE = "osgiConsole";
    public static final String ENABLE_OSGI_DEBUG = "osgiDebugOptions";
    public static final String BUNDLE_CREATORS = "bundleCreators";

    public static final String APPLY_PATCHES = "applyPatches";
    public static final String BUNDLE_BACKUP_DIR = "patch0000";

    public static final String LAUNCH_INI = "launch.ini";

    public static final String CARBON_HOME = "carbon.home";
    public static final String AXIS2_HOME = "axis2.home";

    //Bundle manifest constants
    public static final String MANIFEST_VERSION = "Manifest-Version";
    public static final String BUNDLE_MANIFEST_VERSION = "Bundle-ManifestVersion";
    public static final String BUNDLE_NAME = "Bundle-Name";
    public static final String BUNDLE_SYMBOLIC_NAME = "Bundle-SymbolicName";
    public static final String BUNDLE_VERSION = "Bundle-Version";
    public static final String FRAGMENT_HOST = "Fragment-Host";
    public static final String EXPORT_PACKAGE = "Export-Package";
    public static final String CONFIG_EXTENDED_FRAMEWORK_EXPORTS = "extendedFrameworkExports";
    public static final String BUNDLE_CLASSPATH = "Bundle-ClassPath";
    public static final String DYNAMIC_IMPORT_PACKAGE = "DynamicImport-Package";
}
