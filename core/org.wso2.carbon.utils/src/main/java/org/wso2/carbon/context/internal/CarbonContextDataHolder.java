/*
*  Copyright (c) 2005-2011, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.carbon.context.internal;

import net.sf.jsr107cache.Cache;
import net.sf.jsr107cache.CacheException;
import net.sf.jsr107cache.CacheFactory;
import net.sf.jsr107cache.CacheManager;
import org.apache.axiom.om.OMElement;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.description.Parameter;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.axis2.util.XMLUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.CarbonConstants;
import org.wso2.carbon.base.DiscoveryService;
import org.wso2.carbon.base.UnloadTenantTask;
import org.wso2.carbon.caching.core.CacheConfiguration;
import org.wso2.carbon.caching.core.CarbonCacheManager;
import org.wso2.carbon.queuing.CarbonQueue;
import org.wso2.carbon.queuing.CarbonQueueManager;
import org.wso2.carbon.queuing.QueuingException;
import org.wso2.carbon.registry.api.Registry;
import org.wso2.carbon.user.api.UserRealm;
import org.wso2.carbon.user.api.UserRealmService;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.utils.CarbonUtils;
import org.wso2.carbon.utils.ServerConstants;
import org.wso2.carbon.utils.ThriftSession;
import org.wso2.carbon.utils.multitenancy.MultitenantCarbonQueueManager;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NameClassPair;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.NoInitialContextException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.event.EventContext;
import javax.naming.event.EventDirContext;
import javax.naming.event.NamingListener;
import javax.naming.ldap.Control;
import javax.naming.ldap.ExtendedRequest;
import javax.naming.ldap.ExtendedResponse;
import javax.naming.ldap.LdapContext;
import javax.naming.spi.InitialContextFactory;
import javax.naming.spi.InitialContextFactoryBuilder;
import javax.naming.spi.NamingManager;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.xml.namespace.QName;
import java.io.File;
import java.lang.ref.WeakReference;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * This class will preserve an instance the current CarbonContext as a thread local variable. If a
 * CarbonContext is available on a global-scope (i.e. HTTP Session or AxisConfiguration) this class
 * will do the required lookup and obtain the corresponding instance.
 * <p/>
 * The CarbonContext provides the API for sub-tenant/super-tenant programming around <a
 * href="http://wso2.com/products/carbon">WSO2 Carbon</a> and <a href="http://wso2.com/cloud/stratos">WSO2
 * Stratos</a>.
 */
@SuppressWarnings("unused")
public final class CarbonContextDataHolder {
    private static final Log log = LogFactory.getLog(CarbonContextDataHolder.class);

    /**
     * The name of the property that stores a reference to the local repository instance of the
     * current tenant.
     */                                                                                                               
    protected Registry localRepository;

    /**
     * The name of the property that stores a reference to the configuration registry instance of
     * the current tenant, as visible to the system.
     */
    protected Registry configSystemRegistry;

    /**
     * The name of the property that stores a reference to the governance registry instance of the
     * current tenant, as visible to the system.
     */
    protected Registry governanceSystemRegistry;

    /**
     * The name of the property that stores a reference to the configuration registry instance of
     * the current tenant, as visible to a user.
     */
    protected Registry configUserRegistry;

    /**
     * The name of the property that stores a reference to the governance registry instance of the
     * current tenant, as visible to a user.
     */
    protected Registry governanceUserRegistry;

    /**
     * The name of the property that stores a reference to the UserRealm instance of the current
     * tenant, as visible to a user.
     */
    protected UserRealm userRealm;

    // The CarbonContextHolder parameter is restricted. We have used the hardcoded string value in
    // TenantAxisConfiguration and CarbonHttpSession and ThriftSession. Any change to this constant, should also be
    // propagated to the three classes mentioned above.
    private static final String CARBON_CONTEXT_HOLDER = "carbonContextHolder";

    private int tenantId = org.wso2.carbon.base.MultitenantConstants.INVALID_TENANT_ID;
    private static final int CARBON_AUTHENTICATION_UTIL_INDEX = 5;
    private static final String CARBON_AUTHENTICATION_UTIL_CLASS = "org.wso2.carbon.core.services.util.CarbonAuthenticationUtil";
    private static final int CARBON_AUTHENTICATION_HANDLER_INDEX = 5;
    private static final String CARBON_AUTHENTICATION_HANDLER_CLASS = "org.wso2.carbon.server.admin.module.handler.AuthenticationHandler";

    private String username;
    private String tenantDomain;
    private String applicationName;

    private Map<String, Object> properties;

    private static List<UnloadTenantTask> unloadTenantTasks = null;

    private static final AtomicReference<DiscoveryService> discoveryServiceProvider = new AtomicReference<DiscoveryService>();

    // stores the current CarbonContext local to the running thread.
    private static ThreadLocal<CarbonContextDataHolder> currentContextHolder = new ThreadLocal<CarbonContextDataHolder>() {
        protected CarbonContextDataHolder initialValue() {
            return new CarbonContextDataHolder();
        }
    };

    // stores references to the existing CarbonContexts when starting tenant
    // flows. These
    // references will be popped back, when a tenant flow is ended.
    private static ThreadLocal<Stack<CarbonContextDataHolder>> parentContextHolderStack =
            new ThreadLocal<Stack<CarbonContextDataHolder>>();

    // contains static initializations for multi-tenant caching, queuing and JNDI support available
    // via the CarbonContext.
    static {
        try {
            log.debug("Started Setting up Authenticator Configuration");
            CarbonAuthenticator authenticator = new CarbonAuthenticator();
            try {
                setupAuthenticator(authenticator);
            } finally {
                String username = System.getProperty("http.proxyUser");
                String password = System.getProperty("http.proxyPassword");
                if (username != null && password != null) {
                    authenticator.addAuthenticator("proxy", ".*", username, password);
                }
                Authenticator.setDefault(authenticator);
            }
            log.debug("Completed Setting up Authenticator Configuration");
        } catch (NoClassDefFoundError ignore) {
            // There can be situations where the CarbonContext is accessed, when there is no Axis2
            // library on the classpath.
            if (log.isDebugEnabled()) {
                log.debug("There can be situations where the CarbonContext is accessed, when there is no Axis2" +
                          "library on the classpath.", ignore);
            }
        } catch (Exception e) {
            String msg = "Unable to read Server Configuration";
            log.error(msg, e);
        }

        unloadTenantTasks = new LinkedList<UnloadTenantTask>();
        registerUnloadTenantTask(new CarbonContextCleanupTask());

        try {
            MultitenantCarbonCacheManager multitenantCacheManager =
                    new MultitenantCarbonCacheManager();
            CacheManager.setInstance(multitenantCacheManager);
            CacheConfiguration configuration = CacheConfiguration.getInstance();
            CarbonCacheManager cacheManager;
            log.debug("Loading Cache Configuration");
            configuration.load(
                    CarbonUtils.getEtcCarbonConfigDirPath() + File.separator + "cache.xml");
            cacheManager = configuration.getCacheManager();
            log.debug("Initializing Cache Manager");
            cacheManager.initialize(CarbonUtils.getCarbonHome());
            multitenantCacheManager.setCarbonCacheManager(cacheManager);
        } catch (Exception e) {
            log.error("Error while instantiating the cache. ", e);
            throw new RuntimeException("Unable to initialize the cache manager.", e);
        }
        try {
            CarbonQueueManager.setInstance(new InternalCarbonQueueManager());
        } catch (RuntimeException ignore) {
            // We don't mind an exception being thrown in here. Since there can be a possibility of
            // the same class loading twice and then trying to reset the queue manager.
            if (log.isDebugEnabled()) {
                log.debug("there can be a possibility of the same class loading twice and then trying " +
                          "to reset the initial context factory builder", ignore);
            }
        }
        try {
            NamingManager.
                    setInitialContextFactoryBuilder(new CarbonInitialJNDIContextFactoryBuilder());
        } catch (NamingException ignore) {
            // We don't mind an exception being thrown in here. Since there can be a possibility of
            // the same class loading twice and then trying to reset the initial context factory
            // builder.
            if (log.isDebugEnabled()) {
                log.debug("there can be a possibility of the same class loading twice and then trying " +
                          "to reset the initial context factory builder", ignore);
            }
        } catch (RuntimeException ignore) {
            // We don't mind an exception being thrown in here. Since there can be a possibility of
            // the same class loading twice and then trying to reset the initial context factory
            // builder. We are also catching Runtime exceptions here, since some JDKs do throw them
            // instead of the expected NamingException.
            if (log.isDebugEnabled()) {
                log.debug("there can be a possibility of the same class loading twice and then trying " +
                          "to reset the initial context factory builder", ignore);
            }
        }
    }

