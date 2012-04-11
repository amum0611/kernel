package org.wso2.carbon.tomcat.api;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;

import javax.servlet.Servlet;

/**
 * interface that exposes {@link org.wso2.carbon.tomcat.internal.CarbonTomcat} functionality
 */
public interface CarbonTomcatService {
    Context addWebApp(String contextPath, String webappFilePath);

    Context addWebApp(Host host, String contextPath, String webappFilePath);

    Context addWebApp(Host host, String contextPath, String webappFilePath, LifecycleListener lifecycleListener);

    Context addWebApp(String contextPath, String webappFilePath, LifecycleListener lifecycleListener);

    Wrapper addServlet(String contextPath, String servletName, Servlet servlet);

    Tomcat getTomcat();

    int getPort(String scheme);

    void startConnectors(int port);

    void stopConnectors();

    void startConnector(String scheme, int port);

    void stopConnector(String scheme);
}
