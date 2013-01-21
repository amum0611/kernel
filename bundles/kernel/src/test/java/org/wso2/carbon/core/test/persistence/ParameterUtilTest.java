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
package org.wso2.carbon.core.test.persistence;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.axis2.description.Parameter;
import org.wso2.carbon.core.util.ParameterUtil;

public class ParameterUtilTest extends TestCase {

    public void setUp() throws Exception {
        super.setUp();
    }

    public void tearDown() throws Exception {
        super.tearDown();
    }

    public void testCreateParameter1() throws Exception {
        Parameter p = ParameterUtil.createParameter("name", "value");
        String s = p.getValue().toString();
        assertTrue(s.equals("value"));
    }

    public void testCreateParameter2() throws Exception {
        Parameter p = null;
        try {
            p = ParameterUtil.createParameter(null, "value");
        } catch (Exception e) {
            assertTrue(e.getMessage().equals("Parameter name is madatory."));
        }
        assertNull(p);
    }

    public static Test suite() {
        return new TestSuite(ParameterUtilTest.class);
    }
}