    public Registry getLocalRepository() {
        return localRepository;
    }

    public void setLocalRepository(Registry localRepository) {
        this.localRepository = localRepository;
    }

    public Registry getConfigSystemRegistry() {
        return configSystemRegistry;
    }

    public void setConfigSystemRegistry(Registry configSystemRegistry) {
        this.configSystemRegistry = configSystemRegistry;
    }

    public Registry getGovernanceSystemRegistry() {
        return governanceSystemRegistry;
    }

    public void setGovernanceSystemRegistry(Registry governanceSystemRegistry) {
        this.governanceSystemRegistry = governanceSystemRegistry;
    }

    public Registry getConfigUserRegistry() {
        return configUserRegistry;
    }

    public void setConfigUserRegistry(Registry configUserRegistry) {
        this.configUserRegistry = configUserRegistry;
    }

    public Registry getGovernanceUserRegistry() {
        return governanceUserRegistry;
    }

    public void setGovernanceUserRegistry(Registry governanceUserRegistry) {
        this.governanceUserRegistry = governanceUserRegistry;
    }

    public UserRealm getUserRealm() {
        if(userRealm == null){
            UserRealmService userRealmService = OSGiDataHolder.getInstance().getUserRealmService();
            if (userRealmService != null) {
                try {
                    userRealm = userRealmService.getTenantUserRealm(tenantId);
                } catch (UserStoreException e) {
                    log.error("Cannot retrieve UserRealm for tenant " + tenantId, e);
                }
            }
        }
        return userRealm;
    }

