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

import org.apache.axis2.AxisFault;
import org.apache.axis2.client.Options;
import org.apache.axis2.client.ServiceClient;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.context.OperationContext;
import org.apache.axis2.description.WSDL2Constants;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.CarbonConstants;
import org.wso2.carbon.authenticator.proxy.AuthenticationAdminClient;
import org.wso2.carbon.core.common.AuthenticationException;
import org.wso2.carbon.core.commons.stub.loggeduserinfo.ExceptionException;
import org.wso2.carbon.core.commons.stub.loggeduserinfo.LoggedUserInfo;
import org.wso2.carbon.core.commons.stub.loggeduserinfo.LoggedUserInfoAdminStub;
import org.wso2.carbon.ui.util.CarbonUIAuthenticationUtil;
import org.wso2.carbon.utils.CarbonUtils;
import org.wso2.carbon.utils.ServerConstants;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;

import javax.servlet.ServletContext;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.xml.namespace.QName;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

/**
 * Default implementation of CarbonUIAuthenticator.
 */
public class DefaultCarbonAuthenticator extends AbstractCarbonUIAuthenticator {

    protected static final Log log = LogFactory.getLog(DefaultCarbonAuthenticator.class);

    private static final String AUTHENTICATOR_NAME = "DefaultCarbonAuthenticator";

