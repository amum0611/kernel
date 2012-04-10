/*
 * Copyright 2005-2007 WSO2, Inc. (http://wso2.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.core.services.authentication;

import org.apache.axis2.context.MessageContext;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;
import org.wso2.carbon.CarbonConstants;
import org.wso2.carbon.base.ServerConfiguration;
import org.wso2.carbon.base.api.ServerConfigurationService;
import org.wso2.carbon.core.AbstractAdmin;
import org.wso2.carbon.core.common.AuthenticationException;
import org.wso2.carbon.core.security.AuthenticatorsConfiguration;
import org.wso2.carbon.core.services.internal.CarbonServicesServiceComponent;
import org.wso2.carbon.core.services.util.CarbonAuthenticationUtil;
import org.wso2.carbon.core.util.AnonymousSessionUtil;
import org.wso2.carbon.registry.core.service.RegistryService;
import org.wso2.carbon.user.api.UserRealm;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.carbon.user.core.util.UserCoreUtil;
import org.wso2.carbon.utils.AuthenticationObserver;
import org.wso2.carbon.utils.ServerConstants;
import org.wso2.carbon.utils.CarbonUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * /**
 * @deprecated As of Carbon 4.0.0, replaced by BasicAccessAuthenticator.
 * @see BasicAccessAuthenticator
 *
 * TODO Need to add @Deprecated parameter - Once axis issue related to annotations are fixed
 *
 * Admin service to manage operations on the <code>AxisConfiguration</code>
 */
public class AuthenticationAdmin extends AbstractAdmin implements CarbonServerAuthenticator {

    private static final Log log = LogFactory.getLog(AuthenticationAdmin.class);
    protected static final String AUTHENTICATION_ADMIN_SERVICE = "AuthenticationAdminService";
    private static final int DEFAULT_PRIORITY_LEVEL = 5;
    private static final String AUTHENTICATOR_NAME = "DefaultCarbonAuthenticator";

    private static final String IP_ADDRESS_PATTERN =
            "^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                    "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                    "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                    "([01]?\\d\\d?|2[0-4]\\d|25[0-5])$";


