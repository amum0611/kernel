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
package org.wso2.carbon.application.deployer.persistence;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.axis2.engine.AxisConfiguration;
import org.wso2.carbon.CarbonException;
import org.wso2.carbon.CarbonConstants;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.context.RegistryType;
import org.wso2.carbon.application.deployer.AppDeployerConstants;
import org.wso2.carbon.application.deployer.internal.ApplicationManager;
import org.wso2.carbon.application.deployer.internal.AppDeployerServiceComponent;
import org.wso2.carbon.application.deployer.CarbonApplication;
import org.wso2.carbon.application.deployer.AppDeployerUtils;
import org.wso2.carbon.application.deployer.config.ApplicationConfiguration;
import org.wso2.carbon.application.deployer.config.Artifact;
import org.wso2.carbon.application.deployer.config.RegistryConfig;
import org.wso2.carbon.registry.core.Collection;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.session.UserRegistry;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.utils.MediaTypesUtils;
import org.wso2.carbon.registry.synchronization.RegistrySynchronizer;
import org.wso2.carbon.roles.mgt.ServerRoleConstants;
import org.wso2.carbon.roles.mgt.ServerRoleUtils;
import org.wso2.carbon.utils.CarbonUtils;

import java.io.*;
import java.util.List;
import java.util.ArrayList;
import java.util.zip.ZipInputStream;


public class CarbonAppPersistenceManager {

    private AxisConfiguration axisConfig;
    private Registry localRegistry;
    private Registry configRegistry;
    private Registry governanceRegistry;
    private Registry rootRegistry;

    private static final Log log = LogFactory.getLog(CarbonAppPersistenceManager.class);

    public CarbonAppPersistenceManager(AxisConfiguration axisConfig) throws CarbonException {
        this.axisConfig = axisConfig;
        try {
            PrivilegedCarbonContext carbonCtx = PrivilegedCarbonContext.getCurrentContext(axisConfig);
            localRegistry = (Registry) carbonCtx.getRegistry(RegistryType.LOCAL_REPOSITORY);
            configRegistry = (Registry) carbonCtx.getRegistry(RegistryType.SYSTEM_CONFIGURATION);
            governanceRegistry = (Registry) carbonCtx.getRegistry(RegistryType.SYSTEM_GOVERNANCE);

            // get the root registry for the current tenant from RegistryService
            rootRegistry = AppDeployerServiceComponent.getRegistryService().getRegistry(
                    CarbonConstants.REGISTRY_SYSTEM_USERNAME, carbonCtx.getTenantId());
        } catch (Exception e) {
            log.error("Error while retrieving config registry from Axis configuration", e);
        }
        if (configRegistry == null) {
            throw new CarbonException("Configuration Registry is not available");
        }
    }

