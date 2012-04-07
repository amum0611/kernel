package org.wso2.carbon.core.persistence.file;

import org.apache.axiom.om.*;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.axiom.om.xpath.AXIOMXPath;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.axis2.util.XMLPrettyPrinter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jaxen.JaxenException;
import org.wso2.carbon.core.Resources;
import org.wso2.carbon.core.persistence.PersistenceDataNotFoundException;
import org.wso2.carbon.core.persistence.PersistenceException;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractFilePersistenceManager {

    protected AxisConfiguration axisConfig;
    File metafilesDir;

    Map<String, ResourceFileData> resourceMap = new HashMap<String, ResourceFileData>();

    XMLInputFactory xif = XMLInputFactory.newInstance();
    protected OMFactory omFactory = OMAbstractFactory.getOMFactory();

    private static final Log log = LogFactory.getLog(AbstractFilePersistenceManager.class);

    protected AbstractFilePersistenceManager(AxisConfiguration axisConfig) {
        this.axisConfig = axisConfig;
    }

    public abstract void beginTransaction(String resourceId) throws
            IOException, XMLStreamException, PersistenceException;

    public synchronized void commitTransaction(String resourceId) throws PersistenceException {
        try {
            synchronized (resourceMap.get(resourceId)) {
                ResourceFileData fileData = resourceMap.get(resourceId);
                File f;
                if (fileData != null && fileData.getOMElement() == null) {   //the resource has been deleted
                    f = new File(metafilesDir, resourceId + Resources.METAFILE_EXTENSION);
                    if (f.exists() && !f.delete()) {
                        handleExceptionWithRollback(resourceId, "Couldn't delete " + f.getName(), new Throwable());
                    } else {
                        log.debug("Successfully deleted persisted resource contents " + resourceId);
                    }
                    resourceMap.remove(resourceId);
                    return;
                } else if (fileData != null) {
                    f = fileData.getFile();
                } else {
                    resourceMap.remove(resourceId);
                    throw new PersistenceException("persistence data not found");
                }
                f.getParentFile().mkdirs();
                f.createNewFile(); //creates a file only if it does not exist
                OutputStream outputStream = new FileOutputStream(f);
                fileData.getOMElement().serializeAndConsume(outputStream);
                //todo does pretty printing really needed? consider removing after testing
                XMLPrettyPrinter.prettify(fileData.getFile());
                resourceMap.remove(resourceId);
                outputStream.close();
            }
        } catch (XMLStreamException e1) {
            log.error("Exception in persisting the transaction of " + resourceId, e1);
            handleExceptionWithRollback(resourceId, "Exception in persisting the transaction " + resourceId, e1);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            handleExceptionWithRollback(resourceId, "Exception in persisting the transaction " + resourceId, e);
        }

        if (log.isDebugEnabled()) {
            log.debug("Added new resource - " + resourceId);
        }
    }

    /**
     * @param resourceId service group name of module name
     */
    public void rollbackTransaction(String resourceId) {
        ResourceFileData fileData = resourceMap.get(resourceId);
        if (fileData != null) {
            fileData.setOMElement(null);
            fileData.setTransactionStarted(false);
        }
    }

    /**
     * Returns the root resource's element
     * This simply calls #get(String resourceId, String xpathStr)
     * with xpathStr = Resources.ServiceGroupProperties.ROOT_XPATH or "/module[1]" as relevant
     *
     * @param resourceId module name or service group name as applicable
     * @return An OMNode. This could be an OMElement, OMAttribute etc. Cast as necessary.
     * @throws PersistenceDataNotFoundException
     *
     * @see #get(String, String)
     */
    public abstract OMElement get(String resourceId) throws PersistenceDataNotFoundException;

    /**
     * Use this to retrieve a matched OMNode.
     * NOTE: OMAttribute does not extend OMNode.
     *
     * @param resourceId module name or service group name as applicable
     * @param xpathStr   The path in the xml file where the said xml content can be found
     * @return An OMNode. This could be an OMElement, OMAttribute etc. Cast as necessary.
     * @throws PersistenceDataNotFoundException
     *
     * @see #getAttribute(String, String)
     */
    public OMNode get(String resourceId, String xpathStr) throws PersistenceDataNotFoundException {
        List list = getAll(resourceId, xpathStr);
        if (list.size() > 0) {
            return (OMNode) list.get(0);
        } else {
            return null;
        }
    }

    /**
     * Use this if you are retrieving an attribute using an xpath.
     * Since OMNode is not extended by OMAttribute interface, we cannot use
     * same get method for this.
     *
     * @param resourceId module name or service group name as applicable
     * @param xpathStr   The path in the xml file where the said xml content can be found
     * @return An OMNode. This could be an OMElement, OMAttribute etc. Cast as necessary.
     * @throws PersistenceDataNotFoundException
     *
     * @see #get(String, String)
     */
    public OMAttribute getAttribute(String resourceId, String xpathStr) throws PersistenceDataNotFoundException {
        List list = getAll(resourceId, xpathStr);
        if (list.size() > 0) {
            return (OMAttribute) list.get(0);
        } else {
            return null;
        }
    }

    /**
     * @param resourceName  service group name / module name
     * @param content       the OMElement to be added as a child to the location of xpathOfParent
     * @param xpathOfParent The parent path in the xml file where the said xml content should be added.
     * @throws org.wso2.carbon.core.persistence.PersistenceDataNotFoundException
     *
     */
    public void put(String resourceName, OMElement content, String xpathOfParent) throws
            PersistenceDataNotFoundException {
        try {
            ResourceFileData fileData = resourceMap.get(resourceName);
            if (fileData != null) {
                OMElement sgElement = fileData.getOMElement();
                AXIOMXPath xpathExpr = new AXIOMXPath(xpathOfParent);
                OMElement parent = (OMElement) xpathExpr.selectSingleNode(sgElement);
                if (parent != null) {
                    if (!parent.equals(content.getParent())) {
                        parent.addChild(content);
                    } else {
                        log.debug("Trying add a child to the same parent. " + resourceName + content.toString());
                    }
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("parent can not be found - " + resourceName + fileData.getOMElement());
                    }
                    throw new PersistenceDataNotFoundException("The resource can not be added because Parent could " +
                            "not be found." + resourceName + " The XPath - " + xpathOfParent);
                }
            } else {
                throw new PersistenceDataNotFoundException("ResourceFileData not found. " +
                        "The Transaction May not have been initialized. see #beginTransaction. " + resourceName +
                        "The xpath used was " + xpathOfParent);
            }
        } catch (JaxenException e) {
            log.debug("Error selecting parent in " + resourceName, e);
            e.printStackTrace();
            throw new PersistenceDataNotFoundException("Error selecting parent in " + resourceName, e);
        }
    }

    /**
     * @param resourceName   The service group ID or module id
     * @param attr           Content to be added
     * @param xpathOfElement The Xpath of the OMElement where this attribute should be added
     * @throws org.wso2.carbon.core.persistence.PersistenceDataNotFoundException
     *
     */
    public void put(String resourceName, OMAttribute attr, String xpathOfElement) throws
            PersistenceDataNotFoundException {
        try {
            ResourceFileData fileData = resourceMap.get(resourceName);
            if (fileData != null) {
                OMElement sgElement = fileData.getOMElement();
                AXIOMXPath xpathExpr = new AXIOMXPath(xpathOfElement);
                OMElement parent = (OMElement) xpathExpr.selectSingleNode(sgElement);
                parent.addAttribute(attr);
            } else {
                log.error("put attr = " + attr.getAttributeType() + attr.getAttributeValue());
                throw new PersistenceDataNotFoundException("ResourceFileData not found. " +
                        "The Transaction May not have been initialized. see #beginTransaction. " + resourceName +
                        "The xpath used was " + xpathOfElement);
            }
        } catch (JaxenException e) {
            log.error("XPath syntaxt error " + xpathOfElement, e);
            throw new PersistenceDataNotFoundException("XPath syntaxt error for " + resourceName + xpathOfElement, e);
        }
    }

    public abstract boolean fileExists(String resourceId);

    public abstract boolean elementExists(String resourceId, String elementXpathStr);

    public List getAll(String resourceId, String xpathStr) throws PersistenceDataNotFoundException {
        try {
            ResourceFileData fileData = resourceMap.get(resourceId);
            //If a transaction is in progress. Just use the in-memory OM.
            if (fileData != null && fileData.isTransactionStarted()) {
                OMElement resourceElement = fileData.getOMElement();
                AXIOMXPath xpathExpr = new AXIOMXPath(xpathStr);
                return xpathExpr.selectNodes(resourceElement);
            } else {
                File resourceFile = new File(metafilesDir, resourceId + Resources.METAFILE_EXTENSION);
                if (resourceFile.exists()) {
                    FileInputStream fis = new FileInputStream(resourceFile);
                    XMLStreamReader reader = xif.createXMLStreamReader(fis);

                    StAXOMBuilder builder = new StAXOMBuilder(reader);
                    OMElement resourceOMElement = builder.getDocumentElement();

                    /**
                     * xpath expression behaves differently when there is a OMDocument holding the OMElement.
                     * So, to have one expression work everywhere, we detach the element if the parent has the
                     * type OMDocument
                     */
                    if (resourceOMElement.getParent() instanceof OMDocument) {
                        resourceOMElement.detach();
                    }
                    AXIOMXPath xpathExpr = new AXIOMXPath(xpathStr);
                    return xpathExpr.selectNodes(resourceOMElement);
                }
            }
        } catch (JaxenException e) {
            log.error("Error parsing xpath string " + resourceId + xpathStr, e);
            throw new PersistenceDataNotFoundException("Error parsing xpath string ", e);
        } catch (FileNotFoundException e) {
            log.error("metafile for resource " + resourceId + " not found. ", e);
            throw new PersistenceDataNotFoundException("metafile for resource " + resourceId + " not found. ", e);
        } catch (XMLStreamException e) {
            log.error("XMLStreamException " + resourceId + " not found. ", e);
            throw new PersistenceDataNotFoundException("XMLStreamException " + resourceId + " not found. ", e);
        }
        return new ArrayList(0);
    }

    /**
     * @param resourceId module name or service group name as applicable
     * @param xpathStr   xpath to the element which needs to be deleted
     * @throws PersistenceDataNotFoundException
     *
     */
    public abstract void delete(String resourceId, String xpathStr) throws PersistenceDataNotFoundException;

    /**
     * Handles exception and rollbacks an already started transaction. Don't use this method if
     * you haven't already started a registry transaction
     * <p/>
     * For a serviceGroup or service or operation we need the resourceId of serviceGroup.
     * For modules, we need the module id.
     *
     * @param resourceId The id/name of resource
     * @param msg        - Message to log
     * @param e          - original exception
     * @throws PersistenceException
     */
    protected void handleExceptionWithRollback(String resourceId, String msg, Throwable e) throws
            PersistenceException {
        log.error(msg, e);
        rollbackTransaction(resourceId);
        throw new PersistenceException(msg, e);
    }

    protected void handleException(String msg, Throwable e) throws PersistenceException {
        log.error(msg, e);
        throw new PersistenceException(msg, e);
    }

    protected void handleException(String msg) throws PersistenceException {
        log.error(msg);
        throw new PersistenceException(msg);
    }

    public abstract boolean isTransactionStarted(String resourceId);

}
