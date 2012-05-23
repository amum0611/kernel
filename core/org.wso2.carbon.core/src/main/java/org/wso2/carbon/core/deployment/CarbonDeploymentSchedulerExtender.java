package org.wso2.carbon.core.deployment;

import org.apache.axis2.engine.AxisConfiguration;

/**
 * This can be used as an Extender in order to schedule some task while the deployment task is
 * running. (Refer OGSI Extender pattern for more information.)
 */
public interface CarbonDeploymentSchedulerExtender {

    /**
     * invoke the extender methods. Put your logic inside this method.
     * @param axisConfig axisConfiguration
     * @throws Exception
     */
    public void invoke(AxisConfiguration axisConfig) throws Exception;
}