    /**
     * Load all existing Applications. For each application found, artifacts.xml file is read to
     * create the application config and them artifact.xml files of all individual artifacts are
     * also read and added to the configuration.
     *
     * @throws Exception - on registry transaction error
     */
    public void loadApps() throws Exception {
        try {
            //check whether applications are empty
            if (!configRegistry.resourceExists(AppDeployerConstants.APPLICATIONS)) {
                return;
            }

            Collection allApps = (Collection) configRegistry.get(AppDeployerConstants.APPLICATIONS);
            String[] appPaths = allApps.getChildren();
            ApplicationManager appManager = ApplicationManager.getInstance();
            String tenantId = AppDeployerUtils.getTenantIdString(axisConfig);

            for (String currentAppPath : appPaths) {
                CarbonApplication carbonApp = new CarbonApplication();

                //get application collection
                Collection currentAppCollection = (Collection) configRegistry.get(currentAppPath);
                carbonApp.setAppName(currentAppCollection.getProperty(AppDeployerConstants.NAME));
                carbonApp.setAppFilePath(currentAppCollection.
                        getProperty(AppDeployerConstants.APP_FILE_PATH));
                String appVersion = currentAppCollection.
                        getProperty(AppDeployerConstants.APP_VERSION);
                if (appVersion != null) {
                    carbonApp.setAppVersion(appVersion);
                }

                //read the artifacts.xml and construct the ApplicationConfiguration
                String artifactsXmlPath = currentAppPath + AppDeployerConstants.APP_ARTIFACTS_XML;
                if (configRegistry.resourceExists(artifactsXmlPath)) {
                    Resource artifactXmlResource = configRegistry.get(artifactsXmlPath);
                    InputStream artifactsXmlStream = artifactXmlResource.getContentStream();
                    if (artifactsXmlStream != null) {
                        ApplicationConfiguration appConfig =
                                new ApplicationConfiguration(this, artifactsXmlStream);
                        carbonApp.setAppConfig(appConfig);
                    }
                }

                String dependencyPath = currentAppPath + AppDeployerConstants.APP_DEPENDENCIES;
                // list to keep all artifacts
                List<Artifact> allArtifacts = new ArrayList<Artifact>();
                if (configRegistry.resourceExists(dependencyPath)) {
                    Collection dependencies = (Collection) configRegistry.get(dependencyPath);

                    for (String depPath : dependencies.getChildren()) {
                        Resource artifactResource = configRegistry.get(depPath +
                                AppDeployerConstants.ARTIFACT_XML);
                        InputStream xmlStream = artifactResource.getContentStream();
                        Artifact artifact = null;
                        if (xmlStream != null) {
                            artifact = appManager.buildAppArtifact(carbonApp, xmlStream);
                        }
                        if (artifact != null) {
                            Collection artCollection = (Collection) configRegistry.get(depPath);
                            artifact.setRuntimeObjectName(artCollection
                                    .getProperty(AppDeployerConstants.RUNTIME_OBJECT_NAME));
                            allArtifacts.add(artifact);
                        }
                    }
                }
                Artifact appArtifact = carbonApp.getAppConfig().getApplicationArtifact();
                appManager.buildDependencyTree(appArtifact, allArtifacts);
                appManager.addCarbonApp(tenantId, carbonApp);
            }
        } catch (RegistryException e) {
            String msg = "Unable to load Applications. Registry transactions failed.";
            log.error(msg, e);
            throw new Exception(msg, e);
        }
    }

    /**
     * Persist the given Carbon app in the registry. Basically we persist the artifacts.xml file
     * in the registry..
     *
     * @param carbonApp - CarbonApplication instance
     * @throws Exception - on registry transaction error
     */
    public void persistCarbonApp(CarbonApplication carbonApp) throws Exception {
        try {
            //if there is an already existing app in the configRegistry, delete it..
            deleteApplication(carbonApp.getAppName());

            String appResourcePath = AppDeployerConstants.APPLICATIONS + carbonApp.getAppName();

            Collection app = configRegistry.newCollection();
            app.setProperty(AppDeployerConstants.NAME, carbonApp.getAppName());
            app.setProperty(AppDeployerConstants.APP_FILE_PATH, carbonApp.getAppFilePath());

            String appVersion = carbonApp.getAppVersion();
            if (appVersion != null) {
                app.setProperty(AppDeployerConstants.APP_VERSION, carbonApp.getAppVersion());
            }

            //set last updated time as a property
            File appFile = new File(carbonApp.getAppFilePath());
            if (appFile.exists()) {
                String hashValue = CarbonUtils.getMD5(CarbonUtils.getBytesFromFile(appFile));
                app.setProperty(AppDeployerConstants.HASH_VALUE, hashValue);
            }

            // persist application resource
            configRegistry.put(appResourcePath, app);

            // persist artifacts.xml file
            File artifactsXml = new File(carbonApp.getExtractedPath() +
                    ApplicationConfiguration.ARTIFACTS_XML);
            Resource artifactsXmlResource = configRegistry.newResource();
            artifactsXmlResource.setContentStream(new FileInputStream(artifactsXml));
            configRegistry.put(appResourcePath +
                    AppDeployerConstants.APP_ARTIFACTS_XML, artifactsXmlResource);

            persistArtifactList(carbonApp.getAppConfig().getApplicationArtifact().
                    getDependencies(), appResourcePath + AppDeployerConstants.APP_DEPENDENCIES);
        } catch (RegistryException e) {
            String msg = "Unable to persist new Application : " + carbonApp.getAppName()
                    + " Registry transactions failed.";
            log.error(msg, e);
            throw new Exception(msg, e);
        }
    }