    public void setUserRealm(UserRealm userRealm) {
        this.userRealm = userRealm;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    private static void setupAuthenticator(CarbonAuthenticator authenticator) throws Exception {
        OMElement documentElement = XMLUtils.toOM(
                CarbonUtils.getServerConfiguration().getDocumentElement());
        OMElement authenticators = documentElement.getFirstChildWithName(
                new QName(ServerConstants.CARBON_SERVER_XML_NAMESPACE, "Security")).
                getFirstChildWithName(
                        new QName(ServerConstants.CARBON_SERVER_XML_NAMESPACE, "NetworkAuthenticatorConfig"));

        if (authenticators == null) {
            return;
        }

        for (Iterator iterator = authenticators.getChildElements(); iterator.hasNext(); ) {
            OMElement authenticatorElement = (OMElement) iterator.next();
            if (!authenticatorElement.getLocalName().equalsIgnoreCase("Credential")) {
                continue;
            }
            String pattern = authenticatorElement.getFirstChildWithName(
                    new QName(ServerConstants.CARBON_SERVER_XML_NAMESPACE, "Pattern")).getText();
            String type = authenticatorElement.getFirstChildWithName(
                    new QName(ServerConstants.CARBON_SERVER_XML_NAMESPACE, "Type")).getText();
            String username = authenticatorElement.getFirstChildWithName(
                    new QName(ServerConstants.CARBON_SERVER_XML_NAMESPACE, "Username")).getText();
            String password = authenticatorElement.getFirstChildWithName(
                    new QName(ServerConstants.CARBON_SERVER_XML_NAMESPACE, "Password")).getText();
            authenticator.addAuthenticator(type, pattern, username, password);
        }
    }

    /**
     * Method to be called when this tenant is unloaded. This will clear all the resources created
     * by this tenant on the carbon contexts.
     */
    public void unloadTenant() {
        // The security checks are done at the CarbonContextHolderBase level.
        int tenantId = getTenantId();
        unloadTenant(tenantId);
    }

    ////////////////////////////////////////////////////////
    // The CarbonContext will be obtained in the following manner:
    //
    // 1. If a NoClassDefFoundError occurs, Axis2 libraries are not on the classpath and hence the
    //    CarbonContext will be obtained from the thread local copy.
    //
    // 2. If a message context does not exist, the CarbonContext will be obtained from the thread
    //    local copy.
    //
    // 3. If a message context exists, and if a HTTP session exists, we will attempt to fetch the
    //    CarbonContext from the HTTP session. If a CarbonContext exists on the HTTP session, it
    //    will be returned.
    //
    // 4. If not, we will attempt to fetch the CarbonContext from the Axis2 Configuration. If a
    //    CarbonContext exists on the Axis2 Configuration, it will be returned.
    //
    // 5. If the CarbonContext is still not found and a HTTP session exists, the thread local copy
    //    will first be copied to the HTTP session and then returned.
    //
    // 6. If no HTTP session exists, and the Axis2 Configuration exists, the thread local copy will
    //    first be copied to the Axis2 Configuration and then returned.
    ////////////////////////////////////////////////////////

    /**
     * Provides an instance of the current CarbonContext available on the Axis2 Configuration
     * Context. If an instance is not available, this method will first add a new CarbonContext onto
     * the Axis2 Configuration Context.
     *
     * @param configurationContext the Axis2 Configuration Context.
     * @return the CarbonContext holder.
     */
    public static CarbonContextDataHolder getCurrentCarbonContextHolder(
            ConfigurationContext configurationContext) {
        return getCurrentCarbonContextHolder(configurationContext, true);
    }

    /**
     * This utility method is used to obtain an instance of the CarbonContext available on the Axis
     * Configuration Context. It also accepts an argument explaining whether we need to add the
     * CarbonContext if it was not already available.
     *
     * @param configurationContext the Axis2 Configuration.
     * @param addToConfigContext   whether the CarbonContext should be added if it doesn't already
     *                             exist.
     * @return the CarbonContext holder if it was existing, or if it was newly added.
     */
    private static CarbonContextDataHolder getCurrentCarbonContextHolder(
            ConfigurationContext configurationContext, boolean addToConfigContext) {
        if (configurationContext != null) {
            if (configurationContext.getAxisConfiguration() != null) {
                return getCurrentCarbonContextHolder(configurationContext.getAxisConfiguration(),
                                                     addToConfigContext);
            }
        }
        return getThreadLocalCarbonContextHolder();
    }

    /**
     * Provides an instance of the current CarbonContext available on the Axis2 Configuration. If an
     * instance is not available, this method will first add a new CarbonContext onto the Axis2
     * Configuration.
     *
     * @param axisConfiguration the Axis2 Configuration.
     * @return the CarbonContext holder.
     */
    public static CarbonContextDataHolder getCurrentCarbonContextHolder(
            AxisConfiguration axisConfiguration) {
        return getCurrentCarbonContextHolder(axisConfiguration, true);
    }

    private static CarbonContextDataHolder getClone() {
        return new CarbonContextDataHolder(getCurrentCarbonContextHolder());
    }

    /**
     * This utility method is used to obtain an instance of the CarbonContext available on the Axis
     * Configuration. It also accepts an argument explaining whether we need to add the
     * CarbonContext if it was not already available.
     *
     * @param axisConfiguration  the Axis2 Configuration.
     * @param addToConfiguration whether the CarbonContext should be added if it doesn't already
     *                           exist.
     * @return the CarbonContext holder if it was existing, or if it was newly added.
     */
    private static CarbonContextDataHolder getCurrentCarbonContextHolder(
            AxisConfiguration axisConfiguration, boolean addToConfiguration) {
        Parameter param = axisConfiguration.getParameter(CARBON_CONTEXT_HOLDER);
        if (param != null && param.getValue() != null) {
            return (CarbonContextDataHolder) param.getValue();
        } else if (!addToConfiguration) {
            return null;
        }
        try {
            CarbonContextDataHolder context = getClone();
            log.debug("Added CarbonContext to the Axis Configuration");
            axisConfiguration.addParameter(CARBON_CONTEXT_HOLDER, context);
            return context;
        } catch (Exception e) {
            throw new RuntimeException("Failed to add CarbonContext to the AxisConfiguration.", e);
        }
    }

    /**
     * Provides an instance of the current CarbonContext available on the HTTP Session. If an
     * instance is not available, this method will first add a new CarbonContext onto the HTTP
     * Session.
     *
     * @param httpSession the HTTP Session.
     * @return the CarbonContext holder.
     */
    public static CarbonContextDataHolder getCurrentCarbonContextHolder(HttpSession httpSession) {
        return getCurrentCarbonContextHolder(httpSession, true);
    }

    /**
     * This utility method is used to obtain an instance of the CarbonContext available on the HTTP
     * Session. It also accepts an argument explaining whether we need to add the CarbonContext if
     * it was not already available.
     *
     * @param httpSession  the HTTP Session.
     * @param addToSession whether the CarbonContext should be added if it doesn't already exist.
     * @return the CarbonContext holder if it was existing, or if it was newly added.
     */
    private static CarbonContextDataHolder getCurrentCarbonContextHolder(HttpSession httpSession,
                                                                         boolean addToSession) {
        Object contextObject = httpSession.getAttribute(CARBON_CONTEXT_HOLDER);
        if (contextObject != null) {
            return (CarbonContextDataHolder) contextObject;
        } else if (!addToSession) {
            return null;
        }
        CarbonContextDataHolder context = getClone();
        log.debug("Added CarbonContext to the HTTP Session");
        httpSession.setAttribute(CARBON_CONTEXT_HOLDER, context);
        return context;
    }

    /**
     * Provides an instance of the current CarbonContext available on the Thrift Session. If an
     * instance is not available, this method will first add a new CarbonContext onto the Thrift
     * Session.
     *
     * @param thriftSession the Thrift Session.
     * @return the CarbonContext holder.
     */
    public static CarbonContextDataHolder getCurrentCarbonContextHolder(ThriftSession thriftSession) {
        return getCurrentCarbonContextHolder(thriftSession, true);
    }

    /**
     * This utility method is used to obtain an instance of the CarbonContext available on the Thrift
     * Session. It also accepts an argument explaining whether we need to add the CarbonContext if
     * it was not already available.
     *
     * @param thriftSession the Thirft Session.
     * @param addToSession  whether the CarbonContext should be added if it doesn't already exist.
     * @return the CarbonContext holder if it was existing, or if it was newly added.
     */
    private static CarbonContextDataHolder getCurrentCarbonContextHolder(ThriftSession thriftSession,
                                                                         boolean addToSession) {
        Object contextObject = thriftSession.getSessionCarbonContextHolder();
        if (contextObject != null) {
            return (CarbonContextDataHolder) contextObject;
        } else if (!addToSession) {
            return null;
        }
        CarbonContextDataHolder context = getClone();
        log.debug("Added CarbonContext to the Thrift Session");
       //thriftSession.setAttribute(CARBON_CONTEXT_HOLDER, context);
        return context;
    }

    /**
     * Provides an instance of the current CarbonContext available on the Message Context. This
     * method will lookup the Message Context and see whether a CarbonContext is available on either
     * the HTTP Session or the Axis2 Configuration available through the Message Context. If an
     * instance is not available, this method will add it to the HTTP Session if an HTTP Session is
     * already available. Failing that, it will try to add it to the Axis2 Configuration.
     *
     * @param messageContext the Message Context.
     * @return the CarbonContext holder.
     */
    public static CarbonContextDataHolder getCurrentCarbonContextHolder(
            MessageContext messageContext) {
        HttpServletRequest request = (HttpServletRequest) messageContext.getProperty(
                HTTPConstants.MC_HTTP_SERVLETREQUEST);
        if (request != null) {
            HttpSession httpSession = request.getSession(false);
            if (httpSession != null) {
                CarbonContextDataHolder context = getCurrentCarbonContextHolder(httpSession, false);
                if (context != null) {
                    return context;
                }
                if (messageContext.getConfigurationContext() != null) {
                    context = getCurrentCarbonContextHolder(
                            messageContext.getConfigurationContext(), false);
                    if (context != null) {
                        return context;
                    }
                }
                context = getClone();
                log.debug("Added CarbonContext to the HTTP Session");
                httpSession.setAttribute(CARBON_CONTEXT_HOLDER, context);
                return context;
            }
        }
        return getCurrentCarbonContextHolder(messageContext.getConfigurationContext());
    }

    /**
     * This method will return the current CarbonContext from the thread-local copy.
     * If that is not available, then this method will attempt to obtain an instance of a CarbonContext on the Message Context.
     * This method will lookup the Message Context and see whether a CarbonContext is available on
     * either the HTTP Session or the Axis2 Configuration available through the Message Context.
     *
     * @return the CarbonContext holder.
     */

    public static CarbonContextDataHolder getCurrentCarbonContextHolder() {
        try {
            MessageContext messageContext = MessageContext.getCurrentMessageContext();
            if (messageContext != null) {
                return getCurrentCarbonContextHolder(messageContext);
            } else {
                return getCurrentCarbonContextHolder((ConfigurationContext) null);
            }
        } catch (NullPointerException ignore) {
            // This is thrown when the message context is not initialized
            // So return the Threadlocal
            return getThreadLocalCarbonContextHolder();
        } catch (NoClassDefFoundError ignore) {
            // There can be situations where the CarbonContext is accessed, when there is no Axis2
            // library on the classpath.
            return getThreadLocalCarbonContextHolder();
        }
    }

    /**
     * This method will always attempt to obtain an instance of the current CarbonContext from the
     * thread-local copy.
     *
     * @return the CarbonContext holder.
     */
    public static CarbonContextDataHolder getThreadLocalCarbonContextHolder() {
        return currentContextHolder.get();
    }

    /**
     * This method will set the current multi-tenant queue manager instance.
     *
     * @param queueManager the multi-tenant queue manager.
     * @throws org.wso2.carbon.queuing.QueuingException
     *          if the operation failed.
     */
    public void setQueueManager(MultitenantCarbonQueueManager queueManager)
            throws QueuingException {
        CarbonQueueManager manager = CarbonQueueManager.getInstance();
        if (manager instanceof InternalCarbonQueueManager) {
            ((InternalCarbonQueueManager) manager).setQueueManager(queueManager);
        }
        log.debug("Successfully set the Queue Manager");
    }

    /**
     * This method will remove the current multi-tenant queue manager instance.
     *
     * @throws QueuingException if the operation failed.
     */
    public void removeQueueManager() throws QueuingException {
        CarbonQueueManager manager = CarbonQueueManager.getInstance();
        if (manager instanceof InternalCarbonQueueManager) {
            ((InternalCarbonQueueManager) manager).removeQueueManager();
        }
        log.debug("Successfully removed the Queue Manager");
    }

    // Checks whether the given tenant is a sub-tenant or not.
    private static boolean isSubTenant(int tenantId) {
        return (tenantId != MultitenantConstants.SUPER_TENANT_ID &&
                tenantId != MultitenantConstants.INVALID_TENANT_ID);
    }

    // A tenant-aware queue manager implementation. This will internally hold an instance of the
    // {@link MultitenantCarbonQueueManager}.
    private static class InternalCarbonQueueManager extends CarbonQueueManager {

        private AtomicReference<MultitenantCarbonQueueManager> queueManager =
                new AtomicReference<MultitenantCarbonQueueManager>();

        public CarbonQueue<?> getQueue(String name) {
            int tenantId = getCurrentCarbonContextHolder().getTenantId();
            if (queueManager.get() != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Retrieving named queue: " + name);
                }
                return queueManager.get().getQueue(name,
                                                   isSubTenant(tenantId) ?
                                                   tenantId : MultitenantConstants.SUPER_TENANT_ID);
            }
            return null;
        }

        public synchronized void setQueueManager(MultitenantCarbonQueueManager queueManager)
                throws QueuingException {
            CarbonUtils.checkSecurity();
            if (getCurrentCarbonContextHolder().getTenantId() !=
                MultitenantConstants.SUPER_TENANT_ID) {
                throw new QueuingException("Only the super-tenant can set the queue manager.");
            }
            InternalCarbonQueueManager carbonQueueManager = null;
            if (this.queueManager.get() != null) {
                throw new QueuingException("The queue manager has already been set.");
            }
            this.queueManager.set(queueManager);
        }

        public synchronized void removeQueueManager() throws QueuingException {
            CarbonUtils.checkSecurity();
            if (getCurrentCarbonContextHolder().getTenantId() !=
                MultitenantConstants.SUPER_TENANT_ID) {
                throw new QueuingException("Only the super-tenant can remove the queue manager.");
            }
            this.queueManager.set(null);
        }
    }

