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
package org.wso2.carbon.application.deployer.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.axiom.om.OMElement;
import org.apache.axis2.engine.AxisConfiguration;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.wso2.carbon.application.deployer.config.ApplicationConfiguration;
import org.wso2.carbon.application.deployer.config.Artifact;
import org.wso2.carbon.application.deployer.persistence.CarbonAppPersistenceManager;
import org.wso2.carbon.application.deployer.handler.*;
import org.wso2.carbon.application.deployer.CarbonApplication;
import org.wso2.carbon.application.deployer.AppDeployerUtils;
import org.wso2.carbon.application.deployer.service.ApplicationManagerService;
import org.wso2.carbon.CarbonException;
import org.wso2.carbon.utils.CarbonUtils;

import javax.xml.stream.XMLStreamException;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Manages all cApp deployment aspects. Carbon App deployment is done in two steps. A cApp is
 * basically a p2 repo which contains artifacts as features.
 *
 * Step 1 : Find out the artifacts to be installed in this server and install their features
 *          through p2
 * Step 2 : After needed features are installed, complete the cApp deployment by sending the cApp
 *          through a chain of handlers
 *
 * This handler chain can contain different cApp artifact deployers. Those can be registered or
 * unregistered using this ApplicationManager API.
 */
public final class ApplicationManager implements ApplicationManagerService {

    private static final Log log = LogFactory.getLog(ApplicationManager.class);

    private static ApplicationManager instance = new ApplicationManager();

    private List<AppDeploymentHandler> appDeploymentHandlers;
    private List<PendingApplication> pendingCarbonApps;

    private Map<String, ArrayList<CarbonApplication>> tenantcAppMap;
    private Map<String, CarbonAppPersistenceManager> tenantPMMap;

    private int initialHandlers;
    private int handlerCount;

    //provisioning actions can't be performed paralelly.. this lock is used for those actions
//    private final ReentrantLock lock = new ReentrantLock();


    /**
     * Constructor initializes public instances and finds the initial handlers
     */
    private ApplicationManager() {
        tenantcAppMap = new HashMap<String, ArrayList<CarbonApplication>>();
        tenantPMMap = new HashMap<String, CarbonAppPersistenceManager>();

        appDeploymentHandlers = new ArrayList<AppDeploymentHandler>();
        pendingCarbonApps = new ArrayList<PendingApplication>();

        // set the initial handler counter. default handler and registry handler are always there
        initialHandlers = 2 + findInitialHandlerCount();
    }

    /**
     * ApplicationManager is singleton. This can be used to access the instance.
     * @return - ApplicationManager instance
     */
    public static ApplicationManager getInstance() {
        return instance;
    }

    /**
     * All app deployers register their deployers throgh this method
     * @param handler - app deployer which implements the AppDeploymentHandler interface
     */
    public synchronized void registerDeploymentHandler(AppDeploymentHandler handler) {
        appDeploymentHandlers.add(handler);
        handlerCount++;

        if (handlerCount == initialHandlers) {
            //if we have cApps waiting to be deployed, deploy those as well
            for (PendingApplication application : pendingCarbonApps) {
                try {
                    this.deployCarbonApp(application.getPath(), application.getAxisConfig());
                } catch (Exception e) {
                    log.error("Error while deploying stored cApp : " + application, e);
                }
            }
            pendingCarbonApps.clear();
        }
    }

    /**
     * Unregister the specified handler if it is already regitered
     * @param handler - input deployer handler
     */
    public synchronized void unregisterDeploymentHandler(AppDeploymentHandler handler) {
        if (appDeploymentHandlers.contains(handler)) {
            appDeploymentHandlers.remove(handler);
        }
    }

