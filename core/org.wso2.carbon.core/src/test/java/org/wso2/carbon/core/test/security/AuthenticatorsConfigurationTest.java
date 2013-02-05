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

package org.wso2.carbon.core.test.security;

import junit.framework.Assert;
import junit.framework.TestCase;
import org.wso2.carbon.core.security.AuthenticatorsConfiguration;
import org.wso2.carbon.utils.CarbonUtils;
import org.wso2.carbon.utils.ServerConstants;

/**
 * Test class for authenticator configurations.
 */
public class AuthenticatorsConfigurationTest extends TestCase {

    public void setUp() {
        System.setProperty(ServerConstants.CARBON_CONFIG_DIR_PATH,
                "src/test/resources");

    }

    public void testConfigurationReadSAMLSSO() {

        AuthenticatorsConfiguration authenticatorsConfiguration = AuthenticatorsConfiguration.getInstance();
        AuthenticatorsConfiguration.AuthenticatorConfig authenticatorConfig
                = authenticatorsConfiguration.getAuthenticatorConfig("SAML2SSOAuthenticator");

        Assert.assertNotNull(authenticatorConfig);
        Assert.assertEquals(authenticatorConfig.getAuthenticationSkippingUrls().size(), 0);
        Assert.assertEquals(authenticatorConfig.getSessionValidationSkippingUrls().size(), 0);

        Assert.assertTrue(authenticatorConfig.isDisabled());

        Assert.assertEquals(authenticatorConfig.getPriority(), 10);

        Assert.assertEquals(authenticatorConfig.getParameters().size(), 3);

        Assert.assertEquals(authenticatorConfig.getParameters().get("LoginPage"), "/carbon/admin/login.jsp");
        Assert.assertEquals(authenticatorConfig.getParameters().get("ServiceProviderID"), "carbonServer");
        Assert.assertEquals(authenticatorConfig.getParameters().get("IdentityProviderSSOServiceURL"),
                "https://localhost:9443/samlsso");

    }

    public void testDefaultConfiguration() {

        AuthenticatorsConfiguration authenticatorsConfiguration = AuthenticatorsConfiguration.getInstance();
        AuthenticatorsConfiguration.AuthenticatorConfig authenticatorConfig
                = authenticatorsConfiguration.getAuthenticatorConfig("DefaultAuthenticator");

        Assert.assertNotNull(authenticatorConfig);
        Assert.assertEquals(authenticatorConfig.getAuthenticationSkippingUrls().size(), 2);
        Assert.assertEquals(authenticatorConfig.getSessionValidationSkippingUrls().size(), 3);

        Assert.assertFalse(authenticatorConfig.isDisabled());

        Assert.assertEquals(authenticatorConfig.getPriority(), 10);

        Assert.assertEquals(authenticatorConfig.getParameters().size(), 3);

        Assert.assertEquals(authenticatorConfig.getParameters().get("LoginPage"), "/carbon/login.jsp");
        Assert.assertEquals(authenticatorConfig.getParameters().get("ServiceProviderID"), "server");
        Assert.assertEquals(authenticatorConfig.getParameters().get("IdentityProviderSSOServiceURL"),
                "https://127.0.0.1:9443/samlsso");

        for (String url : authenticatorConfig.getAuthenticationSkippingUrls()) {
            if (url.equals("/samlsso") || url.equals("sso-saml/login.jsp")) {
                continue;
            } else {
                Assert.fail("Expected authentication skipping urls are missing");
            }
        }

        for (String url : authenticatorConfig.getSessionValidationSkippingUrls()) {
            if (url.equals("stratos-sso/redirect_ajaxprocessor.jsp") || url.equals("sso-acs/redirect_ajaxprocessor.jsp")
                    || url.equals("stratos-auth/redirect_ajaxprocessor.jsp")) {
                continue;
            } else {
                Assert.fail("Expected session validation skipping urls are missing");
            }
        }

    }

}