    /**
     * This should be called after satisfying all carbon app dependency artifacts. This method
     * basically writes the artifact.xml files into registry
     *
     * @throws Exception - on registry errors
     */
//    public void completePersistence(CarbonApplication carbonApp) throws Exception {
//        try {
//            String appPath = AppDeployerConstants.APPLICATIONS + carbonApp.getAppName();
//            if (!configRegistry.resourceExists(appPath)) {
//                log.error("Registry collection not found for application : " +
//                        carbonApp.getAppName());
//                return;
//            }
//            // set the new deployment status in the app collection
//            Collection appCollection = (Collection) configRegistry.get(appPath);
//            configRegistry.put(appPath, appCollection);
//            persistArtifactList(carbonApp.getAppConfig().getApplicationArtifact().
//                    getDependencies(), appPath + AppDeployerConstants.APP_DEPENDENCIES);
//        } catch (RegistryException e) {
//            String msg = "Unable to complete persistence of Application : " + carbonApp.getAppName()
//                    + " Registry transactions failed.";
//            log.error(msg, e);
//            throw new Exception(msg, e);
//        }
//    }

    /**
     * Reads the hash value property of the given cApp from registry..
     *
     * @param appName - cApp name
     * @return - hash value of the capp artifact
     * @throws CarbonException -
     */
    public String getHashValue(String appName) throws CarbonException {
        try {
            String appResourcePath = AppDeployerConstants.APPLICATIONS + appName;
            //if the app exists in the configRegistry, read the property..
            if (configRegistry.resourceExists(appResourcePath)) {
                Resource app = configRegistry.get(appResourcePath);
                return app.getProperty(AppDeployerConstants.HASH_VALUE);
            }
        } catch (RegistryException e) {
            String msg = "Unable to read hash value of the Application : " + appName
                    + ". Registry transactions failed.";
            log.error(msg, e);
            throw new CarbonException(msg, e);
        }
        return null;
    }

    /**
     * Delete the specified app from registry if already exists
     *
     * @param appName - application name
     * @throws Exception - on registry transaction error
     */
    public void deleteApplication(String appName) throws Exception {
        try {
            String appResourcePath = AppDeployerConstants.APPLICATIONS + appName;
            //if the app exists in the configRegistry, delete it..
            if (configRegistry.resourceExists(appResourcePath)) {
                configRegistry.delete(appResourcePath);
            }
        } catch (RegistryException e) {
            String msg = "Unable to delete the Application : " + appName
                    + ". Registry transactions failed.";
            log.error(msg, e);
            throw new Exception(msg, e);
        }
    }

    /**
     * Removes all registry collections, resources and associations introduced through this
     * Capp artifact.
     *
     * @param registryConfig - RegistryConfig instance
     */
    public void removeArtifactResources(RegistryConfig registryConfig) {
        try {
            // remove collections
            List<RegistryConfig.Collection> collections = registryConfig.getCollections();
            for (RegistryConfig.Collection col : collections) {
                Registry reg = getRegistryInstance(col.getRegistryType());
                if (reg != null && reg.resourceExists(col.getPath())) {
                    reg.delete(col.getPath());
                }
            }
            // remove dumps
            List<RegistryConfig.Dump> dumps = registryConfig.getDumps();
            for (RegistryConfig.Dump dump : dumps) {
                Registry reg = getRegistryInstance(dump.getRegistryType());
                if (reg != null && reg.resourceExists(dump.getPath())) {
                    reg.delete(dump.getPath());
                }
            }
            // remove resources
            List<RegistryConfig.Resourse> resources = registryConfig.getResources();
            for (RegistryConfig.Resourse res : resources) {
                Registry reg  = getRegistryInstance(res.getRegistryType());
                if (reg != null && reg.resourceExists(res.getPath())) {
                    reg.delete(AppDeployerUtils.computeResourcePath(res.getPath(),
                            res.getFileName()));
                }
            }
            // remove associations
            List<RegistryConfig.Association> associations = registryConfig.getAssociations();
            for (RegistryConfig.Association association : associations) {
                Registry reg = getRegistryInstance(association.getRegistryType());
                if (reg != null) {
                    reg.removeAssociation(association.getSourcePath(),
                            association.getTargetPath(), association.getAssociationType());
                }
            }
        } catch (RegistryException e) {
            log.error("Error while removing registry resources of the artifact : " +
                    registryConfig.getParentArtifactName());
        }
    }

