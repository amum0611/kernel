/*
 *  Copyright (c) 2005-2009, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.carbon.utils.logging;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.log4j.PatternLayout;
import org.apache.log4j.helpers.FormattingInfo;
import org.apache.log4j.helpers.PatternConverter;
import org.apache.log4j.helpers.PatternParser;
import org.apache.log4j.spi.LoggingEvent;
import org.wso2.carbon.base.ServerConfiguration;
import org.wso2.carbon.utils.ServerConstants;
import org.wso2.carbon.utils.multitenancy.CarbonApplicationContextHolder;
import org.wso2.carbon.utils.multitenancy.CarbonContextHolder;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;

/**
 * A log4j pattern layout implementation capable of capturing tenant details
 * from the Carbon Context. In addition to the default conversion characters,
 * the character 'T' can be used to insert a tenant identifier, the character
 * 'D' can be used to insert a tenant domain, and the character 'U' can be used
 * to insert the username, character 'S' can be used to insert the Service name.
 * <p/>
 * The special character 'P' can be used to insert a pattern layout, that will
 * get printed if tenant information is available. The default layout for this
 * is <code>%U@%D [%T]</code>. If tenant information is not available an empty
 * string will be returned. The tenant pattern can be changed via the log4j
 * configuration. You simply need to add a line in the format,
 * <code>log4j.appender.NAME.layout.TenantPattern=%U@%D [%T]</code>.
 */

public class TenantAwarePatternLayout extends PatternLayout {

	/**
	 * The default pattern to be injected in tenant-mode.
	 */
	public static final String DEFAULT_TENANT_PATTERN = "%U%@%D [%T][%S]";

	private static String tenantPattern = DEFAULT_TENANT_PATTERN;
	private static String superTenantText = null;

	/**
	 * The default constructor.
	 */
	public TenantAwarePatternLayout() {
		super();
	}

	/**
	 * Constructor accepting a layout pattern.
	 * 
	 * @param pattern
	 *            the layout pattern.
	 */
	public TenantAwarePatternLayout(String pattern) {
		super(pattern);
	}

	/**
	 * {@inheritDoc}
	 */
	protected PatternParser createPatternParser(String pattern) {
		return new TenantAwarePatternParser(pattern);
	}

	/**
	 * Method to set the log pattern layout for tenants.
	 * 
	 * @param tenantPattern
	 *            the log pattern layout for tenants.
	 */
	public synchronized void setTenantPattern(String tenantPattern) {
		TenantAwarePatternLayout.tenantPattern = tenantPattern;
	}

	/**
	 * Method to set the string that will be put in place of the log pattern
	 * layout for the super tenant.
	 * 
	 * @param superTenantText
	 *            the string that will be put in place of the log pattern layout
	 *            for the super tenant.
	 */
	public static void setSuperTenantText(String superTenantText) {
		TenantAwarePatternLayout.superTenantText = superTenantText;
	}

	// A tenant-aware pattern parser implementation. For more information on the
	// structure of this
	// class see log4j PatternParser code.
	private static class TenantAwarePatternParser extends PatternParser {

		public TenantAwarePatternParser(String pattern) {
			super(pattern);
		}

		protected void finalizeConverter(char c) {
			PatternConverter pc = null;
			switch (c) {
			case 'D':
				pc = new TenantDomainPatternConverter(formattingInfo, extractPrecisionOption());
				break;
			case '@':
				pc = new AtSignPatternConverter(formattingInfo);
				break;
			case 'P':
				pc = new TenantPatternConverter(formattingInfo, extractPrecisionOption());
				break;
			case 'T':
				pc = new TenantIdPatternConverter(formattingInfo, extractPrecisionOption());
				break;
			case 'S':
				pc = new ServiceNamePatternConverter(formattingInfo, extractPrecisionOption());
				break;
			case 'U':
				pc = new UserNamePatternConverter(formattingInfo, extractPrecisionOption());
				break;
			case 'A':
				pc = new AppNamePatternConverter(formattingInfo, extractPrecisionOption());
				break;
			case 'H':
				pc = new HostNamePatternConverter(formattingInfo, extractPrecisionOption());
				break;
			case 'I':
				pc = new InstanceIdPatternConverter(formattingInfo, extractPrecisionOption());
				break;
			default:
				super.finalizeConverter(c);
			}
			if (pc != null) {
				currentLiteral.setLength(0);
				addConverter(pc);
			}
		}

		private abstract static class TenantAwareNamedPatternConverter extends PatternConverter {

			private int precision;

			public TenantAwareNamedPatternConverter(FormattingInfo formattingInfo, int precision) {
				super(formattingInfo);
				this.precision = precision;
			}

			protected abstract String getFullyQualifiedName(LoggingEvent event);

			public String convert(LoggingEvent event) {
				String n = getFullyQualifiedName(event);
				if (n == null) {
					return "";
				}
				if (precision <= 0) {
					return n;
				} else {
					int len = n.length();

					// We subtract 1 from 'len' when assigning to 'end' to avoid
					// out of
					// bounds exception in return r.substring(end+1, len). This
					// can happen if
					// precision is 1 and the category name ends with a dot.
					int end = len - 1;
					for (int i = precision; i > 0; i--) {
						end = n.lastIndexOf('.', end - 1);
						if (end == -1) {
							return n;
						}
					}
					return n.substring(end + 1, len);
				}
			}
		}

