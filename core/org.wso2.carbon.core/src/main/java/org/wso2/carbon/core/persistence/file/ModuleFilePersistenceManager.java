package org.wso2.carbon.core.persistence.file;

import org.apache.axiom.om.*;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.axiom.om.xpath.AXIOMXPath;
import org.apache.axis2.AxisFault;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.axis2.util.XMLPrettyPrinter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jaxen.JaxenException;
import org.wso2.carbon.core.Resources;
import org.wso2.carbon.core.persistence.PersistenceDataNotFoundException;
import org.wso2.carbon.core.persistence.PersistenceException;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Acts as the immediate lower level of ModulePersistenceManager class.
 * This abstracts the persisting the metadata to a file in a synchronized,
 * abstract way.
 * <p/>
 * The methods are synchronized per ResourceFileData object.
 */
public class ModuleFilePersistenceManager extends AbstractFilePersistenceManager {

    private static final Log log = LogFactory.getLog(ModuleFilePersistenceManager.class);

    public ModuleFilePersistenceManager(AxisConfiguration axisConfig) throws AxisFault {
        super(axisConfig);
        try {
            URL repositoryURL = axisConfig.getRepository();
            if (repositoryURL != null) {
                String repoPath = URLDecoder.decode(axisConfig.getRepository().getPath(), "UTF-8");
                metafilesDir = new File(repoPath +
                        File.separator + Resources.MODULES_METAFILES_DIR);
            }
        } catch (UnsupportedEncodingException e) {
            log.error("metafiles directory URL can not bde decoded.", e);
            throw new AxisFault("metafiles directory URL can not bde decoded.", e);
        }
        if (this.metafilesDir == null) {
            log.error("metafiles directory for services must exist. ");
            throw new AxisFault("metafiles directory for services must exist. " +
                    "May be AxisConfiguration does not have repository url set.");
        }
    }

    public void init() {
        if (!metafilesDir.exists()) {
            metafilesDir.mkdirs();
        }
    }

    /**
     * Reads the relevant service group file from FS and loads the OM to memory.
     * If the file does not exist, create a new OM.
     * This is supposed to work for both serviceGroups and modules.xml metafiles
     * Note: Nested beginTransactions is NOT supported.
     * todo do locking mechanism
     *
     * @param moduleName
     * @throws java.io.IOException
     * @throws javax.xml.stream.XMLStreamException
     *
     * @throws org.wso2.carbon.core.persistence.PersistenceException
     *
     */
    public synchronized void beginTransaction(String moduleName) throws PersistenceException {
        File moduleFile = new File(metafilesDir, moduleName + Resources.METAFILE_EXTENSION);
        try {
            OMElement sgElement;
            if (moduleFile.exists()) {
                FileInputStream fis = new FileInputStream(moduleFile);
                XMLStreamReader reader = xif.createXMLStreamReader(fis);

                StAXOMBuilder builder = new StAXOMBuilder(reader);
                sgElement = builder.getDocumentElement();
                sgElement.detach();
                reader.close();
                fis.close();
            } else {                        //the file does not exist.
                sgElement = omFactory.createOMElement(Resources.ModuleProperties.MODULE_XML_TAG, null);
            }

            ResourceFileData fileData = new ResourceFileData(sgElement, moduleFile, true);
            resourceMap.put(moduleName, fileData);
        } catch (XMLStreamException e1) {
            log.error("Failed to use XMLStreamReader. Exception in beginning the transaction ", e1);
            throw new PersistenceException("Exception in beginning the transaction " + e1);
        } catch (FileNotFoundException e) {
            log.error("File not found. Exception in beginning the transaction " + moduleFile.getAbsolutePath(), e);
            throw new PersistenceException("Exception in beginning the transaction", e);
        } catch (IOException e) {
            log.error("Exception in closing service group file " + moduleName, e);
            throw new PersistenceException("Exception in closing service group file", e);
        }
    }

    /**
     * Returns the root module element
     * This simply calls #get(String serviceGroupId, String xpathStr) with xpathStr = "/"
     *
     * @param moduleId The module name
     * @return An OMNode. This could be an OMElement, OMAttribute etc. Cast as necessary.
     * @throws org.wso2.carbon.core.persistence.PersistenceDataNotFoundException
     *
     * @see #get(String, String)
     */
    public OMElement get(String moduleId) throws PersistenceDataNotFoundException {
        return (OMElement) get(moduleId, Resources.ModuleProperties.ROOT_XPATH);
    }

    @Override
    public void delete(String moduleId, String xpathStr) throws PersistenceDataNotFoundException {
        ResourceFileData fileData = resourceMap.get(moduleId);

        try {
            if (fileData != null && fileData.isTransactionStarted()) {
                OMElement sgElement = fileData.getOMElement();
                AXIOMXPath xpathExpr = new AXIOMXPath(xpathStr);
                OMElement el = (OMElement) xpathExpr.selectSingleNode(sgElement);
                if (el.getParent() == null) { //this is the root element
                    fileData.setOMElement(null);
                } else {
                    el.detach();
                }
            } else {
                throw new PersistenceDataNotFoundException("transaction isn't started");
            }
        } catch (JaxenException e) {
            log.error("Error parsing xpath string " + moduleId + xpathStr, e);
            e.printStackTrace();
            throw new PersistenceDataNotFoundException("Error parsing xpath string " + e);
        }
    }

    public boolean fileExists(String moduleId) {
        ResourceFileData fileData = resourceMap.get(moduleId);
        //if a transaction is started
        if (fileData != null && fileData.isTransactionStarted() && fileData.getFile() != null) {
            return fileData.getFile().exists();
        } else {
            return new File(metafilesDir, moduleId + Resources.METAFILE_EXTENSION).
                    exists();
        }
    }

    public boolean elementExists(String moduleName, String elementXpathStr) {
        try {
            ResourceFileData fileData = resourceMap.get(moduleName);
            AXIOMXPath xpathExpr = new AXIOMXPath(elementXpathStr);
            //if a transaction is started
            if (fileData != null && fileData.isTransactionStarted() && fileData.getOMElement() != null) {
                return xpathExpr.selectSingleNode(fileData.getOMElement()) != null;
            } else if ((fileData != null && !fileData.isTransactionStarted()) ||
                    fileData == null) {
                File f = new File(metafilesDir, moduleName + Resources.METAFILE_EXTENSION);
                if (f.exists()) {
                    String filePath = f.getAbsolutePath();
                    OMElement element = new StAXOMBuilder(filePath).getDocumentElement();
                    element.detach();
                    return xpathExpr.selectSingleNode(element) != null;
                }
            }
        } catch (JaxenException e) {
            e.printStackTrace();
        } catch (XMLStreamException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Checks whether a transaction is already started on the given module.
     * Since, this implementation does not support nested transactions, use this method
     * to make sure that a transaction is not yet started for a given module.
     *
     * @param moduleId
     * @return
     */
    public boolean isTransactionStarted(String moduleId) {

        return resourceMap.get(moduleId) != null &&
                resourceMap.get(moduleId).isTransactionStarted();

    }
}