    /**
     * Writes all registry contents (resources, collections and associations) of the given
     * artifact to the registry.
     *
     * @param regConfig - Artifact instance
     * @throws Exception - on registry errors
     */
    public void writeArtifactResources(RegistryConfig regConfig) throws Exception {
        // write collections
        List<RegistryConfig.Collection> collections = regConfig.getCollections();
        for (RegistryConfig.Collection col : collections) {
            Registry reg = getRegistryInstance(col.getRegistryType());
            String dirPath = regConfig.getExtractedPath() + File.separator +
                    AppDeployerConstants.RESOURCES_DIR + File.separator + col.getDirectory();

            // check whether the collection dir exists
            File file = new File(dirPath);
            if (!file.exists()) {
                log.error("Specified collection directory not found at : " + dirPath);
                continue;
            }
            if (reg != null) {
                RegistrySynchronizer.checkIn((UserRegistry) reg, dirPath,
                        col.getPath(), true, true);
            }
        }

        // write resources
        List<RegistryConfig.Resourse> resources = regConfig.getResources();
        for (RegistryConfig.Resourse resource : resources) {
            Registry reg = getRegistryInstance(resource.getRegistryType());
            String filePath = regConfig.getExtractedPath() + File.separator +
                    AppDeployerConstants.RESOURCES_DIR + File.separator + resource.getFileName();

            // check whether the file exists
            File file = new File(filePath);
            if (!file.exists()) {
                log.error("Specified file to be written as a resource is " +
                        "not found at : " + filePath);
                continue;
            }
            if (reg != null) {
				writeFromFile(
						reg,
						file,
						resource.getPath() + "/" + resource.getFileName(),
						resource.getMediaType() != null ? resource.getMediaType() : 
						AppDeployerUtils.readMediaType(regConfig.getExtractedPath(),
						resource.getFileName()));
            }
        }

        // write dumps
        List<RegistryConfig.Dump> dumps = regConfig.getDumps();
        for (RegistryConfig.Dump dump : dumps) {
            Registry reg = getRegistryInstance(dump.getRegistryType());
            String filePath = regConfig.getExtractedPath() + File.separator +
                    AppDeployerConstants.RESOURCES_DIR + File.separator + dump.getDumpFileName();

            // check whether the file exists
            File file = new File(filePath);
            if (!file.exists()) {
                log.error("Specified file to be written as a dump is " +
                        "not found at : " + filePath);
                continue;
            }
            // .dump file is a zip, so create a ZipInputStream
            ZipInputStream zis = new ZipInputStream(new FileInputStream(file));
            zis.getNextEntry();
            Reader reader = new InputStreamReader(zis);
            if (reg != null) {
                reg.restore(dump.getPath(), reader);
            }
        }

        // write associations
        List<RegistryConfig.Association> associations = regConfig.getAssociations();
        for (RegistryConfig.Association association : associations) {
            Registry reg = getRegistryInstance(association.getRegistryType());
            try {
                if (reg != null) {
                    reg.addAssociation(association.getSourcePath(),
                            association.getTargetPath(), association.getAssociationType());
                }
            } catch (RegistryException e) {
                log.error("Error while adding the association. Source path : " + association
                        .getSourcePath() + " Target path : " + association.getTargetPath());
            }
        }
    }

