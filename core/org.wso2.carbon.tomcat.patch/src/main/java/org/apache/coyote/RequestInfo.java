/*
 * Copyright (c) 2011, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *                No exported packages

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.coyote;

import org.wso2.carbon.tomcat.ext.transport.statistics.TransportStatisticsContainer;
import org.wso2.carbon.tomcat.ext.transport.statistics.TransportStatisticsEntry;

import javax.management.ObjectName;

public class RequestInfo {
    RequestGroupInfo global = null;

    // ----------------------------------------------------------- Constructors

    public RequestInfo(Request req) {
        this.req = req;
    }

    public RequestGroupInfo getGlobalProcessor() {
        return global;
    }

    public void setGlobalProcessor(RequestGroupInfo global) {
        if (global != null) {
            this.global = global;
            global.addRequestProcessor(this);
        } else {
            if (this.global != null) {
                this.global.removeRequestProcessor(this);
                this.global = null;
            }
        }
    }


    // ----------------------------------------------------- Instance Variables
    Request req;
    int stage = Constants.STAGE_NEW;
    String workerThreadName;
    ObjectName rpName;

    // -------------------- Information about the current request  -----------
    // This is useful for long-running requests only

    public String getMethod() {
        return req.method().toString();
    }

    public String getCurrentUri() {
        return req.requestURI().toString();
    }

    public String getCurrentQueryString() {
        return req.queryString().toString();
    }

    public String getProtocol() {
        return req.protocol().toString();
    }

    public String getVirtualHost() {
        return req.serverName().toString();
    }

    public int getServerPort() {
        return req.getServerPort();
    }

    public String getRemoteAddr() {
        req.action(ActionCode.REQ_HOST_ADDR_ATTRIBUTE, null);
        return req.remoteAddr().toString();
    }

    public int getContentLength() {
        return req.getContentLength();
    }

    public long getRequestBytesReceived() {
        return req.getBytesRead();
    }

    public long getRequestBytesSent() {
        return req.getResponse().getContentWritten();
    }

    public long getRequestProcessingTime() {
        if (getStage() == org.apache.coyote.Constants.STAGE_ENDED) {
           return 0;
        } else {
            return (System.currentTimeMillis() - req.getStartTime());
        }
    }

    // -------------------- Statistical data  --------------------
    // Collected at the end of each request.
    private long bytesSent;
    private long bytesReceived;

    // Total time = divide by requestCount to get average.
    private long processingTime;
    // The longest response time for a request
    private long maxTime;
    // URI of the request that took maxTime
    private String maxRequestUri;

    private int requestCount;
    // number of response codes >= 400
    private int errorCount;

    //the time of the last request
    private long lastRequestProcessingTime = 0;

    // Size of the last request
    private long lastRequestSize;

    // Size of the last response
    private long lastResponseSize;

    // URI of the last request
    private String lastRequestUri;


    /**
     * Called by the processor before recycling the request. It'll collect
     * statistic information.
     */
    void updateCounters() {
        bytesReceived += req.getBytesRead();
        bytesSent += req.getResponse().getContentWritten();

        // Patch to record bandwidth statistics data. /////
        publishBandwidthUsageStatistics(req);            //
        ///////////////////////////////////////////////////

        requestCount++;
        if (req.getResponse().getStatus() >= 400) {
            errorCount++;
        }
        long t0 = req.getStartTime();
        long t1 = System.currentTimeMillis();
        long time = t1 - t0;
        this.lastRequestProcessingTime = time;
        processingTime += time;
        if (maxTime < time) {
            maxTime = time;
            maxRequestUri = req.requestURI().toString();
        }
    }

    /**
     * Publish bandwidth usage data(request size, response size and request url) to carbon.
     * @param request Coyote request object
     */
    private void publishBandwidthUsageStatistics(Request request){
        TransportStatisticsEntry entry = new TransportStatisticsEntry(request.getBytesRead(),
                request.getResponse().getContentWritten(),
                request.requestURI().toString());
        String constructedUri = entry.constructRequestUrl(request.requestURI().toString(), request.getHeader("HOST"));
        if(!constructedUri.equalsIgnoreCase(request.requestURI().toString())) {
            entry.setRequestUrl(constructedUri);
        }
        if (entry.getContext() != null && (entry.getContext().equals("services") || entry.getContext().equals("webapps"))) {
            TransportStatisticsContainer.addTransportStatisticsEntry(entry);
        }
    }

    public int getStage() {
        return stage;
    }

    public void setStage(int stage) {
        this.stage = stage;
    }

    public long getBytesSent() {
        return bytesSent;
    }

    public void setBytesSent(long bytesSent) {
        this.bytesSent = bytesSent;
    }

    public long getBytesReceived() {
        return bytesReceived;
    }

    public void setBytesReceived(long bytesReceived) {
        this.bytesReceived = bytesReceived;
    }

    public long getProcessingTime() {
        return processingTime;
    }

    public void setProcessingTime(long processingTime) {
        this.processingTime = processingTime;
    }

    public long getMaxTime() {
        return maxTime;
    }

    public void setMaxTime(long maxTime) {
        this.maxTime = maxTime;
    }

    public String getMaxRequestUri() {
        return maxRequestUri;
    }

    public void setMaxRequestUri(String maxRequestUri) {
        this.maxRequestUri = maxRequestUri;
    }

    public int getRequestCount() {
        return requestCount;
    }

    public void setRequestCount(int requestCount) {
        this.requestCount = requestCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(int errorCount) {
        this.errorCount = errorCount;
    }

    public String getWorkerThreadName() {
        return workerThreadName;
    }

    public ObjectName getRpName() {
        return rpName;
    }

    public long getLastRequestProcessingTime() {
        return lastRequestProcessingTime;
    }

    public void setWorkerThreadName(String workerThreadName) {
        this.workerThreadName = workerThreadName;
    }

    public void setRpName(ObjectName rpName) {
        this.rpName = rpName;
    }

    public void setLastRequestProcessingTime(long lastRequestProcessingTime) {
        this.lastRequestProcessingTime = lastRequestProcessingTime;
    }

    public long getLastRequestSize() {
        return lastRequestSize;
    }

    public long getLastResponseSize() {
        return lastResponseSize;
    }

    public String getLastRequestUri() {
        return lastRequestUri;
    }
}