    /**
     * Deploy a sigle app by iterating all handlers and sending apps throgh the handler
     * chain..
     * @param archPath - carbon app archive path
     * @param axisConfig - AxisConfiguration of the current tenant
     * @throws Exception - error on registry actions
     */
    public synchronized void deployCarbonApp(String archPath, AxisConfiguration
            axisConfig) throws Exception {
        //if all handlers are not yet registered, we store the cApp to deploy later
        if (initialHandlers != handlerCount) {
            pendingCarbonApps.add(new PendingApplication(archPath, axisConfig));
            return;
        }

        CarbonAppPersistenceManager capm = getPersistenceManager(axisConfig);
        String tenantId = AppDeployerUtils.getTenantIdString(axisConfig);
        String archPathToProcess = AppDeployerUtils.formatPath(archPath);
        String fileName = archPathToProcess.substring(archPathToProcess.lastIndexOf('/') + 1);
        //check whether this app already exists..
        CarbonApplication existingApp = null;
        for (CarbonApplication carbonApp : getCarbonApps(tenantId)) {
            if (archPathToProcess.equals(carbonApp.getAppFilePath())) {
                existingApp = carbonApp;
                break;
            }
        }

        //If the app already exists, check the last updated time and redeploy if needed.
        //Return if not updated..
        if (existingApp != null) {
            File file = new File(archPathToProcess);
            if (file.exists()) {
                String hashValue = CarbonUtils.getMD5(CarbonUtils.getBytesFromFile(file));
                String hashValueFromRegistry = capm.getHashValue(existingApp.getAppName());
                if (hashValueFromRegistry != null && hashValueFromRegistry.equals(hashValue)) {
                    existingApp.setDeploymentCompleted(true);
                    return;
                } else {
                    // we are going to do an update for the application
                    log.warn("Carbon Application : " + fileName + " has been updated. Removing" +
                            " the existing application and redeploying...");
                    // undeploy the existing one before proceeding
                    undeployCarbonApp(existingApp, axisConfig);
                }
            }
        }

        log.info("Deploying Carbon Application : " + fileName + "...");

        CarbonApplication currentApp = new CarbonApplication();
        currentApp.setAppFilePath(archPathToProcess);
        String extractedPath = AppDeployerUtils.extractCarbonApp(archPathToProcess);

        // Build the app configuration by providing the artifacts.xml path
        ApplicationConfiguration appConfig = new ApplicationConfiguration(capm, extractedPath +
                ApplicationConfiguration.ARTIFACTS_XML);

        // If we don't have features (artifacts) for this server, ignore
        if (appConfig.getApplicationArtifact().getDependencies().size() == 0) {
            log.warn("No artifacts found to be deployed in this server. " +
                    "Ignoring Carbon Application : " + fileName);
            return;
        }

        currentApp.setExtractedPath(extractedPath);
        currentApp.setAppConfig(appConfig);

        String appName = appConfig.getAppName();
        if (appName == null) {
            log.warn("No application name found in Carbon Application : " + fileName + ". Using " +
                    "the file name as the application name");
            appName = fileName.substring(0, fileName.lastIndexOf('.'));
        }

        if (appExists(appName, axisConfig)) {
            String msg = "Carbon Application : " + appName + " already exists. Two applications " +
                    "can't have the same Id. Deployment aborted.";
            log.error(msg);
            throw new Exception(msg);
        }

        currentApp.setAppName(appName);
        // Set App Version
        String appVersion = appConfig.getAppVersion();
        if (appVersion != null && !("").equals(appVersion)) {
            currentApp.setAppVersion(appVersion);
        }

//        lock.lock();
//        try {
//            installArtifactFeatures(currentApp);
//        } catch (Exception e) {
//            handleException("Failed to installed features for cApp : " + appName, e);
//        } finally {
//            lock.unlock();
//        }

        // deploy sub artifacts of this cApp
        this.searchArtifacts(currentApp.getExtractedPath(), currentApp);

        // If all dependencies are resolved, we deploy the entire app
        if (isArtifactReadyToDeploy(currentApp.getAppConfig().getApplicationArtifact())) {
            // send the CarbonApplication instance through the handler chain
            for (AppDeploymentHandler handler : appDeploymentHandlers) {
                handler.deployArtifacts(currentApp, axisConfig);
            }
            // persist the completed cApp into registry..
            capm.persistCarbonApp(currentApp);

        } else {
            log.error("Some dependencies in cApp : " + appName + " were not satisfied. Check " +
                    "whether all dependent artifacts are included in cApp file : " + archPathToProcess);
            return;
        }

        currentApp.setDeploymentCompleted(true);
        this.addCarbonApp(tenantId, currentApp);
        log.info("Successfully Deployed Carbon Application : " + currentApp.getAppName()
                + AppDeployerUtils.getTenantIdLogString(AppDeployerUtils.getTenantId(axisConfig)));
    }

