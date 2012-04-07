/*
*  Copyright (c) 2005-2011, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.carbon.integration.tests;

import org.testng.Assert;
import org.testng.annotations.Test;
import org.wso2.carbon.integration.framework.LoginLogoutUtil;

/**
 * A test case which tests logging in & logging out of a Carbon core server
 */
public class LoginLogoutTestCase {

    private LoginLogoutUtil util = new LoginLogoutUtil();

    @Deprecated
    @Test(groups = {"carbon.core"},
          description = "Tests the server login functionality", dependsOnMethods = {"loginWithBasicAuth"})
    public void login() throws Exception {
        util.login();
    }

    @Test(groups = {"carbon.core"},
          description = "Tests the server login functionality")
    public void loginWithBasicAuth() throws Exception {
        boolean b = util.loginWithBasicAuth();
        Assert.assertTrue(b, "Authentication failed !!");
    }

    @Test(groups = {"carbon.core"}, dependsOnMethods = {"loginWithBasicAuth"},
          description = "Tests the server logout functionality")
    public void logout() throws Exception {
        util.logout();
    }
}
