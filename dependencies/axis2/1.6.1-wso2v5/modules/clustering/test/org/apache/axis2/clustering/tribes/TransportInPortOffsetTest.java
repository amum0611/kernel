package org.apache.axis2.clustering.tribes;

import junit.framework.TestCase;

import org.apache.axis2.clustering.ClusteringConstants;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.ConfigurationContextFactory;
import org.apache.axis2.description.Parameter;
import org.junit.Test;

public class TransportInPortOffsetTest extends TestCase {

	protected TribesClusteringAgent clusterManager1 = null;
	protected TribesClusteringAgent clusterManager2 = null;
	protected ConfigurationContext configurationContext1 = null;
	protected ConfigurationContext configurationContext2 = null;

	private static final Parameter domainParam =
	                                             new Parameter(
	                                                           ClusteringConstants.Parameters.DOMAIN,
	                                                           "wso2.carbon.domain");

	private static final Parameter schemeParam =
	                                             new Parameter(
	                                                           ClusteringConstants.Parameters.MEMBERSHIP_SCHEME,
	                                                           "wka");

	protected void setUp() throws Exception {

		// first TribesClusteringAgent
		configurationContext1 =
		                        ConfigurationContextFactory.createBasicConfigurationContext("axis2.xml");

		clusterManager1 = new TribesClusteringAgent();
		clusterManager1.setConfigurationContext(configurationContext1);
		clusterManager1.addParameter(domainParam);
		clusterManager1.addParameter(schemeParam);
		clusterManager1.init();

		// set port offset
		System.setProperty("portOffset", "3");

		// second TribesClusteringAgent after setting portOffset
		configurationContext2 =
		                        ConfigurationContextFactory.createBasicConfigurationContext("axis2.xml");

		clusterManager2 = new TribesClusteringAgent();
		clusterManager2.setConfigurationContext(configurationContext2);
		clusterManager2.addParameter(domainParam);
		clusterManager2.addParameter(schemeParam);
		clusterManager2.init();

	}

	protected void tearDown() throws Exception {
		super.tearDown();
		clusterManager1.shutdown();
		clusterManager2.shutdown();
	}

	@Test
	public void test() {
		// without setting portOffset
		assertEquals(8080,
		             TribesUtil.toAxis2Member(clusterManager1.getPrimaryMembershipManager()
		                                                     .getLocalMember()).getHttpPort());

		// with portOffset=3
		assertEquals(8083,
		             TribesUtil.toAxis2Member(clusterManager2.getPrimaryMembershipManager()
		                                                     .getLocalMember()).getHttpPort());

	}

}
