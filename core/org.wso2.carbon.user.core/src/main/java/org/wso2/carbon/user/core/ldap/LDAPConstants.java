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
package org.wso2.carbon.user.core.ldap;

public class LDAPConstants {
    public static final String DRIVER_NAME = "com.sun.jndi.ldap.LdapCtxFactory";
    public static final String CONNECTION_URL = "ConnectionURL";
    public static final String CONNECTION_NAME = "ConnectionName";
    public static final String CONNECTION_PASSWORD = "ConnectionPassword";
    public static final String USER_SEARCH_BASE = "UserSearchBase";
    public static final String GROUP_SEARCH_BASE = "GroupSearchBase";
    public static final String USER_FILTER = "UserNameListFilter";
    public static final String USER_NAME_ATTRIBUTE_NAME = "UserNameAttribute";
    public static final String DEFAULT_TENANT_USER_FILTER = "DefaultTenantUserFilter";
    public static final String USER_DN_PATTERN = "UserDNPattern";
    //Property that defines the status of the referral to be used:
    public static final String PROPERTY_REFERRAL = "Referral";

    //filter attribute in user-mgt.xml that filters users by user name
    public static final String USER_NAME_FILTER = "UserNameSearchFilter";
    //this property indicates which object class should be used for user entries in LDAP
    public static final String USER_ENTRY_OBJECT_CLASS = "UserEntryObjectClass";
    // roles
    public static final String ROLE_FILTER = "GroupNameListFilter";
    public static final String ROLE_NAME_FILTER = "GroupNameSearchFilter";
    public static final String ROLE_NAME_ATTRIBUTE_NAME = "GroupNameAttribute";
    public static final String READ_EXTERNAL_ROLES = "ReadLDAPGroups";
    public static final String WRITE_EXTERNAL_ROLES = "WriteLDAPGroups";
    public static final String MEMBEROF_ATTRIBUTE = "MemberOfAttribute";
    public static final String MEMBERSHIP_ATTRIBUTE = "MembershipAttribute";
    public static final String EMPTY_ROLES_ALLOWED= "EmptyRolesAllowed";
    public static final String BACK_LINKS_ENABLED= "BackLinksEnabled";

    //ldap glossary
    public static final String OBJECT_CLASS_NAME = "objectClass";
    public static final String GROUP_ENTRY_OBJECT_CLASS = "GroupEntryObjectClass";
    public static final String ADMIN_ENTRY_NAME = "admin";

    //used in tenant management
    public static final String USER_CONTEXT_NAME = "users";
    public static final String GROUP_CONTEXT_NAME = "groups";
}
