/*
 * Copyright 2005-2007 WSO2, Inc. (http://wso2.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.user.api;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;


/**
 * The object representing the realm configuration.
 */
public class RealmConfiguration {
    
    protected String userStoreClass = null;
    protected String authorizationManagerClass = null;
    protected String adminRoleName = null;
    protected String adminUserName = null;
    protected String adminPassword = null;
    protected String everyOneRoleName = null;
    protected String realmClassName = null;
    protected Map<String, String> userStoreProperties = new HashMap<String, String>();
    protected Map<String, String> authzProperties = new HashMap<String, String>();
    protected Map<String, String> realmProperties = new HashMap<String, String>();
    protected int tenantId;
    protected Date persistedTimestamp;
    protected boolean passwordsExternallyManaged = false;
    protected RealmConfiguration secondaryRealmConfig;

    public boolean isPasswordsExternallyManaged() {
        return passwordsExternallyManaged;
    }

    public void setPasswordsExternallyManaged(boolean passwordsExternallyManaged) {
        this.passwordsExternallyManaged = passwordsExternallyManaged;
    }

    public RealmConfiguration() {
        tenantId = -1234;
    }
    
    public RealmConfiguration cloneRealmConfiguration() throws Exception {
        RealmConfiguration realmConfig = new RealmConfiguration();
                
        realmConfig.setRealmClassName(realmClassName);
        realmConfig.setUserStoreClass(userStoreClass);
        realmConfig.setAuthorizationManagerClass(authorizationManagerClass);
        realmConfig.setAdminRoleName(adminRoleName);
        realmConfig.setAdminUserName(adminUserName);
        realmConfig.setAdminPassword(adminPassword);
        realmConfig.setEveryOneRoleName(everyOneRoleName);
	
        if (secondaryRealmConfig != null) {
			realmConfig.setSecondaryRealmConfig(secondaryRealmConfig
					.cloneRealmConfiguration());
		}

        Map<String, String> mapUserstore = new HashMap<String, String>();
        mapUserstore.putAll(userStoreProperties);
        realmConfig.setUserStoreProperties(mapUserstore);
        
        Map<String, String> mapAuthz = new HashMap<String, String>();
        mapAuthz.putAll(authzProperties);
        realmConfig.setAuthzProperties(mapAuthz);
        
        Map<String, String> mapRealm = new HashMap<String, String>();
        mapRealm.putAll(realmProperties);
        realmConfig.setRealmProperties(mapRealm);
        
        return realmConfig;
    }	
	
	public RealmConfiguration getSecondaryRealmConfig() {
		return secondaryRealmConfig;
	}

	public void setSecondaryRealmConfig(RealmConfiguration secondaryRealm) {
		this.secondaryRealmConfig = secondaryRealm;
	}

	public void setRealmClassName(String realmClassName) {
        this.realmClassName = realmClassName;
    }

    public String getAuthorizationPropertyValue(String propertyName) {
        return authzProperties.get(propertyName);
    }

    public String getRealmProperty(String propertyName) {
        return realmProperties.get(propertyName);
    }

    public String getUserStoreProperty(String propertyName){
        return userStoreProperties.get(propertyName);
    }
    
    public String getAdminRoleName() {
        return adminRoleName;
    }

    public String getAdminUserName() {
        return adminUserName;
    }

    public String getAdminPassword() {
        return adminPassword;
    }
    
    public String getEveryOneRoleName() {
        return everyOneRoleName;
    }
   
    public String getAuthorizationManagerClass() {
        return authorizationManagerClass;
    }
        
      
    public String getAuthorizationManagerProperty(String key){
        return authzProperties.get(key); 
    }
    
    public String getUserStoreClass() {
        return userStoreClass;
    }


    public Map<String, String> getUserStoreProperties() {
        return userStoreProperties;
    }


    public Map<String, String> getAuthzProperties() {
        return authzProperties;
    }


    public Map<String, String> getRealmProperties() {
        return realmProperties;
    }
    
    public void setAdminRoleName(String adminRoleName) {
        this.adminRoleName = adminRoleName;
    }

    public void setEveryOneRoleName(String everyOneRoleName) {
        this.everyOneRoleName = everyOneRoleName;
    }

    public void setAuthzProperties(Map<String, String> authzProperties) {
        this.authzProperties = authzProperties;
    }

    public void setRealmProperties(Map<String, String> realmProperties) {
        this.realmProperties = realmProperties;
    }

    public void setAuthorizationManagerClass(String authorizationManagerClass) {
        this.authorizationManagerClass = authorizationManagerClass;
    }


    public void setUserStoreClass(String userStoreClass) {
        this.userStoreClass = userStoreClass;
    }


    public void setUserStoreProperties(Map<String, String> userStoreProperties) {
        this.userStoreProperties = userStoreProperties;
    }

    // two public setter methods used for external editing
    public void setAdminUserName(String adminUserName) {
        this.adminUserName = adminUserName;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public int getTenantId() {
        return tenantId;
    }

    public void setTenantId(int tenantId) {
        this.tenantId = tenantId;
    }

    public Date getPersistedTimestamp() {
        if (null != persistedTimestamp) {
            return (Date)persistedTimestamp.clone();
        } else {
            return null;
        }        
    }

    public void setPersistedTimestamp(Date persistedTimestamp) {
        if (null != persistedTimestamp) {
            this.persistedTimestamp = (Date)persistedTimestamp.clone();
        } else {
            this.persistedTimestamp = null;
        }

    }
    
    public String getRealmClassName() {
        if(this.realmClassName == null) {
            return "org.wso2.carbon.user.core.common.DefaultRealm";
        }
        return realmClassName;
    }
    
}