    // A tenant-aware cache manager implementation.
    private static class MultitenantCarbonCacheManager extends CacheManager implements CarbonCacheManager {

        private String getNameForTenant(String name) {
            int tenantId = getCurrentCarbonContextHolder().getTenantId();
            if (name != null) {
                // Tenants can only use the default cache for their work. The super-tenant and the
                // system may create named caches as needed. However, we allow tenants to create
                // registry path caches, and also authorization caches in the UM kernel.
                CarbonUtils.checkSecurity();
            }
            String cacheName = (name != null) ? name : carbonCacheManager.getDefaultCacheName();
            return (isSubTenant(tenantId)) ? cacheName + "$" + tenantId : cacheName;
        }

        private CarbonCacheManager carbonCacheManager;
        private CacheCleanupTask cacheCleanupTask;

        public void setCarbonCacheManager(CarbonCacheManager carbonCacheManager) {
            this.carbonCacheManager = carbonCacheManager;
            cacheCleanupTask = new CacheCleanupTask(carbonCacheManager);
            registerUnloadTenantTask(cacheCleanupTask);
        }

        public Cache getCache(String cacheName) {
            Cache cache = carbonCacheManager.getCache(getNameForTenant(cacheName));
            if (cache != null) {
                cacheCleanupTask.register(getCurrentCarbonContextHolder().getTenantId(),
                                          getNameForTenant(cacheName));
            }
            if (log.isDebugEnabled()) {
                log.debug("Retrieving named cache: " + cacheName);
            }
            return cache;
        }

        public void registerCache(String cacheName, Cache cache) {
            if (log.isDebugEnabled()) {
                log.debug("Registering named cache: " + getNameForTenant(cacheName));
            }
            carbonCacheManager.registerCache(getNameForTenant(cacheName), cache);
        }

        public CacheFactory getCacheFactory() throws CacheException {
            return carbonCacheManager.getCacheFactory();
        }

        private static class CacheCleanupTask implements UnloadTenantTask<String> {

            private CarbonCacheManager carbonCacheManager;
            private Map<Integer, ArrayList<String>> cacheNames
                    = new ConcurrentHashMap<Integer, ArrayList<String>>();

            public CacheCleanupTask(CarbonCacheManager carbonCacheManager) {
                this.carbonCacheManager = carbonCacheManager;
            }

            public void register(int tenantId, String cacheName) {
                ArrayList<String> list = cacheNames.get(tenantId);
                if (list == null) {
                    list = new ArrayList<String>();
                    list.add(cacheName);
                    cacheNames.put(tenantId, list);
                } else if (!list.contains(cacheName)) {
                    list.add(cacheName);
                }
            }

            public void cleanup(int tenantId) {
                ArrayList<String> list = cacheNames.remove(tenantId);
                if (list != null) {
                    for (String cacheName : list) {
                        carbonCacheManager.registerCache(cacheName, null);
                    }
                    list.clear();
                }
            }
        }

        @Override
        public void initialize(String carbonHome) {
            // TODO Auto-generated method stub

        }

        @Override
        public String getDefaultCacheName() {
            // TODO Auto-generated method stub
            return null;
        }
    }

    // A tenant-aware JNDI Initial Context Factory Builder implementation.
    private static class CarbonInitialJNDIContextFactoryBuilder implements
                                                                InitialContextFactoryBuilder {

        private static final String defaultInitialContextFactory =
                CarbonUtils.getServerConfiguration().getFirstProperty(
                        "JNDI.DefaultInitialContextFactory");

        public InitialContextFactory createInitialContextFactory(Hashtable<?, ?> h)
                throws NamingException {
            try {
                // get factory class name
                String factoryClassName = (String) h.get(Context.INITIAL_CONTEXT_FACTORY);
                // if the factory class has not been provided use the default initial context
                // factory defined in carbon.xml.
                if (factoryClassName == null) {
                    factoryClassName = defaultInitialContextFactory;
                }
                if (factoryClassName == null) {
                    throw new NoInitialContextException("Failed to create " +
                                                        "InitialContext. No factory specified in hash table.");
                }
                // new factory instance
                if (log.isDebugEnabled()) {
                    log.debug("Loading JNDI Initial Context Factory: " + factoryClassName);
                }
                Class<?> factoryClass = classForName(factoryClassName);
                return new CarbonInitialJNDIContextFactory(
                        (InitialContextFactory) factoryClass.newInstance());
            } catch (Exception e) {
                NamingException nex = new NoInitialContextException("Failed to create " +
                                                                    "InitialContext using factory specified in hash table.");
                nex.setRootCause(e);
                throw nex;
            }
        }
    }

    // A tenant-aware JNDI Initial Context Factory implementation.
    private static class CarbonInitialJNDIContextFactory implements InitialContextFactory {

        private InitialContextFactory factory;

        public CarbonInitialJNDIContextFactory(InitialContextFactory factory) {
            this.factory = factory;
        }

        public Context getInitialContext(Hashtable<?, ?> h) throws NamingException {
            return new CarbonInitialJNDIContext(factory.getInitialContext(h));
        }
    }

    // A tenant-aware JNDI Initial Context implementation.
    private static class CarbonInitialJNDIContext implements EventDirContext, LdapContext {

        private Context initialContext;
        private Map<String, Context> contextCache =
                Collections.synchronizedMap(new HashMap<String, Context>());
        private static ContextCleanupTask contextCleanupTask;
        private static List<String> superTenantOnlyUrlContextSchemes;
        private static List<String> allTenantUrlContextSchemes;

        static {
            contextCleanupTask = new ContextCleanupTask();
            registerUnloadTenantTask(contextCleanupTask);
            superTenantOnlyUrlContextSchemes = Arrays.asList(
                    CarbonUtils.getServerConfiguration().getProperties(
                            "JNDI.Restrictions.SuperTenantOnly.UrlContexts.UrlContext.Scheme"));
            allTenantUrlContextSchemes = Arrays.asList(
                    CarbonUtils.getServerConfiguration().getProperties(
                            "JNDI.Restrictions.AllTenants.UrlContexts.UrlContext.Scheme"));
        }

