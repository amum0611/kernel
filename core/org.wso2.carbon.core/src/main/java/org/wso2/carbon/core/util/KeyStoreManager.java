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
package org.wso2.carbon.core.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.CarbonException;
import org.wso2.carbon.base.api.ServerConfigurationService;
import org.wso2.carbon.core.RegistryResources;
import org.wso2.carbon.core.internal.CarbonCoreDataHolder;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.session.UserRegistry;
import org.wso2.carbon.utils.CarbonUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The purpose of this class is to centrally manage the key stores.
 * Load key stores only once.
 * Reloading them over and over result a in a performance penalty.
 */
public class KeyStoreManager {

    private KeyStore primaryKeyStore = null;
    private static ConcurrentHashMap<String, KeyStoreManager> mtKeyStoreManagers =
            new ConcurrentHashMap<String, KeyStoreManager>();
    private static Log log = LogFactory.getLog(KeyStoreManager.class);

    private UserRegistry registry = null;
    private ConcurrentHashMap<String, KeyStoreBean> loadedKeyStores = null;
    private int tenantId = 0;

    /**
     * The private constructor of KeyStoreManager. This class implements a Singleton
     *
     * @param userRegistry governance registry instance
     */
    private KeyStoreManager(UserRegistry userRegistry) {
        loadedKeyStores = new ConcurrentHashMap<String, KeyStoreBean>();
        if (userRegistry == null) {
            try {
                registry = CarbonCoreDataHolder.getRegistryService().getGovernanceSystemRegistry();
            } catch (Exception e) {
                String message = "Error when retrieving the system governance registry";
                log.error(message, e);
            }
        } else {
            registry = userRegistry;
            tenantId = userRegistry.getTenantId();
        }
    }


    /**
     * Get a KeyStoreManager instance for that tenant. This method will return an KeyStoreManager
     * instance if exists, or creates a new one
     *
     * @param userRegistry Governance Registry instance of the corresponding tenant
     * @return KeyStoreManager instance for that tenant
     */
    public static KeyStoreManager getInstance(UserRegistry userRegistry) {
        CarbonUtils.checkSecurity();
        String tenantId = "0";
        if (userRegistry != null) {
            tenantId = Integer.valueOf(userRegistry.getTenantId()).toString();
        }
        if (!mtKeyStoreManagers.containsKey(tenantId)) {
            mtKeyStoreManagers.put(tenantId, new KeyStoreManager(userRegistry));
        }
        return mtKeyStoreManagers.get(tenantId);
    }

    /**
     * Get the key store object for the given key store name
     *
     * @param keyStoreName key store name
     * @return KeyStore object
     * @throws Exception If there is not a key store with the given name
     */
    public KeyStore getKeyStore(String keyStoreName) throws Exception {

        if (KeyStoreUtil.isPrimaryStore(keyStoreName)) {
            return getPrimaryKeyStore();
        }

        if (isCachedKeyStoreValid(keyStoreName)) {
            return loadedKeyStores.get(keyStoreName).getKeyStore();
        }

        String path = RegistryResources.SecurityManagement.KEY_STORES + "/" + keyStoreName;
        if (registry.resourceExists(path)) {
            Resource resource = registry.get(path);
            byte[] bytes = (byte[]) resource.getContent();
            KeyStore keyStore = KeyStore.getInstance(resource
                                                             .getProperty(RegistryResources.SecurityManagement.PROP_TYPE));
            CryptoUtil cryptoUtil = CryptoUtil.getDefaultCryptoUtil();
            String encryptedPassword = resource
                    .getProperty(RegistryResources.SecurityManagement.PROP_PASSWORD);
            String password = new String(cryptoUtil.base64DecodeAndDecrypt(encryptedPassword));
            ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
            keyStore.load(stream, password.toCharArray());
            KeyStoreBean keyStoreBean = new KeyStoreBean(keyStore, resource.getLastModified());
            resource.discard();

            if (loadedKeyStores.containsKey(keyStoreName)) {
                loadedKeyStores.replace(keyStoreName, keyStoreBean);
            } else {
                loadedKeyStores.put(keyStoreName, keyStoreBean);
            }
            return keyStore;
        } else {
            throw new SecurityException("Key Store with a name : " + keyStoreName + " does not exist.");
        }
    }