    public boolean reAuthenticateOnSessionExpire(Object object) throws AuthenticationException {
        boolean isValidRememberMe = false;
        try {
            HttpServletRequest request = (HttpServletRequest) object;
            Cookie[] cookies = request.getCookies();
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(CarbonConstants.REMEMBER_ME_COOKE_NAME)) {
                    isValidRememberMe = authenticate(cookie, request);
                }
            }

        } catch (AxisFault e) {
            log.debug("Unable to authenticate with the cookie", e);
            QName errorCode = e.getFaultCode();
            if (errorCode != null) {
                throw new AuthenticationException(e.getMessage(), e, errorCode.getLocalPart());
            } else {
                throw new AuthenticationException(e.getMessage(), e);
            }
        }
        return isValidRememberMe;
    }

    protected boolean authenticate(Cookie cookie, HttpServletRequest request) throws AxisFault {

        try {
            retrieveUserAuthorizationData(cookie, request);

            // No exception means authentication successful
            return true;

        } catch (AxisFault axisFault) {
            throw axisFault;
        } catch (RemoteException e) {
            throw new AxisFault("Unable to access backend server", e);
        } catch (Exception e) {
            throw new AxisFault("Exception occurred", e);
        }

    }

    public boolean isHandle(Object object) {
        // try to authenticate any request that comes
        // least priority authenticator
        return true;
    }

    /**
     * {@inheritDoc}
     */
    public boolean authenticate(Object object) throws AuthenticationException {
        HttpServletRequest request = (HttpServletRequest) object;
        String userName = request.getParameter("username");
        String password = request.getParameter("password");
        String value = request.getParameter("rememberMe");
        boolean isRememberMe = false;
        if (value != null && value.equals("rememberMe")) {
            isRememberMe = true;
        }
        boolean isAuthenticated;
        try {
            isAuthenticated = authenticate(request, userName, password, isRememberMe);
        } catch (AxisFault e) {
            log.debug("Unable to authenticate with the cookie", e);
            QName errorCode = e.getFaultCode();
            if (errorCode != null) {
                throw new AuthenticationException(e.getMessage(), e, errorCode.getLocalPart());
            } else {
                throw new AuthenticationException(e.getMessage(), e);
            }
        } catch (RemoteException e) {
            throw new AuthenticationException(e.getMessage(), e);
        }

        return isAuthenticated;
    }

    /**
     *
     */
    public void unauthenticate(Object object) throws Exception {
        try {
            getAuthenticationAdminCient(((HttpServletRequest) object)).logout();
        } catch (Exception ignored) {
            String msg = "Configuration context is null.";
            log.error(msg);
            throw new Exception(msg);
        }
    }

    protected boolean authenticate(HttpServletRequest request, String userName, String password,
            boolean isRememberMe) throws RemoteException {
        try {

            String userNameWithDomain = userName;
            String domainName = (String) request.getAttribute(MultitenantConstants.TENANT_DOMAIN);
            if (domainName != null) {
                userNameWithDomain += "@" + domainName;
            }
            userNameWithDomain = userNameWithDomain.trim();

            retrieveUserAuthorizationData(userNameWithDomain, password, isRememberMe, request);

            // No exception means authentication successful
            return true;

        } catch (AxisFault axisFault) {
            throw axisFault;
        } catch (RemoteException e) {
            throw e;
        } catch (Exception e) {
            throw new AxisFault("Exception occurred", e);
        }
    }


    private LoggedUserInfoAdminStub getLoggedUserInfoAdminStub(String backendServerURL, HttpSession session)
            throws AxisFault {

        ServletContext servletContext = session.getServletContext();
        ConfigurationContext configContext = (ConfigurationContext) servletContext
                .getAttribute(CarbonConstants.CONFIGURATION_CONTEXT);

        if (configContext == null) {
            String msg = "Configuration context is null.";
            log.error(msg);
            throw new AxisFault(msg);
        }

        return new LoggedUserInfoAdminStub(configContext,
                backendServerURL + "LoggedUserInfoAdmin");

    }

    private void retrieveUserAuthorizationData(String userName, String password,
                                               boolean rememberMe,
                                               HttpServletRequest request) throws AxisFault {

        String backendServerURL = getBackendUrl(request);

        HttpSession session = request.getSession();

        LoggedUserInfoAdminStub stub = getLoggedUserInfoAdminStub(backendServerURL, session);

        try {

            ServiceClient client = stub._getServiceClient();

            CarbonUtils.setBasicAccessSecurityHeaders(userName, password, rememberMe, client);
            
            if(CarbonUtils.isRunningOnLocalTransportMode()){
                //call AuthenticationAdmin, since BasicAuth are not validated for LocalTransport
                AuthenticationAdminClient authClient = getAuthenticationAdminCient(request);
                try {
                    authClient.login(userName, password, "127.0.0.1");
                } catch (AuthenticationException e) {
                    throw new AxisFault(e.getMessage(), e);
                }
                
            }

            // Make the actual call and store results
            setUserAuthorizationInfo(stub, session);

            // If authentication successful set cookies
            setAdminCookie(session, client, null);

            // Process remember me data in reply
            if (rememberMe) {
                processRememberMeData(client, request);
            }

        } catch (AxisFault axisFault) {
            throw axisFault;
        } catch (RemoteException e) {
            throw new AxisFault(e.getMessage(), e);
        } catch (ExceptionException e) {
            throw new AxisFault("Exception occurred while accessing user authorization info", e);
        }
    }

    private void retrieveUserAuthorizationData(Cookie rememberMeCookie,
                                               HttpServletRequest request) throws AxisFault {

        String backendServerURL = getBackendUrl(request);

        HttpSession session = request.getSession();

        LoggedUserInfoAdminStub stub = getLoggedUserInfoAdminStub(backendServerURL, session);

        try {
            ServiceClient client = stub._getServiceClient();

            CarbonUIAuthenticationUtil.setCookieHeaders(rememberMeCookie, client);

            // Make the actual call and store results
            setUserAuthorizationInfo(stub, session);

            // If authentication successful set cookies
            setAdminCookie(session, client, rememberMeCookie.getValue());

        } catch (AxisFault axisFault) {
            throw axisFault;
        } catch (RemoteException e) {
            throw new AxisFault(e.getMessage(), e);
        } catch (ExceptionException e) {
            throw new AxisFault("Exception occurred while accessing user authorization info", e);
        }
    }

    private void setUserAuthorizationInfo(LoggedUserInfoAdminStub loggedUserInfoAdminStub, HttpSession session)
            throws ExceptionException, RemoteException {

        ServiceClient client = loggedUserInfoAdminStub._getServiceClient();
        Options options = client.getOptions();
        options.setManageSession(true);

        LoggedUserInfo userInfo = loggedUserInfoAdminStub.getUserInfo();

        String[] permissionArray = userInfo.getUIPermissionOfUser();
        ArrayList<String> list = new ArrayList<String>();

        Collections.addAll(list, permissionArray);

        session.setAttribute(ServerConstants.USER_PERMISSIONS, list);
        if (userInfo.getPasswordExpiration() != null) {
            session.setAttribute(ServerConstants.PASSWORD_EXPIRATION,
                    userInfo.getPasswordExpiration());
        }
    }

    private void processRememberMeData(ServiceClient serviceClient, HttpServletRequest httpServletRequest)
            throws AxisFault {

        OperationContext operationContext = serviceClient.getLastOperationContext();
        MessageContext inMessageContext = operationContext.getMessageContext(WSDL2Constants.MESSAGE_LABEL_IN);

        Map transportHeaders = (Map) inMessageContext.getProperty(MessageContext.TRANSPORT_HEADERS);

        String cookieValue = (String) transportHeaders.get("RememberMeCookieValue");
        String cookieAge = (String) transportHeaders.get("RememberMeCookieAge");

        if (cookieValue == null || cookieAge == null) {
            throw new AxisFault("Unable to load remember me date from response. " +
                    "Cookie value or cookie age or both are null");
        }

        if (log.isDebugEnabled()) {
            log.debug("Cookie value returned " + cookieValue + " cookie age " + cookieAge);
        }

        httpServletRequest.setAttribute(CarbonConstants.REMEMBER_ME_COOKIE_VALUE, cookieValue);
        httpServletRequest.setAttribute(CarbonConstants.REMEMBER_ME_COOKIE_AGE,
                cookieAge);
    }

    private void setAdminCookie(HttpSession session,
                                ServiceClient serviceClient, String rememberMeCookie) throws AxisFault {
        String cookie = (String) serviceClient.getServiceContext().
                getProperty(HTTPConstants.COOKIE_STRING);

        // TODO need to figure out whether we need this any more ...
        if (rememberMeCookie != null) {
            cookie = cookie + "; " + rememberMeCookie;
        }

        if (session != null) {
            session.setAttribute(ServerConstants.ADMIN_SERVICE_AUTH_TOKEN, cookie);
        }
    }



    public String getAuthenticatorName() {
        return AUTHENTICATOR_NAME;
    }



    public String getBackendUrl(HttpServletRequest request) {

        HttpSession session = request.getSession();

        ServletContext servletContext = session.getServletContext();

        String backendServerURL = request.getParameter("backendURL");
        if (backendServerURL == null) {
            backendServerURL = CarbonUIUtil.getServerURL(servletContext, request.getSession());
        }

        session.setAttribute(CarbonConstants.SERVER_URL, backendServerURL);

        return backendServerURL;
    }

    protected AuthenticationAdminClient getAuthenticationAdminCient(HttpServletRequest request)
            throws AxisFault {
        HttpSession session = request.getSession();
        ServletContext servletContext = session.getServletContext();
        String backendServerURL = request.getParameter("backendURL");
        if (backendServerURL == null) {
            backendServerURL = CarbonUIUtil.getServerURL(servletContext, request.getSession());
        }
        session.setAttribute(CarbonConstants.SERVER_URL, backendServerURL);

        ConfigurationContext configContext = (ConfigurationContext) servletContext
                .getAttribute(CarbonConstants.CONFIGURATION_CONTEXT);

        String cookie = (String) session.getAttribute(ServerConstants.ADMIN_SERVICE_AUTH_TOKEN);

        return new AuthenticationAdminClient(configContext, backendServerURL, cookie, session, true);

    }



}
