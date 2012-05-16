package org.wso2.carbon.core.persistence.file.deployer;

import org.apache.axiom.om.OMElement;
import org.apache.axis2.AxisFault;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.deployment.AbstractDeployer;
import org.apache.axis2.deployment.DeploymentException;
import org.apache.axis2.deployment.repository.util.DeploymentFileData;
import org.apache.axis2.description.AxisModule;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.description.AxisServiceGroup;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.core.Resources;
import org.wso2.carbon.core.persistence.ModulePersistenceManager;
import org.wso2.carbon.core.persistence.PersistenceDataNotFoundException;
import org.wso2.carbon.core.persistence.PersistenceFactory;
import org.wso2.carbon.core.persistence.PersistenceUtils;
import org.wso2.carbon.core.persistence.ServiceGroupPersistenceManager;
import org.wso2.carbon.core.persistence.ServicePersistenceManager;

import java.util.Iterator;

public class PersistenceMetaDataDeployer extends AbstractDeployer {
    private static final Log log = LogFactory.getLog(PersistenceMetaDataDeployer.class);

    private AxisConfiguration axisConfig;
    String metaDataDir;
    private PersistenceFactory pf;

    @Override
    public void init(ConfigurationContext configurationContext) {
        axisConfig = configurationContext.getAxisConfiguration();
        try {
            pf = PersistenceFactory.getInstance(axisConfig);
        } catch (AxisFault axisFault) {
            log.error("Error obtaining persistence factory.");
            axisFault.printStackTrace();
        }
    }

    @Override
    public void setDirectory(String directory) {
        metaDataDir = directory;
    }

    @Override
    public void setExtension(String extension) {
    }

    /**
     * Axis2 deployment engine will call this method when a .xml meta file is changed. Then the
     * changes will get applied to respective services and modules.
     *
     * @param deploymentFileData - info about the deployed file
     * @throws DeploymentException - error while deploying cApp
     */
    public void deploy(DeploymentFileData deploymentFileData) throws DeploymentException {
        String fileName = deploymentFileData.getName();
        String name = fileName.substring(0, fileName.lastIndexOf(".xml"));

        if (Resources.SERVICES_METAFILES_DIR.equals(metaDataDir)) {

            if(log.isDebugEnabled()){
                log.debug("Detected Service Meta File change.." + name);
            }
            AxisServiceGroup serviceGroup = axisConfig.getServiceGroup(name);

            if (serviceGroup == null) {
                if(log.isDebugEnabled()){
                    log.debug("Error getting service from axisConfig.");
                }
                return;
            }
            if(pf.getServiceGroupFilePM().isUserModification(serviceGroup.getServiceGroupName())){
                if(log.isDebugEnabled()){
                    log.debug("User modified service : " + serviceGroup.getServiceGroupName());
                }
                return;
            }

            ServiceGroupPersistenceManager sgpm = pf.getServiceGroupPM();
            ServicePersistenceManager spm = pf.getServicePM();

            try {
                sgpm.handleExistingServiceGroupInit(serviceGroup);
                for (Iterator itr = serviceGroup.getServices(); itr.hasNext(); ) {
                    AxisService axisService = (AxisService) itr.next();
                    OMElement serviceEle = pf.getServicePM().getService(axisService);
                    if (serviceEle != null) {
                        spm.handleExistingServiceInit(axisService);
                    }
                }

            } catch (AxisFault axisFault) {
                log.error(axisFault.getMessage());
                axisFault.printStackTrace();
            } catch (Exception e) {
                log.error(e.getMessage());
                throw new DeploymentException(e);
            }

        } else if (Resources.MODULES_METAFILES_DIR.equals(metaDataDir)) {
            if(log.isDebugEnabled()){
                log.debug("Detected Module Meta File change.." + name);
            }

            AxisModule module = axisConfig.getModule(name);
            try {
                OMElement moduleEle = (OMElement) pf.getModuleFilePM().get(name,
                                                   Resources.ModuleProperties.VERSION_XPATH +
                                                   PersistenceUtils.getXPathAttrPredicate(
                                                           Resources.ModuleProperties.VERSION_ID,
                                                           module.getVersion().toString()));
                ModulePersistenceManager mpm = pf.getModulePM();

                if(pf.getModuleFilePM().isUserModification(module.getName())){
                    if(log.isDebugEnabled()){
                        log.debug("user modified module : " + module.getName());
                    }
                    return;
                }
                mpm.handleExistingModuleInit(moduleEle, module);
            } catch (PersistenceDataNotFoundException e) {
                log.error(e.getMessage());
                e.printStackTrace();
            } catch (Exception e) {
                log.error(e.getMessage());
                e.printStackTrace();
            }

        }
    }

    public void undeploy(String filePath) throws DeploymentException {
        //do nothing
    }

    public void cleanup() throws DeploymentException {
        // do nothing
    }
}
