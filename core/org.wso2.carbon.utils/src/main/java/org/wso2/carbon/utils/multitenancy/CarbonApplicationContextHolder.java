/*
 *  Copyright (c) 2005-2009, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.carbon.utils.multitenancy;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.description.Parameter;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.base.CarbonApplicationContextHolderBase;


public class CarbonApplicationContextHolder {

	// instance of the base of this Carbon Context Holder
	private CarbonApplicationContextHolderBase carbonAppContextHolderBase;

	private static final String CARBON_APPLICATION_CONTEXT_HOLDER = "carbonContextHolder";

	private static final Log log = LogFactory.getLog(CarbonApplicationContextHolder.class);

	private CarbonApplicationContextHolderBase getCarbonAppContextHolderBase() {
		if (carbonAppContextHolderBase == null) {
			return CarbonApplicationContextHolderBase.getCurrentCarbonAppContextHolderBase();
		}
		return carbonAppContextHolderBase;
	}

	/**
	 * This method will destroy the current CarbonAppContext holder.
	 */
	public static void destroyCurrentCarbonAppContextHolder() {
		CarbonApplicationContextHolderBase.destroyCurrentCarbonaAppContextHolder();
	}

	public void startApplicationFlow() {
		// This class will not be exposed to tenant code
		getCarbonAppContextHolderBase().startApplicationFlow();
	}

	/**
	 * This will end the tenant flow and restore the previous CarbonContext.
	 */
	public void endApplicationFlow() {
		// The security checks are done at the CarbonContextHolderBase level.
		getCarbonAppContextHolderBase().endApplicationFlow();
	}

	private CarbonApplicationContextHolder(
			CarbonApplicationContextHolderBase carbonAppContextHolderBase) {
		this.carbonAppContextHolderBase = carbonAppContextHolderBase;
	}

	private CarbonApplicationContextHolder() {
       this(null);
		//this.carbonAppContextHolderBase = new CarbonApplicationContextHolderBase();
    }
	/**
	 * Method to obtain a clone of the current Carbon application Context
	 * Holder.
	 * 
	 * @return clone of the current Carbon application Context Holder.
	 */
	private static CarbonApplicationContextHolder getClone() {
		return new CarbonApplicationContextHolder(new CarbonApplicationContextHolderBase(
				CarbonApplicationContextHolderBase.getCurrentCarbonAppContextHolderBase()));
	}

	public static CarbonApplicationContextHolder getCurrentCarbonAppContextHolder(
			ConfigurationContext configurationContext) {
		return getCurrentCarbonAppContextHolder(configurationContext, true);
	}

	private static CarbonApplicationContextHolder getCurrentCarbonAppContextHolder(
			ConfigurationContext configurationContext, boolean addToConfigContext) {
		if (configurationContext != null) {
			if (configurationContext.getAxisConfiguration() != null) {
				return getCurrentCarbonAppContextHolder(
						configurationContext.getAxisConfiguration(), addToConfigContext);
			}
		}
		return getThreadLocalCarbonApplicationContextHolder();
	}

	public static CarbonApplicationContextHolder getCurrentCarbonAppContextHolder(
			AxisConfiguration axisConfiguration) {
		return getCurrentCarbonAppContextHolder(axisConfiguration, true);
	}

	private static CarbonApplicationContextHolder getCurrentCarbonAppContextHolder(
			AxisConfiguration axisConfiguration, boolean addToConfiguration) {
		Parameter param = axisConfiguration.getParameter(CARBON_APPLICATION_CONTEXT_HOLDER);
		if (param != null && param.getValue() != null) {
			return (CarbonApplicationContextHolder) param.getValue();
		} else if (!addToConfiguration) {
			return null;
		}
		try {
			CarbonApplicationContextHolder context = getClone();
			log.debug("Added CarbonApplicationContext to the Axis Configuration");
			axisConfiguration.addParameter(CARBON_APPLICATION_CONTEXT_HOLDER, context);
			return context;
		} catch (Exception e) {
			throw new RuntimeException(
					"Failed to add CarbonApplicationContext to the AxisConfiguration.", e);
		}
	}

	public static CarbonApplicationContextHolder getCurrentCarbonAppContextHolder(
			HttpSession httpSession) {
		return getCurrentCarbonAppContextHolder(httpSession, true);
	}

	private static CarbonApplicationContextHolder getCurrentCarbonAppContextHolder(
			HttpSession httpSession, boolean addToSession) {
		Object contextObject = httpSession.getAttribute(CARBON_APPLICATION_CONTEXT_HOLDER);
		if (contextObject != null) {
			return (CarbonApplicationContextHolder) contextObject;
		} else if (!addToSession) {
			return null;
		}
		CarbonApplicationContextHolder context = getClone();
		log.debug("Added CarbonContext to the HTTP Session");
		httpSession.setAttribute(CARBON_APPLICATION_CONTEXT_HOLDER, context);
		return context;
	}

	public static CarbonApplicationContextHolder getCurrentCarbonAppContextHolder(
			MessageContext messageContext) {
		HttpServletRequest request = (HttpServletRequest) messageContext
				.getProperty(HTTPConstants.MC_HTTP_SERVLETREQUEST);
		if (request != null) {
			HttpSession httpSession = request.getSession(false);
			if (httpSession != null) {
				CarbonApplicationContextHolder context = getCurrentCarbonAppContextHolder(
						httpSession, false);
				if (context != null) {
					return context;
				}
				if (messageContext.getConfigurationContext() != null) {
					context = getCurrentCarbonAppContextHolder(
							messageContext.getConfigurationContext(), false);
					if (context != null) {
						return context;
					}
				}
				context = getClone();
				log.debug("Added CarbonContext to the HTTP Session");
				httpSession.setAttribute(CARBON_APPLICATION_CONTEXT_HOLDER, context);
				return context;
			}
		}
		return getCurrentCarbonAppContextHolder(messageContext.getConfigurationContext());
	}

	public static CarbonApplicationContextHolder getCurrentCarbonAppContextHolder() {
		try {
			MessageContext messageContext = MessageContext.getCurrentMessageContext();

			if (messageContext != null) {
				return getCurrentCarbonAppContextHolder(messageContext);
			} else {
				return getCurrentCarbonAppContextHolder((ConfigurationContext) null);
			}
		} catch (NullPointerException ignore) {
			// This is thrown when the message context is not initialized
			// So return the Threadlocal
			return getThreadLocalCarbonApplicationContextHolder();
		} catch (NoClassDefFoundError ignore) {
			// There can be situations where the CarbonContext is accessed, when
			// there is no Axis2
			// library on the classpath.
			return getThreadLocalCarbonApplicationContextHolder();
		}
	}
	
	
	/**
     * This method will always attempt to obtain an instance of the current CarbonContext from the
     * thread-local copy.
     *
     * @return the CarbonContext holder.
     */
    public static CarbonApplicationContextHolder getThreadLocalCarbonApplicationContextHolder() {
        return new CarbonApplicationContextHolder();
    }

    
	public String getApplicationName() {
		return getCarbonAppContextHolderBase().getApplicationName();
	}

	public void setApplicationName(String applicationName) {
		getCarbonAppContextHolderBase().setApplicationName(applicationName);
	}

}
