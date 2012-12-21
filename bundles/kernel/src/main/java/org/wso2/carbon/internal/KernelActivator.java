package org.wso2.carbon.internal;/*
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

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.wso2.carbon.base.internal.CarbonBaseActivator;
import org.wso2.carbon.context.internal.CarbonContextActivator;
import org.wso2.carbon.core.internal.CarbonCoreActivator;
import org.wso2.carbon.tomcat.internal.TomcatBundleActivator;

public class KernelActivator implements BundleActivator {
    CarbonBaseActivator carbonBaseActivator = new CarbonBaseActivator();
    TomcatBundleActivator tomcatBundleActivator = new TomcatBundleActivator();
    CarbonCoreActivator carbonCoreActivator = new CarbonCoreActivator();
    CarbonContextActivator carbonContextActivator = new CarbonContextActivator();


    public void start(BundleContext bundleContext) throws Exception {
        carbonBaseActivator.start(bundleContext);
        carbonCoreActivator.start(bundleContext);
        carbonContextActivator.start(bundleContext);
        tomcatBundleActivator.start(bundleContext);
    }

    public void stop(BundleContext bundleContext) throws Exception {
        carbonBaseActivator.stop(bundleContext);
        carbonCoreActivator.stop(bundleContext);
        carbonContextActivator.stop(bundleContext);
        tomcatBundleActivator.stop(bundleContext);
    }
}
