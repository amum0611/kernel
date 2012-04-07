/*
 * Copyright (c) 2008, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.registry.synchronization.operation;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMText;
import org.apache.axiom.om.util.Base64;
import org.apache.commons.io.FileUtils;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.utils.RegistryUtils;
import org.wso2.carbon.registry.synchronization.SynchronizationConstants;
import org.wso2.carbon.registry.synchronization.SynchronizationException;
import org.wso2.carbon.registry.synchronization.UserInputCallback;
import org.wso2.carbon.registry.synchronization.Utils;
import org.wso2.carbon.registry.synchronization.message.Message;
import org.wso2.carbon.registry.synchronization.message.MessageCode;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.*;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.ZipInputStream;

/**
 * This command is used to perform a check-in operation which will upload the files and directories
 * from the local filesystem into the provided registry instance.
 */
@SuppressWarnings({"ResultOfMethodCallIgnored", "unused"})
public class CheckInCommand {

    private String inputFile = null;
    private String workingDir = null;
    private String registryUrl = null;
    private String checkInPath = null;
    private String userUrl = null;
    private String username = null;
    private boolean ignoreConflicts = true;
    private boolean cleanRegistry = false;
    private List<String> filesToClean = new LinkedList<String>();
    private boolean testContentChanged = true;

    ////////////////////////////////////////////////////////
    // Fields maintaining status of command execution
    ////////////////////////////////////////////////////////

    private int sentCount = -1;

    /**
     * Creates an instance of a check-in command which can be executed against a provided registry
     * instance.
     *
     * @param inputFile          if the content is to be uploaded from a single meta file, this
     *                           parameter can be used to specify the path to the meta file.
     * @param workingDir         if the content is to be uploaded from a directory on the
     *                           filesystem, this parameter can be used to specify the path to the
     *                           corresponding location.
     * @param userUrl            aggregate URL containing a concatenation of the registry URL and
     *                           the resource path that is capable of referencing a remote resource.
     *                           This url will contain only the resource path if the resource was
     *                           local to the given registry instance.
     * @param username           the name of the user (which should be a valid username on the
     *                           target server on which the provided registry instance is running)
     *                           that performs this operation.
     * @param ignoreConflicts    ignore conflicts in the server side
     * @param cleanRegistry      whether the embedded registry instance must be cleaned after the
     *                           execution of the operation.
     * @param testContentChanged when this parameter is set to true, check-in will only happen if
     *                           the content has changed.
     *
     * @throws SynchronizationException if the operation failed.
     */
    public CheckInCommand(String inputFile,
                          String workingDir,
                          String userUrl,
                          String username,
                          boolean ignoreConflicts,
                          boolean cleanRegistry,
                          boolean testContentChanged) throws SynchronizationException {
        // now if the user url is different to the registry url we are going to consider that as
        // well.

        this.inputFile = inputFile;
        this.workingDir = workingDir;
        this.userUrl = userUrl;
        this.username = username;
        this.ignoreConflicts = ignoreConflicts;
        this.cleanRegistry = cleanRegistry;
        this.testContentChanged = testContentChanged;

        // get the update details form the meta element of the current checkout
        OMElement metaOMElement = Utils.getMetaOMElement(workingDir);
        if (metaOMElement != null) {
            checkInPath = metaOMElement.getAttributeValue(new QName("path"));
        }

        if (userUrl != null) {
            registryUrl = Utils.getRegistryUrl(userUrl);
            String suggestedCheckInPath = Utils.getPath(userUrl);
            if (suggestedCheckInPath == null || suggestedCheckInPath.equals("")) {
                suggestedCheckInPath = "/";
                // we are converting the root path to the current directory of the file system
            }
            if (!suggestedCheckInPath.equals(checkInPath)) {
                this.testContentChanged = false;
                checkInPath = suggestedCheckInPath;
            }
        } else {
            if (metaOMElement == null) {
                throw new SynchronizationException(MessageCode.CHECKOUT_BEFORE_CHECK_IN);
            }
            registryUrl = metaOMElement.getAttributeValue(new QName("registryUrl"));
        }
    }