    /**
     * Deploys all artifacts under a root artifact..
     *
     * @param rootDirPath - root dir of the extracted artifact
     * @param parentApp - capp instance
     * @throws org.wso2.carbon.CarbonException - on error
     */
    private void searchArtifacts(String rootDirPath,
                                 CarbonApplication parentApp) throws CarbonException {
        File extractedDir = new File(rootDirPath);
        File[] allFiles = extractedDir.listFiles();
        if (allFiles == null) {
            return;
        }

        // list to keep all artifacts
        List<Artifact> allArtifacts = new ArrayList<Artifact>();

        // search for all directories under the extracted path
        for (File artifactDirectory : allFiles) {
            if (!artifactDirectory.isDirectory()) {
                continue;
            }

            String directoryPath = AppDeployerUtils.formatPath(artifactDirectory.getAbsolutePath());
            String artifactXmlPath =  directoryPath + File.separator + Artifact.ARTIFACT_XML;

            File f = new File(artifactXmlPath);
            // if the artifact.xml not found, ignore this dir
            if (!f.exists()) {
                continue;
            }

            Artifact artifact = null;
            InputStream xmlInputStream = null;
            try {
                xmlInputStream = new FileInputStream(f);
                artifact = this.buildAppArtifact(parentApp, xmlInputStream);
            } catch (FileNotFoundException e) {
                handleException("artifacts.xml File cannot be loaded from " + artifactXmlPath, e);
            } finally {
                if (xmlInputStream != null) {
                    try {
                        xmlInputStream.close();
                    } catch (IOException e) {
                        log.error("Error while closing input stream.", e);
                    }
                }
            }

            if (artifact == null) {
                return;
            }
            artifact.setExtractedPath(directoryPath);
//            searchArtifacts(directoryPath, parentApp);
            allArtifacts.add(artifact);
        }
        Artifact appArtifact = parentApp.getAppConfig().getApplicationArtifact();
        buildDependencyTree(appArtifact, allArtifacts);
    }

    /**
     * Builds the artifact from the given input steam and adds it as a dependency in the provided
     * parent carbon application.
     *
     * @param parentApp - parent application
     * @param artifactXmlStream - xml input stream of the artifact.xml
     * @return - Artifact instance if successfull. otherwise null..
     * @throws CarbonException - error while building
     */
    public Artifact buildAppArtifact(CarbonApplication parentApp, InputStream artifactXmlStream)
            throws CarbonException {
        Artifact artifact = null;
        try {
            OMElement artElement = new StAXOMBuilder(artifactXmlStream).getDocumentElement();

            if (Artifact.ARTIFACT.equals(artElement.getLocalName())) {
                artifact = AppDeployerUtils.populateArtifact(artElement);
            } else {
                log.error("artifact.xml is invalid. Parent Application : "
                        + parentApp.getAppName());
                return null;
            }
        } catch (XMLStreamException e) {
            handleException("Error while parsing the artifact.xml file ", e);
        }

        if (artifact == null || artifact.getName() == null) {
            log.error("Invalid artifact found in Carbon Application : " + parentApp.getAppName());
            return null;
        }
        return artifact;
    }

