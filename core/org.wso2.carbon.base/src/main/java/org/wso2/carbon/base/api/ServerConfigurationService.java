package org.wso2.carbon.base.api;

import org.w3c.dom.Element;

public interface ServerConfigurationService {
    public void setConfigurationProperty(String key, String value);

    public void overrideConfigurationProperty(String key, String value);

    public String getFirstProperty(String key);

    public String[] getProperties(String key);

    public Element getDocumentElement();

}
