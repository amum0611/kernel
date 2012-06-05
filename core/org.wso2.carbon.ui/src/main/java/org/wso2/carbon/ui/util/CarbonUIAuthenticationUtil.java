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

package org.wso2.carbon.ui.util;


import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.axiom.util.base64.Base64Utils;
import org.apache.axis2.AxisFault;
import org.apache.axis2.client.Options;
import org.apache.axis2.client.ServiceClient;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.commons.httpclient.Header;
import org.apache.commons.logging.Log;
import org.wso2.carbon.CarbonConstants;
import org.wso2.carbon.ui.CarbonUIUtil;
import org.wso2.carbon.ui.UIAuthenticationExtender;
import org.wso2.carbon.ui.internal.CarbonUIServiceComponent;
import org.wso2.carbon.core.commons.stub.loggeduserinfo.LoggedUserInfoAdminStub;

import org.wso2.carbon.utils.ServerConstants;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;

public class CarbonUIAuthenticationUtil {
    private static Log audit = CarbonConstants.AUDIT_LOG;


    /**
     * @deprecated
     */
    public static void onSuccessAdminLogin(HttpServletRequest request, String userName)
            throws Exception {
        HttpSession session = request.getSession();
        String tenantDomain = MultitenantUtils.getTenantDomain(userName);
        if (tenantDomain != null && tenantDomain.trim().length() > 0) {
            session.setAttribute(MultitenantConstants.TENANT_DOMAIN, tenantDomain);
            // we will make it an attribute on request as well
            if (request.getAttribute(MultitenantConstants.TENANT_DOMAIN) == null) {
                request.setAttribute(MultitenantConstants.TENANT_DOMAIN, tenantDomain);
            }
        } else {
        	audit.info("User with null domain tried to login.");
        	return;
        }
        onSuccessAdminLogin(request, userName, tenantDomain);
    }

    public static void onSuccessAdminLogin(HttpServletRequest request, String userName,
            String domain) throws Exception {
        HttpSession session = request.getSession();
        
        String serverURL =
            CarbonUIUtil.getServerURL(CarbonUIServiceComponent.getServerConfiguration());
        String cookie = (String)session.getAttribute(ServerConstants.ADMIN_SERVICE_COOKIE);
        
        if (serverURL == null || cookie == null) {
            throw new Exception("Cannot proceed logging in. The server URL and/or Cookie is null");
        }
        
        String tenantDomain = MultitenantUtils.getTenantDomain(userName);
        if(tenantDomain != null && MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain.trim())){
            request.getSession().setAttribute(MultitenantConstants.IS_SUPER_TENANT, "true");
        }else if (tenantDomain != null && tenantDomain.trim().length() > 0) {
            session.setAttribute(MultitenantConstants.TENANT_DOMAIN, tenantDomain);
            // we will make it an attribute on request as well
            if (request.getAttribute(MultitenantConstants.TENANT_DOMAIN) == null) {
                request.setAttribute(MultitenantConstants.TENANT_DOMAIN, tenantDomain);
            }
        } else {
        	audit.info("User with null domain tried to login.");
        	return;
        }

        String tenantAwareUserName = MultitenantUtils.getTenantAwareUsername(userName);     

        setUserInformation(cookie, serverURL,session);
        session.setAttribute(CarbonConstants.LOGGED_USER, tenantAwareUserName);
        session.getServletContext().setAttribute(CarbonConstants.LOGGED_USER, tenantAwareUserName);
        session.setAttribute("authenticated", Boolean.parseBoolean("true"));
        
        UIAuthenticationExtender[] uiAuthenticationExtenders =
            CarbonUIServiceComponent.getUIAuthenticationExtenders();
        for (UIAuthenticationExtender uiAuthenticationExtender : uiAuthenticationExtenders) {
            uiAuthenticationExtender.onSuccessAdminLogin(request, tenantAwareUserName,
                    tenantDomain, serverURL);
        }
        
        
    }



    /**
     * Sets the cookie information, i.e. whether remember me cookie is enabled of disabled. If enabled
     * we will send that information in a HTTP header.
     * @param cookie  The remember me cookie.
     * @param serviceClient The service client used in communication.
     */
    public static void setCookieHeaders(Cookie cookie, ServiceClient serviceClient) {

        List<Header> headers = new ArrayList<Header>();
        Header rememberMeHeader = new Header("RememberMeCookieData", cookie.getValue());
        headers.add(rememberMeHeader);

        serviceClient.getOptions().setProperty(HTTPConstants.HTTP_HEADERS, headers);
    }

    private static void setUserInformation(String cookie, String backendServerURL,
                                                          HttpSession session) throws RemoteException {
        try {

            if (session.getAttribute(ServerConstants.USER_PERMISSIONS) != null) {
                return;
            }
            
            ServletContext servletContext = session.getServletContext();
            ConfigurationContext configContext = (ConfigurationContext) servletContext
                    .getAttribute(CarbonConstants.CONFIGURATION_CONTEXT);
            
            LoggedUserInfoAdminStub stub = new LoggedUserInfoAdminStub(configContext,
                    backendServerURL + "LoggedUserInfoAdmin");
            ServiceClient client = stub._getServiceClient();
            Options options = client.getOptions();
            options.setManageSession(true);
            options.setProperty(HTTPConstants.COOKIE_STRING, cookie);
            org.wso2.carbon.core.commons.stub.loggeduserinfo.LoggedUserInfo userInfo = stub.getUserInfo();

            String[] permissionArray = userInfo.getUIPermissionOfUser();
            ArrayList<String> list = new ArrayList<String>();
            for (String permission : permissionArray) {
                list.add(permission);
            }

            session.setAttribute(ServerConstants.USER_PERMISSIONS, list);
            if (userInfo.getPasswordExpiration() != null) {
                session.setAttribute(ServerConstants.PASSWORD_EXPIRATION,
                        userInfo.getPasswordExpiration());
            }
        } catch (AxisFault e) {
            throw e;
        } catch (RemoteException e) {
            throw e;
        } catch (Exception e) {
            throw new AxisFault("Exception occured", e);
        }
    }

}