    /**
     * Undeploy the provided carbon App by sending it through the registered undeployment handler
     * chain..
     * @param carbonApp - CarbonApplication instance
     * @param axisConfig - AxisConfiguration of the current tenant
     */
    public synchronized void undeployCarbonApp(CarbonApplication carbonApp,
                                               AxisConfiguration axisConfig) {
        log.info("Undeploying Carbon Application : " + carbonApp.getAppName() + "...");
        // Call the undeployer handler chain
        for (AppDeploymentHandler handler : appDeploymentHandlers) {
            handler.undeployArtifacts(carbonApp, axisConfig);
        }
        // Remove the app from tenant cApp list
        removeCarbonApp(AppDeployerUtils.getTenantIdString(axisConfig), carbonApp);

        // Remove the app from registry
        try {
            CarbonAppPersistenceManager capm = getPersistenceManager(axisConfig);
            capm.deleteApplication(carbonApp.getAppName());
        } catch (Exception e) {
            log.error("Can't delete the Application from Registry : " + carbonApp.getAppName());
        }

        // Uninstall features
//        lock.lock();
//        try {
//            removeExistingFeatures(carbonApp.getAppConfig().getFeaturesForCurrentServer());
//        } catch (Exception e) {
//            log.error("Error while uninstalling features for cApp : " + carbonApp.getAppName(), e);
//        } finally {
//            lock.unlock();
//        }
        log.info("Successfully Undeployed Carbon Application : " + carbonApp.getAppName()
                + AppDeployerUtils.getTenantIdLogString(AppDeployerUtils.getTenantId(axisConfig)));
        if (AppDeployerUtils.hasLibs(carbonApp.getAppConfig().
                getApplicationArtifact().getDependencies())) {
            log.warn("Your Carbon Application has installed OSGi bundles into the system. It " +
                    "is recommended to restart the server to completely remove those..");
        }
    }

    /**
     * Get the list of CarbonApplications for the give tenant id. If the list is null, return
     * an empty ArrayList
     *
     * @param tenantId - tenant id to find cApps
     * @return - list of tenant cApps
     */
    public ArrayList<CarbonApplication> getCarbonApps(String tenantId) {
        ArrayList<CarbonApplication> cApps = tenantcAppMap.get(tenantId);
        if (cApps == null) {
            cApps = new ArrayList<CarbonApplication>();
        }
        return cApps;
    }

    /**
     * Add a new cApp for a particular tenant. If there are no cApps currently, create a new
     * ArrayList and add the new cApp.
     *
     * @param tenantId - tenant id of the cApp
     * @param carbonApp - CarbonApplication instance
     */
    public void addCarbonApp(String tenantId, CarbonApplication carbonApp) {
        ArrayList<CarbonApplication> cApps = tenantcAppMap.get(tenantId);
        if (cApps == null) {
            cApps = new ArrayList<CarbonApplication>();
            tenantcAppMap.put(tenantId, cApps);
        }
        // don't add the cApp if it already exists
        for (CarbonApplication cApp : cApps) {
            String appName = cApp.getAppName();
            String appVersion = cApp.getAppVersion();
            if (appName != null && appName.equals(carbonApp.getAppName()) &&
                    appVersion != null && appVersion.equals(carbonApp.getAppVersion())) {
                return;
            }
        }
        cApps.add(carbonApp);
    }

    /**
     * Remove a cApp for a particular tenant
     *
     * @param tenantId - tenant id of the cApp
     * @param carbonApp - CarbonApplication instance
     */
    public void removeCarbonApp(String tenantId, CarbonApplication carbonApp) {
        ArrayList<CarbonApplication> cApps = tenantcAppMap.get(tenantId);
        if (cApps != null && cApps.contains(carbonApp)) {
            cApps.remove(carbonApp);
        }
    }

    /**
     * Checks whether the given cApp artifact is complete with all it's dependencies. Recursively
     * checks all it's dependent artifacts as well..
     *
     * @param rootArtifact - artifact to check
     * @return true if ready, else false
     */
    private boolean isArtifactReadyToDeploy(Artifact rootArtifact) {
        if (rootArtifact == null) {
            return false;
        }
        boolean isReady = true;
        for (Artifact.Dependency dep : rootArtifact.getDependencies()) {
            isReady = isArtifactReadyToDeploy(dep.getArtifact());
            if (!isReady) {
                return false;
            }
        }
        if (rootArtifact.unresolvedDepCount > 0) {
            isReady = false;
        }
        return isReady;
    }

