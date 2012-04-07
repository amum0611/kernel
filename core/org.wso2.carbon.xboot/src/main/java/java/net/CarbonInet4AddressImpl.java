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
package java.net;

/**
 * Fix for Reverse DNS lookup delay issue. For more details see solution provided at 
 * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4939977
 */
public class CarbonInet4AddressImpl extends Inet4AddressImpl{

    public String getLocalHostName() {
        return "localhost";
    }

    // This is causing a compilation error on JDK 1.5. But not having it does not seems to
    // be an issue
    /*public InetAddress[] lookupAllHostAddr(String hostname) throws UnknownHostException {
        System.out.println("-------- lookupAllHostAddr IPv4 ");
        if (hostname.equals("localhost")) {
            return new InetAddress[] {
                InetAddress.getByAddress(new byte[] {
                        (byte)127, (byte)0, (byte)0, (byte)1
                    })
            };
        }
        return super.lookupAllHostAddr(hostname);
    }*/
    
    public String getHostByAddr(byte[] addr) throws UnknownHostException{
        StringBuffer host = new StringBuffer();
        for (int i=0; i<addr.length; i++) {
            if (i > 0) {
                host.append(".");
            }
            host.append(addr[i]&0xFF);
        }
        return host.toString();
    }
}
