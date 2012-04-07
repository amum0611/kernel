/*
 *  Copyright (c) 2005-2008, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.wso2.carbon.server.admin.module.handler;

import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.description.Parameter;
import org.apache.axis2.handlers.AbstractHandler;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.CarbonConstants;
import org.wso2.carbon.core.multitenancy.SuperTenantCarbonContext;
import org.wso2.carbon.registry.core.RegistryConstants;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * This handler will restrict super tenant services from non super tenant services..
 * The authorization internal to tenant will be handled in the authorization handler 
 */
public class TenantAuthorizationHandler extends AbstractHandler {

    private static Log log = LogFactory.getLog(TenantAuthorizationHandler.class.getClass());
    public static final String TENANT_AUTHZ_FAULT_CODE = "WSO2CarbonTenantAuthorizationFailure";

    public InvocationResponse invoke(MessageContext msgContext) throws AxisFault {
        if (this.callToGeneralService(msgContext)) {
            return InvocationResponse.CONTINUE;
        }

        // skip authz in case of local tranport
        if (Constants.TRANSPORT_LOCAL.equals(msgContext.getIncomingTransportName())) {
            return InvocationResponse.CONTINUE;
        }

        int tenantId = SuperTenantCarbonContext.getCurrentContext(msgContext).getTenantId();

        if (tenantId != 0) {
            // for non tenantId = 0 access throw an exception
            String errorMsg = "Denied accessing super tenant service.";
            log.error(errorMsg + " Accessed tenant id: " + tenantId + ".");
            throw new AxisFault(errorMsg, TENANT_AUTHZ_FAULT_CODE );
        }
        // this is super tenant..
        return InvocationResponse.CONTINUE;
    }


    private boolean callToGeneralService(MessageContext msgContext) {
        boolean isGeneral = true;
        AxisService service = msgContext.getAxisService();
        Parameter param = service.getParameter("superTenantService");
        if (param != null && "true".equals(param.getValue())) {
            isGeneral = false;
        }
        return isGeneral;
    }
}