        public CarbonInitialJNDIContext(Context initialContext) throws NamingException {
            this.initialContext = initialContext;
        }

        private static String getScheme(String url) {
            if (null == url) {
                return null;
            }
            int colPos = url.indexOf(':');
            if (colPos < 0) {
                return null;
            }
            String scheme = url.substring(0, colPos);
            char c;
            boolean inCharSet;
            for (int i = 0; i < scheme.length(); i++) {
                c = scheme.charAt(i);
                inCharSet = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                            || (c >= '0' && c <= '9') || c == '+' || c == '.'
                            || c == '-' || c == '_';
                if (!inCharSet) {
                    return null;
                }
            }
            return scheme;
        }

        private Context getInitialContext(Name name) {
            return getInitialContext(name.get(0));
        }

        private Context getInitialContext() {
            return getInitialContext((String) null);
        }

        private boolean isBaseContextRequested() {

            try {
                String baseContextRequested = (String) this.initialContext.getEnvironment().
                        get(CarbonConstants.REQUEST_BASE_CONTEXT);
                if (baseContextRequested != null && baseContextRequested.equals("true")) {
                    return true;
                }
            } catch (NamingException e) {
                log.warn(
                        "An error occurred while retrieving environment properties from initial context.",
                        e);
            }

            return false;
        }

        private Context getInitialContext(String name) {

            /**
             * If environment is requesting a base context return the
             * base context.
             */

            if (isBaseContextRequested()) {
                return initialContext;
            }

            Context base = null;
            String scheme = null;
            if (name != null) {
                // If the name has components
                scheme = getScheme(name);
                if (scheme != null) {
                    if (contextCache.containsKey(scheme)) {
                        base = contextCache.get(scheme);
                    } else {
                        try {
                            Context urlContext = NamingManager.getURLContext(scheme,
                                                                             initialContext.getEnvironment());
                            if (urlContext != null) {
                                contextCache.put(scheme, urlContext);
                                base = urlContext;
                            }
                        } catch (NamingException ignored) {
                            // If we are unable to find the context, we use the default context.
                            if (log.isDebugEnabled()) {
                                log.debug("If we are unable to find the context, we use the default context.", ignored);
                            }
                        }
                    }
                }
            }
            if (base == null) {
                base = initialContext;
                scheme = null;
            }
            int tenantId = getCurrentCarbonContextHolder().getTenantId();
            if (!isSubTenant(tenantId)) {
                return base;
            } else if (scheme != null) {
                if (allTenantUrlContextSchemes.contains(scheme)) {
                    return base;
                } else if (superTenantOnlyUrlContextSchemes.contains(scheme)) {
                    throw new SecurityException("Tenants are not allowed to use JNDI contexts " +
                                                "with scheme: " + scheme);
                }
            }
            String tenantContextName = Integer.toString(tenantId);
            Context subContext;
            try {
                subContext = (Context) base.lookup(tenantContextName);
                if (subContext != null) {
                    return subContext;
                }
            } catch (NamingException ignored) {
                // Depending on the JNDI Initial Context factory, the above operation may or may not
                // throw an exception. But, since we don't mind the exception, we can ignore it.
                if (log.isDebugEnabled()) {
                    log.debug(ignored);
                }

            }
            try {
                log.debug("Creating Sub-Context: " + tenantContextName);
                subContext = base.createSubcontext(tenantContextName);
                contextCleanupTask.register(tenantId, subContext);
                if (subContext == null) {
                    throw new RuntimeException("Initial context was not created for tenant: " +
                                               tenantId);
                }
                return subContext;
            } catch (NamingException e) {
                throw new RuntimeException("An error occurred while creating the initial context " +
                                           "for tenant: " + tenantId, e);
            }
        }

        private static class ContextCleanupTask implements UnloadTenantTask<Context> {

            private Map<Integer, ArrayList<Context>> contexts
                    = new ConcurrentHashMap<Integer, ArrayList<Context>>();

            public void register(int tenantId, Context context) {
                ArrayList<Context> list = contexts.get(tenantId);
                if (list == null) {
                    list = new ArrayList<Context>();
                    list.add(context);
                    contexts.put(tenantId, list);
                } else if (!list.contains(context)) {
                    list.add(context);
                }
            }

            public void cleanup(int tenantId) {
                ArrayList<Context> list = contexts.remove(tenantId);
                // We need to close the context in a LIFO fashion.
                if (list != null) {
                    Collections.reverse(list);
                    for (Context context : list) {
                        try {
                            context.close();
                        } catch (NamingException ignore) {
                            // We are not worried about the exception thrown here, as we are simply
                            // doing a routine cleanup.
                            if (log.isDebugEnabled()) {
                                log.debug("Exception while outine cleanup", ignore);
                            }
                        }
                    }
                    list.clear();
                }
            }
        }

        public Object lookup(String s) throws NamingException {
            return getInitialContext(s).lookup(s);
        }

        public Object lookup(Name name) throws NamingException {
            return getInitialContext(name).lookup(name);
        }

        public void bind(String s, Object o) throws NamingException {
            getInitialContext(s).bind(s, o);
        }

        public void bind(Name name, Object o) throws NamingException {
            getInitialContext(name).bind(name, o);
        }

        public void rebind(String s, Object o) throws NamingException {
            getInitialContext(s).rebind(s, o);
        }

        public void rebind(Name name, Object o) throws NamingException {
            getInitialContext(name).rebind(name, o);
        }

        public void unbind(String s) throws NamingException {
            getInitialContext(s).unbind(s);
        }

        public void unbind(Name name) throws NamingException {
            getInitialContext(name).unbind(name);
        }

        public void rename(String s, String s1) throws NamingException {
            getInitialContext(s).rename(s, s1);
        }

        public void rename(Name name, Name name1) throws NamingException {
            getInitialContext(name).rename(name, name1);
        }

        public NamingEnumeration<NameClassPair> list(String s) throws NamingException {
            return getInitialContext(s).list(s);
        }

        public NamingEnumeration<NameClassPair> list(Name name) throws NamingException {
            return getInitialContext(name).list(name);
        }

        public NamingEnumeration<Binding> listBindings(String s) throws NamingException {
            return getInitialContext(s).listBindings(s);
        }

        public NamingEnumeration<Binding> listBindings(Name name) throws NamingException {
            return getInitialContext(name).listBindings(name);
        }

        public void destroySubcontext(String s) throws NamingException {
            getInitialContext(s).destroySubcontext(s);
        }

        public void destroySubcontext(Name name) throws NamingException {
            getInitialContext(name).destroySubcontext(name);
        }

        public Context createSubcontext(String s) throws NamingException {
            return getInitialContext(s).createSubcontext(s);
        }

        public Context createSubcontext(Name name) throws NamingException {
            return getInitialContext(name).createSubcontext(name);
        }

        public Object lookupLink(String s) throws NamingException {
            return getInitialContext(s).lookupLink(s);
        }

        public Object lookupLink(Name name) throws NamingException {
            return getInitialContext(name).lookupLink(name);
        }

        public NameParser getNameParser(String s) throws NamingException {
            return getInitialContext(s).getNameParser(s);
        }

        public NameParser getNameParser(Name name) throws NamingException {
            return getInitialContext(name).getNameParser(name);
        }

        public String composeName(String s, String s1) throws NamingException {
            return getInitialContext(s).composeName(s, s1);
        }

        public Name composeName(Name name, Name name1) throws NamingException {
            return getInitialContext(name).composeName(name, name1);
        }