    public boolean login(String username, String password, String remoteAddress)
            throws AuthenticationException {
        HttpSession httpSession = getHttpSession();
        try {

            if ((username == null) || (password == null) || (remoteAddress == null)
                    || username.trim().equals("") || password.trim().equals("")
                    || remoteAddress.trim().equals("")) {
                CarbonAuthenticationUtil.onFailedAdminLogin(httpSession, username, -1,
                        remoteAddress, "Data");
                return false;
            }

            validateRemoteAddress(remoteAddress);

            RegistryService registryService = CarbonServicesServiceComponent.getRegistryService();
            RealmService realmService = CarbonServicesServiceComponent.getRealmService();

            String tenantDomain = UserCoreUtil.getTenantDomain(realmService, username);
            int tenantId = realmService.getTenantManager().getTenantId(tenantDomain);
            handleAuthenticationStarted(tenantId);
            String userNameWithDomain = username;
            username = UserCoreUtil.getTenantLessUsername(username);
            UserRealm realm = AnonymousSessionUtil.getRealmByTenantDomain(registryService,
                    realmService, tenantDomain);
            if (realm == null) {
                throw new AuthenticationException("Invalid domain or unactivated tenant login");
            }
            ServerConfigurationService serverConfig = CarbonServicesServiceComponent
                    .getServerConfiguration();
            boolean isAuthenticated = realm.getUserStoreManager().authenticate(username, password);
            boolean isAuthorized = realm.getAuthorizationManager().isUserAuthorized(username,
                    "/permission/admin/login", CarbonConstants.UI_PERMISSION_ACTION);

            if (isAuthenticated && isAuthorized) {
                CarbonAuthenticationUtil.onSuccessAdminLogin(httpSession, username, tenantId,
                        tenantDomain, remoteAddress);
                handleAuthenticationCompleted(tenantId, true);
                return true;
            } else {
                CarbonAuthenticationUtil.onFailedAdminLogin(httpSession, username, tenantId,
                        remoteAddress, "Invalid credential");
                handleAuthenticationCompleted(tenantId, false);
                return false;
            }
        } catch (AuthenticationException e) {
            log.error(e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            String msg = "System error while Authenticating/Authorizing User : " + e.getMessage();
            log.error(msg, e);
            return false;
        }
    }

    private void validateRemoteAddress(String address) throws AuthenticationException {

        if (address == null || address.isEmpty()) {
            return;
        }

        address = address.replaceAll("\\s+", "");
        address = address.trim();

        if (!isValidIPAddress(address)) {
            if (!isValidDNSAddress(address)) {
                throw new AuthenticationException("Authentication Failed : Invalid remote address passed - " + address);
            }
        }
    }

    private boolean isValidDNSAddress(String address) {
        try {
            InetAddress ipAddress = InetAddress.getByName(address);
            return isValidIPAddress(ipAddress.getHostAddress());
        } catch (UnknownHostException e) {
            log.warn("Could not find IP address for domain name : " + address);
        }

        return false;
    }

    private boolean isValidIPAddress(String ipAddress) {

      Pattern pattern = Pattern.compile(IP_ADDRESS_PATTERN);
      Matcher matcher = pattern.matcher(ipAddress);
      return matcher.matches();
    }

    public RememberMeData loginWithRememberMeOption(String username, String password, String remoteAddress)
            throws AuthenticationException {
        boolean isLoggedIn = this.login(username, password, remoteAddress);
        RememberMeData data = null;
        try {
            if (isLoggedIn) {
                String uuid = UUID.randomUUID().toString();
                data = new RememberMeData();
                data.setMaxAge(CarbonConstants.REMEMBER_ME_COOKIE_TTL);
                data.setValue(username + "-" + uuid);
                RealmService realmService = CarbonServicesServiceComponent.getRealmService();
                String tenantDomain = UserCoreUtil.getTenantDomain(realmService, username);
                int tenantId = realmService.getTenantManager().getTenantId(tenantDomain);
                UserRealm realm = realmService.getTenantUserRealm(tenantId);
                realm.getUserStoreManager().addRememberMe(username, uuid);
                data.setAuthenticated(true);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new AuthenticationException(e.getMessage(), e);
        }
        return data;
    }
    
    public boolean loginWithRememberMeCookie(String cookie) {
        return createSessionForValidRememberMe(cookie, this.getHttpSession());
    }

    private void handleAuthenticationStarted(int tenantId) throws Exception {
        BundleContext bundleContext = CarbonServicesServiceComponent.getBundleContext();
        if (bundleContext != null) {
            ServiceTracker tracker = new ServiceTracker(bundleContext,
                    AuthenticationObserver.class.getName(), null);
            tracker.open();
            Object[] services = tracker.getServices();
            if (services != null) {
                for (Object service : services) {
                    ((AuthenticationObserver) service).startedAuthentication(tenantId);
                }
            }
            tracker.close();
        }
    }

    private void handleAuthenticationCompleted(int tenantId, boolean isSuccessful) throws Exception {
        BundleContext bundleContext = CarbonServicesServiceComponent.getBundleContext();
        if (bundleContext != null) {
            ServiceTracker tracker = new ServiceTracker(bundleContext,
                    AuthenticationObserver.class.getName(), null);
            tracker.open();
            Object[] services = tracker.getServices();
            if (services != null) {
                for (Object service : services) {
                    ((AuthenticationObserver) service).completedAuthentication(tenantId,
                            isSuccessful);
                }
            }
            tracker.close();
        }
    }

    public void logout() throws AuthenticationException {
        String loggedInUser;
        String delegatedBy;
        Date currentTime = Calendar.getInstance().getTime();
        SimpleDateFormat date = new SimpleDateFormat("'['yyyy-MM-dd HH:mm:ss,SSSS']'");
        HttpSession session = getHttpSession();

        if (session != null) {
            loggedInUser = (String) session.getAttribute(ServerConstants.USER_LOGGED_IN);
            delegatedBy = (String) session.getAttribute("DELEGATED_BY");
            if (delegatedBy == null && loggedInUser != null) {
                log.info("'" + loggedInUser + "' logged out at " + date.format(currentTime));
            } else if (loggedInUser != null) {
                log.info("'" + loggedInUser + "' logged out at " + date.format(currentTime)
                        + " delegated by " + delegatedBy);
            }
            //We should not invalidate the session if the system is running on local transport
            if(!CarbonUtils.isRunningOnLocalTransportMode()){
                session.invalidate();
            }
        }
    }

    public String getAuthenticatorName() {
        return AUTHENTICATOR_NAME;
    }

    public int getPriority() {
        AuthenticatorsConfiguration authenticatorsConfiguration = AuthenticatorsConfiguration.getInstance();
        AuthenticatorsConfiguration.AuthenticatorConfig authenticatorConfig =
                authenticatorsConfiguration.getAuthenticatorConfig(AUTHENTICATOR_NAME);
        if (authenticatorConfig != null && authenticatorConfig.getPriority() > 0) {
            return authenticatorConfig.getPriority();
        }
        return DEFAULT_PRIORITY_LEVEL;
    }

    public boolean isAuthenticated(MessageContext messageContext) {
        HttpServletRequest request = (HttpServletRequest) messageContext
                .getProperty(HTTPConstants.MC_HTTP_SERVLETREQUEST);
        HttpSession httpSession = request.getSession();
        String userLoggedIn = (String) httpSession.getAttribute(ServerConstants.USER_LOGGED_IN);
        return (userLoggedIn != null);
    }
    
    public boolean authenticateWithRememberMe(MessageContext messageContext) {
        HttpServletRequest request =
                                     (HttpServletRequest) messageContext.
                                                                         getProperty(HTTPConstants.MC_HTTP_SERVLETREQUEST);
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                String name = cookie.getName();
                if (CarbonConstants.REMEMBER_ME_COOKE_NAME.equals(name)) {
                    return createSessionForValidRememberMe(cookie.getValue(), request.getSession());
                }
            }
        }
        return false;
    }