    /**
     * If the given artifact is a dependent artifact for the rootArtifact, include it as
     * the actual dependency. The existing one is a dummy one. So remove it. Do this recursively
     * for the dependent artifacts as well..
     *
     * @param rootArtifact - root to start search
     * @param allArtifacts - all artifacts found under current cApp
     */
    public void buildDependencyTree(Artifact rootArtifact, List<Artifact> allArtifacts) {
        for (Artifact.Dependency dep : rootArtifact.getDependencies()) {
            for (Artifact temp : allArtifacts) {
                if (dep.getName().equals(temp.getName())) {
                    String depVersion = dep.getVersion();
                    String attVersion = temp.getVersion();
                    if ((depVersion == null && attVersion == null) ||
                            (depVersion != null && depVersion.equals(attVersion))) {
                        dep.setArtifact(temp);
                        rootArtifact.unresolvedDepCount--;
                        break;
                    }
                }
            }

            // if we've found the dependency, check for it's dependencies as well..
            if (dep.getArtifact() != null) {
                buildDependencyTree(dep.getArtifact(), allArtifacts);
            }
        }
    }

    /**
     * Retuns the persistence manger for the given axis config (tenant). First try to find it
     * in the PM map. If it is not there, create a new one.
     *
     * @param axisConfig - AxisConfiguration of the current tenant
     * @return - CarbonAppPersistenceManager for the current tenant
     */
    public CarbonAppPersistenceManager getPersistenceManager(AxisConfiguration axisConfig) {
        String tenantId = AppDeployerUtils.getTenantIdString(axisConfig);
        CarbonAppPersistenceManager capm = tenantPMMap.get(tenantId);
        try {
            if (capm == null) {
                capm = new CarbonAppPersistenceManager(axisConfig);
                tenantPMMap.put(tenantId, capm);
            }
        } catch (Exception e) {
            log.error("Error while initializing CAppPersistenceManger for tenant : " + tenantId, e);
        }
        return capm;
    }

    /**
     * Installs all artifact features in the given Application. Features are found in the p2-repo
     * which is inside the Carbon application.
     * 1. adding the repo
     * 2. convert feature id list to iu's
     * 3. review
     * 4. install
     *
     * @param carbonApp - application instance to perform on
     * @throws org.wso2.carbon.CarbonException - error on feature installation
     */
//    private void installArtifactFeatures(CarbonApplication carbonApp) throws Exception {
//        List<ApplicationConfiguration.Feature> features = carbonApp.
//                getAppConfig().getFeaturesForCurrentServer();
//        if (features.size() == 0) {
//            return;
//        }
//        // add the repository
//        String repoPath = carbonApp.getExtractedPath() + CarbonApplication.P2_REPO;
//        repoPath = AppDeployerUtils.formatPath(repoPath);
//
//        if (repoPath.startsWith("/")) {
//            // on linux
//            repoPath = "file://" + repoPath;
//        } else {
//            // on windows
//            repoPath = "file:///" + repoPath;
//        }
//        URI repoUrl = new URI(repoPath);
//        RepositoryUtils.addRepository(repoUrl, carbonApp.getAppName() +
//                System.currentTimeMillis());
//
//        IInstallableUnit[] units = new IInstallableUnit[features.size()];
//
//        for (int i = 0 ; i < features.size() ; i++) {
//            ApplicationConfiguration.Feature f = features.get(i);
//            InstallableUnitQuery query = new InstallableUnitQuery(f.getId(),
//                    Version.create(f.getVersion()));
//
//            Collector collector = RepositoryUtils.getInstallableUnitsInRepositories(repoUrl,
//                    query, new Collector(), null);
//            units[i] = getInstalledFeatureInfo(collector);
//        }
//        ProfileModificationAction profModificationAction =
//                ProfileModificationActionFactory.getProfileModificationAction(
//                        ProfileModificationActionFactory.INSTALL_ACTION);
//        profModificationAction.setIusToInstall(units);
//
//        ResolutionResult rr = ProvisioningUtils
//                .reviewProvisioningAction(profModificationAction);
//        ProvisioningUtils.performProvisioningAction(rr, true);
//
//        RepositoryUtils.removeRepository(repoUrl);
//    }

