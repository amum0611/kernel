package org.wso2.carbon.tomcat.ext.valves;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.context.RegistryType;
import org.wso2.carbon.registry.api.RegistryService;
import org.wso2.carbon.registry.core.ghostregistry.GhostRegistry;
import org.wso2.carbon.tomcat.ext.internal.CarbonRealmServiceHolder;
import org.wso2.carbon.tomcat.ext.internal.Utils;
import org.wso2.carbon.user.api.TenantManager;
import org.wso2.carbon.user.api.UserRealmService;
import org.wso2.carbon.utils.CarbonUtils;
import org.wso2.carbon.utils.multitenancy.CarbonContextHolder;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;

/**
 * tomcat does not allow us to engage a valve programmatically once it is started. Hence we register this
 * {@link CompositeValve} instance that delegates the {@link #invoke(org.apache.catalina.connector.Request, org.apache.catalina.connector.Response)}
 * calls to CarbonTomcatValves.
 * <p>
 * @see CarbonTomcatValve
 */
@SuppressWarnings("unused")
public class CompositeValve extends ValveBase {
    private static Log log = LogFactory.getLog(CompositeValve.class);
    public static final String ENABLE_SAAS = "carbon.enable.saas";

    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        try {

            /**
             * To enable SaaS for webapp, add the following to the web.xml file
             *
             * <context-param>
             *     <param-name>carbon.enable.saas</param-name>
             *     <param-value>true</param-value>
             * </context-param>
             */
            String enableSaaSParam =
                    request.getContext().findParameter(ENABLE_SAAS);
            if (enableSaaSParam != null && Boolean.valueOf(enableSaaSParam)) {
                // Set the SaaS enabled ThreadLocal variable
                //TODO: uncomment the following code and fix it properly
                /*if (realm != null) {
                    realm.enableSaaS();
                }*/
            }

            initCarbonContext(request);
            TomcatValveContainer.invokeValves(request, response);
            int status = response.getStatus();
            if (status != Response.SC_MOVED_TEMPORARILY && status != Response.SC_FORBIDDEN) {
                // See  http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html
                getNext().invoke(request, response);
            }
        } catch (Exception e) {
            log.error("Could not handle request: " + request.getRequestURI(), e);
        } finally {
            // This will destroy the carbon context holder on the current thread after
            // invoking
            // subsequent valves.
            CarbonContextHolder.destroyCurrentCarbonContextHolder();
        }
    }


    public void initCarbonContext(Request request) throws Exception {
        if (!isWebappMgtEnabled()) {
            return;
        }
        String tenantDomain = Utils.getTenantDomain(request);
        CarbonContextHolder carbonContextHolder =
                CarbonContextHolder.getThreadLocalCarbonContextHolder();
        carbonContextHolder.setTenantDomain(tenantDomain);
        UserRealmService userRealmService = CarbonRealmServiceHolder.getRealmService();
        if (userRealmService != null) {
            TenantManager tenantManager = userRealmService.getTenantManager();
            int tenantId = tenantManager.getTenantId(tenantDomain);
            carbonContextHolder.setTenantId(tenantId);
            carbonContextHolder.setProperty(CarbonContextHolder.USER_REALM,
                                            userRealmService.getTenantUserRealm(tenantId));

            RegistryService registryService = CarbonRealmServiceHolder.getRegistryService();
            carbonContextHolder.setProperty(CarbonContextHolder.CONFIG_SYSTEM_REGISTRY_INSTANCE,
                                            new GhostRegistry(registryService, tenantId,
                                                              RegistryType.SYSTEM_CONFIGURATION));
            carbonContextHolder.setProperty(CarbonContextHolder.GOVERNANCE_SYSTEM_REGISTRY_INSTANCE,
                                            new GhostRegistry(registryService, tenantId,
                                                              RegistryType.SYSTEM_GOVERNANCE));
        }
    }

    /**
     * Checks whether the webapp.mgt Carbon components are available
     *
     * @return true - if webapp.mgt components are available, false - otherwise
     */
    private boolean isWebappMgtEnabled() {
        File osgiPluginsDir =
                new File(CarbonUtils.getCarbonHome() + File.separator + "repository" +
                         File.separator + "components" + File.separator + "plugins");
        String[] plugins = osgiPluginsDir.list();
        for (String plugin : plugins) {
            if (plugin.contains("org.wso2.carbon.webapp.")) {
                return true;
            }
        }
        return false;
    }

}

