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

package org.wso2.carbon.registry.core.test.jdbc;

import org.wso2.carbon.registry.core.Association;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.jdbc.EmbeddedRegistryService;
import org.wso2.carbon.registry.core.test.utils.BaseTestCase;

public class AssociationsTest extends BaseTestCase {

    protected static Registry registry = null;
    protected static Registry governanceRegistry = null;

    protected static EmbeddedRegistryService embeddedRegistryService = null;

    public void setUp() {
        super.setUp();
        if (embeddedRegistryService != null) {
            return;
        }
        try {
            embeddedRegistryService = ctx.getEmbeddedRegistryService();
            RealmUnawareRegistryCoreServiceComponent comp =
                    new RealmUnawareRegistryCoreServiceComponent();
            comp.setRealmService(ctx.getRealmService());
            comp.registerBuiltInHandlers(embeddedRegistryService);
            registry = embeddedRegistryService.getSystemRegistry();
            governanceRegistry = embeddedRegistryService.getGovernanceSystemRegistry();
        } catch (RegistryException e) {
            fail("Failed to initialize the registry. Caused by: " + e.getMessage());
        }
    }

    // todo: uncomment below part after completing the associations
    public void testSettingDependenciesInResources() throws RegistryException {

        Resource r1 = registry.newResource();
        r1.setContent("some content for first r1".getBytes());
        r1.addProperty("url", "http://test.property");
        String path = registry.put("/depTest/test1/r1", r1);
        r1 = registry.get(path);

        Resource r2 = registry.newResource();
        r2.setContent("this is dependent on r1".getBytes());
        path = registry.put("/depTest/test1/r2", r2);
        r2 = registry.get(path);
        registry.addAssociation(r2.getPath(), "/depTest/test1/r1", Association.DEPENDS);
        registry.addAssociation(r1.getPath(), "/depTest/test1/r2", Association.DEPENDS);


        Resource r21 = registry.get("/depTest/test1/r1");
        Resource r22 = registry.get("/depTest/test1/r2");
        Association association[] = registry.getAllAssociations(r21.getPath());
        assertNotNull(association);
        boolean foundForward = false;
        boolean foundBackward = false;
        for (Association association1 : association) {
            if ("/depTest/test1/r2".equals(association1.getDestinationPath())) {
                foundForward = true;
            }
            if ("/depTest/test1/r1".equals(association1.getDestinationPath())) {
                foundBackward = true;
            }
        }
        assertTrue(foundForward && foundBackward);
        association = registry.getAllAssociations(r22.getPath());
        assertNotNull(association);
        foundForward = foundBackward = false;
        for (Association association1 : association) {
            if ("/depTest/test1/r1".equals(association1.getDestinationPath())) {
                foundForward = true;
            }
            if ("/depTest/test1/r2".equals(association1.getDestinationPath())) {
                foundBackward = true;
            }
        }
        assertTrue(foundForward && foundBackward);
    }

    public void testSettingDependenciesFromAPI() throws RegistryException {

        Resource r1 = registry.newResource();
        r1.setContent("some content for second r1".getBytes());
        registry.put("/depTest/test2/r1", r1);

        Resource r2 = registry.newResource();
        r2.setContent("this is dependent on r2".getBytes());
        registry.put("/depTest/test2/r2", r2);

        registry.addAssociation("/depTest/test2/r2", "/depTest/test2/r1", Association.DEPENDS);
        registry.addAssociation("/depTest/test2/r1", "/depTest/test2/r2", Association.DEPENDS);

        Resource r21 = registry.get("/depTest/test2/r1");
        Resource r22 = registry.get("/depTest/test2/r2");
        Association association[] = registry.getAllAssociations(r21.getPath());
        assertNotNull(association);
        boolean found = false;
        for (Association association1 : association) {
            if ("/depTest/test2/r2".equals(association1.getDestinationPath())) {
                found = true;
            }
        }
        assertTrue(found);
        association = registry.getAllAssociations(r22.getPath());
        assertNotNull(association);
        found = false;
        for (Association association1 : association) {
            if ("/depTest/test2/r1".equals(association1.getDestinationPath())) {
                found = true;
            }
        }
        assertTrue(found);
    }

    public void testAssociationsThroughChrootedRegistry() throws RegistryException {

        Resource r1 = registry.newResource();
        r1.setContent("some content for second r1".getBytes());
        registry.put("/_system/governance/depTest/test2/r1", r1);

        Resource r2 = registry.newResource();
        r2.setContent("this is dependent on r2".getBytes());
        registry.put("/depTest/test2/r2", r2);

        registry.addAssociation("/depTest/test2/r2", "/_system/governance/depTest/test2/r1", Association.DEPENDS);
        registry.addAssociation("/_system/governance/depTest/test2/r1", "/depTest/test2/r2", Association.DEPENDS);

        Resource r21 = registry.get("/_system/governance/depTest/test2/r1");
        Resource r22 = registry.get("/depTest/test2/r2");
        Association association[] = registry.getAllAssociations(r21.getPath());
        assertNotNull(association);
        boolean found = false;
        for (Association association1 : association) {
            if ("/depTest/test2/r2".equals(association1.getDestinationPath())) {
                found = true;
            }
        }
        assertTrue(found);
        association = registry.getAllAssociations(r22.getPath());
        assertNotNull(association);
        found = false;
        for (Association association1 : association) {
            if ("/_system/governance/depTest/test2/r1".equals(association1.getDestinationPath())) {
                found = true;
            }
        }
        assertTrue(found);
        association = governanceRegistry.getAllAssociations("/depTest/test2/r1");
        assertNotNull(association);
        for (Association association1 : association) {
            if ("//depTest/test2/r2".equals(association1.getDestinationPath()) ||
                    "//beep/depTest/test2/r2".equals(association1.getDestinationPath())) {
                fail();
            }
        }
    }

    public void testAssociationsAfterMove() throws RegistryException {

        Resource r1 = registry.newResource();
        r1.setContent("r1 content");
        registry.put("/test/move/a1/r1", r1);

        Resource r2 = registry.newResource();
        r2.setContent("r2 content");
        registry.put("/test/move/a1/r2", r2);

        registry.addAssociation("/test/move/a1/r1", "/test/move/a1/r2", "GENERAL");

        registry.move("/test/move/a1/r1", "/test/move/a2/r1");

        Association[] associations = registry.getAllAssociations("/test/move/a2/r1");
        assertEquals("Associations are not moved correctly.", associations.length, 1);
        assertEquals("Associations are not moved correctly.",
                associations[0].getSourcePath(), "/test/move/a2/r1");
        assertEquals("Associations are not moved correctly.",
                associations[0].getDestinationPath(), "/test/move/a1/r2");
    }
}
