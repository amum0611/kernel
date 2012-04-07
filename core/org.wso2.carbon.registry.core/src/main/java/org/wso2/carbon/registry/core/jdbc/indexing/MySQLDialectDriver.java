/*
 * Copyright (c) 2008, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.carbon.registry.core.jdbc.indexing;

import org.apache.lucene.store.jdbc.dialect.MySQLInnoDBDialect;

/**
 * This class overrides two methods, dealing with blobs in MySQLInnoDBDialect class to avoid the
 * error caused by not setting emulateLocators property to true in MySQL datasource.
 */
public class MySQLDialectDriver extends MySQLInnoDBDialect {

    public String openBlobSelectQuote() {
        return "`";
    }

    public String closeBlobSelectQuote() {
        return "`";
    }
}
