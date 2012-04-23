/*
 * Copyright 2005-2007 WSO2, Inc. (http://wso2.com)
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

package org.wso2.carbon.user.core.authorization;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.CarbonConstants;
import org.wso2.carbon.caching.core.BaseCache;
import org.wso2.carbon.caching.core.permissiontree.PermissionTreeCache;
import org.wso2.carbon.caching.core.permissiontree.PermissionTreeCacheEntry;
import org.wso2.carbon.caching.core.permissiontree.PermissionTreeCacheKey;
import org.wso2.carbon.user.core.UserCoreConstants;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.util.DatabaseUtil;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class PermissionTree {

    private static Log log = LogFactory.getLog(PermissionTree.class);
    protected TreeNode root;
    protected int tenantId;
    protected volatile int hashValueOfRootNode;
    protected DataSource dataSource;
    private static BaseCache permissionCache = PermissionTreeCache.getInstance();
    private Boolean lock = new Boolean(false);

    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final Lock read = readWriteLock.readLock();
    private final Lock write = readWriteLock.writeLock();

    /**
     * On the server startup, all permissions are populated from the DB and the
     * permission tree is built in memory..
     * 
     * @throws UserStoreException
     *             - SQL exceptions
     */

    public PermissionTree(int tenantId, DataSource dataSource) {
        root = new TreeNode("/");
        this.tenantId = tenantId;
        this.dataSource = dataSource;
    }

    /**
     * This private constructor will be used by the database loading code to
     * create a new copy of the permission node.
     */
    private PermissionTree() {
        root = new TreeNode("/");
    }

    void authorizeUserInTree(String userName, String resourceId, String action) {
        write.lock();
        try {
            SearchResult sr = getNode(root, PermissionTreeUtil.toComponenets(resourceId));
            if (sr.getUnprocessedPaths() != null) {
                List<String> paths = sr.getUnprocessedPaths();
                TreeNode tn = sr.getLastNode().create(paths);
                tn.authorizeUser(userName, PermissionTreeUtil.actionToPermission(action));

            } else {
                sr.getLastNode().authorizeUser(userName,
                        PermissionTreeUtil.actionToPermission(action));
            }
            updatePermissionTreeCache(root);
        } finally {
            write.unlock();
        }
    }

    void denyUserInTree(String userName, String resourceId, String action) {
        write.lock();
        try {
            SearchResult sr = getNode(root, PermissionTreeUtil.toComponenets(resourceId));
            if (sr.getUnprocessedPaths() != null) {
                List<String> paths = sr.getUnprocessedPaths();
                TreeNode tn = sr.getLastNode().create(paths);
                tn.denyUser(userName, PermissionTreeUtil.actionToPermission(action));
            } else {
                sr.getLastNode().denyUser(userName, PermissionTreeUtil.actionToPermission(action));
            }
            updatePermissionTreeCache(root);
        } finally {
            write.unlock();
        }
    }

    void authorizeRoleInTree(String roleName, String resourceId, String action) {
        write.lock();
        try {
            SearchResult sr = getNode(root, PermissionTreeUtil.toComponenets(resourceId));
            if (sr.getUnprocessedPaths() != null) {
                List<String> paths = sr.getUnprocessedPaths();
                TreeNode tn = sr.getLastNode().create(paths);
                tn.authorizeRole(roleName, PermissionTreeUtil.actionToPermission(action));

            } else {
                sr.getLastNode().authorizeRole(roleName,
                        PermissionTreeUtil.actionToPermission(action));
            }
            updatePermissionTreeCache(root);
        } finally {
            write.unlock();
        }
    }

    void denyRoleInTree(String roleName, String resourceId, String action) {
        write.lock();
        try {
            SearchResult sr = getNode(root, PermissionTreeUtil.toComponenets(resourceId));
            if (sr.getUnprocessedPaths() != null) {
                List<String> paths = sr.getUnprocessedPaths();
                TreeNode tn = sr.getLastNode().create(paths);
                tn.denyRole(roleName, PermissionTreeUtil.actionToPermission(action));

            } else {
                sr.getLastNode().denyRole(roleName, PermissionTreeUtil.actionToPermission(action));
            }
            updatePermissionTreeCache(root);
        } finally {
            write.unlock();
        }
    }

    /**
     * Find permission for given 'role' starting from the TreeNode 'node' and
     * using the path segments given in the List as pathParts
     * 
     * @param role
     *            the role for which the permissions are searched
     * @param permission
     *            the permission checked
     * @param sr
     *            the SearchResult that merges the permissions as the tree is
     *            traversed
     * @param node
     *            the current node
     * @param pathParts
     *            the list of path segments to traverse
     * @return the final SearchResult that merges the permissions
     */
    SearchResult getRolePermission(String role, TreeNode.Permission permission, SearchResult sr,
            TreeNode node, List<String> pathParts) {
        read.lock();
        try {
            if (node == null) {
                node = root;
            }

            if (sr == null) {
                sr = new SearchResult();
            }

            Boolean currentNodeAllows = node.isRoleAuthorized(role, permission);
            if (currentNodeAllows == Boolean.TRUE) {
                sr.setLastNodeAllowedAccess(Boolean.TRUE);
            } else if (currentNodeAllows == Boolean.FALSE) {
                sr.setLastNodeAllowedAccess(Boolean.FALSE);
            }

            if (pathParts == null || pathParts.isEmpty()) {
                sr.setLastNode(node);
                sr.setUnprocessedPaths(null);
                return sr;

            } else {

                String key = pathParts.get(0);
                if (key != null && key.length() > 0) {
                    TreeNode child = node.getChild(key);
                    if (child != null) {
                        pathParts.remove(0);
                        return getRolePermission(role, permission, sr, child, pathParts);
                    }
                }
                sr.setLastNode(node);
                return sr;
            }
        } finally {
            read.unlock();
        }
    }

    /**
     * Find permission for given 'user' starting from the TreeNode 'node' and
     * using the path segments given in the List as pathParts
     * 
     * @param user
     *            the user for which the permissions are searched
     * @param permission
     *            the permission checked
     * @param sr
     *            the SearchResult that merges the permissions as the tree is
     *            traversed
     * @param node
     *            the current node
     * @param pathParts
     *            the list of path segments to traverse
     * @return the final SearchResult that merges the permissions
     */
    SearchResult getUserPermission(String user, TreeNode.Permission permission, SearchResult sr,
            TreeNode node, List<String> pathParts) {
        read.lock();
        try {
            if (node == null) {
                node = root;
            }
            if (sr == null) {
                sr = new SearchResult();
            }

            Boolean currentNodeAllows = node.isUserAuthorized(user, permission);
            if (currentNodeAllows == Boolean.TRUE) {
                sr.setLastNodeAllowedAccess(Boolean.TRUE);
            } else if (currentNodeAllows == Boolean.FALSE) {
                sr.setLastNodeAllowedAccess(Boolean.FALSE);
            }

            if (pathParts == null || pathParts.isEmpty()) {
                sr.setLastNode(node);
                sr.setUnprocessedPaths(null);
                return sr;

            } else {

                String key = pathParts.get(0);
                if (key != null && key.length() > 0) {
                    TreeNode child = node.getChild(key);
                    if (child != null) {
                        pathParts.remove(0);
                        return getUserPermission(user, permission, sr, child, pathParts);
                    }
                }
                sr.setLastNode(node);
                return sr;
            }
        } finally {
            read.unlock();
        }
    }

    /**
     * Find the allowed Users for a given resource by traversing the whole
     * pemission tree.
     * 
     * @param sr
     *            - search result to contain allowed users
     * @param node
     *            - current node
     * @param permission
     *            - permission
     * @param pathParts
     *            - list of path segments to traverse
     * @return - search result with allowed users
     */

    SearchResult getAllowedUsersForResource(SearchResult sr, TreeNode node,
            TreeNode.Permission permission, List<String> pathParts) {
        read.lock();
        try {
            if (node == null) {
                node = root;
            }
            if (sr == null) {
                sr = new SearchResult();
            }

            /**
             * Add allowed users of the current node to our list in the sr
             */
            Map<String, BitSet> allowUsers = node.getUserAllowPermissions();
            for (Map.Entry<String, BitSet> entry : allowUsers.entrySet()) {
                BitSet bs = entry.getValue();
                if (bs.get(permission.ordinal())) {
                    if (!sr.getAllowedEntities().contains(entry.getKey())) {
                        sr.getAllowedEntities().add(entry.getKey());
                    }
                }
            }

            /**
             * Remove denied users of the current node from our list in the sr
             */
            Map<String, BitSet> denyUsers = node.getUserDenyPermissions();
            for (Map.Entry<String, BitSet> entry : denyUsers.entrySet()) {
                BitSet bs = entry.getValue();
                if (bs.get(permission.ordinal())
                        && sr.getAllowedEntities().contains(entry.getKey())) {
                    sr.getAllowedEntities().remove(entry.getKey());
                }
            }

            if (pathParts == null || pathParts.isEmpty()) {
                sr.setLastNode(node);
                sr.setUnprocessedPaths(null);
                return sr;
            } else {
                String key = pathParts.get(0);
                if (key != null && key.length() > 0) {
                    TreeNode child = node.getChild(key);
                    if (child != null) {
                        pathParts.remove(0);
                        return getAllowedUsersForResource(sr, child, permission, pathParts);
                    }
                }
                sr.setLastNode(node);
                return sr;
            }
        } finally {
            read.unlock();
        }
    }

    /**
     * Find the allowed Roles for a given resource by traversing the whole
     * pemission tree.
     * 
     * @param sr
     *            - search result to contain allowed roles
     * @param node
     *            - current node
     * @param permission
     *            - permission
     * @param pathParts
     *            - list of path segments to traverse
     * @return - search result with allowed roles
     */

    SearchResult getAllowedRolesForResource(SearchResult sr, TreeNode node,
            TreeNode.Permission permission, List<String> pathParts) {
        read.lock();
        try {
            if (node == null) {
                node = root;
            }

            if (sr == null) {
                sr = new SearchResult();
            }

            /**
             * Add allowed roles of the current node to our list in the sr
             */
            Map<String, BitSet> allowRoles = node.getRoleAllowPermissions();
            for (Map.Entry<String, BitSet> entry : allowRoles.entrySet()) {
                BitSet bs = entry.getValue();
                if (bs.get(permission.ordinal())) {
                    if (!sr.getAllowedEntities().contains(entry.getKey())) {
                        sr.getAllowedEntities().add(entry.getKey());
                    }
                }
            }

            /**
             * Remove denied roles of the current node from our list in the sr
             */
            Map<String, BitSet> denyRoles = node.getRoleDenyPermissions();
            for (Map.Entry<String, BitSet> entry : denyRoles.entrySet()) {
                BitSet bs = entry.getValue();
                if (bs.get(permission.ordinal())
                        && sr.getAllowedEntities().contains(entry.getKey())) {
                    sr.getAllowedEntities().remove(entry.getKey());
                }
            }

            if (pathParts == null || pathParts.isEmpty()) {
                sr.setLastNode(node);
                sr.setUnprocessedPaths(null);
                return sr;
            } else {
                String key = pathParts.get(0);
                if (key != null && key.length() > 0) {
                    TreeNode child = node.getChild(key);
                    if (child != null) {
                        pathParts.remove(0);
                        return getAllowedRolesForResource(sr, child, permission, pathParts);
                    }
                }
                sr.setLastNode(node);
                return sr;
            }
        } finally {
            read.unlock();
        }
    }

    /**
     * Find the denied Roles for a given resource by traversing the whole
     * pemission tree.
     * 
     * @param sr
     *            - search result to contain denied roles
     * @param node
     *            - current node
     * @param permission
     *            - permission
     * @param pathParts
     *            - list of path segments to traverse
     * @return - search result with denied roles
     */
    SearchResult getDeniedRolesForResource(SearchResult sr, TreeNode node,
            TreeNode.Permission permission, List<String> pathParts) {

        read.lock();
        try {
            if (sr == null) {
                sr = new SearchResult();
            }

            if (node == null) {
                node = root;
            }

            /**
             * Add denied roles of the current node to our list in the sr
             */
            Map<String, BitSet> denyRoles = node.getRoleDenyPermissions();
            for (Map.Entry<String, BitSet> entry : denyRoles.entrySet()) {
                BitSet bs = entry.getValue();
                if (bs.get(permission.ordinal())) {
                    if (!sr.getDeniedEntities().contains(entry.getKey())) {
                        sr.getDeniedEntities().add(entry.getKey());
                    }
                }
            }

            /**
             * Remove allowed roles of the current node from our list in the sr
             */
            Map<String, BitSet> allowRoles = node.getRoleAllowPermissions();
            for (Map.Entry<String, BitSet> entry : allowRoles.entrySet()) {
                BitSet bs = entry.getValue();
                if (bs.get(permission.ordinal()) && sr.getDeniedEntities().contains(entry.getKey())) {
                    sr.getDeniedEntities().remove(entry.getKey());
                }
            }

            if (pathParts == null || pathParts.isEmpty()) {
                sr.setLastNode(node);
                sr.setUnprocessedPaths(null);
                return sr;
            } else {
                String key = pathParts.get(0);
                if (key != null && key.length() > 0) {
                    TreeNode child = node.getChild(key);
                    if (child != null) {
                        pathParts.remove(0);
                        return getDeniedRolesForResource(sr, child, permission, pathParts);
                    }
                }
                sr.setLastNode(node);
                return sr;
            }
        } finally {
            read.unlock();
        }
    }

    /**
     * Find the denied users for a given resource by traversing the whole
     * pemission tree.
     * 
     * @param sr
     *            - search result to contain denied users
     * @param node
     *            - current node
     * @param permission
     *            - permission
     * @param pathParts
     *            - list of path segments to traverse
     * @return - search result with denied users
     */
    SearchResult getDeniedUsersForResource(SearchResult sr, TreeNode node,
            TreeNode.Permission permission, List<String> pathParts) {
        read.lock();
        try {
            if (sr == null) {
                sr = new SearchResult();
            }

            if (node == null) {
                node = root;
            }

            /**
             * Add denied users of the current node to our list in the sr
             */
            Map<String, BitSet> denyUsers = node.getUserDenyPermissions();
            for (Map.Entry<String, BitSet> entry : denyUsers.entrySet()) {
                BitSet bs = entry.getValue();
                if (bs.get(permission.ordinal())) {
                    if (!sr.getDeniedEntities().contains(entry.getKey())) {
                        sr.getDeniedEntities().add(entry.getKey());
                    }
                }
            }

            /**
             * Remove allowed users of the current node from our list in the sr
             */
            Map<String, BitSet> allowUsers = node.getUserAllowPermissions();
            for (Map.Entry<String, BitSet> entry : allowUsers.entrySet()) {
                BitSet bs = entry.getValue();
                if (bs.get(permission.ordinal()) && sr.getDeniedEntities().contains(entry.getKey())) {
                    sr.getDeniedEntities().remove(entry.getKey());
                }
            }

            if (pathParts == null || pathParts.isEmpty()) {
                sr.setLastNode(node);
                sr.setUnprocessedPaths(null);
                return sr;
            } else {
                String key = pathParts.get(0);
                if (key != null && key.length() > 0) {
                    TreeNode child = node.getChild(key);
                    if (child != null) {
                        pathParts.remove(0);
                        return getDeniedUsersForResource(sr, child, permission, pathParts);
                    }
                }
                sr.setLastNode(node);
                return sr;
            }
        } finally {
            read.unlock();
        }
    }

    /**
     * Clear all role authorization based on action starting from the given node
     * 
     * @param node
     *            the start node to begin the search
     * @param
     * @return the result as a SearchResultpathParts a List of
     * @see SearchResult
     */
    void clearRoleAuthorization(String roleName, TreeNode node, TreeNode.Permission permission) {
        write.lock();
        try {
            Map<String, BitSet> allowRoles = node.getRoleAllowPermissions();
            Map<String, BitSet> denyRoles = node.getRoleDenyPermissions();

            BitSet bs = allowRoles.get(roleName);
            if (bs != null) {
                bs.clear(permission.ordinal());
            }

            bs = denyRoles.get(roleName);
            if (bs != null) {
                bs.clear(permission.ordinal());
            }

            Map<String, TreeNode> childMap = node.getChildren();
            if (childMap != null && childMap.size() > 0) {
                for (TreeNode treeNode : childMap.values()) {
                    clearRoleAuthorization(roleName, treeNode, permission);
                }
            }
            updatePermissionTreeCache(root);
        } finally {
            write.unlock();
        }
    }

    /**
     * Clear all role authorization based on action starting from the given node
     * 
     * @param node
     *            the start node to begin the search
     * @param
     * @return the result as a SearchResultpathParts a List of
     * @see SearchResult
     */
    void clearRoleAuthorization(String roleName, TreeNode node) {
        write.lock();
        try {
            Map<String, BitSet> allowRoles = node.getRoleAllowPermissions();
            Map<String, BitSet> denyRoles = node.getRoleDenyPermissions();

            BitSet bs = allowRoles.get(roleName);
            if (bs != null) {
                allowRoles.remove(roleName);
            }

            bs = denyRoles.get(roleName);
            if (bs != null) {
                denyRoles.remove(roleName);
            }

            Map<String, TreeNode> childMap = node.getChildren();
            if (childMap != null && childMap.size() > 0) {
                for (TreeNode treeNode : childMap.values()) {
                    clearRoleAuthorization(roleName, treeNode);
                }
            }
            updatePermissionTreeCache(root);
        } finally {
            write.unlock();
        }
    }

    void updateRoleNameInCache(String roleName, String newRoleName, TreeNode node) {
        Map<String, BitSet> allowRoles = node.getRoleAllowPermissions();
        Map<String, BitSet> denyRoles = node.getRoleDenyPermissions();
        write.lock();
        try {
            BitSet bs = allowRoles.get(roleName);
            if (bs != null) {
                allowRoles.remove(roleName);
                allowRoles.put(newRoleName, bs);
            }

            bs = denyRoles.get(roleName);
            if (bs != null) {
                denyRoles.remove(roleName);
                denyRoles.put(newRoleName, bs);
            }

            Map<String, TreeNode> childMap = node.getChildren();
            if (childMap != null && childMap.size() > 0) {
                for (TreeNode treeNode : childMap.values()) {
                    updateRoleNameInCache(roleName, newRoleName, treeNode);
                }
            }
            updatePermissionTreeCache(root);
        } finally {
            write.unlock();
        }
    }

    void clearRoleAuthorization(String roleName, String resourceId, String action) {
        SearchResult sr = getNode(root, PermissionTreeUtil.toComponenets(resourceId));
        write.lock();
        try {
            if (sr.getUnprocessedPaths() == null) {
                TreeNode.Permission permission = PermissionTreeUtil.actionToPermission(action);

                Map<String, BitSet> allowRoles = sr.getLastNode().getRoleAllowPermissions();
                BitSet bs = allowRoles.get(roleName);
                if (bs != null) {
                    bs.clear(permission.ordinal());
                }

                Map<String, BitSet> denyRoles = sr.getLastNode().getRoleDenyPermissions();
                bs = denyRoles.get(roleName);
                if (bs != null) {
                    bs.clear(permission.ordinal());
                }
            }
            updatePermissionTreeCache(root);
        } finally {
            write.unlock();
        }
    }

    void clearRoleAuthorization(String roleName, String action) {
        TreeNode.Permission permission = PermissionTreeUtil.actionToPermission(action);
        clearRoleAuthorization(roleName, root, permission);
    }

    void updateRoleNameInCache(String roleName, String newRoleName) {
        updateRoleNameInCache(roleName, newRoleName, root);
    }

    void clearRoleAuthorization(String roleName) {
        clearRoleAuthorization(roleName, root);
    }

    void clearUserAuthorization(String userName, TreeNode node) {
        write.lock();
        try {
            Map<String, BitSet> allowUsers = node.getUserAllowPermissions();
            Map<String, BitSet> denyUsers = node.getUserDenyPermissions();

            BitSet bs = allowUsers.get(userName);
            if (bs != null) {
                allowUsers.remove(userName);
            }

            bs = denyUsers.get(userName);
            if (bs != null) {
                denyUsers.remove(userName);
            }

            Map<String, TreeNode> childMap = node.getChildren();
            if (childMap != null && childMap.size() > 0) {
                for (TreeNode treeNode : childMap.values()) {
                    clearUserAuthorization(userName, treeNode);
                }
            }
            updatePermissionTreeCache(root);
        } finally {
            write.unlock();
        }
    }

    void clearUserAuthorization(String userName) {
        clearUserAuthorization(userName, root);
    }

    void clearUserAuthorization(String userName, String resourceId, String action) {
        write.lock();
        try {
            SearchResult sr = getNode(root, PermissionTreeUtil.toComponenets(resourceId));
            if (sr.getUnprocessedPaths() == null || sr.getUnprocessedPaths().isEmpty()) {
                TreeNode.Permission permission = PermissionTreeUtil.actionToPermission(action);

                Map<String, BitSet> allowUsers = sr.getLastNode().getUserAllowPermissions();
                BitSet bs = allowUsers.get(userName);
                if (bs != null) {
                    bs.clear(permission.ordinal());
                }

                Map<String, BitSet> denyUsers = sr.getLastNode().getUserDenyPermissions();
                bs = denyUsers.get(userName);
                if (bs != null) {
                    bs.clear(permission.ordinal());
                }
            }
            updatePermissionTreeCache(root);
        } finally {
            write.unlock();
        }
    }

    void clearResourceAuthorizations(String resourceId) {
        write.lock();
        try {
            SearchResult sr = getNode(root, PermissionTreeUtil.toComponenets(resourceId));
            if (sr.getUnprocessedPaths() == null) {
                sr.getLastNode().getUserAllowPermissions().clear();
                sr.getLastNode().getUserDenyPermissions().clear();
                sr.getLastNode().getRoleAllowPermissions().clear();
                sr.getLastNode().getRoleDenyPermissions().clear();
            }
            updatePermissionTreeCache(root);
        } finally {
            write.unlock();
        }
    }

    /**
     * This method is only used for UI permissions
     * 
     * @param roles
     *            - Roles that needs to get resources
     * @param node
     *            - current node
     * @param permission
     *            - permission
     * @param path
     *            - list of path segments to traverse
     * @return - search result with allowed users
     */

    void getUIResourcesForRoles(String[] roles, List<String> resources, String path) throws UserStoreException {
        List<String> paths = PermissionTreeUtil.toComponenets(path);
        TreeNode node = null;
        read.lock();
        try {
            node = root;
            for (String name : paths) {
                node = node.getChild(name);
                if (node == null) {
                    break;
                }
            }
        } finally {
            read.unlock();
        }

        if (node == null) {
            throw new UserStoreException("Invalid Permission root path provided");
        }

        TreeNode permissionNode = node.getChild(CarbonConstants.UI_PERMISSION_NAME);
        if (permissionNode == null) {
            throw new UserStoreException("Invalid Permission root path provided");
        }
        
        if (path.endsWith("/")) {
            path = path.substring(0, path.length()-1);
        }
        
        getUIResourcesForRoles(roles, 
                               resources, 
                               "", /* The old code has this. So use the same thing*/ 
                               PermissionTreeUtil.actionToPermission(CarbonConstants.UI_PERMISSION_ACTION), 
                               permissionNode);
    }

    void getUIResourcesForRoles(String[] roles, List<String> resources, String path,
            TreeNode.Permission permission, TreeNode node) {
        read.lock();
        try {
            String currentPath = path + "/" + node.getName();

            Map<String, BitSet> bsAllowed = node.getRoleAllowPermissions();
            for (String role : roles) {
                BitSet bs = bsAllowed.get(role);
                if (bs != null && bs.get(permission.ordinal())) {
                    resources.add(currentPath);
                    return;
                }
            }

            Map<String, TreeNode> children = node.getChildren();
            for (TreeNode treeNode : children.values()) {
                if (treeNode != null) {
                    getUIResourcesForRoles(roles, resources, currentPath, permission, treeNode);
                }
            }
        } finally {
            read.unlock();
        }
    }
   

    /**
     * Clears all permission information in current node.
     */
    void clear() {
        write.lock();
        try {
            this.root.clearNodes();
            this.hashValueOfRootNode = -1;
        } finally {
            write.unlock();
        }

    }

    /**
     * update permission tree from cache
     * 
     * @throws org.wso2.carbon.user.core.UserStoreException
     *             throws if fail to update permission tree from DB
     */
    void updatePermissionTree() throws UserStoreException {
        PermissionTreeCacheKey cacheKey = new PermissionTreeCacheKey(tenantId);
        PermissionTreeCacheEntry cacheEntry = (PermissionTreeCacheEntry) permissionCache
                .getValueFromCache(cacheKey);
        if (cacheEntry != null) {
            if (cacheEntry.getPermissionTreeCacheEntry() != hashValueOfRootNode) {
                synchronized (this) {
                    cacheKey = new PermissionTreeCacheKey(tenantId);
                    cacheEntry = (PermissionTreeCacheEntry) permissionCache.getValueFromCache(cacheKey);
                    if (cacheEntry.getPermissionTreeCacheEntry() != hashValueOfRootNode) {
                        updatePermissionTreeFromDB();
                        
                        read.lock();
                        try {
                            updatePermissionTreeCache(root);
                        } finally {
                            read.unlock();
                        }
                        
                        log.info("Updated permission tree from database for tenant " + tenantId);
                    }
                }
            }
        } else {
            // create the entry
            synchronized (this) {
                cacheKey = new PermissionTreeCacheKey(tenantId);
                cacheEntry = (PermissionTreeCacheEntry) permissionCache.getValueFromCache(cacheKey);
                if (cacheEntry == null) {
                    updatePermissionTreeFromDB();
                    
                    read.lock();
                    try {
                        updatePermissionTreeCache(root);
                    } finally {
                        read.unlock();
                    }
                    
                    if (log.isDebugEnabled()) {
                        log.debug("Newly loaded permission tree from database for tenant " + 
                                   tenantId);
                    }
                }
            }
        }
    }