		private static class TenantIdPatternConverter extends TenantAwareNamedPatternConverter {

			public TenantIdPatternConverter(FormattingInfo formattingInfo, int precision) {
				super(formattingInfo, precision);
			}

			public String getFullyQualifiedName(LoggingEvent event) {
				if (event instanceof TenantAwareLoggingEvent) {
					return ((TenantAwareLoggingEvent) event).getTenantId();
				} else {
					if (CarbonContextHolder.getCurrentCarbonContextHolder().getTenantId() != 
							MultitenantConstants.INVALID_TENANT_ID) {
						return Integer.toString(CarbonContextHolder.getCurrentCarbonContextHolder()
								.getTenantId());
					}
				}

				return null;
			}
		}

		private static class UserNamePatternConverter extends TenantAwareNamedPatternConverter {

			public UserNamePatternConverter(FormattingInfo formattingInfo, int precision) {
				super(formattingInfo, precision);
			}

			public String getFullyQualifiedName(LoggingEvent event) {

				return CarbonContextHolder.getCurrentCarbonContextHolder().getUsername();
			}
		}

		private static class TenantDomainPatternConverter extends TenantAwareNamedPatternConverter {

			public TenantDomainPatternConverter(FormattingInfo formattingInfo, int precision) {
				super(formattingInfo, precision);
			}

			public String getFullyQualifiedName(LoggingEvent event) {
				return CarbonContextHolder.getCurrentCarbonContextHolder().getTenantDomain();
			}
		}

		private static class ServiceNamePatternConverter extends TenantAwareNamedPatternConverter {

			public ServiceNamePatternConverter(FormattingInfo formattingInfo, int precision) {
				super(formattingInfo, precision);
			}

			public String getFullyQualifiedName(LoggingEvent event) {
				return ServerConfiguration.getInstance().getFirstProperty("Name");
			}
		}

		private static class HostNamePatternConverter extends TenantAwareNamedPatternConverter {

			public HostNamePatternConverter(FormattingInfo formattingInfo, int precision) {
				super(formattingInfo, precision);
			}

			public String getFullyQualifiedName(LoggingEvent event) {
				InetAddress inetAddress;
				try {
					inetAddress = InetAddress.getLocalHost();
					return inetAddress.getHostAddress();
				} catch (UnknownHostException e) {
					return "0.0.0.0";
				}
			}
		}
		
		private static class InstanceIdPatternConverter extends TenantAwareNamedPatternConverter {

			public InstanceIdPatternConverter(FormattingInfo formattingInfo, int precision) {
				super(formattingInfo, precision);
			}

			public String getFullyQualifiedName(LoggingEvent event) {
				  String stratosInstance = System.getProperty(ServerConstants.STRATOS_INSTANCE);
				  if (stratosInstance != null) {
					  return stratosInstance;
				  }
				  return "";
			}
		}


		private static class AppNamePatternConverter extends TenantAwareNamedPatternConverter {

			public AppNamePatternConverter(FormattingInfo formattingInfo, int precision) {
				super(formattingInfo, precision);
			}

			public String getFullyQualifiedName(LoggingEvent event) {
				if (event instanceof TenantAwareLoggingEvent) {
					if (((TenantAwareLoggingEvent) event).getServiceName() != null) {
						return ((TenantAwareLoggingEvent) event).getServiceName();
					} else {
						return "";
					}
				} else {
					if (CarbonApplicationContextHolder.getCurrentCarbonAppContextHolder().getApplicationName() != null) {
						return CarbonApplicationContextHolder.getCurrentCarbonAppContextHolder().getApplicationName();
					} else {
						return "";
					}
					
				}
				
//				
			}
		}

		private static class AtSignPatternConverter extends TenantAwareNamedPatternConverter {

			public AtSignPatternConverter(FormattingInfo formattingInfo) {
				super(formattingInfo, -1);
			}

			public String getFullyQualifiedName(LoggingEvent event) {
				if (CarbonContextHolder.getCurrentCarbonContextHolder().getTenantDomain() != null) {
					return "@";
				}
				return null;
			}
		}

		private static class TenantPatternConverter extends TenantAwareNamedPatternConverter {

			public TenantPatternConverter(FormattingInfo formattingInfo, int precision) {
				super(formattingInfo, precision);
			}

			public String getFullyQualifiedName(LoggingEvent event) {
				if (CarbonContextHolder.getCurrentCarbonContextHolder().getTenantId() !=
						MultitenantConstants.INVALID_TENANT_ID
						&& CarbonContextHolder.getCurrentCarbonContextHolder().getTenantId() != MultitenantConstants.SUPER_TENANT_ID) {
					return new TenantAwarePatternLayout(tenantPattern).format(event);
				}
				return superTenantText;
			}
		}
	}
}