    /**
     * Method to obtain the count of files sent.
     *
     * @return the count of files sent.
     */
    public int getSentCount() {
        return sentCount;
    }

    /**
     * This method will execute the check-in command utilizing the various parameters passed when
     * creating the instance of the command. This method accepts the users preference if a deletion
     * of a file or directory is required in the process.
     *
     * @param registry the registry instance to be used.
     * @param callback the instance of a callback that can be used to determine the user's
     *                 preference before deleting an existing file or directory during the update
     *                 after the check-in has been done. If this parameter is null, the default
     *                 behaviour of deleting the existing file will be used.
     *
     * @throws SynchronizationException if the operation failed.
     */
    public void execute(Registry registry, UserInputCallback callback)
            throws SynchronizationException {
        if (inputFile != null) {
            // restore a single file.
            restoreFromFile(registry);
        } else {
            // restore from a file system only if the content did not change.
            if (!testContentChanged || (workingDir != null
                    && Utils.contentChanged(new File(workingDir)))) {
                restoreFromFileSystem(registry, callback);
            }
        }
    }

    /**
     * This method will execute the check-in command utilizing the various parameters passed when
     * creating the instance of the command.
     *
     * @param registry the registry instance to be used.
     *
     * @throws SynchronizationException if the operation failed.
     */
    public void execute(Registry registry) throws SynchronizationException {
        execute(registry, null);
    }

    // Restores the given resources and collections from a dump file.
    private void restoreFromFile(Registry registry) throws SynchronizationException {
        String workingDir = this.workingDir;

        if (workingDir != null) {
            inputFile = workingDir + File.separator + inputFile;
        }

        // do the restoring
        try {
            ZipInputStream zis = new ZipInputStream(new FileInputStream(inputFile));
            zis.getNextEntry();
            Reader reader = new InputStreamReader(zis);
            registry.restore(checkInPath, reader);
        } catch (FileNotFoundException e) {
            throw new SynchronizationException(MessageCode.FILE_DOES_NOT_EXIST, e,
                    new String[]{"Output file" + inputFile});
        } catch (Exception e) {
            if(e.getCause() instanceof UnknownHostException) {
                  throw new SynchronizationException(MessageCode.ERROR_IN_CONNECTING_REGISTRY, e,
                        new String[] {" registry url:" + registryUrl});
            }
            throw new SynchronizationException(MessageCode.ERROR_IN_RESTORING, e,
                    new String[]{"path: " + checkInPath,
                            "registry url: " + registryUrl,
                            "username: " + username});
        }

        if (cleanRegistry && registryUrl == null) {
            Utils.cleanEmbeddedRegistry();
        }
    }