    /**
     * This method loads the private key of a given key store
     *
     * @param keyStoreName name of the key store
     * @param alias        alias of the private key
     * @return private key corresponding to the alias
     */
    public Key getPrivateKey(String keyStoreName, String alias) {
        try {
            if (KeyStoreUtil.isPrimaryStore(keyStoreName)) {
                return getDefaultPrivateKey();
            }

            String path = RegistryResources.SecurityManagement.KEY_STORES + "/" + keyStoreName;
            Resource resource;
            KeyStore keyStore;

            if (registry.resourceExists(path)) {
                resource = registry.get(path);
            } else {
                throw new SecurityException("Given Key store is not available in registry : " + keyStoreName);
            }

            CryptoUtil cryptoUtil = CryptoUtil.getDefaultCryptoUtil();
            String encryptedPassword = resource
                    .getProperty(RegistryResources.SecurityManagement.PROP_PRIVATE_KEY_PASS);
            String privateKeyPasswd = new String(cryptoUtil.base64DecodeAndDecrypt(encryptedPassword));

            if (isCachedKeyStoreValid(keyStoreName)) {
                keyStore = loadedKeyStores.get(keyStoreName).getKeyStore();
                return keyStore.getKey(alias, privateKeyPasswd.toCharArray());
            } else {
                byte[] bytes = (byte[]) resource.getContent();
                String keyStorePassword = new String(cryptoUtil.base64DecodeAndDecrypt(resource.getProperty(
                        RegistryResources.SecurityManagement.PROP_PASSWORD)));
                keyStore = KeyStore.getInstance(resource
                                                        .getProperty(RegistryResources.SecurityManagement.PROP_TYPE));
                ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
                keyStore.load(stream, keyStorePassword.toCharArray());

                KeyStoreBean keyStoreBean = new KeyStoreBean(keyStore, resource.getLastModified());
                updateKeyStoreCache(keyStoreName, keyStoreBean);
                return keyStore.getKey(alias, privateKeyPasswd.toCharArray());
            }
        } catch (Exception e) {
            log.error("Error loading the private key from the key store : " + keyStoreName);
            throw new SecurityException("Error loading the private key from the key store : " +
                                        keyStoreName, e);
        }
    }

    /**
     * Get the key store password of the given key store resource
     *
     * @param resource key store resource
     * @return password of the key store
     * @throws Exception Error when reading the registry resource of decrypting the password
     */
    public String getPassword(Resource resource) throws Exception {
        CryptoUtil cryptoUtil = CryptoUtil.getDefaultCryptoUtil();
        String encryptedPassword = resource
                .getProperty(RegistryResources.SecurityManagement.PROP_PRIVATE_KEY_PASS);
        return new String(cryptoUtil.base64DecodeAndDecrypt(encryptedPassword));

    }

    /**
     * Update the key store with the given name using the modified key store object provided.
     *
     * @param name     key store name
     * @param keyStore modified key store object
     * @throws Exception Registry exception or Security Exception
     */
    public void updateKeyStore(String name, KeyStore keyStore) throws Exception {
        ServerConfigurationService config = CarbonCoreDataHolder.getServerConfigurationService();

        if (KeyStoreUtil.isPrimaryStore(name)) {
            String file = new File(
                    config
                            .getFirstProperty(RegistryResources.SecurityManagement.SERVER_PRIMARY_KEYSTORE_FILE))
                    .getAbsolutePath();
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(file);
                String password = config
                        .getFirstProperty(RegistryResources.SecurityManagement.SERVER_PRIMARY_KEYSTORE_PASSWORD);
                keyStore.store(out, password.toCharArray());
            } finally {
                if (out != null) {
                    out.close();
                }
            }
            return;
        }

        String path = RegistryResources.SecurityManagement.KEY_STORES + "/" + name;

        Resource resource = registry.get(path);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        CryptoUtil cryptoUtil = CryptoUtil.getDefaultCryptoUtil();
        String encryptedPassword = resource
                .getProperty(RegistryResources.SecurityManagement.PROP_PASSWORD);
        String password = new String(cryptoUtil.base64DecodeAndDecrypt(encryptedPassword));
        keyStore.store(outputStream, password.toCharArray());
        outputStream.flush();
        outputStream.close();

        resource.setContent(outputStream.toByteArray());

