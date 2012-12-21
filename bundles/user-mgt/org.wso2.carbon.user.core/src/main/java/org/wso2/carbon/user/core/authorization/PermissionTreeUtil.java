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
package org.wso2.carbon.user.core.authorization;

import java.util.ArrayList;
import java.util.List;

public class PermissionTreeUtil {

    /**
     * @param path
     * @return
     */

    public static void main(String[] args) {
        List<String> lst = PermissionTreeUtil.toComponenets("/");
        System.out.println(lst.size());
        System.out.println(lst.get(0));
    }

    public static List<String> toComponenets(String path) {
        String[] results = path.split("/");
        List<String> resultArr = new ArrayList<String>();
        int start = 0;
        //Removing the empty strings at start, but split javadoc
        //says it removes empty spaces
        if (results.length > 0 && results[0].length() == 0) {
            start = 1;
        }
        for (int i = start; i < results.length; i++) {
            resultArr.add(results[i]);
        }
        return resultArr;
    }

    public static TreeNode.Permission actionToPermission(String action) {
        if ("add".equals(action)) {
            return TreeNode.Permission.ADD;
        } else if ("get".equals(action)) {
            return TreeNode.Permission.GET;
        } else if ("delete".equals(action)) {
            return TreeNode.Permission.DELETE;
        } else if ("write".equals(action)) {
            return TreeNode.Permission.ADD;
        } else if ("read".equals(action)) {
            return TreeNode.Permission.GET;
        } else if ("edit".equals(action)) {
            return TreeNode.Permission.EDIT;
        } else if ("login".equals(action)) {
            return TreeNode.Permission.LOGIN;
        } else if ("manage-configuration".equals(action)) {
            return TreeNode.Permission.MAN_CONFIG;
        } else if ("manage-lc-configuration".equals(action)) {
            return TreeNode.Permission.MAN_LC_CONFIG;
        } else if ("manage-security".equals(action)) {
            return TreeNode.Permission.MAN_SEC;
        } else if ("upload-services".equals(action)) {
            return TreeNode.Permission.UP_SERV;
        } else if ("manage-services".equals(action)) {
            return TreeNode.Permission.MAN_SERV;
        } else if ("manage-mediation".equals(action)) {
            return TreeNode.Permission.MAN_MEDIA;
        } else if ("monitor-system".equals(action)) {
            return TreeNode.Permission.MON_SYS;
        } else if ("http://www.wso2.org/projects/registry/actions/get".equals(action)) {
            return TreeNode.Permission.GET;
        } else if ("http://www.wso2.org/projects/registry/actions/add".equals(action)) {
            return TreeNode.Permission.ADD;
        } else if ("http://www.wso2.org/projects/registry/actions/delete".equals(action)) {
            return TreeNode.Permission.DELETE;
        } else if ("authorize".equals(action)) {
            return TreeNode.Permission.AUTHORIZE;
        } else if ("delegate-identity".equals(action)) {
            return TreeNode.Permission.DEL_ID;
        } else if ("invoke-service".equals(action)) {
            return TreeNode.Permission.INV_SER;
        } else if ("ui.execute".equals(action)) {
            return TreeNode.Permission.UI_EXECUTE;
        } else if ("subscribe".equals(action)) {
            return TreeNode.Permission.SUBSCRIBE;
        } else if ("publish".equals(action)) {
            return TreeNode.Permission.PUBLISH;
        } else if ("browse".equals(action)) {
            return TreeNode.Permission.BROWSE;
        } else if ("consume".equals(action)) {
            return TreeNode.Permission.CONSUME;
        } else if ("changePermission".equals(action)) {
            return TreeNode.Permission.CHANGE_PERMISSION;
        } else if ("SendMessage".equals(action)) {
            return TreeNode.Permission.SQS_SEND_MESSAGE;
        } else if ("ReceiveMessage".equals(action)) {
            return TreeNode.Permission.SQS_RECEIVE_MESSAGE;
        } else if ("DeleteMessage".equals(action)) {
            return TreeNode.Permission.SQS_DELETE_MESSAGE;
        } else if ("ChangeMessageVisibility".equals(action)) {
            return TreeNode.Permission.SQS_CHANGE_MESSAGE_VISIBILITY;
        } else if ("GetQueueAttributes".equals(action)) {
            return TreeNode.Permission.SQS_GET_QUEUE_ATTRIBUTES;
        }


        throw new IllegalArgumentException("Invalid action : " + action);
    }

}