    /**
     * Write the file content as a registry resource to the given path
     *
     * @param reg          - correct registry instance
     * @param file         - file to be written
     * @param registryPath - path to write the resource
     * @param mediaType    - media type of the resource to be added
     */
    public void writeFromFile(Registry reg, File file, String registryPath, String mediaType) {
        // convert the file content into bytes and then encode it as a string
        byte[] content = getBytesFromFile(file);
        if (content == null) {
            log.error("Error while writing file content into Registry. File content is null..");
            return;
        }

        try {
            reg.beginTransaction();
            Resource resource = reg.newResource();
            resource.setContent(content);
            if (mediaType == null) {
                mediaType = MediaTypesUtils.getMediaType(file.getName());
            }
            resource.setMediaType(mediaType);
            reg.put(registryPath, resource);
            reg.commitTransaction();
        } catch (RegistryException e) {
            try {
                reg.rollbackTransaction();
            } catch (RegistryException e1) {
                log.error("Error while transaction rollback", e1);
            }
            log.error("Error while checking in resource to path: " + registryPath +
                    " from file: " + file.getAbsolutePath(), e);
        }
    }

    /**
     * Persist a list of Artifacts. Writes the artifact.xml of each artifact into the registry.
     *
     * @param depList - list of artifacts
     * @param artifactPath - path for persisting
     * @throws RegistryException - on registry errors
     */
    private void persistArtifactList(List<Artifact.Dependency> depList,
                                    String artifactPath) throws Exception {
        for (Artifact.Dependency dep : depList) {
            Artifact artifact = dep.getArtifact();
            if (artifact == null) {
                continue;
            }
            // create the artifact collection
            Collection artifactCollection = configRegistry.newCollection();
            artifactCollection.setProperty(AppDeployerConstants.RUNTIME_OBJECT_NAME,
                    artifact.getRuntimeObjectName());
            configRegistry.put(artifactPath + artifact.getName(), artifactCollection);

            // persist artifact.xml
            Resource resource = configRegistry.newResource();
            File artifactXml = new File(artifact.getExtractedPath() +
                    File.separator + Artifact.ARTIFACT_XML);
            resource.setContentStream(new FileInputStream(artifactXml));
            configRegistry.put(artifactPath + artifact.getName() +
                    AppDeployerConstants.ARTIFACT_XML, resource);

            // persist registry-info.xml if exists
            File regInfoXml = new File(artifact.getExtractedPath() +
                    File.separator + Artifact.REG_INFO_XML);
            if (regInfoXml.exists()) {
                Resource regInfoResource = configRegistry.newResource();
                regInfoResource.setContentStream(new FileInputStream(regInfoXml));
                configRegistry.put(artifactPath + artifact.getName() +
                        AppDeployerConstants.REG_CONFIG_XML, regInfoResource);
            }

        }
    }

    /**
     * Persits the registry config file to registry
     *
     * @param artifactPath - registry path of the "registry/resource" artifact
     * @param regConfig - RegistryConfig instance
     * @throws Exception - on registry errors
     */
    public void persistRegConfig(String artifactPath, RegistryConfig regConfig)
            throws Exception {
        if (regConfig == null) {
            return;
        }
        Resource resource = configRegistry.newResource();
        File regConfigXml = new File(regConfig.getExtractedPath() +
                File.separator + regConfig.getConfigFileName());
        resource.setContentStream(new FileInputStream(regConfigXml));
        configRegistry.put(artifactPath + AppDeployerConstants.REG_CONFIG_XML , resource);
    }

