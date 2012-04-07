<!--
 ~ Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 ~
 ~ WSO2 Inc. licenses this file to you under the Apache License,
 ~ Version 2.0 (the "License"); you may not use this file except
 ~ in compliance with the License.
 ~ You may obtain a copy of the License at
 ~
 ~    http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing,
 ~ software distributed under the License is distributed on an
 ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 ~ KIND, either express or implied.  See the License for the
 ~ specific language governing permissions and limitations
 ~ under the License.
 -->
<%@ page import="org.apache.axis2.context.ConfigurationContext" %>
<%@ page import="org.wso2.carbon.CarbonConstants" %>
<%@ page import="org.wso2.carbon.feature.mgt.ui.FeatureWrapper" %>
<%@ page import="org.wso2.carbon.feature.mgt.ui.RepositoryAdminServiceClient" %>
<%@ page import="org.wso2.carbon.feature.mgt.stub.prov.data.Feature" %>
<%@ page import="org.wso2.carbon.feature.mgt.ui.util.Utils" %>
<%@ page import="org.wso2.carbon.ui.CarbonUIUtil" %>
<%@ page import="org.wso2.carbon.utils.ServerConstants" %>
<%@ page import="java.util.Stack" %>
<%@ taglib uri="http://wso2.org/projects/carbon/taglibs/carbontags.jar" prefix="carbon" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>

<%
    String queryType = request.getParameter("queryType");
    String repositoryURL = request.getParameter("repoURL");
    boolean groupByCategory = Boolean.parseBoolean(request.getParameter("groupByCategory"));
    boolean showLatest = Boolean.parseBoolean(request.getParameter("showLatest"));
    boolean hideInstalled = Boolean.parseBoolean(request.getParameter("hideInstalled"));
    String filterStr = request.getParameter("filterStr");

    String backendServerURL = CarbonUIUtil.getServerURL(config.getServletContext(), session);
    ConfigurationContext configContext =
            (ConfigurationContext) config.getServletContext().getAttribute(CarbonConstants.CONFIGURATION_CONTEXT);
    String cookie = (String) session.getAttribute(ServerConstants.ADMIN_SERVICE_COOKIE);
    RepositoryAdminServiceClient repositoryAdminClient = null;
    FeatureWrapper[] featureWrappers = null;
    int maxheight;

    try {
    	repositoryAdminClient = new RepositoryAdminServiceClient(cookie, backendServerURL, configContext, request.getLocale());
        featureWrappers = repositoryAdminClient.getInstallableFeatures(repositoryURL, groupByCategory, hideInstalled, showLatest);
        request.getSession(true).setAttribute(RepositoryAdminServiceClient.AVAILABLE_FEATURES, featureWrappers);
        if(featureWrappers != null){
            if("searchQuery".equals(queryType)){
                featureWrappers = Utils.filterFeatures(featureWrappers, filterStr);
            }
        } 
		
        maxheight = Utils.getMaxHeight(featureWrappers, 0);
     } catch (Exception e) {
%>
<p id="compMgtErrorMsg"><%=e.getMessage()%></p>
<%
        return;
    }
%>
<br/>
<fmt:bundle basename="org.wso2.carbon.feature.mgt.ui.i18n.Resources">
<carbon:itemGroupSelector selectAllInPageFunction="selecteAllAvailableFeatures(true);return false"
                          selectAllFunction="selectAllInAllPages()"
                          selectNoneFunction="selecteAllAvailableFeatures(false);return false"
                          addRemoveFunction="doNext('AF-RF')"
                          addRemoveButtonId="install1"
                          resourceBundle="org.wso2.carbon.feature.mgt.ui.i18n.Resources"
                          selectAllInPageKey="select.all.in.page"
                          selectAllKey="select.all"
                          selectNoneKey="select.none"
                          addRemoveKey="install"/>
    
