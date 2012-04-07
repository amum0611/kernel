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
package org.wso2.carbon.user.mgt.ui;

import org.wso2.carbon.user.mgt.stub.types.carbon.ClaimValue;
import org.wso2.carbon.user.mgt.stub.types.carbon.FlaggedName;
import org.wso2.carbon.user.mgt.stub.types.carbon.UIPermissionNode;
import org.wso2.carbon.user.mgt.stub.types.carbon.UserStoreInfo;
import org.wso2.carbon.utils.DataPaginator;

import javax.activation.DataHandler;
import javax.mail.util.ByteArrayDataSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Util {

    public static FlaggedName[] toADBFlaggedNames(
            org.wso2.carbon.user.mgt.common.FlaggedName[] flaggedNames) {
        if(flaggedNames == null){
            return new FlaggedName[0];
        }
        FlaggedName[] names = new FlaggedName[flaggedNames.length];
        int i = 0;
        for (org.wso2.carbon.user.mgt.common.FlaggedName fName : flaggedNames) {
            FlaggedName name = new FlaggedName();
            name.setItemName(fName.getItemName());
            name.setEditable(fName.isEditable());
            name.setSelected(fName.isSelected());
            names[i] = name;
            i++;
        }
        return names;
    }

    public static org.wso2.carbon.user.mgt.common.FlaggedName[] toCommonFlaggedNames(
            FlaggedName[] flaggedNames) {
        if(flaggedNames == null) {
            return new org.wso2.carbon.user.mgt.common.FlaggedName[0];
        }
        org.wso2.carbon.user.mgt.common.FlaggedName[] names = new org.wso2.carbon.user.mgt.common.FlaggedName[flaggedNames.length];
        int i = 0;
        for (FlaggedName fName : flaggedNames) {
            org.wso2.carbon.user.mgt.common.FlaggedName name = new org.wso2.carbon.user.mgt.common.FlaggedName();
            name.setItemName(fName.getItemName());
            name.setSelected(fName.getSelected());
            name.setEditable(fName.getEditable());
            name.setRoleType(fName.getRoleType());
            names[i] = name;
            i++;
        }
        return names;
    }

    public static ClaimValue[] toADBClaimValues(
            org.wso2.carbon.user.mgt.common.ClaimValue[] claimValues) {
        if (claimValues == null) {
            return new ClaimValue[0];
        }
        ClaimValue[] values = new ClaimValue[claimValues.length];
        for (org.wso2.carbon.user.mgt.common.ClaimValue cvalue : claimValues) {
            ClaimValue value = new ClaimValue();
            value.setClaimURI(cvalue.getClaimURI());
            value.setValue(cvalue.getValue());
        }
        return values;
    }

    public static org.wso2.carbon.user.mgt.common.UserStoreInfo toCommonUserStoreInfo(
            UserStoreInfo userStoreInfo) {
        org.wso2.carbon.user.mgt.common.UserStoreInfo usInfo = new org.wso2.carbon.user.mgt.common.UserStoreInfo();
        usInfo.setAdminRole(userStoreInfo.getAdminRole());
        usInfo.setAdminUser(userStoreInfo.getAdminUser());
        usInfo.setEveryOneRole(userStoreInfo.getEveryOneRole());
        usInfo.setReadOnly(userStoreInfo.getReadOnly());
        usInfo.setMaxUserListCount(userStoreInfo.getMaxUserListCount());
        usInfo.setJsRegEx(userStoreInfo.getJsRegEx());
        usInfo.setUserNameRegEx(userStoreInfo.getUserNameRegEx());
        usInfo.setRoleNameRegEx(userStoreInfo.getRoleNameRegEx());
        usInfo.setPasswordsExternallyManaged(userStoreInfo.getPasswordsExternallyManaged());
        usInfo.setBulkImportSupported(userStoreInfo.getBulkImportSupported());
        usInfo.setExternalIdP(userStoreInfo.getExternalIdP());
        return usInfo;
    }

    public static org.wso2.carbon.user.mgt.common.FlaggedName[] buildFalggedArray(
            String[] shownUsers, String[] selectedUsers) {
        Arrays.sort(selectedUsers);
        org.wso2.carbon.user.mgt.common.FlaggedName[] flaggedNames = new org.wso2.carbon.user.mgt.common.FlaggedName[shownUsers.length];
        for (int i = 0; i < shownUsers.length; i++) {
            String name = shownUsers[i];
            org.wso2.carbon.user.mgt.common.FlaggedName flagName = new org.wso2.carbon.user.mgt.common.FlaggedName();
            flagName.setItemName(name);
            if (Arrays.binarySearch(selectedUsers, name) > -1) {
                flagName.setSelected(true);
            }
            flaggedNames[i] = flagName;
        }
        return flaggedNames;
    }

    public static org.wso2.carbon.user.mgt.common.UIPermissionNode toCommonUIPermissionNode(
            UIPermissionNode parentNode) {
        org.wso2.carbon.user.mgt.common.UIPermissionNode uiPermissionNode = new org.wso2.carbon.user.mgt.common.UIPermissionNode();
        uiPermissionNode.setDisplayName(parentNode.getDisplayName());
        uiPermissionNode.setResourcePath(parentNode.getResourcePath());
        uiPermissionNode.setSelected(parentNode.getSelected());
        org.wso2.carbon.user.mgt.common.UIPermissionNode[] children = null;
        if (parentNode.getNodeList() != null) {
            children = new org.wso2.carbon.user.mgt.common.UIPermissionNode[parentNode
                    .getNodeList().length];
            for (int i = 0; i < parentNode.getNodeList().length; i++) {
                org.wso2.carbon.user.mgt.common.UIPermissionNode child = toCommonUIPermissionNode(parentNode
                        .getNodeList()[i]);
                children[i] = child;

            }
        } else {
            children = new org.wso2.carbon.user.mgt.common.UIPermissionNode[0];
        }
        uiPermissionNode.setNodeList(children);
        return uiPermissionNode;
    }
    
    public static DataHandler buildDataHandler(byte[] content) {   
        DataHandler dataHandler = new DataHandler(new ByteArrayDataSource(content,
                "application/octet-stream"));
        return dataHandler;
    }
    public static PaginatedNamesBean retrivePaginatedFlggedName(int pageNumber,
                                                                String[] names){

        List <org.wso2.carbon.user.mgt.common.FlaggedName>list
                =new ArrayList<org.wso2.carbon.user.mgt.common.FlaggedName>();
        org.wso2.carbon.user.mgt.common.FlaggedName flaggedName;
        for(String name:names){
            flaggedName=new org.wso2.carbon.user.mgt.common.FlaggedName();
            flaggedName.setItemName(name);
            list.add(flaggedName);
        }
        return retrivePaginatedFlggedName(pageNumber,list.toArray(
                new org.wso2.carbon.user.mgt.common.FlaggedName[list.size()]));
    }
    public static PaginatedNamesBean retrivePaginatedFlggedName(int pageNumber,
                                            org.wso2.carbon.user.mgt.common.FlaggedName[] names){
        PaginatedNamesBean bean=new PaginatedNamesBean();
        List<org.wso2.carbon.user.mgt.common.FlaggedName> list
                =new ArrayList<org.wso2.carbon.user.mgt.common.FlaggedName>();
        for(org.wso2.carbon.user.mgt.common.FlaggedName name:names){
            list.add(name);
        }
        DataPaginator.doPaging(pageNumber, list, bean);
        return bean;
    }
}
