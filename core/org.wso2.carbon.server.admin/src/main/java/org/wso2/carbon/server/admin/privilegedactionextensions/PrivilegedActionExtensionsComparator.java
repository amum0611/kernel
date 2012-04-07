package org.wso2.carbon.server.admin.privilegedactionextensions;

import java.io.Serializable;
import java.util.Comparator;

import org.wso2.carbon.core.services.privilegedactionextensions.PrivilegedActionExtension;

public class PrivilegedActionExtensionsComparator implements Comparator<PrivilegedActionExtension>,Serializable{

	@Override
    public int compare(PrivilegedActionExtension ex1, PrivilegedActionExtension ex2) {
		return ex2.getPriority()-ex1.getPriority();
    }

}