    // Restores the given resources and collections from files and folders on the filesystem.
    private void restoreFromFileSystem(Registry registry, UserInputCallback callback)
            throws SynchronizationException {
        sentCount = 0;

        // we are doing the check-in through a temp file. (so assumed enough spaces are there)
        File tempFile = null;
        boolean deleteTempFileFailed = false;
        XMLStreamWriter xmlWriter = null;
        Writer writer = null;
        try {
            try {
                tempFile = File.createTempFile(SynchronizationConstants.DUMP_META_FILE_NAME,
                        SynchronizationConstants.META_FILE_EXTENSION);

                try {
                    writer = new FileWriter(tempFile);
                    // wrap the writer with an xml stream writer
                    xmlWriter = XMLOutputFactory.newInstance().createXMLStreamWriter(writer);
                    // prepare the dump xml
                    xmlWriter.writeStartDocument();
                    createMetaElement(xmlWriter, workingDir, checkInPath, callback);
                    xmlWriter.writeEndDocument();
                } finally {
                    try {
                        if (xmlWriter != null) {
                            xmlWriter.close();
                        }
                    } finally {
                        if (writer != null) {
                            writer.close();
                        }
                    }
                }
            } catch (IOException e) {
                throw new SynchronizationException(
                        MessageCode.ERROR_IN_CREATING_TEMP_FILE_FOR_DUMP,
                        e);
            } catch (XMLStreamException e) {
                throw new SynchronizationException(
                        MessageCode.ERROR_IN_CREATING_XML_STREAM_WRITER, e);
            }

            // do the restoring
            try {
                Reader reader = null;
                try {
                    reader = new FileReader(tempFile);
                    registry.restore(checkInPath, reader);
                } finally {
                    if (reader != null) {
                        reader.close();
                    }
                }
            } catch (IOException e) {
                throw new SynchronizationException(
                        MessageCode.ERROR_IN_READING_TEMP_FILE_OF_DUMP, e);
            } catch (RegistryException e) {
                throw new SynchronizationException(MessageCode.ERROR_IN_RESTORING, e,
                        new String[]{"path: " + checkInPath,
                                "registry url: " + registryUrl,
                                "username: " + username});
            }
        } finally {
            if (tempFile != null) {
                // Our intention here is to delete the temporary file. We are not bothered whether
                // this operation fails.
                deleteTempFileFailed = !FileUtils.deleteQuietly(tempFile);
            }
        }
        if (deleteTempFileFailed) {
            throw new SynchronizationException(MessageCode.ERROR_IN_CLEANING_UP,
                    new String[]{"file path: " + tempFile.getAbsolutePath()});
        }

        if (cleanRegistry && registryUrl == null) {
            Utils.cleanEmbeddedRegistry();
        }

        // clean all the dangling meta files.
        if (filesToClean != null && filesToClean.size() > 0) {
            for (String filePath : filesToClean) {
                if (!Utils.deleteFile(new File(filePath))) {
                    throw new SynchronizationException(MessageCode.ERROR_IN_CLEANING_UP,
                            new String[]{"file path: " + filePath});
                }
            }
        }

        // at the end we are doing an update too
        UpdateCommand updateCommand =
                new UpdateCommand(inputFile, workingDir, userUrl, true, username, cleanRegistry);
        updateCommand.execute(registry, callback);
        updateCommand.setSilentUpdate(false);
    }

    // Creates the dump element from the given file or directory.
    private void createMetaElement(XMLStreamWriter xmlWriter, String filePath, String path,
                                   UserInputCallback callback)
            throws SynchronizationException, XMLStreamException {
        File file = new File(filePath);
        if (file.isDirectory()) {
            createDirectoryMetaElement(xmlWriter, filePath, path, callback);
        } else {
            createMetaElementForChild(xmlWriter, filePath, path, null, callback);
        }
    }

    // Creates the dump element from the given directory. If the path is not given it is retrieved
    // from the meta file.
    private void createDirectoryMetaElement(XMLStreamWriter xmlWriter, String filePath, String path,
                                            UserInputCallback callback)
            throws SynchronizationException, XMLStreamException {
        // first get the meta file of the directory.
        String metaDirectoryPath = filePath + File.separator +
                SynchronizationConstants.META_DIRECTORY;
        String metaFilePath = metaDirectoryPath + File.separator +
                SynchronizationConstants.META_FILE_PREFIX +
                SynchronizationConstants.META_FILE_EXTENSION;

        // confirm the existence of the meta file.
        OMElement metaElement = Utils.getOMElementFromMetaFile(metaFilePath);
        if (metaElement == null) {
            // will update the meta file in the file system asap it is generated.
            metaElement = Utils.createDefaultMetaFile(true, path, username);
            Utils.createMetaFile(metaFilePath, metaElement);
        }

        // alerting non-backward compatibility...
        String checkoutPathAttribute = metaElement.getAttributeValue(new QName("checkoutPath"));
        if (checkoutPathAttribute != null) {
            throw new SynchronizationException(MessageCode.CHECKOUT_OLD_VERSION);
        }

        // we are re-adjusting the name of the resource to make sure the file name and the resource
        // name is equal
        String resourceName = RegistryUtils.getResourceName(path);
        metaElement.addAttribute("name", resourceName, null);

        // now write the meta data of the meta element to the writer (except children)
        Utils.writeMetaElement(xmlWriter, metaElement);
        if (callback != null) {
            callback.displayMessage(new Message(MessageCode.SENT, new String[]{filePath}));
        }
        sentCount++;

        // now add the child element to the meta element
        xmlWriter.writeStartElement("children");

        File directory = new File(filePath);
        String[] childrenNames = directory.list();
        List<String> filesToPreserve = new LinkedList<String>();
        if (childrenNames != null) {
            for (String childFileName : childrenNames) {
                // Get childFileName of file or directory
                String childResourceName = Utils.decodeFilename(childFileName);

                if (childResourceName.equals(SynchronizationConstants.META_DIRECTORY)) {
                    continue;
                }
                if (childResourceName.endsWith(SynchronizationConstants.MINE_FILE_POSTFIX) ||
                        childResourceName.endsWith(SynchronizationConstants.SERVER_FILE_POSTFIX)) {
                    // there is an conflicts
                    throw new SynchronizationException(MessageCode.RESOLVE_CONFLICTS);
                }

                String childPath;
                String childFilePath;
                if (path.equals("/")) {
                    childPath = "/" + childResourceName;
                } else {
                    childPath = path + "/" + childResourceName;
                }
                childFilePath = filePath + File.separator + childFileName;
                createMetaElementForChild(xmlWriter, childFilePath, childPath, filesToPreserve,
                        callback);
            }
            filesToPreserve.add(getAbsoluteFilePath(metaFilePath));
            filesToClean.addAll(
                    Utils.cleanUpDirectory(new File(metaDirectoryPath), filesToPreserve));
        }
        xmlWriter.writeEndElement(); // to end children tag.
        xmlWriter.writeEndElement(); // to end resource tag.
        xmlWriter.flush();
    }