    public boolean isDisabled() {
        AuthenticatorsConfiguration authenticatorsConfiguration = AuthenticatorsConfiguration.getInstance();
        AuthenticatorsConfiguration.AuthenticatorConfig authenticatorConfig =
                authenticatorsConfiguration.getAuthenticatorConfig(AUTHENTICATOR_NAME);
        if (authenticatorConfig != null) {
            return authenticatorConfig.isDisabled();
        }
        return false;
    }

    public boolean isHandle(MessageContext msgContext) {
        return true;
    }
    
    private boolean createSessionForValidRememberMe(String cookie, HttpSession httpSession) {
        boolean isValid = false;
        try {

            RealmService realmService = CarbonServicesServiceComponent.getRealmService();

            int index = cookie.indexOf('-');
            String userNameWithTenant = cookie.substring(0, index);
            String tenantDomain = UserCoreUtil.getTenantDomain(realmService, userNameWithTenant);
            int tenantId = realmService.getTenantManager().getTenantId(tenantDomain);
            handleAuthenticationStarted(tenantId);

            String userName = UserCoreUtil.getTenantLessUsername(userNameWithTenant);
            String uuid = cookie.substring(index + 1);
            UserRealm realm = realmService.getTenantUserRealm(tenantId);
            boolean isAuthenticated = realm.getUserStoreManager().isValidRememberMeToken(userName,
                                                                                         uuid);
            boolean isAuthorized = false;
            if (isAuthenticated) {
                isAuthorized =
                               realm.getAuthorizationManager()
                                    .isUserAuthorized(userName,
                                                      "/permission/admin/login",
                                                      CarbonConstants.UI_PERMISSION_ACTION);
            }

            if (isAuthenticated && isAuthorized) {
                CarbonAuthenticationUtil.onSuccessAdminLogin(httpSession, userName, tenantId,
                                                             tenantDomain, "");
                handleAuthenticationCompleted(tenantId, true);
                isValid = true;
            } else {
                CarbonAuthenticationUtil.onFailedAdminLogin(httpSession, userName, tenantId, "",
                                                            "Invalid credential");
                handleAuthenticationCompleted(tenantId, false);
            }
            return isValid;
        } catch (Exception e) {
            String msg = "Error while Authenticating/Authorizing User : " + e.getMessage();
            log.error(msg, e);
            return false;
        }
    }
}
