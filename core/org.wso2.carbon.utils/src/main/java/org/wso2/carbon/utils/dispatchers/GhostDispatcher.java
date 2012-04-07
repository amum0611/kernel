/*
 * Copyright 2005-2011 WSO2, Inc. (http://wso2.com)
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

package org.wso2.carbon.utils.dispatchers;

import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.description.AxisOperation;
import org.apache.axis2.description.HandlerDescription;
import org.apache.axis2.engine.AbstractDispatcher;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.utils.deployment.GhostDeployerUtils;

/**
 * GhostDispatcher is responsible for making sure that all incoming messages will be dispatched
 * only into an actual service. When there are Ghost services in the system, if a message comes
 * into such a serivce, this dispatcher will load the actual service and dispatch the message
 * into that service.
 */
public class GhostDispatcher extends AbstractDispatcher {

    private static Log log = LogFactory.getLog(GhostDispatcher.class);

    private static final String NAME = "GhostDispatcher";

    public InvocationResponse invoke(MessageContext msgctx) throws AxisFault {
        // if the service is not already dispatched, try to find the service within ghost list
        if (msgctx.getAxisService() == null) {
            String serviceName = GhostDeployerUtils.dispatchServiceFromTransitGhosts(msgctx);
            if (serviceName != null) {
                // if the service is found in the temp ghost list, we have to wait until the
                // particular actual service is deployed..
                GhostDeployerUtils.waitForActualServiceToDeploy(serviceName, msgctx);
            }
            return InvocationResponse.CONTINUE;
        }
        // find the service and operation
        AxisService service = findService(msgctx);
        if (service != null) {
            findOperation(service, msgctx);
        }
        // set the last usage timestamp in the dispatched service
        if (msgctx.getAxisService() != null) {
            GhostDeployerUtils.updateLastUsedTime(msgctx.getAxisService());
        }
        return InvocationResponse.CONTINUE;
    }

    @Override
    public AxisOperation findOperation(AxisService service, MessageContext
            messageContext) {
        AxisOperation newOperation = null;
        if (service != null && messageContext.getAxisOperation() != null) {
            AxisOperation existingOperation = messageContext.getAxisOperation();
            newOperation = service.getOperation(existingOperation.getName());
            if (newOperation != null) {
                messageContext.setAxisOperation(newOperation);
            }
        }
        return newOperation;
    }

    @Override
    public AxisService findService(MessageContext messageContext) throws AxisFault {
        AxisService dispatchedService = messageContext.getAxisService();
        AxisService newService = null;
        
        // check whether this is a ghost service
        if (GhostDeployerUtils.isGhostService(dispatchedService)) {
            AxisConfiguration axisConfig = messageContext.getConfigurationContext()
                    .getAxisConfiguration();
            
            try {
            	newService = GhostDeployerUtils.deployActualService(axisConfig,
                    dispatchedService);
            } catch (AxisFault e) {
            	log.error("Error deploying service. ", e);
            	throw e;
            }
            if (newService != null) {
                messageContext.setAxisService(newService);
                // we have to remove the old binding message as well. Message context will
                // generate the new binding message when needed..
                messageContext.removeProperty(Constants.AXIS_BINDING_MESSAGE);
            }
        }
        return newService;
    }

    @Override
    public void initDispatcher() {
        init(new HandlerDescription(NAME));
    }
}