    // Creates the dump element from the given child file or directory. If the path is not given it
    // is retrieved from the meta file.
    private void createMetaElementForChild(XMLStreamWriter xmlWriter,
                                           String filePath,
                                           String path,
                                           List<String> filesToPreserve,
                                           UserInputCallback callback)
            throws SynchronizationException, XMLStreamException {

        File file = new File(filePath);
        String parentFilePath = file.getParent();
        String filename = file.getName();
        OMElement metaElement;
        if (!file.isDirectory()) {

            String metaFilePath =
                    parentFilePath + File.separator + SynchronizationConstants.META_DIRECTORY +
                            File.separator +
                            SynchronizationConstants.META_FILE_PREFIX + filename +
                            SynchronizationConstants.META_FILE_EXTENSION;
            if (filesToPreserve != null) {
                filesToPreserve.add(getAbsoluteFilePath(metaFilePath));
            }

            // confirm the existence of the meta file.
            metaElement = Utils.getOMElementFromMetaFile(metaFilePath);
            // will update the meta file in the file system asap it is generated.
            if (metaElement == null) {
                metaElement = Utils.createDefaultMetaFile(false, path, username);
                Utils.createMetaFile(metaFilePath, metaElement);
            }

            // we are re-adjusting the name of the resource to make sure the file name and the
            // resource name is equal
            String resourceName = RegistryUtils.getResourceName(path);
            metaElement.addAttribute("name", resourceName, null);
            if (!ignoreConflicts) {
                // we only set the ignoreConflict attribute only if it is failing.
                metaElement.addAttribute("ignoreConflicts", "false", null);
            }

            // now write the meta data of the meta element to the writer (except children)
            Utils.writeMetaElement(xmlWriter, metaElement);
            if (callback != null) {
                callback.displayMessage(new Message(MessageCode.SENT, new String[]{filePath}));
            }
            sentCount++;

            // adding the content
            byte[] content = Utils.getBytesFromFile(file);
            String encodedContent = Base64.encode(content);

            OMFactory factory = OMAbstractFactory.getOMFactory();

            OMElement contentEle = factory.createOMElement(new QName("content"));
            OMText contentText = factory.createOMText(encodedContent);
            contentEle.addChild(contentText);

            contentEle.serialize(xmlWriter);

            xmlWriter.writeEndElement(); // to end resource tag.
            xmlWriter.flush();
        } else {
            createDirectoryMetaElement(xmlWriter, filePath, path, callback);
        }
    }

    // Gets the absolute path of the given file path.
    private String getAbsoluteFilePath(String filePath) {
        return (new File(filePath)).getAbsolutePath();
    }
}