//////////////////////////////////////// private methods follows //////////////////////////////////////////////////
   
    /**
     * populate permission tree from database
     * 
     * @throws org.wso2.carbon.user.core.UserStoreException
     *             throws if fail to update permission tree from DB
     */
    private void updatePermissionTreeFromDB() throws UserStoreException {
        PermissionTree tree = new PermissionTree();
        ResultSet rs = null;
        PreparedStatement prepStmt1 = null;
        PreparedStatement prepStmt2 = null;
        Connection dbConnection = null;
        try {
            dbConnection = getDBConnection();
            // Populating role permissions
            prepStmt1 = dbConnection.prepareStatement(DBConstants.GET_EXISTING_ROLE_PERMISSIONS);
            prepStmt1.setInt(1, tenantId);
            prepStmt1.setInt(2, tenantId);
            rs = prepStmt1.executeQuery();

            while (rs.next()) {
                short allow = rs.getShort(3);
                if (allow == UserCoreConstants.ALLOW) {
                    tree.authorizeRoleInTree(rs.getString(1), rs.getString(2), rs.getString(4));
                } else {
                    tree.denyRoleInTree(rs.getString(1), rs.getString(2), rs.getString(4));
                }
            }

            // Populating user permissions
            prepStmt2 = dbConnection.prepareStatement(DBConstants.GET_EXISTING_USER_PERMISSIONS);
            prepStmt2.setInt(1, tenantId);
            prepStmt2.setInt(2, tenantId);
            rs = prepStmt2.executeQuery();

            while (rs.next()) {
                short allow = rs.getShort(3);
                if (allow == UserCoreConstants.ALLOW) {
                    tree.authorizeUserInTree(rs.getString(1), rs.getString(2), rs.getString(4));
                } else {
                    tree.denyUserInTree(rs.getString(1), rs.getString(2), rs.getString(4));
                }

            }
            
            write.lock();
            try {
                this.root = tree.root;
            } finally {
                write.unlock();
            }

        } catch (SQLException e) {
            throw new UserStoreException(
                    "Error loading authorizations. Please check the database. Error message is "
                            + e.getMessage(), e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt1, prepStmt2);
        }

    }

    /**
     * update permission key cache with hash code of in-memory permission tree
     */
    private void updatePermissionTreeCache(TreeNode root) {
        hashValueOfRootNode = root.hashCode();
        PermissionTreeCacheEntry cacheEntry = new PermissionTreeCacheEntry(hashValueOfRootNode);
        PermissionTreeCacheKey keyEntry = new PermissionTreeCacheKey(tenantId);
        permissionCache.addToCache(keyEntry, cacheEntry);
    }

    /**
     * Find a node on the tree, starting from the given nodes, and using the
     * list of path segments
     * 
     * @param node
     *            the start node to begin the search
     * @param pathParts
     *            a List of path segments - i.e. collection/resource names
     * @return the result as a SearchResult
     * @see SearchResult
     */
    private SearchResult getNode(TreeNode node, List<String> pathParts) {
        if (pathParts == null || pathParts.isEmpty()) {
            return new SearchResult(node, null);

        } else {
            String key = pathParts.get(0);
            if (key != null && key.length() > 0) {
                TreeNode child = node.getChild(key);
                if (child != null) {
                    pathParts.remove(0);
                    if (!pathParts.isEmpty()) {
                        return getNode(child, pathParts);
                    } else {
                        return new SearchResult(child, null);
                    }
                }
            }
            return new SearchResult(node, pathParts);
        }
    }
    
    private Connection getDBConnection() throws SQLException {
        Connection dbConnection = dataSource.getConnection();
        dbConnection.setAutoCommit(false);
        return dbConnection;
    }

}