<table class="styledLeft" cellspacing="1" width="100%" id="_table_available_features_list_main"
           style="margin-left: 0px;">
        <tbody>

        <tr>
            <td style="padding-left:0px !important;padding-right:0px !important">
                <table class="styledLeft" cellspacing="1" width="100%" id="_table_available_features_list"
                   style="margin-left: 0px;">
                    <thead>
                    <tr>
                        <th><fmt:message key="features"/></th>
                        <th><fmt:message key="version"/></th>
                        <th><fmt:message key="actions"/></th>
                    </tr>
                    </thead>
                    <tbody id="_table_available_features_list_body">
            <%
                if(featureWrappers == null || featureWrappers.length == 0){
            %>
                    <tr>
                        <td colspan="0"><fmt:message key="no.available.features"/></td>
                    </tr>
            <%
                } else {
                	featureWrappers=Utils.truncatePrefixAndSuffixOfFeature(featureWrappers);
                	Utils.sortAscendingByFeatureName(featureWrappers);
                    for (FeatureWrapper featureWrapper : featureWrappers) {
                    	Stack<FeatureWrapper> stack = new Stack<FeatureWrapper>();
                        stack.add(featureWrapper);
                        while (!stack.isEmpty()) {
                            FeatureWrapper popedFeature = stack.pop();
                            Feature feature = popedFeature.getWrappedFeature();
                            String featureVersion = feature.getFeatureVersion();
                            String featureID = feature.getFeatureID();
                            int height = popedFeature.getHeight();
                            boolean hasChildren = popedFeature.hasChildren();
							boolean isFeatureCategoryType = false;
							if(("org.eclipse.equinox.p2.type.category").equals(feature.getFeatureType())){
								isFeatureCategoryType = true;
							}
							                          
                            String trID = featureID.replace('.', '_') + "_" + featureVersion.replace('.', '_');
                            String parentElementID = popedFeature.getParentElementID();
                            
                            FeatureWrapper[] requiredFeatures = popedFeature.getRequiredFeatures();
                            if(requiredFeatures != null && requiredFeatures.length > 0){
                            	requiredFeatures=Utils.truncatePrefixAndSuffixOfFeature(requiredFeatures);
                            	Utils.sortDescendingByFeatureName(requiredFeatures);
                            	
                                for(FeatureWrapper requiredFeature: requiredFeatures){
                                    		requiredFeature.setParentElementID(trID);                                        	
                                    		stack.push(requiredFeature);                                      	
                                    }                          
                             }

                            if(height == 0 || !popedFeature.isHiddenRow()){
                        	
             %>
                    <tr id="<%=trID%>">
             <%
                            } else {
              %>
                    <tr id="<%=trID%>" style="display:none; background-color:#fff;" >
             <%
                                                    }
                                     %>
	                                     <td class="featureNameCol" abbr="<%=((maxheight + 3) - (height + 2))%>"><%--featureNameCol is not for styling it's to get the elements from client side--%>
                                     <%
                                     		int spaceWidth = height*16;
                                     		if(!hasChildren){
                                     			spaceWidth += 16;
                                     		}
				     %>                                                    
                                                <img src="images/spacer.png" style="height:1px;width:<%=spaceWidth%>px;"  />
                                     <%
                                                    if(hasChildren){
                                     %>
                                                <img src="images/plus.gif" style="cursor:pointer;" style="cursor:pointer" onclick="collapseTree(this)" class="ui-plusMinusIcon" />
                                     <%
                                          	}
                                                    //if groupByCategory is ON enable select option for the immediate child list of the Category type IUs ORR
                                                    //if groupByCategory is OFF enable select option only for the root elements in the Group type IUs
                                                	if( (groupByCategory && !isFeatureCategoryType &&  height > 1) || (!groupByCategory && height != 0) ){ 
                                     %>
                                                <img src="images/spacer.png" style="height:1px;width:10px;"  />

                                     <%
                                                   } else {
                                     %>
                                                    <input class="checkbox-select" type="checkbox"
                                                           name="chkSelectFeaturesToInstall"                                      
                                                           value="<%=feature.getFeatureID()%>::<%=feature.getFeatureVersion()%>"
                                                           onclick="checkBoxSelectedinstall(this);"/>
                                     <%
                                                    }
                                     %>
                                                <span class="featureDiscriptionOfAvailableFeatures" id="<%=feature.getFeatureID().replace('.','-')%>">
                                                <%=feature.getFeatureName()%> </span></td> 
                                                <%
                                                	//not showing version and more-info/actions to feature category row 
                                                	if(!isFeatureCategoryType){
                                                	 
                                                %>
                                                  
                                                <td><%=featureVersion%></td>
                                                <td>
                                                    <a class="icon-link" style="background-image: url(images/more-info.gif);"
                                                           href="#" onclick="loadSelectedFeatureDetails('<%=featureID%>','<%=featureVersion%>', false, '_div_tabs01-FD', '_div_tabs01-step-00-FR', 1);"><fmt:message key="more.info"/></a>
                                                </td>
                                               <%
                                                	
                                                	}else{ 
                                                %> 
                                                <td/>
                                                <td/>
                                                <% 
                                                	}
                                                %>
                                            </tr>
            <%                                                    
                            }
                        }
                    }
            %>
                    </tbody>
                </table>
            </td>
        </tr>
        </tbody>
    </table>

<carbon:itemGroupSelector selectAllInPageFunction="selecteAllAvailableFeatures(true);return false"
                          selectAllFunction="selectAllInAllPages()"
                          selectNoneFunction="selecteAllAvailableFeatures(false);return false"
                          addRemoveFunction="doNext('AF-RF')"
                          addRemoveButtonId="install2"
                          resourceBundle="org.wso2.carbon.feature.mgt.ui.i18n.Resources"
                          selectAllInPageKey="select.all.in.page"
                          selectAllKey="select.all"
                          selectNoneKey="select.none"
                          addRemoveKey="install"/>
</fmt:bundle>
<script type="text/javascript">
    YAHOO.util.Event.onDOMReady(function() {
        customAlternateTableRows('_table_available_features_list', 'tableEvenRow', 'tableOddRow');
    });
</script>