    /**
     * Loads the registry config stream for the given artifact path and builds a RegistryConfig
     * instance.
     *
     * @param artifactPath - registry path of the "registry/resource" artifact
     * @return - RegistryConfig instance built
     * @throws Exception - on registry errors
     */
    public RegistryConfig loadRegistryConfig(String artifactPath) throws Exception {
        RegistryConfig regConfig = null;
        String regConfigPath = artifactPath + AppDeployerConstants.REG_CONFIG_XML;
        if (configRegistry.resourceExists(regConfigPath)) {
            Resource artifactResource = configRegistry.get(regConfigPath);
            InputStream xmlStream = artifactResource.getContentStream();
            if (xmlStream != null) {
                regConfig = AppDeployerUtils.populateRegistryConfig(
                        new StAXOMBuilder(xmlStream).getDocumentElement());
            }
        }
        return regConfig;
    }

    /**
     * Checks whether default server roles in carbon.xml are overridden through the UI
     * @return - true if modified, else false
     */
    public boolean areRolesOverridden() {
        String defaultPath = ServerRoleUtils.getRegistryPath(ServerRoleConstants.DEFAULT_ROLES_ID);

        try {
            if (configRegistry.resourceExists(defaultPath)) {
                Resource defResource = configRegistry.get(defaultPath);
                if (ServerRoleConstants.MODIFIED_TAG_TRUE.equals(defResource
                        .getProperty(ServerRoleConstants.MODIFIED_TAG))) {
                    return true;
                }
            }
        } catch (RegistryException e) {
            log.error("Error while reading server role resources", e);
        }
        return false;
    }

    /**
     * Reads the server roles which are stored in registry
     * @param roleType - default or custom
     * @return - list of roles
     */
    public List<String> readServerRoles(String roleType) {
        String rolesPath = ServerRoleUtils.getRegistryPath(roleType);
        List<String> roles = new ArrayList<String>();

        try {
            if (configRegistry.resourceExists(rolesPath)) {
                Resource resource = configRegistry.get(rolesPath);
                List<String> rolesRead = resource.getPropertyValues(roleType);
                if (rolesRead != null) {
                    return rolesRead;
                }
            }
        } catch (RegistryException e) {
            log.error("Error while reading server role resources", e);
        }
        return roles;
    }

    /**
     * Returns the contents of the file in a byte array.
     *
     * @param file - file to convert
     * @return - byte array
     */
    private byte[] getBytesFromFile(File file) {
        InputStream is = null;
        byte[] bytes = null;
        try {
            is = new FileInputStream(file);
            long length = file.length();
            // to ensure that file is not larger than Integer.MAX_VALUE.
            if (length > Integer.MAX_VALUE) {
                // File is too large
                log.error("File " + file.getName() + "is too large.");
            }

            // byte array to keep the data
            bytes = new byte[(int) length];
            int offset = 0;
            int numRead;
            try {
                while (offset < bytes.length
                        && (numRead = is.read(bytes, offset, bytes.length - offset)) >= 0) {
                    offset += numRead;
                }
            } catch (IOException e) {
                log.error("Error in reading data", e);
            }

            if (offset < bytes.length) {
                log.error("Could not completely read file " + file.getName());
            }
        } catch (FileNotFoundException e) {
            log.error("Expected file: " + file.getName() + " Not found", e);
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {
                log.error("Error in closing the stream", e);
            }
        }
        return bytes;
    }

    /**
     * Returns the correct registry instance according to the given registry type
     *
     * @param registryType - type string
     * @return - Registry instance
     */
    private Registry getRegistryInstance(String registryType) {
        Registry registry = null;
        // we use the rootRegistry if registryType is not found. this is to make it 
        // backward compatible with CStudio 1.0
        if (registryType == null || "".equals(registryType)) {
            registry = rootRegistry;
        } else if (RegistryConfig.LOCAL_REGISTRY.equals(registryType)) {
            registry = localRegistry;
        } else if (RegistryConfig.CONFIG_REGISTRY.equals(registryType)) {
            registry = configRegistry;
        } else if (RegistryConfig.GOVERNANCE_REGISTRY.equals(registryType)) {
            registry = governanceRegistry;
        }
        return registry;
    }

}
