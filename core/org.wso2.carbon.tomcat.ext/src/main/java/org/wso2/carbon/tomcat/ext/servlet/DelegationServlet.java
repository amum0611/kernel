package org.wso2.carbon.tomcat.ext.servlet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.equinox.http.servlet.HttpServiceServlet;

import javax.servlet.*;
import java.io.IOException;

/**
 * This class register itself under a tomcat web-context and delegates all the calls to the
 * {@link HttpServiceServlet#service(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)}
 * method
 */
public class DelegationServlet implements Servlet {

    private static Log log = LogFactory.getLog(DelegationServlet.class);
    private Servlet httpServiceServlet = new HttpServiceServlet();
    private boolean initiated = false;

    public void init(ServletConfig config) throws ServletException {
        if (log.isDebugEnabled()) {
            log.debug("within the init method of DelegationServlet ");
        }
        if (!initiated) {
            initiated = true;
            httpServiceServlet.init(config);
        }
    }

    public void destroy() {
        httpServiceServlet.destroy();
    }

    /**
     * All the service calls get delegated to httpProxy servlet implemented by equinox org.eclipse.equinox.http.servlet
     * bundle
     *
     * @param request  requestObject injected by servlet Engine
     * @param response responseObject injected by servlet Ending
     * @throws ServletException
     * @throws java.io.IOException
     */
    public void service(ServletRequest request, ServletResponse response) throws ServletException, IOException {
        httpServiceServlet.service(request, response);
    }

    public ServletConfig getServletConfig() {
        return httpServiceServlet.getServletConfig();
    }

    public String getServletInfo() {
        return httpServiceServlet.getServletInfo();
    }
}