        registry.put(path, resource);
        resource.discard();
        updateKeyStoreCache(name, new KeyStoreBean(keyStore, new Date()));
    }

    /**
     * Load the primary key store, this is allowed only for the super tenant
     *
     * @return primary key store object
     * @throws Exception Carbon Exception when trying to call this method from a tenant other
     *                   than tenant 0
     */
    public KeyStore getPrimaryKeyStore() throws Exception {
        if (tenantId == 0) {
            if (primaryKeyStore == null) {

                ServerConfigurationService config = CarbonCoreDataHolder.getServerConfigurationService();
                String file =
                        new File(config
                                         .getFirstProperty(RegistryResources.SecurityManagement.SERVER_PRIMARY_KEYSTORE_FILE))
                                .getAbsolutePath();
                KeyStore store = KeyStore
                        .getInstance(config
                                             .getFirstProperty(RegistryResources.SecurityManagement.SERVER_PRIMARY_KEYSTORE_TYPE));
                String password = config
                        .getFirstProperty(RegistryResources.SecurityManagement.SERVER_PRIMARY_KEYSTORE_PASSWORD);
                FileInputStream in = null;
                try {
                    in = new FileInputStream(file);
                    store.load(in, password.toCharArray());
                    primaryKeyStore = store;
                } finally {
                    if (in != null) {
                        in.close();
                    }
                }
            }
            return primaryKeyStore;
        } else {
            throw new CarbonException("Permission denied for accessing primary key store");
        }
    }

    /**
     * Get the default private key, only allowed for tenant 0
     *
     * @return Private key
     * @throws Exception Carbon Exception for tenants other than tenant 0
     */
    public PrivateKey getDefaultPrivateKey() throws Exception {
        if (tenantId == 0) {
            ServerConfigurationService config = CarbonCoreDataHolder.getServerConfigurationService();
            String password = config
                    .getFirstProperty(RegistryResources.SecurityManagement.SERVER_PRIMARY_KEYSTORE_PASSWORD);
            String alias = config
                    .getFirstProperty(RegistryResources.SecurityManagement.SERVER_PRIMARY_KEYSTORE_KEY_ALIAS);
            return (PrivateKey) primaryKeyStore.getKey(alias, password.toCharArray());
        }
        throw new CarbonException("Permission denied for accessing primary key store");
    }

    /**
     * Get default pub. key
     *
     * @return Public Key
     * @throws Exception Exception Carbon Exception for tenants other than tenant 0
     */
    public PublicKey getDefaultPublicKey() throws Exception {
        if (tenantId == 0) {
            ServerConfigurationService config = CarbonCoreDataHolder.getServerConfigurationService();
            String alias = config
                    .getFirstProperty(RegistryResources.SecurityManagement.SERVER_PRIMARY_KEYSTORE_KEY_ALIAS);
            return (PublicKey) primaryKeyStore.getCertificate(alias).getPublicKey();
        }
        throw new CarbonException("Permission denied for accessing primary key store");
    }

    /**
     * Get the private key password
     *
     * @return private key password
     * @throws CarbonException Exception Carbon Exception for tenants other than tenant 0
     */
    public String getPrimaryPrivateKeyPasssword() throws CarbonException {
        if (tenantId == 0) {
            ServerConfigurationService config = CarbonCoreDataHolder.getServerConfigurationService();
            return config
                    .getFirstProperty(RegistryResources.SecurityManagement.SERVER_PRIMARY_KEYSTORE_PASSWORD);
        }
        throw new CarbonException("Permission denied for accessing primary key store");
    }

    /**
     * This method is used to load the default public certificate of the primary key store
     *
     * @return Default public certificate
     * @throws Exception Permission denied for accessing primary key store
     */
    public X509Certificate getDefaultPrimaryCertificate() throws Exception {
        if (tenantId == 0) {
            ServerConfigurationService config = CarbonCoreDataHolder.getServerConfigurationService();
            String alias = config
                    .getFirstProperty(RegistryResources.SecurityManagement.SERVER_PRIMARY_KEYSTORE_KEY_ALIAS);
            return (X509Certificate) getPrimaryKeyStore().getCertificate(alias);
        }
        throw new CarbonException("Permission denied for accessing primary key store");
    }

    private boolean isCachedKeyStoreValid(String keyStoreName) {
        String path = RegistryResources.SecurityManagement.KEY_STORES + "/" + keyStoreName;
        boolean cachedKeyStoreValid = false;
        try {
            if (loadedKeyStores.containsKey(keyStoreName)) {
                Resource metaDataResource = registry.get(path);
                KeyStoreBean keyStoreBean = loadedKeyStores.get(keyStoreName);
                if (keyStoreBean.getLastModifiedDate().equals(metaDataResource.getLastModified())) {
                    cachedKeyStoreValid = true;
                }
            }
        } catch (RegistryException e) {
            String errorMsg = "Error reading key store meta data from registry.";
            log.error(errorMsg, e);
            throw new SecurityException(errorMsg, e);
        }
        return cachedKeyStoreValid;
    }

    private void updateKeyStoreCache(String keyStoreName, KeyStoreBean keyStoreBean) {
        if (loadedKeyStores.containsKey(keyStoreName)) {
            loadedKeyStores.replace(keyStoreName, keyStoreBean);
        } else {
            loadedKeyStores.put(keyStoreName, keyStoreBean);
        }
    }

    public KeyStore loadKeyStoreFromFileSystem(String keyStorePath, String password, String type) {
        CarbonUtils.checkSecurity();
        String absolutePath = new File(keyStorePath).getAbsolutePath();
        FileInputStream inputStream = null;
        try {
            KeyStore store = KeyStore.getInstance(type);
            inputStream = new FileInputStream(absolutePath);
            store.load(inputStream, password.toCharArray());
            return store;
        } catch (Exception e) {
            String errorMsg = "Error loading the key store from the given location.";
            log.error(errorMsg);
            throw new SecurityException(errorMsg, e);
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                log.warn("Error when closing the input stream.", e);
            }
        }
    }
}
