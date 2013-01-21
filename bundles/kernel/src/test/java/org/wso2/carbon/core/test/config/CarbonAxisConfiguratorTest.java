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
package org.wso2.carbon.core.test.config;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class CarbonAxisConfiguratorTest extends TestCase {

    private String basedir;
    private String repoLocation;
    private String weblocation;
    private RegistryServiceCreater grs;

    public void setUp() throws Exception {
        super.setUp();
//        grs = new RegistryServiceCreater();
//        grs.setRegistryService();
//        File file =new File("../org.wso2.carbon.core/src/main");
//        basedir = file.getAbsolutePath();
        repoLocation = "src/test/resources/repository";
        System.setProperty("weblocation","");
        weblocation = System.getProperty("weblocation");
        System.setProperty("axis2.xml",repoLocation+"../axis2.xml");
    }

    public void testTest() throws Exception{
    }

    public void tearDown() throws Exception {
        super.tearDown();
    }



    public static Test suite() {
        return new TestSuite(CarbonAxisConfiguratorTest.class);
    }
}