        public Object addToEnvironment(String s, Object o) throws NamingException {
            return getInitialContext().addToEnvironment(s, o);
        }

        public Object removeFromEnvironment(String s) throws NamingException {
            return getInitialContext().removeFromEnvironment(s);
        }

        public Hashtable<?, ?> getEnvironment() throws NamingException {
            if (isSubTenant(getCurrentCarbonContextHolder().getTenantId())) {
                throw new NamingException("Tenants cannot retrieve the environment.");
            }
            return getInitialContext().getEnvironment();
        }

        public void close() throws NamingException {
            if (isSubTenant(getCurrentCarbonContextHolder().getTenantId()) &&
                !isBaseContextRequested()) {
                throw new NamingException("Tenants cannot close the context.");
            }

            Context ctx = this.getInitialContext();
            /* the below condition is there, because of a bug in Tomcat JNDI context close method,
             * see org.apache.naming.NamingContext#close() */
            if (!ctx.getClass().getName().equals("org.apache.naming.SelectorContext")) {
                ctx.close();
            }
        }

        public String getNameInNamespace() throws NamingException {
            return getInitialContext().getNameInNamespace();
        }

        public int hashCode() {
            return initialContext.hashCode();
        }

        public boolean equals(Object o) {
            return (o instanceof CarbonInitialJNDIContext) && initialContext.equals(o);
        }

        ////////////////////////////////////////////////////////
        // Methods Required by a DirContext
        ////////////////////////////////////////////////////////

        private DirContext getDirectoryContext(Name name) throws NamingException {
            return getDirectoryContext(name.get(0));
        }

        private DirContext getDirectoryContext() throws NamingException {
            return getDirectoryContext((String) null);
        }

        private DirContext getDirectoryContext(String name) throws NamingException {
            Context initialContext = getInitialContext(name);
            if (initialContext instanceof DirContext) {
                return (DirContext) initialContext;
            }
            throw new NamingException("The given Context is not an instance of "
                                      + DirContext.class.getName());
        }

        public Attributes getAttributes(Name name) throws NamingException {
            return getDirectoryContext(name).getAttributes(name);
        }

        public Attributes getAttributes(String s) throws NamingException {
            return getDirectoryContext(s).getAttributes(s);
        }

        public Attributes getAttributes(Name name, String[] strings) throws NamingException {
            return getDirectoryContext(name).getAttributes(name, strings);
        }

        public Attributes getAttributes(String s, String[] strings)
                throws NamingException {
            return getDirectoryContext(s).getAttributes(s, strings);
        }

        public void modifyAttributes(Name name, int i, Attributes attributes)
                throws NamingException {
            getDirectoryContext(name).modifyAttributes(name, i, attributes);
        }

        public void modifyAttributes(String s, int i, Attributes attributes)
                throws NamingException {
            getDirectoryContext(s).modifyAttributes(s, i, attributes);
        }

        public void modifyAttributes(Name name, ModificationItem[] modificationItems)
                throws NamingException {
            getDirectoryContext(name).modifyAttributes(name, modificationItems);
        }

        public void modifyAttributes(String s, ModificationItem[] modificationItems)
                throws NamingException {
            getDirectoryContext(s).modifyAttributes(s, modificationItems);
        }

        public void bind(Name name, Object o, Attributes attributes) throws NamingException {
            getDirectoryContext(name).bind(name, o, attributes);
        }

        public void bind(String s, Object o, Attributes attributes) throws NamingException {
            getDirectoryContext(s).bind(s, o, attributes);
        }

        public void rebind(Name name, Object o, Attributes attributes) throws NamingException {
            getDirectoryContext(name).rebind(name, o, attributes);
        }

        public void rebind(String s, Object o, Attributes attributes) throws NamingException {
            getDirectoryContext(s).rebind(s, o, attributes);
        }

        public DirContext createSubcontext(Name name, Attributes attributes)
                throws NamingException {
            return getDirectoryContext(name).createSubcontext(name, attributes);
        }

        public DirContext createSubcontext(String s, Attributes attributes)
                throws NamingException {
            return getDirectoryContext(s).createSubcontext(s, attributes);
        }

        public DirContext getSchema(Name name) throws NamingException {
            return getDirectoryContext(name).getSchema(name);
        }

        public DirContext getSchema(String s) throws NamingException {
            return getDirectoryContext(s).getSchema(s);
        }

        public DirContext getSchemaClassDefinition(Name name) throws NamingException {
            return getDirectoryContext(name).getSchemaClassDefinition(name);
        }

        public DirContext getSchemaClassDefinition(String s) throws NamingException {
            return getDirectoryContext(s).getSchemaClassDefinition(s);
        }

        public NamingEnumeration<SearchResult> search(Name name, Attributes attributes,
                                                      String[] strings) throws NamingException {
            return getDirectoryContext(name).search(name, attributes, strings);
        }

        public NamingEnumeration<SearchResult> search(String s, Attributes attributes,
                                                      String[] strings)
                throws NamingException {
            return getDirectoryContext(s).search(s, attributes, strings);
        }

        public NamingEnumeration<SearchResult> search(Name name, Attributes attributes)
                throws NamingException {
            return getDirectoryContext(name).search(name, attributes);
        }

        public NamingEnumeration<SearchResult> search(String s, Attributes attributes)
                throws NamingException {
            return getDirectoryContext(s).search(s, attributes);
        }

        public NamingEnumeration<SearchResult> search(Name name, String filter,
                                                      SearchControls searchControls)
                throws NamingException {
            return getDirectoryContext(name).search(name, filter, searchControls);
        }

        public NamingEnumeration<SearchResult> search(String s, String filter,
                                                      SearchControls searchControls)
                throws NamingException {
            return getDirectoryContext(s).search(s, filter, searchControls);
        }

        public NamingEnumeration<SearchResult> search(Name name, String filter, Object[] objects,
                                                      SearchControls searchControls)
                throws NamingException {
            return getDirectoryContext(name).search(name, filter, objects, searchControls);
        }

        public NamingEnumeration<SearchResult> search(String s, String filter, Object[] objects,
                                                      SearchControls searchControls)
                throws NamingException {
            return getDirectoryContext(s).search(s, filter, objects, searchControls);
        }

        ////////////////////////////////////////////////////////
        // Methods Required by a LdapContext
        ////////////////////////////////////////////////////////

        private LdapContext getLdapContext() throws NamingException {
            DirContext dirContext = getDirectoryContext();
            if (dirContext instanceof EventContext) {
                return (LdapContext) dirContext;
            }
            throw new NamingException("The given Context is not an instance of "
                                      + LdapContext.class.getName());
        }

        public ExtendedResponse extendedOperation(ExtendedRequest extendedRequest)
                throws NamingException {
            return getLdapContext().extendedOperation(extendedRequest);
        }

        public LdapContext newInstance(Control[] controls) throws NamingException {
            return getLdapContext().newInstance(controls);
        }

        public void reconnect(Control[] controls) throws NamingException {
            getLdapContext().reconnect(controls);
        }

        public Control[] getConnectControls() throws NamingException {
            return getLdapContext().getConnectControls();
        }

        public void setRequestControls(Control[] controls) throws NamingException {
            getLdapContext().setRequestControls(controls);
        }

        public Control[] getRequestControls() throws NamingException {
            return getLdapContext().getRequestControls();
        }

        public Control[] getResponseControls() throws NamingException {
            return getLdapContext().getResponseControls();
        }

        ////////////////////////////////////////////////////////
        // Methods Required by a EventContext
        ////////////////////////////////////////////////////////

        private EventContext getEventContext(Name name) throws NamingException {
            return getEventContext(name.get(0));
        }

        private EventContext getEventContext() throws NamingException {
            return getEventContext((String) null);
        }

