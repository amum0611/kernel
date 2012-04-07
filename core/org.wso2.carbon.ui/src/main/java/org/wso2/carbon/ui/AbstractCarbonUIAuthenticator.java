/*
*  Copyright (c) WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.carbon.ui;

import org.wso2.carbon.core.security.AuthenticatorsConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An abstract implementation if CarbonUIAuthenticator.
 */
public abstract class AbstractCarbonUIAuthenticator implements CarbonUIAuthenticator {

    private static final int DEFAULT_PRIORITY_LEVEL = 5;

    /**
     * In default implementation this will read the authenticator configuration and will
     * return true if authenticator is disabled in the configuration.
     * @return <code>true</code> if authenticator is disabled, else <code>false</code>.
     */
    public boolean isDisabled() {
        AuthenticatorsConfiguration.AuthenticatorConfig authenticatorConfig =
                getAuthenticatorConfig();
        return authenticatorConfig != null && authenticatorConfig.isDisabled();
    }

    /**
     * In default implementation this will read the priority from authenticator
     * configuration.
     * @return The priority value.
     */
    public int getPriority() {
        AuthenticatorsConfiguration.AuthenticatorConfig authenticatorConfig =
                getAuthenticatorConfig();
        if (authenticatorConfig != null && authenticatorConfig.getPriority() > 0) {
            return authenticatorConfig.getPriority();
        }

        return DEFAULT_PRIORITY_LEVEL;
    }

    /**
     * In default implementation this will return some SSO links to be skipped.
     * TODO : check whether we can move this t SSO authenticators.
     * @return A list with following urls.
     */
    public List<String> getSessionValidationSkippingUrls() {
        List<String> skippingUrls = new ArrayList<String>(Arrays.asList(
                "/samlsso",
                "sso-saml/login.jsp",
                "stratos-sso/login_ajaxprocessor.jsp",
                "sso-saml/redirect_ajaxprocessor.jsp",
                "stratos-sso/redirect_ajaxprocessor.jsp",
                "sso-acs/redirect_ajaxprocessor.jsp",
                "stratos-auth/redirect_ajaxprocessor.jsp"
        ));

        AuthenticatorsConfiguration.AuthenticatorConfig authenticatorConfig =
                getAuthenticatorConfig();
        if (authenticatorConfig != null && authenticatorConfig.getPriority() > 0) {
            skippingUrls.addAll(authenticatorConfig.getSessionValidationSkippingUrls());
        }

        return skippingUrls;
    }

    private AuthenticatorsConfiguration.AuthenticatorConfig getAuthenticatorConfig() {

        AuthenticatorsConfiguration authenticatorsConfiguration = AuthenticatorsConfiguration.getInstance();
        AuthenticatorsConfiguration.AuthenticatorConfig authenticatorConfig =
                authenticatorsConfiguration.getAuthenticatorConfig(getAuthenticatorName());

        return authenticatorConfig;
    }

    /**
     * In default implementation this will return an empty list.
     * @return An empty list.
     */
    public List<String> getAuthenticationSkippingUrls() {

        AuthenticatorsConfiguration.AuthenticatorConfig authenticatorConfig =
                getAuthenticatorConfig();

        if (authenticatorConfig != null) {
            return authenticatorConfig.getAuthenticationSkippingUrls();
        } else {
            return new ArrayList<String>(0);
        }
    }

}
