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
import org.wso2.carbon.core.persistence.PersistenceUtils;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.net.URL;
import java.net.URLDecoder;
import java.util.List;

/**
 * Acts as the immediate lower level of other *PersistenceManager classes.
 * This abstracts the persisting the metadata to a file in a synchronized,
 * abstract way.
 * <p/>
 * The methods are synchronized per ResourceFileData object.
 */
public class ServiceGroupFilePersistenceManager extends AbstractFilePersistenceManager {

    XMLInputFactory xif = XMLInputFactory.newInstance();
    protected OMFactory omFactory = OMAbstractFactory.getOMFactory();
    private static final Log log = LogFactory.getLog(ServiceGroupFilePersistenceManager.class);

    public ServiceGroupFilePersistenceManager(AxisConfiguration axisConfig) throws AxisFault {
        super(axisConfig);
        try {
            URL repositoryURL = axisConfig.getRepository();
            if (repositoryURL != null) {
                String repoPath = URLDecoder.decode(axisConfig.getRepository().getPath(), "UTF-8");
                metafilesDir = new File(repoPath +
                        File.separator + Resources.SERVICES_METAFILES_DIR);
            }
        } catch (UnsupportedEncodingException e) {
            log.error("metafiles directory URL can not bde decoded.", e);
            throw new AxisFault("metafiles directory URL can not bde decoded.", e);
        }
        if (this.metafilesDir == null) {
            log.error("metafiles directory for services must exist. ");
            throw new AxisFault("metafiles directory for services must exist. " +
                    "May be AxisConfiguration does not have the repository url set.");
        }
    }

    /**
     * Reads the relevant service group file from FS and loads the OM to memory.
     * If the file does not exist, create a new OM.
     * <p/>
     * This is supposed to work for both serviceGroups and modules.xml metafiles
     * todo do locking mechanism
     *
     * @param serviceGroupId service Group name
     * @throws org.wso2.carbon.core.persistence.PersistenceException
     *
     */
    public synchronized void beginTransaction(String serviceGroupId) throws PersistenceException {
        File sgFile = new File(metafilesDir, serviceGroupId + Resources.METAFILE_EXTENSION);

        try {
            if (resourceMap.get(serviceGroupId) != null &&
                    resourceMap.get(serviceGroupId).isTransactionStarted()) {
                throw new PersistenceException("A transaction is already started. " +
                        "Nested transactions are no longer supported in this persistence model");
            }

            OMElement sgElement;
            if (sgFile.exists()) {
                FileInputStream fis = new FileInputStream(sgFile);
                XMLStreamReader reader = xif.createXMLStreamReader(fis);

                StAXOMBuilder builder = new StAXOMBuilder(reader);
                sgElement = builder.getDocumentElement();
                sgElement.detach();
                reader.close();
                fis.close();
            } else {                        //the file does not exist.
                sgElement = omFactory.createOMElement(Resources.ServiceGroupProperties.SERVICE_GROUP_XML_TAG, null);
            }

            ResourceFileData fileData = new ResourceFileData(sgElement, sgFile, true);
            resourceMap.put(serviceGroupId, fileData);
        } catch (XMLStreamException e1) {
            log.error("Failed to use XMLStreamReader. Exception in beginning the transaction ", e1);
            throw new PersistenceException("Exception in beginning the transaction " + e1);
        } catch (FileNotFoundException e) {
            log.error("File not found. Exception in beginning the transaction " + sgFile.getAbsolutePath(), e);
            throw new PersistenceException("Exception in beginning the transaction" + e);
        } catch (IOException e) {
            log.error("Exception in closing service group file " + serviceGroupId, e);
            throw new PersistenceException("Exception in closing service group file", e);
        }
    }

    /**
     * Returns the root serviceGroup element
     * This simply calls #get(String serviceGroupId, String xpathStr) with xpathStr = "/serviceGroup[1]"
     *
     * @param serviceGroupId service Group name
     * @return An OMNode. This could be an OMElement, OMAttribute etc. Cast as necessary.
     * @see #get(String, String)
     */
    public OMElement get(String serviceGroupId) throws PersistenceDataNotFoundException {
        return (OMElement) get(serviceGroupId, Resources.ServiceGroupProperties.ROOT_XPATH);
    }

