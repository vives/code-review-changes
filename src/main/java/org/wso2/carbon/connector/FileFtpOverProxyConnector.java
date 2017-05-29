/*
 *  Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.carbon.connector;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPConnectionClosedException;
import org.apache.commons.net.ftp.FTPHTTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.wso2.carbon.connector.core.AbstractConnector;
import org.wso2.carbon.connector.core.Connector;
import org.wso2.carbon.connector.core.util.ConnectorUtils;
import org.wso2.carbon.connector.util.FileConstants;
import org.wso2.carbon.connector.util.ResultPayloadCreator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * This class is used to tunnel FTP client over an HTTP proxy connection.
 */
public class FileFtpOverProxyConnector extends AbstractConnector {
	private static final Log log = LogFactory.getLog(FileFtpOverProxyConnector.class);

	/**
	 * Initiate the ftpOverHttpProxy method.
	 *
	 * @param messageContext The message context that is used in file ftp over proxy mediation flow.
	 */
	public void connect(MessageContext messageContext) {
		ResultPayloadCreator.generateResult(messageContext, ftpOverHttpProxy(messageContext));
	}

	/**
	 * Send file FTP over Proxy.
	 *
	 * @param messageContext The message context that is generated for processing the file.
	 * @return true, if the FTP client tunnels over an HTTP proxy connection or stores a file on the server.
	 *
	 */
	public boolean ftpOverHttpProxy(MessageContext messageContext) {
		String proxyHost = StringUtils
				.trim((String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.PROXY_HOST));
		String proxyPort = StringUtils
				.trim((String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.PROXY_PORT));
		String proxyUsername = StringUtils
				.trim((String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.PROXY_USERNAME));
		String proxyPassword = StringUtils
				.trim((String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.PROXY_PASSWORD));
		String ftpHost = StringUtils
				.trim((String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.FTP_SERVER));
		String ftpPort = StringUtils
				.trim((String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.FTP_PORT));
		String ftpUsername = StringUtils
				.trim((String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.FTP_USERNAME));
		String ftpPassword = StringUtils
				.trim((String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.FTP_PASSWORD));
		String keepAliveTimeout = StringUtils.trim((String) ConnectorUtils
				.lookupTemplateParamater(messageContext, FileConstants.KEEP_ALIVE_TIMEOUT));
		String controlKeepAliveReplyTimeout = StringUtils.trim((String) ConnectorUtils
				.lookupTemplateParamater(messageContext, FileConstants.CONTROL_KEEP_ALIVE_REPLY_TIMEOUT));
		String targetPath = StringUtils
				.trim((String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.TARGET_PATH));
		String targetFile = StringUtils
				.trim((String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.TARGET_FILE));
		String activeMode = StringUtils
				.trim((String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.ACTIVE_MODE));
		String fileType = StringUtils
				.trim((String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.FILE_TYPE));
		boolean activeModeParameter = false;

		if (StringUtils.isEmpty(keepAliveTimeout)) {
			keepAliveTimeout = FileConstants.DEFAULT_KEEP_ALIVE_TIMEOUT;
		}
		if (StringUtils.isEmpty(controlKeepAliveReplyTimeout)) {
			controlKeepAliveReplyTimeout = FileConstants.DEFAULT_CONTROL_KEEP_ALIVE_REPLY_TIMEOUT;
		}
		if (StringUtils.isNotEmpty(activeMode)) {
			activeModeParameter = Boolean.parseBoolean(activeMode);
		}

		InputStream inputStream = null;
		FTPClient ftp = new FTPClient();
		if (StringUtils.isNotEmpty(proxyHost) && StringUtils.isNotEmpty(proxyPort) &&
		    StringUtils.isNotEmpty(proxyUsername) && StringUtils.isNotEmpty(proxyPassword)) {
			ftp = new FTPHTTPClient(proxyHost, Integer.parseInt(proxyPort), proxyUsername, proxyPassword);
		}
		//Set the time to wait between sending control connection keep alive messages when processing file upload or
		// download (Zero (or less) disables).
		ftp.setControlKeepAliveTimeout(Long.parseLong(keepAliveTimeout));
		//Set how long to wait for control keep-alive message replies.(defaults to 1000 milliseconds.)
		ftp.setControlKeepAliveReplyTimeout(Integer.parseInt(controlKeepAliveReplyTimeout));
		try {
			int reply;
			int IntFtpPort = Integer.parseInt(ftpPort);
			if (IntFtpPort > 0) {
				ftp.connect(ftpHost, IntFtpPort);
			} else {
				ftp.connect(ftpHost);
			}
			if (log.isDebugEnabled()) {
				log.debug(" Connected to " + ftpHost + " on " + (IntFtpPort > 0 ? ftpPort : ftp.getDefaultPort()));
			}
			// After connection attempt, should check the reply code to verify success.
			reply = ftp.getReplyCode();
			if (!FTPReply.isPositiveCompletion(reply)) {
				ftp.disconnect();
				log.error("FTP ftpServer refused connection.");
			}
			if (!ftp.login(ftpUsername, ftpPassword)) {
				ftp.logout();
				throw new SynapseException("Error while login ftp server.");
			}
			setFileType(fileType, ftp);

			// Use passive mode as default because most of us are behind firewalls these days.
			if (activeModeParameter) {
				ftp.enterLocalActiveMode();
			} else {
				ftp.enterLocalPassiveMode();
			}
			inputStream = new ByteArrayInputStream(messageContext.getEnvelope().getBody().getFirstElement().toString()
			                                                     .getBytes());
			if (StringUtils.isNotEmpty(targetPath)) {
				ftp.changeWorkingDirectory(targetPath);
				ftp.storeFile(targetFile, inputStream);
				if (log.isDebugEnabled()) {
					log.debug("Successfully FTP server transferred the File");
				}
			}
			// check that control connection is working
			if (log.isDebugEnabled()) {
				log.debug("The code received from the server." + ftp.noop());
			}
		} catch (IOException e) {
			throw new SynapseException("Could not connect to FTP Server", e);
		} finally {
			if (ftp.isConnected()) {
				try {
					ftp.disconnect();
					ftp.logout();
				} catch (IOException f) {
					log.error("Error while disconnecting/logging out ftp server");
				}
			}
			if (inputStream != null) {
				try {
					inputStream.close();
				} catch (IOException f) {
					log.error("Error while closing inputStream");
				}
			}
		}
		return true;
	}

	/**
	 * Set the file type to be transferred.
	 *
	 * @param fileType The type of the file.
	 * @param ftp The FTPClient which is used to transfer the file.
	 * @throws IOException If an I/O error occurs while either sending a command to the server or receiving a reply
	 * from the server.
	 */
	private void setFileType(String fileType, FTPClient ftp) throws IOException {
		switch (fileType) {
			case "BINARY":
				ftp.setFileType(FTP.BINARY_FILE_TYPE);
				break;
			case "ASCII":
				ftp.setFileType(FTP.ASCII_FILE_TYPE);
				break;
			case "EBCDIC":
				ftp.setFileType(FTP.EBCDIC_FILE_TYPE);
				break;
			case "LOCAL":
				ftp.setFileType(FTP.LOCAL_FILE_TYPE);
				break;
			default:
				ftp.setFileType(FTP.ASCII_FILE_TYPE);
		}
	}
}