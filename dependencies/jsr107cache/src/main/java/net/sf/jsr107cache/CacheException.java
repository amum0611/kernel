/*
 *  Copyright (c) WSO2 Inc. (http://wso2.com) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package net.sf.jsr107cache;


/**
 * CacheException is a generic exception, which indicates
 * a cache error has occurred. All the other cache exceptions are the
 * subclass of this class. All the methods in the cache package only
 * throw CacheException or the sub class of it.
 * <P>
 *
 */
public class CacheException extends Exception
{
    /**
     * Constructs a new CacheException.
     */
    public CacheException()
    {
        super();
    }

    /**
     * Constructs a new CacheException with a message string.
     */
    public CacheException(String s)
    {
        super(s);
    }

    /**
     * Constructs a CacheException with a message string, and
     * a base exception
     */
    public CacheException(String s, Throwable ex)
    {
        super(s, ex);
    }
}