        private EventContext getEventContext(String name) throws NamingException {
            Context initialContext = getInitialContext(name);
            if (initialContext instanceof EventContext) {
                return (EventContext) initialContext;
            }
            throw new NamingException("The given Context is not an instance of "
                                      + EventContext.class.getName());
        }

        public void addNamingListener(Name name, int i, NamingListener namingListener)
                throws NamingException {
            CarbonUtils.checkSecurity();
            getEventContext(name).addNamingListener(name, i, namingListener);
        }

        public void addNamingListener(String s, int i, NamingListener namingListener)
                throws NamingException {
            CarbonUtils.checkSecurity();
            getEventContext(s).addNamingListener(s, i, namingListener);
        }

        public void removeNamingListener(NamingListener namingListener) throws NamingException {
            CarbonUtils.checkSecurity();
            getEventContext().removeNamingListener(namingListener);
        }

        public boolean targetMustExist() throws NamingException {
            return getEventContext().targetMustExist();
        }

        ////////////////////////////////////////////////////////
        // Methods Required by a EventDirContext
        ////////////////////////////////////////////////////////

        private EventDirContext getEventDirContext(Name name) throws NamingException {
            return getEventDirContext(name.get(0));
        }

        private EventDirContext getEventDirContext(String name) throws NamingException {
            EventContext eventContext = getEventContext(name);
            if (eventContext instanceof EventDirContext) {
                return (EventDirContext) eventContext;
            }
            throw new NamingException("The given Context is not an instance of "
                                      + EventDirContext.class.getName());
        }

        public void addNamingListener(Name name, String filter, SearchControls searchControls,
                                      NamingListener namingListener) throws NamingException {
            CarbonUtils.checkSecurity();
            getEventDirContext(name)
                    .addNamingListener(name, filter, searchControls, namingListener);
        }

        public void addNamingListener(String s, String filter, SearchControls searchControls,
                                      NamingListener namingListener) throws NamingException {
            CarbonUtils.checkSecurity();
            getEventDirContext(s).addNamingListener(s, filter, searchControls, namingListener);
        }

        public void addNamingListener(Name name, String filter, Object[] objects,
                                      SearchControls searchControls, NamingListener namingListener)
                throws NamingException {
            CarbonUtils.checkSecurity();
            getEventDirContext(name).addNamingListener(name, filter, objects, searchControls,
                                                       namingListener);
        }

        public void addNamingListener(String s, String filter, Object[] objects,
                                      SearchControls searchControls, NamingListener namingListener)
                throws NamingException {
            CarbonUtils.checkSecurity();
            getEventDirContext(s).addNamingListener(s, filter, objects, searchControls,
                                                    namingListener);
        }
    }

    private static class CarbonAuthenticator extends Authenticator {

        private static class AuthenticatorBean {

            private Pattern pattern;
            private PasswordAuthentication credential;

            public AuthenticatorBean(String regEx, String username, String password) {
                this.pattern = Pattern.compile(regEx);
                credential = new PasswordAuthentication(username, password.toCharArray());
            }

            public PasswordAuthentication getPasswordAuthentication() {
                return credential;
            }

            public boolean matches(String protocol, String host, int port) {
                return pattern.matcher((protocol + "://" + host + ':' + port).toLowerCase()).matches();
            }

            public boolean matches(URL url) {
                return pattern.matcher(url.toString()).matches();
            }
        }

        private List<AuthenticatorBean> proxyAuthenticators = new LinkedList<AuthenticatorBean>();
        private List<AuthenticatorBean> serverAuthenticators = new LinkedList<AuthenticatorBean>();

        public void addAuthenticator(String type, String regEx, String username, String password) {
            if (type.equalsIgnoreCase("proxy")) {
                proxyAuthenticators.add(new AuthenticatorBean(regEx, username, password));
            } else if (type.equalsIgnoreCase("server")) {
                serverAuthenticators.add(new AuthenticatorBean(regEx, username, password));
            }
        }

        protected PasswordAuthentication getPasswordAuthentication() {
            if (Authenticator.RequestorType.PROXY.equals(getRequestorType())) {
                for (AuthenticatorBean authenticator : proxyAuthenticators) {
                    if (authenticator.matches(getRequestingProtocol(), getRequestingHost(),
                                              getRequestingPort()) || authenticator.matches(getRequestingURL())) {
                        return authenticator.getPasswordAuthentication();
                    }
                }
            } else if (Authenticator.RequestorType.SERVER.equals(getRequestorType())) {
                for (AuthenticatorBean authenticator : serverAuthenticators) {
                    if (authenticator.matches(getRequestingScheme(), getRequestingHost(),
                                              getRequestingPort()) || authenticator.matches(getRequestingURL())) {
                        return authenticator.getPasswordAuthentication();
                    }
                }
            }
            return super.getPasswordAuthentication();
        }
    }

    // loads a class.
    private static Class<?> classForName(final String className)
            throws ClassNotFoundException {

        Class<?> cls = AccessController.doPrivileged(new PrivilegedAction<Class<?>>() {
            public Class<?> run() {
                // try thread context class loader first
                try {
                    return Class.forName(className, true, Thread
                            .currentThread().getContextClassLoader());
                } catch (ClassNotFoundException ignored) {
                    if (log.isDebugEnabled()) {
                        log.debug(ignored);
                    }

                }
                // try system class loader second
                try {
                    return Class.forName(className, true, ClassLoader
                            .getSystemClassLoader());
                } catch (ClassNotFoundException ignored) {
                    if (log.isDebugEnabled()) {
                        log.debug(ignored);
                    }
                }
                // return null, if fail to load class
                return null;
            }
        });

        if (cls == null) {
            throw new ClassNotFoundException("class " + className + " not found");
        }

        return cls;
    }

    /**
     * Method to obtain an instance to the Discovery Service.
     *
     * @return instance of the Discovery Service
     */
    public static DiscoveryService getDiscoveryServiceProvider() {
        return discoveryServiceProvider.get();
    }

    /**
     * Method to define the instance of the Discovery Service.
     *
     * @param discoveryServiceProvider the Discovery Service instance.
     */
    public static void setDiscoveryServiceProvider(DiscoveryService discoveryServiceProvider) {
        CarbonContextDataHolder.discoveryServiceProvider.set(discoveryServiceProvider);
    }

    /**
     * Method to obtain the current carbon context holder's base.
     *
     * @return the current carbon context holder's base.
     */
    public static CarbonContextDataHolder getCurrentCarbonContextHolderBase() {
        return currentContextHolder.get();
    }

    /**
     * Method to register a task that will be executed when a tenant is
     * unloaded.
     *
     * @param unloadTenantTask the task to run.
     * @see UnloadTenantTask
     */
    public static synchronized void registerUnloadTenantTask(
            UnloadTenantTask unloadTenantTask) {
        if (log.isDebugEnabled()) {
            log.debug("Unload Tenant Task: "
                      + unloadTenantTask.getClass().getName() + " was "
                      + "registered.");
        }
        unloadTenantTasks.add(unloadTenantTask);
    }

    /**
     * Method that will be called when a tenant is unloaded. This will run all
     * the corresponding tasks that have been registered to be called when a
     * tenant is unloaded.
     *
     * @param tenantId the tenant's identifier.
     */
    public static void unloadTenant(int tenantId) {
        log.debug("Started unloading tenant");
        for (UnloadTenantTask unloadTenantTask : unloadTenantTasks) {
            unloadTenantTask.cleanup(tenantId);
        }
        log.info("Completed unloading tenant");
    }

