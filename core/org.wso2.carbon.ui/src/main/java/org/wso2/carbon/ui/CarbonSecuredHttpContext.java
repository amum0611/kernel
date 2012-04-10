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
package org.wso2.carbon.ui;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceReference;
import org.wso2.carbon.CarbonConstants;
import org.wso2.carbon.core.common.AuthenticationException;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.ui.deployment.beans.CarbonUIDefinitions;
import org.wso2.carbon.ui.deployment.beans.Context;
import org.wso2.carbon.ui.internal.CarbonUIServiceComponent;
import org.wso2.carbon.ui.util.CarbonUIAuthenticationUtil;
import org.wso2.carbon.user.core.util.UserCoreUtil;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CarbonSecuredHttpContext extends SecuredComponentEntryHttpContext {

    public static final String LOGGED_USER = CarbonConstants.LOGGED_USER;
    private static final Log log = LogFactory.getLog(CarbonSecuredHttpContext.class);
    private Bundle bundle = null;
    private Pattern tenantEnabledUriPattern;
    private static final String TENANT_ENABLED_URI_PATTERN = "/"
            + MultitenantConstants.TENANT_AWARE_URL_PREFIX + "/[^/]*($|/.*)";

    public CarbonSecuredHttpContext(Bundle bundle, String s, UIResourceRegistry uiResourceRegistry,
                                    Registry registry) {
        super(bundle, s, uiResourceRegistry);
        this.registry = registry;
        this.bundle = bundle;
        tenantEnabledUriPattern = Pattern.compile(TENANT_ENABLED_URI_PATTERN);

    }

    private String getUIErrorCode(String code) {

        if (code == null) {
            return null;
        }

        if (code.equals("50977")) {
            return "invalid.credentials";
        } else if (code.equals("50976")) {
            return "authentication.failure";
        } else {
            return null;
        }

    }

    public boolean handleSecurity(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String requestedURI = request.getRequestURI();

        // This check is required for Single Logout implementation.
        // if the request is not for SSO based authentication page or SSO
        // servlet, then if the session
        // is invalid redirect the requests to logout_action.jsp.
        CarbonSSOSessionManager ssoSessionManager = CarbonSSOSessionManager.getInstance();
        if (!skipSSOSessionInvalidation(request)
                && !ssoSessionManager.isSessionValid(request.getSession().getId())) {
            requestedURI = "/carbon/admin/logout_action.jsp";
        }

        if(skipSSOSessionInvalidation(request)
              && !ssoSessionManager.isSessionValid(request.getSession().getId())){
           ssoSessionManager.removeInvalidSession(request.getSession().getId());
        }


        // we eliminate the /tenant/{tenant-domain} from authentications
        Matcher matcher = tenantEnabledUriPattern.matcher(requestedURI);
        if (matcher.matches()) {
            // Tenant webapp requests should never reach Carbon. It can happen
            // if Carbon is
            // deployed at / context and requests for non-existent tenant
            // webapps is made.
            if (requestedURI.contains("/webapps/")) {
                response.sendError(404, "Web application not found. Request URI: " + requestedURI);
                return false;
            }
            return true;
        }
        // when filtered from a servlet filter the request uri always contains 2
        // //, this is a temporary fix
        if (requestedURI.indexOf("//") == 0) {
            requestedURI = requestedURI.substring(1);
        }

        HttpSession session;
        String sessionId;
        boolean authenticated = false;
        try {
            session = request.getSession();
            sessionId = session.getId();
            Boolean authenticatedObj = (Boolean) session.getAttribute("authenticated");
            if (authenticatedObj != null) {
                authenticated = authenticatedObj.booleanValue();
            }
        } catch (Exception e) {
            return false;
        }

        String context = request.getContextPath();
        if ("".equals(context)) {
            context = "/";
        }

        HashMap<String, String> httpUrlsToBeByPassed = new HashMap<String, String>();
        Context defaultContext = null;
        if (bundle != null) {
            ServiceReference reference = bundle.getBundleContext().getServiceReference(
                    CarbonUIDefinitions.class.getName());
            CarbonUIDefinitions carbonUIDefinitions;
            if (reference != null) {
                carbonUIDefinitions = (CarbonUIDefinitions) bundle.getBundleContext().getService(
                        reference);
                if (carbonUIDefinitions != null) {
                    httpUrlsToBeByPassed = carbonUIDefinitions.getHttpUrls();
                    if (carbonUIDefinitions.getContexts().containsKey("default-context")) {
                        defaultContext = carbonUIDefinitions.getContexts().get("default-context");
                    }

                }
            }
        }

        if (requestedURI.equals(context) || requestedURI.equals(context + "/")) {
            if (defaultContext != null && !"".equals(defaultContext.getContextName())
                    && !"null".equals(defaultContext.getContextName())) {
                String adminConsoleURL = CarbonUIUtil.getAdminConsoleURL(request);
                int index = adminConsoleURL.lastIndexOf("carbon");
                String defaultContextUrl = adminConsoleURL.substring(0, index)
                        + defaultContext.getContextName() + "/";
                response.sendRedirect(defaultContextUrl);
            } else {
                response.sendRedirect("carbon");
            }
            return false;
        }
        // when war is deployed on top of an existing app server we cannot use
        // root context
        // for deployment. Hence a new context is added.
        // Now url changes from eg: carbon/admin/index.jsp to
        // wso2/carbon/admin/index.jsp
        // In this case before doing anything, we need to remove web app context
        // (eg: wso2) .
        String tmp = requestedURI;
        String customWarContext = "";
        if (requestedURI.startsWith("/carbon") && !(requestedURI.startsWith("/carbon/carbon/"))) {
            // one can name the folder as 'carbon'
            requestedURI = tmp;
        } else if (requestedURI.indexOf("filedownload") == -1
                && requestedURI.indexOf("fileupload") == -1) {
            // replace first context
            String tmp1 = tmp.replaceFirst("/", "");
            int end = tmp1.indexOf('/');
            if (end > -1) {
                customWarContext = tmp1.substring(0, end);
                // one can rename the war file as 'registry'.
                // This will conflict with our internal 'registry' context
                if (!(requestedURI.startsWith("/registry/registry/"))
                        && !(requestedURI.startsWith("/registry/carbon/"))
                        && (customWarContext.equals("registry")
                        || customWarContext.equals("gadgets") || customWarContext
                        .equals("social"))) {
                    requestedURI = tmp;
                } else {
                    requestedURI = tmp.substring(end + 1);
                }
            }
        }

        // Disabling http access to admin console
        // user guide documents should be allowed to access via http protocol
        if (!request.isSecure() && !(requestedURI.endsWith(".html"))) {

            // by passing items required for try-it & IDE plugins
            if (requestedURI.endsWith(".css") || requestedURI.endsWith(".gif")
                    || requestedURI.endsWith(".GIF") || requestedURI.endsWith(".jpg")
                    || requestedURI.endsWith(".JPG") || requestedURI.endsWith(".png")
                    || requestedURI.endsWith(".PNG") || requestedURI.endsWith(".xsl")
                    || requestedURI.endsWith(".xslt") || requestedURI.endsWith(".js")
                    || requestedURI.endsWith(".ico") || requestedURI.endsWith("/filedownload")
                    || requestedURI.endsWith("/fileupload")
                    || requestedURI.contains("/fileupload/")
                    || requestedURI.contains("admin/jsp/WSRequestXSSproxy_ajaxprocessor.jsp")
                    || requestedURI.contains("registry/atom")
                    || requestedURI.contains("registry/tags")
                    || requestedURI.contains("gadgets/")
                    || requestedURI.contains("registry/resource")) {
                return true;
            }

            String resourceURI = requestedURI.replaceFirst("/carbon/", "../");

            // bypassing the pages which are specified as bypass https
            if (httpUrlsToBeByPassed.containsKey(resourceURI)) {
                if (!authenticated) {
                    try {
                        Cookie[] cookies = request.getCookies();
                        if (cookies != null) {
                            for (Cookie cookie : cookies) {
                                if (cookie.getName().equals(CarbonConstants.REMEMBER_ME_COOKE_NAME) && 
                                		getAuthenticator(request).reAuthenticateOnSessionExpire(request)) {
                                        String cookieValue = cookie.getValue();
                                        CarbonUIAuthenticationUtil.onSuccessAdminLogin(request,
                                                getUserNameFromCookie(cookieValue));
                                 
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                        throw new IOException(e.getMessage(), e);
                    }
                }
                return true;
            }

            String adminConsoleURL = CarbonUIUtil.getAdminConsoleURL(request);
            if (adminConsoleURL != null) {
                if (log.isTraceEnabled()) {
                    log.trace("Request came to admin console via http.Forwarding to : "
                            + adminConsoleURL);
                }
                response.sendRedirect(adminConsoleURL);
                return false;
            }
        }

        String resourceURI = requestedURI.replaceFirst("/carbon/", "../");

        if (log.isDebugEnabled()) {
            log.debug("CarbonSecuredHttpContext -> handleSecurity() requestURI:" + requestedURI
                    + " id:" + sessionId + " resourceURI:" + resourceURI);
        }

        // retrieve urls that should be by-passed from security check
        HashMap<String, String> urlsToBeByPassed = new HashMap<String, String>();
        if (bundle != null) {
            ServiceReference reference = bundle.getBundleContext().getServiceReference(
                    CarbonUIDefinitions.class.getName());
            CarbonUIDefinitions carbonUIDefinitions;
            if (reference != null) {
                carbonUIDefinitions = (CarbonUIDefinitions) bundle.getBundleContext().getService(
                        reference);
                if (carbonUIDefinitions != null) {
                    urlsToBeByPassed = carbonUIDefinitions.getUnauthenticatedUrls();
                    /*
                     * if(log.isDebugEnabled()){ Iterator<String> itr =
                     * urlsToBeByPassed.keySet().iterator();
                     * while(itr.hasNext()){
                     * log.debug("Should bypass url : "+itr.next()); } }
                     */
                }
            }
        }

        // if the current uri is marked to be by-passed, let it pass through
        if (!urlsToBeByPassed.isEmpty() && urlsToBeByPassed.containsKey(resourceURI)) {
                if (log.isDebugEnabled()) {
                    log.debug("By passing authentication check for URI : " + resourceURI);
                }
                // Before bypassing, set the backendURL properly so that it
                // doesn't fail
                String contextPath = request.getContextPath();
                String backendServerURL = request.getParameter("backendURL");
                if (backendServerURL == null) {
                    backendServerURL = CarbonUIUtil.getServerURL(session.getServletContext(),
                            request.getSession());
                }
                if ("/".equals(contextPath)) {
                    contextPath = "";
                }
                backendServerURL = backendServerURL.replace("${carbon.context}", contextPath);
                session.setAttribute(CarbonConstants.SERVER_URL, backendServerURL);
                return true;
            
        }
        String indexPageURL = CarbonUIUtil.getIndexPageURL(session.getServletContext(),
                request.getSession());
        //Reading the requestedURL from the cookie to obtain the request made while not authanticated; and setting it as the indexPageURL
        if (requestedURI.equals("/carbon/admin/login_action.jsp")) {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if (cookie.getName().equals("requestedURI")) {
                        indexPageURL = cookie.getValue();
                    }
                }
            }
        }

        // If a custom index page is used send teh login request with the
        // indexpage specified
        if (request.getParameter(CarbonConstants.INDEX_PAGE_URL) != null) {
            indexPageURL = request.getParameter(CarbonConstants.INDEX_PAGE_URL);
        } else if (indexPageURL == null) {
            indexPageURL = "/carbon/admin/index.jsp";
        }

        // reading home page set on product.xml
        // String defaultHomePage =
        // (String)session.getServletContext().getAttribute(
        // CarbonConstants.PRODUCT_XML_WSO2CARBON +
        // CarbonConstants.DEFAULT_HOME_PAGE);
        String defaultHomePage = null;
        // if the params in the servletcontext is null get them from the UTIL
        if (defaultHomePage == null) {
            defaultHomePage = (String) CarbonUIUtil
                    .getProductParam(CarbonConstants.PRODUCT_XML_WSO2CARBON
                            + CarbonConstants.DEFAULT_HOME_PAGE);
        }

        if (defaultHomePage != null && defaultHomePage.trim().length() > 0
                && indexPageURL.contains("/carbon/admin/index.jsp")) {
            indexPageURL = defaultHomePage;
            if (!indexPageURL.startsWith("/")) {
                indexPageURL = "/" + indexPageURL;
            }
        }

        if (requestedURI.indexOf("login.jsp") > -1
                || requestedURI.indexOf("login_ajaxprocessor.jsp") > -1
                || requestedURI.indexOf("admin/layout/template.jsp") > -1
                || requestedURI.endsWith("/filedownload") || requestedURI.endsWith("/fileupload")
                || requestedURI.indexOf("/fileupload/") > -1
                || requestedURI.indexOf("login_action.jsp") > -1
                || requestedURI.indexOf("admin/jsp/WSRequestXSSproxy_ajaxprocessor.jsp") > -1) {

            if ((requestedURI.indexOf("login.jsp") > -1 || requestedURI
                    .indexOf("login_ajaxprocessor.jsp") > -1 || requestedURI.indexOf("login_action.jsp") > -1) && authenticated) {
                // User has typed the login page url, while being logged in
                response.sendRedirect(indexPageURL);
                return false;
            } else if (requestedURI.indexOf("login_action.jsp") > -1 && !authenticated) {
            	// User is not yet authenticated and now trying to get authenticated
            	// do nothing, leave for authentication at the end 
            	if(log.isDebugEnabled()) {
            		log.debug("User is not yet authenticated and now trying to get authenticated;" +
            	              "do nothing, leave for authentication at the end");
            	}
            } else {
                return true;
            }
        }

        // If authenticator defines to skip URL, return true
        if (skipAuthentication(request)) {
            return true;
        }

        if (requestedURI.endsWith(".jsp")
                && !requestedURI.endsWith("ajaxprocessor.jsp")
                && !requestedURI.endsWith("session_validate.jsp")
                && (request.getSession().getAttribute("authenticated")) != null
                && ((Boolean) (request.getSession().getAttribute("authenticated"))).booleanValue()
                && ((request.getSession().getAttribute(MultitenantConstants.TENANT_DOMAIN) == null && request
                .getAttribute(MultitenantConstants.TENANT_DOMAIN) != null) || ((request
                .getSession().getAttribute(MultitenantConstants.TENANT_DOMAIN) != null &&
                request.getAttribute(MultitenantConstants.TENANT_DOMAIN) != null) && !request
                .getSession().getAttribute(MultitenantConstants.TENANT_DOMAIN)
                .equals(request.getAttribute(MultitenantConstants.TENANT_DOMAIN))))) {
            // if someone signed in from a tenant, try to access a different
            // tenant domain, he
            // should be forced to sign out without any prompt
            // Cloud requirement
            requestedURI = "../admin/logout_action.jsp";
        }

        boolean sessionAuth = false;
        String contextPath = (request.getContextPath().equals("") || request.getContextPath()
                .equals("/")) ? "" : request.getContextPath();

        String httpLogin = request.getParameter("gsHttpRequest");

        if (requestedURI.indexOf("login_action.jsp") > -1) {
            try {
                // check if the username is of type bob@acme.com
                // if so, this is a login from a multitenant deployment
                // The tenant id part(i.e. acme.com) should be set into
                // http session for further UI related processing
                String userName = request.getParameter("username");
                String tenantDomain = UserCoreUtil.getTenantDomain(
                        CarbonUIServiceComponent.getRealmService(), userName);
                if (tenantDomain == null) {
                    tenantDomain = (String) request
                            .getAttribute(MultitenantConstants.TENANT_DOMAIN);
                }
                if (tenantDomain != null) {
                    // we will add it to the context
                    contextPath += "/" + MultitenantConstants.TENANT_AWARE_URL_PREFIX + "/"
                            + tenantDomain;
                }
                sessionAuth = getAuthenticator(request).authenticate(request);
                if (sessionAuth) {
                    String value = request.getParameter("rememberMe");
                    boolean isRememberMe = false;
                    if (value != null && value.equals("rememberMe")) {
                        isRememberMe = true;
                    }
                    if (!authenticated) {
                        try {
                            CarbonUIAuthenticationUtil.onSuccessAdminLogin(request, userName);
                       } catch (Exception e) {
                            log.warn(e.getMessage(), e);
                            getAuthenticator(request).unauthenticate(request);
                            request.getSession().invalidate();
                            response.sendRedirect("../../carbon/admin/login.jsp?loginStatus=false");
                            return false;
                        }
                    }
                    try {
                        if (isRememberMe) {
                            String rememberMeCookieValue = (String) request
                                    .getAttribute(CarbonConstants.REMEMBER_ME_COOKIE_VALUE);
                            int age = Integer.parseInt((String) request
                                    .getAttribute(CarbonConstants.REMEMBER_ME_COOKIE_AGE));

                            Cookie rmeCookie = new Cookie(CarbonConstants.REMEMBER_ME_COOKE_NAME,
                                    rememberMeCookieValue);
                            rmeCookie.setPath("/");
                            rmeCookie.setSecure(true);
                            rmeCookie.setMaxAge(age);
                            response.addCookie(rmeCookie);
                        }
                    } catch (Exception e) {
                        response.sendRedirect(contextPath + indexPageURL +
                                (indexPageURL.indexOf('?') == -1 ? "?" : "&") +
                                "loginStatus=false");
                        return false;
                    }
                    if (contextPath != null) {
                        if (indexPageURL.startsWith("../..")) {
                            indexPageURL = indexPageURL.substring(5);
                        }

                        response.sendRedirect(contextPath + indexPageURL +
                                (indexPageURL.indexOf('?') == -1 ? "?" : "&") +
                                "loginStatus=true");
                    }

                } else if (httpLogin != null) {
                    response.sendRedirect("../.." + httpLogin + "?loginStatus=false");
                } else {
                    response.sendRedirect("../../carbon/admin/login.jsp?loginStatus=false");
                }

            } catch (AuthenticationException e) {
                log.debug("Authentication failure ...", e);

                String uiErrorCode = getUIErrorCode(e.getUiErrorCode());
                if (uiErrorCode != null) {
                    response.sendRedirect("../../carbon/admin/login.jsp?loginStatus=false&errorCode="
                            + uiErrorCode);
                } else {
                    response.sendRedirect("../../carbon/admin/login.jsp?loginStatus=false");
                }

            } catch (Exception e) {
                log.error("error occurred while login", e);
                response.sendRedirect("../../carbon/admin/login.jsp?loginStatus=failed");
            }

            return false;
        } else if (requestedURI.indexOf("logout_action.jsp") > 1) {

            // only applicable if this is SAML2 based SSO. Complete the logout
            // action after receiving the
            // Logout response.
            if ("true".equals(request.getParameter("logoutcomplete"))) {
                HttpSession currentSession = request.getSession(false);
                if (currentSession != null) {
                    // check if current session has expired
                    session.removeAttribute(LOGGED_USER);
                    session.getServletContext().removeAttribute(LOGGED_USER);
                    try {
                        session.invalidate();
                    } catch (Exception ignored) { // Ignore exception when
                        // invalidating and
                        // invalidated session
                    }
                }
                response.sendRedirect("../../carbon/admin/login.jsp");
                return false;
            }

            // Logout the user from the back-end
            try {
                getAuthenticator(request).unauthenticate(request);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                response.sendRedirect("../admin/login.jsp");
                return false;
            }

            if (!skipSSOSessionInvalidation(request)
                    && !ssoSessionManager.isSessionValid(request.getSession().getId())) {
                HttpSession currentSession = request.getSession(false);
                if (currentSession != null) {
                    // check if current session has expired
                    session.removeAttribute(LOGGED_USER);
                    session.getServletContext().removeAttribute(LOGGED_USER);
                    try {
                        session.invalidate();
                    } catch (Exception ignored) { // Ignore exception when
                        // invalidating and
                        // invalidated session
                    	if(log.isDebugEnabled()) {
                    		log.debug("Ignore exception when invalidating session", ignored);
                    	}
                    }
                }
                response.sendRedirect("../.." + indexPageURL);
                return false;
            }

            // memory clean up : remove invalid session from the invalid session
            // list.
            ssoSessionManager.removeInvalidSession(request.getSession().getId());

            // This condition is evaluated when users are logged out in SAML2
            // based SSO
            if (request.getAttribute("logoutRequest") != null) {
                response.sendRedirect("../../carbon/sso-acs/redirect_ajaxprocessor.jsp?logout=true");
                return false;
            }

            HttpSession currentSession = request.getSession(false);
            if (currentSession != null) {
                // check if current session has expired
                session.removeAttribute(LOGGED_USER);
                session.getServletContext().removeAttribute(LOGGED_USER);
                try {
                    session.invalidate();
                } catch (Exception ignored) { // Ignore exception when
                    // invalidating and invalidated
                    // session
                }
            }
            Cookie rmeCookie = new Cookie(CarbonConstants.REMEMBER_ME_COOKE_NAME, null);
            rmeCookie.setPath("/");
            rmeCookie.setSecure(true);
            rmeCookie.setMaxAge(0);
            response.addCookie(rmeCookie);
            response.sendRedirect("../.." + indexPageURL);
            return false;
        }


        if (requestedURI.endsWith("/carbon/")) {
            response.sendRedirect(contextPath + indexPageURL);
            return false;
        } else if (requestedURI.indexOf("/registry/atom") == -1 && requestedURI.endsWith("/carbon")) {
            String redirectUrl = contextPath + indexPageURL;
            if (!("".equals(customWarContext)) && customWarContext.trim().length() > 0) {
                redirectUrl = "/" + customWarContext + redirectUrl;
            }
            response.sendRedirect(redirectUrl);
            return false;
        } else if (requestedURI.endsWith(".css") || requestedURI.endsWith(".gif")
                || requestedURI.endsWith(".GIF") || requestedURI.endsWith(".jpg")
                || requestedURI.endsWith(".JPG") || requestedURI.endsWith(".png")
                || requestedURI.endsWith(".PNG") || requestedURI.endsWith(".xsl")
                || requestedURI.endsWith(".xslt") || requestedURI.endsWith(".js")
                || requestedURI.startsWith("/registry")
                || requestedURI.endsWith(".html")
                || requestedURI.endsWith(".ico")
                || tmp.startsWith("/openid/")
                || // This is ugly - till we find a solution
                requestedURI.indexOf("/openid/") > -1 || tmp.startsWith("/oauth/")
                || tmp.startsWith("/oauth2/")
                || requestedURI.indexOf("/oauth/") > -1
                || requestedURI.indexOf("/openidserver") > -1
                || requestedURI.indexOf("/gadgets") > -1 || requestedURI.indexOf("/samlsso") > -1) {

            return true;
        } else if (requestedURI.endsWith(".jsp") && authenticated) {
            return true;
        }
        if (!authenticated) {
            if (requestedURI.endsWith("ajaxprocessor.jsp")) {
                // prevent login page appearing
                return true;
            } else {
                // saving originally requested url
                // should not forward to error page after login
                if (!requestedURI.endsWith("admin/error.jsp")) {
                    String queryString = request.getQueryString();
                    String tmpURI;
                    if (queryString != null) {
                        tmpURI = requestedURI + "?" + queryString;
                    } else {
                        tmpURI = requestedURI;
                    }
                    tmpURI = "../.." + tmpURI;
                    request.getSession(false).setAttribute("requestedUri", tmpURI);
                   if (!tmpURI.contains("session-validate.jsp")) {
                        //Also setting it in a cookie, for non-rememberme cases
                        Cookie cookie = new Cookie("requestedURI", tmpURI);
                        cookie.setPath("/");
                        response.addCookie(cookie);
                    }
                }

                try {
                    Cookie[] cookies = request.getCookies();
                    if (cookies != null) {
                        for (Cookie cookie : cookies) {
                            if (cookie.getName().equals(CarbonConstants.REMEMBER_ME_COOKE_NAME)  && 
                              getAuthenticator(request).reAuthenticateOnSessionExpire(request)) {
                                    String cookieValue = cookie.getValue();
                                    CarbonUIAuthenticationUtil.onSuccessAdminLogin(request,
                                            getUserNameFromCookie(cookieValue));
                                    return true;
                            }
                         
                        }
                    }
                } catch (AuthenticationException e) {
                    log.debug("Authentication failure ...", e);

                } catch (Exception e) {
                    log.error("error occurred while login", e);
                }

                if (request.getAttribute(MultitenantConstants.TENANT_DOMAIN) != null) {
                    response.sendRedirect("../admin/login.jsp");
                } else {
                    response.sendRedirect(contextPath + "/carbon/admin/login.jsp");
                }
            }
            return false;
        }
        if (request.getSession().isNew()) {
            response.sendRedirect(contextPath + "/carbon/admin/login.jsp");
            return false;
        }

        return true;
    }

    /**
     * This method checks whether the request is for a SSO authentication
     * related page or servlet. If it is so, the session invalidation should be
     * skipped.
     *
     * @param request Request, HTTPServletRequest
     * @return true, if session invalidation should be skipped.
     */
    private boolean skipSSOSessionInvalidation(HttpServletRequest request) {

        String requestedURI = request.getRequestURI();

        CarbonUIAuthenticator uiAuthenticator = getAuthenticator(request);

        List<String> skippingUrls = uiAuthenticator.getSessionValidationSkippingUrls();

        return skip(requestedURI, skippingUrls);
    }

    /**
     * Skips authentication for given URI's.
     * @param request The request to access a page.
     * @return <code>true</code> if request doesnt need to authenticate, else <code>false</code>.
     */
    private boolean skipAuthentication(HttpServletRequest request) {

        String requestedURI = request.getRequestURI();

        CarbonUIAuthenticator uiAuthenticator = getAuthenticator(request);

        List<String> skippingUrls = uiAuthenticator.getAuthenticationSkippingUrls();

        return skip(requestedURI, skippingUrls);
    }

    private boolean skip(String requestedURI, List<String> skippingUrls) {

        for (String skippingUrl : skippingUrls) {
            if (requestedURI.contains(skippingUrl)) {
                return true;
            }
        }

        return false;
    }

    private String getUserNameFromCookie(String cookieValue) {
        int index = cookieValue.indexOf('-');
        return cookieValue.substring(0, index);
    }
}
