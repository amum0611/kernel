package org.wso2.carbon.server.admin.privilegedaction.extension.utills;

import java.io.Serializable;
import java.util.Comparator;

import org.wso2.carbon.server.admin.privilegedaction.extension.core.PrivilegedActionExtension;

public class PrivilegedActionExtensionComparator implements Comparator<PrivilegedActionExtension>,Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = -7899541538227561658L;

	@Override
    public int compare(PrivilegedActionExtension ex1, PrivilegedActionExtension ex2) {
		return ex2.getPriority()-ex1.getPriority();
    }

}