    /**
     * Starts a tenant flow. This will stack the current CarbonContext and begin
     * a new nested flow which can have an entirely different context. This is
     * ideal for scenarios where multiple super-tenant and sub-tenant phases are
     * required within as a single block of execution.
     */
    public void startTenantFlow() {
        Stack<CarbonContextDataHolder> carbonContextDataHolders = parentContextHolderStack.get();
        if(carbonContextDataHolders == null){
            carbonContextDataHolders = new Stack<CarbonContextDataHolder>();
            parentContextHolderStack.set(carbonContextDataHolders);
        }
        carbonContextDataHolders.push(new CarbonContextDataHolder(this));
        this.restore(null);
    }

    /**
     * This will end the tenant flow and restore the previous CarbonContext.
     */
    public void endTenantFlow() {
        Stack<CarbonContextDataHolder> carbonContextDataHolders = parentContextHolderStack.get();
        if (carbonContextDataHolders != null) {
            this.restore(carbonContextDataHolders.pop());
        }
    }

    /**
     * Default constructor to disallow creation of the CarbonContext.
     */
    private CarbonContextDataHolder() {
        this.tenantId = org.wso2.carbon.base.MultitenantConstants.INVALID_TENANT_ID;
        this.username = null;
        this.tenantDomain = null;
    }

    /**
     * Constructor that can be used to create clones.
     *
     * @param carbonContextHolder the CarbonContext holder instance of which the clone will be
     *                            created from.
     */
    public CarbonContextDataHolder(CarbonContextDataHolder carbonContextHolder) {
        this.tenantId = carbonContextHolder.tenantId;
        this.username = carbonContextHolder.username;
        this.tenantDomain = carbonContextHolder.tenantDomain;
        if (carbonContextHolder.properties != null) {
            this.properties = new HashMap<String, Object>(carbonContextHolder.properties);
        }
    }

    /**
     * Method to obtain the tenant id on this CarbonContext instance.
     *
     * @return the tenant id.
     */
    public int getTenantId() {
        return tenantId;
    }

    /**
     * Method to set the tenant id on this CarbonContext instance.
     *
     * @param tenantId the tenant id.
     */
    public void setTenantId(int tenantId) {
        try {
            if (this.tenantId == org.wso2.carbon.base.MultitenantConstants.INVALID_TENANT_ID ||
                this.tenantId == org.wso2.carbon.base.MultitenantConstants.SUPER_TENANT_ID) {
                this.tenantId = tenantId;
            } else if (this.tenantId != tenantId) {
                StackTraceElement[] traces = Thread.currentThread().getStackTrace();
                if (!isAllowedToChangeTenantDomain(traces)) {
                    throw new IllegalStateException("Trying to set the domain from " + this.tenantId + " to " + tenantId);
                }
            }
        } catch (IllegalStateException e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * Method to obtain the username on this CarbonContext instance.
     *
     * @return the username.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Method to set the username on this CarbonContext instance.
     *
     * @param username the username.
     */
    public void setUsername(String username) {
        CarbonUtils.checkSecurity();
        this.username = username;
    }

    /**
     * Method to obtain the tenant domain on this CarbonContext instance.
     *
     * @return the tenant domain.
     */
    public String getTenantDomain() {
        return tenantDomain;
    }

    /**
     * Method to set the tenant domain on this CarbonContext instance.
     *
     * @param domain the tenant domain.
     */
    public void setTenantDomain(String domain) {
        try {
            if (this.tenantDomain == null ||
                this.tenantDomain.equals(org.wso2.carbon.base.MultitenantConstants.SUPER_TENANT_DOMAIN_NAME)) {
                this.tenantDomain = domain;
            } else if (!tenantDomain.equals(domain)) {
                StackTraceElement[] traces = Thread.currentThread().getStackTrace();
                if (!isAllowedToChangeTenantDomain(traces)) {
                    throw new IllegalStateException("Trying to set the domain from " + this.tenantDomain + " to " + domain);
                }
            }
        } catch (IllegalStateException e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * Method to obtain a property on this CarbonContext instance.
     *
     * @param name the property name.
     * @return the value of the property by the given name.
     */
    public Object getProperty(String name) {
        if (properties == null) {
            return null;
        }
        return properties.get(name);
    }

    /**
     * Method to set a property on this CarbonContext instance.
     *
     * @param name  the property name.
     * @param value the value to be set to the property by the given name.
     */
    public void setProperty(String name, Object value) {
        if (properties == null) {
            properties = new HashMap<String, Object>();
        }
        properties.put(name, value);
    }

    // Method to cleanup all properties.
    private void cleanupProperties() {
        // This method would be called to reclaim memory. Therefore, this might
        // be called on an
        // object which has been partially garbage collected. Even unlikely, it
        // might be possible
        // that the object exists without any field-references, until all
        // WeakReferences are
        // cleaned-up.
        if (properties != null) {
            properties.clear();
        }
    }

    private boolean isAllowedToChangeTenantDomain(StackTraceElement[] traces) {
        boolean allowChange = false;
        if ((traces.length > CARBON_AUTHENTICATION_UTIL_INDEX) &&
            (traces[CARBON_AUTHENTICATION_UTIL_INDEX].getClassName().equals(CARBON_AUTHENTICATION_UTIL_CLASS))) {
            allowChange = true;
        } else if ((traces.length > CARBON_AUTHENTICATION_HANDLER_INDEX) &&
                   traces[CARBON_AUTHENTICATION_HANDLER_INDEX].getClassName().equals(CARBON_AUTHENTICATION_HANDLER_CLASS)) {
            allowChange = true;
        }
        return allowChange;
    }

    /**
     * This method will destroy the current CarbonContext holder.
     */
    public static void destroyCurrentCarbonContextHolder() {
        currentContextHolder.remove();
        parentContextHolderStack.remove();
    }

    // Utility method to restore a CarbonContext.
    private void restore(CarbonContextDataHolder carbonContextHolder) {
        if (carbonContextHolder != null) {
            this.tenantId = carbonContextHolder.tenantId;
            this.username = carbonContextHolder.username;
            this.tenantDomain = carbonContextHolder.tenantDomain;
            this.applicationName = carbonContextHolder.applicationName;
            if (carbonContextHolder.properties != null) {
                this.properties = new HashMap<String, Object>(carbonContextHolder.properties);
            }
        } else {
            this.tenantId = org.wso2.carbon.base.MultitenantConstants.INVALID_TENANT_ID;
            this.username = null;
            this.tenantDomain = null;
            this.properties = null;
            this.applicationName = null;
        }
    }

    private static class CarbonContextCleanupTask implements
                                                  UnloadTenantTask<CarbonContextDataHolder> {

        private Map<Integer, ArrayList<WeakReference<CarbonContextDataHolder>>> contextHolderList =
                new ConcurrentHashMap<Integer, ArrayList<WeakReference<CarbonContextDataHolder>>>();

        public void register(int tenantId,
                             CarbonContextDataHolder contextHolderBase) {
            ArrayList<WeakReference<CarbonContextDataHolder>> list = contextHolderList
                    .get(tenantId);
            if (list == null) {
                list = new ArrayList<WeakReference<CarbonContextDataHolder>>();
                list.add(new WeakReference<CarbonContextDataHolder>(
                        contextHolderBase));
                contextHolderList.put(tenantId, list);
            } else {
                list.add(new WeakReference<CarbonContextDataHolder>(
                        contextHolderBase));
            }
        }

        public void cleanup(int tenantId) {
            ArrayList<WeakReference<CarbonContextDataHolder>> list = contextHolderList
                    .remove(tenantId);
            if (list != null) {
                for (WeakReference<CarbonContextDataHolder> carbonContextHolderBaseRef : list) {
                    CarbonContextDataHolder carbonContextHolderBase =
                            carbonContextHolderBaseRef.get();
                    if (carbonContextHolderBase != null) {
                        carbonContextHolderBase.cleanupProperties();
                    }
                }
                list.clear();
            }
        }
    }
}