    /**
     * Returns the root serviceGroup element
     * This simply calls #get(String serviceGroupId, String xpathStr) with xpathStr = "/serviceGroup[1]"
     * <p/>
     * Note: Don't use this to retrieve a module association. It's different in format.
     *
     * @param serviceGroupId       service Group name
     * @param xpathOfParentElement The xpath expression to the association elements. Generally, no need of using attr predicate because this does a this#getAll
     * @param associationName      the type of association. ex. exposedTransports
     * @return An OMNode. This could be an OMElement, OMAttribute etc. Cast as necessary.
     * @throws PersistenceDataNotFoundException
     *          if an error occured during xpath evaluation
     * @see org.wso2.carbon.core.persistence.PersistenceUtils#createModule(String, String, String)
     * @see #get(String, String)
     */
    public List getAssociations(String serviceGroupId, String xpathOfParentElement, String associationName) throws
            PersistenceDataNotFoundException {
        String associationXPath = xpathOfParentElement + "/" + Resources.Associations.ASSOCIATION_XML_TAG +
                PersistenceUtils.getXPathAttrPredicate(Resources.ModuleProperties.TYPE, associationName);
        return getAll(serviceGroupId, associationXPath);
    }


    @Override
    public void delete(String serviceGroupId, String xpathStrOfElement) throws PersistenceDataNotFoundException {
        ResourceFileData fileData = resourceMap.get(serviceGroupId);

        try {
            if (fileData != null && fileData.isTransactionStarted()) {
                OMElement sgElement = fileData.getOMElement();
                AXIOMXPath xpathExpr = new AXIOMXPath(xpathStrOfElement);
                OMElement el = (OMElement) xpathExpr.selectSingleNode(sgElement);
                if (el != null && el.getParent() == null) { //this is the root element
                    fileData.setOMElement(null);
                } else if (el != null) {
                    el.detach();
                } else {
                    throw new PersistenceDataNotFoundException(
                            "The Element specified by path not found" + serviceGroupId + xpathStrOfElement);

                }
            } else {
                throw new PersistenceDataNotFoundException(
                        "The Element specified by path not found or a transaction isn't started yet. " +
                                xpathStrOfElement);
            }
        } catch (JaxenException e) {
            log.error("Error parsing xpath string " + serviceGroupId + xpathStrOfElement, e);
            throw new PersistenceDataNotFoundException("Error parsing xpath string ", e);
        }
    }

    public void init() {
        if (!metafilesDir.exists()) {
            metafilesDir.mkdir();
        }
    }

    public boolean fileExists(String serviceGroupId) {
        ResourceFileData fileData = resourceMap.get(serviceGroupId);
        //if a transaction is started
        if (fileData != null && fileData.isTransactionStarted() && fileData.getFile() != null) {
            return fileData.getFile().exists();
        } else {
            return new File(metafilesDir, serviceGroupId + Resources.METAFILE_EXTENSION).
                    exists();
        }
    }

    public boolean elementExists(String serviceGroupId, String elementXpathStr) {
        try {
            ResourceFileData fileData = resourceMap.get(serviceGroupId);
            AXIOMXPath xpathExpr = new AXIOMXPath(elementXpathStr);
            //if a transaction is started
            if (fileData != null && fileData.isTransactionStarted() && fileData.getOMElement() != null) {
                return xpathExpr.selectSingleNode(fileData.getOMElement()) != null;
            } else if ((fileData != null && !fileData.isTransactionStarted()) ||
                    fileData == null) {
                File f = new File(metafilesDir, serviceGroupId + Resources.METAFILE_EXTENSION);
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
     * Checks whether a transaction is already started on the given service group.
     * Since, this implementation does not support nested transactions, use this method
     * to make sure that a transaction is not started yet for a given SG.
     *
     * @param serviceGroupId service group name
     * @return true if a transaction is started, false otherwise.
     */
    public boolean isTransactionStarted(String serviceGroupId) {

        return resourceMap.get(serviceGroupId) != null &&
                resourceMap.get(serviceGroupId).isTransactionStarted();

    }
}