    /**
     * Uninstalls the given set of features from the system..
     *
     * @param features - features to uninstall
     * @throws org.wso2.carbon.CarbonException - error on uninstalling features
     */
//    private void removeExistingFeatures(List<ApplicationConfiguration.Feature> features)
//            throws Exception {
//        List<IInstallableUnit> featuresToRemove = new ArrayList<IInstallableUnit>();
//        for (ApplicationConfiguration.Feature f : features) {
//            QueryContext queryContext = new QueryContext();
//            queryContext.setQueryable(ProvisioningUtils.getProfile());
//            queryContext.setQuery(new InstallableUnitQuery(f.getId(),
//                    Version.create(f.getVersion())));
//            InstalledIUQuery installedIUQuery = new InstalledIUQuery(queryContext);
//            IInstallableUnit[] installableUnits = ProvisioningUtils.
//                    performIUQuery(installedIUQuery);
//            if(installableUnits != null && installableUnits.length > 0){
//                featuresToRemove.add(installableUnits[0]);
//            }
//        }
//
//        // if there are no feautures already installed, return
//        if (featuresToRemove.size() == 0) {
//            return;
//        }
//
//        IInstallableUnit[] unitsToRemove = new IInstallableUnit[featuresToRemove.size()];
//        unitsToRemove = featuresToRemove.toArray(unitsToRemove);
//        ProfileModificationAction profModificationAction =
//                ProfileModificationActionFactory.getProfileModificationAction(
//                        ProfileModificationActionFactory.UNINSTALL_ACTION);
//        profModificationAction.setIusToUninstall(unitsToRemove);
//        ResolutionResult rr = ProvisioningUtils
//                .reviewProvisioningAction(profModificationAction);
//        ProvisioningUtils.performProvisioningAction(rr, true);
//    }

//    private IInstallableUnit getInstalledFeatureInfo(Collector collector) {
//        IInstallableUnit iu = null;
//        try {
//            IInstallableUnit[] installableUnits = (IInstallableUnit[])collector.
//                    toArray(IInstallableUnit.class);
//            if(installableUnits == null || installableUnits.length == 0){
//                log.error("Error occured while quering feature information");
//            }
//            if (installableUnits != null) {
//                iu = installableUnits[0];
//            }
//        } catch (Exception e) {
//            log.error("Error occured while quering feature information", e);
//        }
//        return iu;
//    }

    /**
     * Finds the number of app deployers at the carbon startup
     * @return - number of app deployers
     */
    private int findInitialHandlerCount() {
        int handlers = 0;
        BundleContext bc = AppDeployerServiceComponent.getBundleContext();
        for (Bundle b : bc.getBundles()) {
            if (AppDeployerUtils.isAppDeployer(b)) {
                handlers++;
            }
        }
        return handlers;
    }

    /**
     * Check whether there is an already existing Carbon application with the given name.
     *
     * @param newAppName - name of the new app
     * @param axisConfig - AxisConfiguration instance
     * @return - true if exits
     */
    private boolean appExists(String newAppName, AxisConfiguration axisConfig) {
        String tenantId = AppDeployerUtils.getTenantIdString(axisConfig);
        CarbonApplication appToRemove = null;
        for (CarbonApplication carbonApp : getCarbonApps(tenantId)) {
            if (newAppName.equals(carbonApp.getAppName())) {
                if (carbonApp.isDeploymentCompleted()) {
                    return true;
                } else {
                    appToRemove = carbonApp;
                    break;
                }
            }
        }
        if (appToRemove != null) {
            undeployCarbonApp(appToRemove, axisConfig);
        }
        return false;
    }

    private void handleException(String msg, Exception e) throws CarbonException {
        log.error(msg, e);
        throw new CarbonException(msg, e);
    }

    /**
     * A private class to hold pending cApps to be deployed
     */
    private final class PendingApplication {

        private String path;
        private AxisConfiguration axisConfig;

        private PendingApplication(String path, AxisConfiguration configCtx) {
            this.path = path;
            this.axisConfig = configCtx;
        }

        public String getPath() {
            return path;
        }

        public AxisConfiguration getAxisConfig() {
            return axisConfig;
        }
    }
    
}
