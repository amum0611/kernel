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
package org.wso2.carbon.user.core.util;

import org.apache.axiom.om.util.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.user.api.RealmConfiguration;
import org.wso2.carbon.user.core.UserCoreConstants;
import org.wso2.carbon.user.core.UserRealm;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.internal.Activator;
import org.wso2.carbon.user.core.service.RealmService;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to handle user kernel utilities.
 */
public final class UserCoreUtil {

    private static Log log = LogFactory.getLog(UserCoreUtil.class);

    private static Boolean isEmailUserName;
    private static String domainCalculation;
    private static Boolean isCrossTenantUniqueUserName;
    private static final SynchronizingClass loadlock = new SynchronizingClass();
    private static RealmService realmService = null;
    private static final String DUMMY_VALUE = "dummy";
    
    private UserCoreUtil() {
    }

    private static class SynchronizingClass {
    }

    public static String[] combineArrays(String[] arr1, String[] arr2) throws UserStoreException {
        if (arr1 == null || arr1.length == 0) {
            return arr2;
        }
        if (arr2 == null || arr2.length == 0) {
            return arr1;
        }
        String[] newArray = new String[arr1.length + arr2.length];
        for (int i = 0; i < arr1.length; i++) {
            newArray[i] = arr1[i];
        }

        int j = 0;
        for (int i = arr1.length; i < newArray.length; i++) {
            Arrays.toString(newArray);
            newArray[i] = arr2[j];
            j++;
        }
        return newArray;
    }

    public static String[] combine(String[] array, List<String> list) throws UserStoreException {
        // TODO :: null checks
        String[] newArray = array;
        if (list.size() > 0) {
            newArray = new String[list.size() + array.length];
            int i = 0;
            for (Iterator<String> ite = list.iterator(); ite.hasNext();) {
                String name = ite.next();
                newArray[i] = name;
                i++;
            }

            for (String name : array) {
                newArray[i] = name;
                i++;
            }
        }
        return newArray;
    }


    private static void loadData() {
        synchronized (loadlock) {
            if (isEmailUserName == null) {
                try {
                    if (realmService != null) {
                        UserRealm realm = (UserRealm) realmService.getBootstrapRealm();
                        RealmConfiguration realmConfig = realm.getRealmConfiguration();

                        if (isCrossTenantUniqueUserName == null) {
                            String isUnique = realmConfig
                                    .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_USERNAME_UNIQUE);
                            if ("true".equals(isUnique)) {
                                isCrossTenantUniqueUserName = true;
                            } else {
                                isCrossTenantUniqueUserName = false;
                            }
                        }

                        if (isEmailUserName == null) {
                            String isEmail = realmConfig
                                    .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_IS_EMAIL_USERNAME);
                            if ("true".equals(isEmail)) {
                                isEmailUserName = true;
                            } else {
                                isEmailUserName = false;
                            }
                        }

                        if (domainCalculation == null) {
                            domainCalculation = realmConfig
                                    .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_DOMAIN_CALCULATION);
                            if (domainCalculation == null) {
                                domainCalculation = UserCoreConstants.RealmConfig.PROPERTY_VALUE_DOMAIN_CALCULATION_DEFAULT;
                            }
                        }
                    } else {
                        log.error("UserCoreUtil.loadData is called before the realm service is set. So using default settings.");
                    }
                } catch (Exception e) {
                    log.error("Failed to load data :" + e.getMessage(), e);
                    throw new RuntimeException("Failed to load data :" + e.getMessage(), e);
                }
            }
        }
    }

    public static String[] optimizePermissions(String[] rawResourcePath) {
        Arrays.sort(rawResourcePath);
        int index = 0;
        List<String> lst = new ArrayList<String>();
        while (index < rawResourcePath.length) {
            String shortestString = rawResourcePath[index];
            lst.add(shortestString);
            index++;
            Pattern p = Pattern.compile("(.*)/.*$");
            while (index < rawResourcePath.length) {
                Matcher m = p.matcher(rawResourcePath[index]);
                if (m.find()) {
                    String s = m.group(1);
                    if (s.equals(shortestString)) {
                        index++;
                    } else {
                        break;
                    }
                }
            }
        }
        return lst.toArray(new String[lst.size()]);
    }

    public static Boolean getIsEmailUserName() {
        return isEmailUserName;
    }


    public static RealmService getRealmService() {
        return realmService;
    }

    public static void setRealmService(RealmService realmService) {
        UserCoreUtil.realmService = realmService;
    }

    public static Boolean getIsCrossTenantUniqueUserName() {
        return isCrossTenantUniqueUserName;
    }

    public static String getPasswordToStore(String password, String passwordHashMethod,
                                             boolean isKdcEnabled) throws UserStoreException {

        if (isKdcEnabled) {
            // If KDC is enabled we will always use plain text passwords.
            // Cause - KDC cannot operate with hashed passwords.

            return password;
        }

        String passwordToStore = password;

        if (passwordHashMethod != null) {

            if (passwordHashMethod.
                    equals(UserCoreConstants.RealmConfig.PASSWORD_HASH_METHOD_PLAIN_TEXT)) {
                return passwordToStore;
            }

            try {
                MessageDigest messageDigest = MessageDigest.getInstance(passwordHashMethod);
                byte[] digestValue = messageDigest.digest(password.getBytes());
                passwordToStore = "{" + passwordHashMethod + "}" + Base64.encode(digestValue);
            } catch (NoSuchAlgorithmException e) {
                throw new UserStoreException("Invalid hashMethod", e);
            }
        }
        return passwordToStore;
    }

    public static boolean isKdcEnabled(RealmConfiguration realmConfig) {

        String stringKdcEnabled = realmConfig.getUserStoreProperty(
                UserCoreConstants.RealmConfig.PROPERTY_KDC_ENABLED);

        if (stringKdcEnabled != null) {
            return Boolean.parseBoolean(stringKdcEnabled);
        } else {
            return false;
        }
    }
    
	public static String getDummyPassword() {
		Random rand = new Random();
		return DUMMY_VALUE + rand.nextInt(999999);
	}
}
