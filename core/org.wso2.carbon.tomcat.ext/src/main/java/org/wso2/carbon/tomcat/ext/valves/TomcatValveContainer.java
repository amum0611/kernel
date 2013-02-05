/*                                                                             
 * Copyright 2004,2005 The Apache Software Foundation.                         
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
package org.wso2.carbon.tomcat.ext.valves;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is used for registering {@link CarbonTomcatValve} instances & invoking them.
 * These valves correspond to Tomcat Valves.
 * <p/>
 * Generally, when a request comes in, the Tomcat valves will notify all the registered
 * CarbonTomcatValves
 * <p/>
 * To read more on Tomcat 5.5 valves, see
 * <a href="http://tomcat.apache.org/tomcat-5.5-doc/config/host.html>
 * http://tomcat.apache.org/tomcat-5.5-doc/config/host.html</a>. Search for valves in that document.
 */
public class TomcatValveContainer {

    private static List<CarbonTomcatValve> valves = new ArrayList<CarbonTomcatValve>();

    /**
     * This is the method used by the Tomcat valves to notify the {@link CarbonTomcatValve}s
     *
     * @param request  The HTTP Request
     * @param response The HTTP Response
     */
    public static void invokeValves(Request request, Response response) {
        for (CarbonTomcatValve valve : valves) {
            valve.invoke(request, response);
        }
    }

    /**
     * Add CarbonTomcatValves. This is generally called by the Carbon webapp-mgt component
     *
     * @param valves The valves to be added
     */
    public static void addValves(List<CarbonTomcatValve> valves) {
        TomcatValveContainer.valves.addAll(valves);
    }

    /**
     * Set the index of the valve based on it priority when invoking it.
     *
     * @param index  the index where the valve need to be added in the list
     * @param valves valves to be added
     */
    public static void addValves(int index, List<CarbonTomcatValve> valves) {
        TomcatValveContainer.valves.addAll(index, valves);
    }

    /**
     * Check before for a valve whether it exists or not.
     *
     * @param carbonTomcatValve the valve to be checked
     * @return if valve exists
     */
    public static boolean isValveExits(CarbonTomcatValve carbonTomcatValve) {
        return valves.contains(carbonTomcatValve);
    }
}
