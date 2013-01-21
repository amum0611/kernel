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
package org.wso2.carbon.user.core.claim;

import org.wso2.carbon.user.api.*;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.claim.dao.ClaimDAO;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultClaimManager implements ClaimManager {

    private Map<String, ClaimMapping> claimMapping = new ConcurrentHashMap<String, ClaimMapping>();
    private ClaimDAO claimDAO = null;
    
    /**
     * 
     * @param claimMapping
     */
    public DefaultClaimManager(Map<String, ClaimMapping> claimMapping, DataSource dataSource,
            int tenantId) {
        this.claimMapping = new ConcurrentHashMap<String, ClaimMapping>();
        this.claimMapping.putAll(claimMapping);
        this.claimDAO = new ClaimDAO(dataSource, tenantId);
    }

    /**
     * {@inheritDoc}
     */
    public String getAttributeName(String claimURI) throws UserStoreException {
        ClaimMapping mapping = claimMapping.get(claimURI);
        if (mapping != null) {
            return mapping.getMappedAttribute();
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public Claim getClaim(String claimURI) throws UserStoreException {
        ClaimMapping mapping = claimMapping.get(claimURI);
        if (mapping != null) {
            return mapping.getClaim();
        }
        return null;
    }

    public ClaimMapping getClaimMapping(String claimURI) throws UserStoreException {
        return claimMapping.get(claimURI);
    }

    /**
     * {@inheritDoc}
     */
    public Claim[] getAllSupportClaimsByDefault() throws UserStoreException {
        List<Claim> claimList = new ArrayList<Claim>();
        Iterator<Entry<String, ClaimMapping>> iterator = claimMapping.entrySet().iterator();

        for (; iterator.hasNext();) {
            ClaimMapping claimMapping = iterator.next().getValue();
            Claim claim = claimMapping.getClaim();
            if (claim.isSupportedByDefault()) {
                claimList.add(claim);
            }
        }

        return claimList.toArray(new Claim[claimList.size()]);
    }

    /**
     * {@inheritDoc}
     */
    public Claim[] getAllClaims() throws UserStoreException {
        List<Claim> claimList = null;
        claimList = new ArrayList<Claim>();
        Iterator<Entry<String, ClaimMapping>> iterator = claimMapping.entrySet().iterator();

        for (; iterator.hasNext();) {
            ClaimMapping claimMapping = iterator.next().getValue();
            Claim claim = claimMapping.getClaim();
            claimList.add(claim);
        }
        return claimList.toArray(new Claim[claimList.size()]);
    }

    /**
     * {@inheritDoc}
     */
    public Claim[] getAllClaims(String dialectUri) throws UserStoreException {
        List<Claim> claimList = null;
        claimList = new ArrayList<Claim>();
        Iterator<Entry<String, ClaimMapping>> iterator = claimMapping.entrySet().iterator();

        for (; iterator.hasNext();) {
            ClaimMapping claimMapping = iterator.next().getValue();
            Claim claim = claimMapping.getClaim();
            if (claim.getDialectURI().equals(dialectUri)) {
                claimList.add(claim);
            }
        }
        return claimList.toArray(new Claim[claimList.size()]);
    }

    /**
     * {@inheritDoc}
     */
    public Claim[] getAllRequiredClaims() throws UserStoreException {
        List<Claim> claimList = null;
        claimList = new ArrayList<Claim>();
        Iterator<Entry<String, ClaimMapping>> iterator = claimMapping.entrySet().iterator();

        for (; iterator.hasNext();) {
            Claim claim = (Claim) iterator.next().getValue().getClaim();
            if (claim.isRequired()) {
                claimList.add(claim);
            }
        }

        return claimList.toArray(new Claim[claimList.size()]);
    }

    public String[] getAllClaimUris() throws UserStoreException {
        return claimMapping.keySet().toArray(new String[claimMapping.size()]);
    }

    public void addNewClaimMapping(org.wso2.carbon.user.api.ClaimMapping mapping)
            throws org.wso2.carbon.user.api.UserStoreException {
        addNewClaimMapping((ClaimMapping) mapping);
    }

    /**
     * {@inheritDoc}
     */
    public void addNewClaimMapping(ClaimMapping mapping) throws UserStoreException {
        if (mapping != null && mapping.getClaim() != null) {
            if (!claimMapping.containsKey(mapping.getClaim().getClaimUri())) {
                claimMapping.put(mapping.getClaim().getClaimUri(), mapping);
                claimDAO.addClaimMapping(mapping);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void deleteClaimMapping(org.wso2.carbon.user.api.ClaimMapping mapping)
            throws UserStoreException {
        if (mapping != null && mapping.getClaim() != null) {
            if (claimMapping.containsKey(mapping.getClaim().getClaimUri())) {
                claimMapping.remove(mapping.getClaim().getClaimUri());
                claimDAO.deleteClaimMapping((ClaimMapping) mapping);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void updateClaimMapping(org.wso2.carbon.user.api.ClaimMapping mapping)
            throws UserStoreException {
        if (mapping != null && mapping.getClaim() != null) {
            if (claimMapping.containsKey(mapping.getClaim().getClaimUri())) {
                claimMapping.put(mapping.getClaim().getClaimUri(), (ClaimMapping) mapping);
                claimDAO.updateClaim((ClaimMapping) mapping);
            }
        }
    }
}
