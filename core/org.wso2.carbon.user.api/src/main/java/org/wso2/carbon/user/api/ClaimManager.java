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
package org.wso2.carbon.user.api;

/**
 * This is the interface to manage claims in the system.
 * 
 * A claim is a name-value pair
 */
public interface ClaimManager {

    /**
     * Retrieves the attribute name of the claim URI.
     * 
     * @param claimURI
     *            The claim URI
     * @return
     * @throws UserStoreException
     */
    public String getAttributeName(String claimURI) throws UserStoreException;

    /**
     * The Claim object of the claim URI
     * 
     * @param claimURI
     *            The claim URI
     * @return
     * @throws UserStoreException
     */
    public Claim getClaim(String claimURI) throws UserStoreException;

    /**
     * Gets the claim mapping.
     * 
     * @param claimURI
     *            The claim URI
     * @return
     * @throws UserStoreException
     */
    public ClaimMapping getClaimMapping(String claimURI) throws UserStoreException;

    /**
     * Gets all supported claims by default in the system.
     * 
     * @return An array of claim objects supported by default
     * @throws UserStoreException
     */
    public Claim[] getAllSupportClaimsByDefault() throws UserStoreException;

    /**
     * Gets all claim objects
     * 
     * @return An array of all claim objects
     * @throws UserStoreException
     */
    public Claim[] getAllClaims() throws UserStoreException;

    /**
     * Gets all claims in the dialect
     * 
     * @param dialectUri
     *            The dialect URI
     * @return
     * @throws UserStoreException
     */
    public Claim[] getAllClaims(String dialectUri) throws UserStoreException;

    /**
     * Gets all mandatory claims
     * 
     * @return
     * @throws UserStoreException
     */
    public Claim[] getAllRequiredClaims() throws UserStoreException;

    /**
     * Gets all claim URIs
     * 
     * @return
     * @throws UserStoreException
     */
    public String[] getAllClaimUris() throws UserStoreException;

    /**
     * Adds a new claim mapping
     * 
     * @param mapping
     *            The claim mapping to be added
     * @throws UserStoreException
     */
    public void addNewClaimMapping(ClaimMapping mapping) throws UserStoreException;

    /**
     * Deletes a claim mapping
     * 
     * @param mapping
     *            The claim mapping to be deleted
     * @throws UserStoreException
     */
    public void deleteClaimMapping(ClaimMapping mapping) throws UserStoreException;

    /**
     * Updates a claim mapping
     * 
     * @param mapping
     *            The claim mapping to be updated
     * @throws UserStoreException
     */
    public void updateClaimMapping(ClaimMapping mapping) throws UserStoreException;

}