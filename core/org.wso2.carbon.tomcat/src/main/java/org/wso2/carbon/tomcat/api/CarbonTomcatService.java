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
    public Context addWebApp(String contextPath, String webappFilePath);

    public Context addWebApp(Host host, String contextPath, String webappFilePath);

    public Context addWebApp(Host host, String contextPath, String webappFilePath, LifecycleListener lifecycleListener);

    public Context addWebApp(String contextPath, String webappFilePath, LifecycleListener lifecycleListener);

    public Wrapper addServlet(String contextPath, String servletName, Servlet servlet);

    public Tomcat getTomcat();

    public int getPort(String scheme);

    public void startConnectors(int port);

    public void stopConnectors();

    public void startConnector(String scheme, int port);

    public void stopConnector(String scheme);
}
