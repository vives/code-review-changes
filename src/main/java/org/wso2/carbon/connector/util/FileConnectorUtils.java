/*
* Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
* WSO2 Inc. licenses this file to you under the Apache License,
* Version 2.0 (the "License"); you may not use this file except
* in compliance with the License.
* You may obtain a copy of the License at
*
*http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied. See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.carbon.connector.util;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.impl.StandardFileSystemManager;
import org.apache.commons.vfs2.provider.ftp.FtpFileSystemConfigBuilder;
import org.apache.commons.vfs2.provider.ftps.FtpsFileSystemConfigBuilder;
import org.apache.commons.vfs2.provider.sftp.SftpFileSystemConfigBuilder;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.wso2.carbon.connector.core.util.ConnectorUtils;

/**
 * FileConnectorUtils to check whether folder or not, initiate StandardFileSystemManager and configure
 * FileSystemOptions.
 */
public class FileConnectorUtils {
	private static final Log log = LogFactory.getLog(FileConnectorUtils.class);

	/**
	 * Check folder or not.
	 *
	 * @param remoteFile Location of the remote file.
	 * @return true, if file object is folder.
	 */
	public static boolean isFolder(FileObject remoteFile) {
		boolean isFolder = false;
		if (StringUtils.isEmpty(remoteFile.getName().getExtension())) {
			isFolder = true;
		}
		return isFolder;
	}

	/**
	 * Initiate File System Manager.
	 *
	 * @return Initiated StandardFileSystemManager.
	 */
	public static StandardFileSystemManager getManager() {
		StandardFileSystemManager fsm;
		try {
			fsm = new StandardFileSystemManager();
			fsm.init();
		} catch (FileSystemException e) {
			throw new SynapseException("Unable to get FileSystemManager", e);
		}
		return fsm;
	}

	/**
	 * Configure file system individually.
	 *
	 * @param messageContext The message context that is used in configure file system options mediation flow.
	 * @return Configured file systems.
	 */
	public static FileSystemOptions init(MessageContext messageContext)  {
		String setTimeout = StringUtils.trim((String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.SET_TIME_OUT));
		String setPassiveMode = StringUtils
				.trim((String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.SET_PASSIVE_MODE));
		String setSoTimeout = StringUtils
				.trim((String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.SET_SO_TIMEOUT));
		String setStrictHostKeyChecking = StringUtils.trim((String) ConnectorUtils
				.lookupTemplateParamater(messageContext, FileConstants.SET_STRICT_HOST_KEY_CHECKING));
		String setUserDirIsRoot = StringUtils.trim((String) ConnectorUtils
				.lookupTemplateParamater(messageContext, FileConstants.SET_USER_DIRISROOT));

		String setStrictHostKeyCheckingParameter = FileConstants.DEFAULT_SET_STRICT_HOST_KEY_CHECKING;
		boolean setUserDirIsRootParameter = false;
		boolean setPassiveModeParameter = true;
		int setTimeoutParameter = FileConstants.TIME_OUT;
		int setSoTimeoutParameter = FileConstants.TIME_OUT;
		if (StringUtils.isNotEmpty(setStrictHostKeyChecking)) {
			setStrictHostKeyCheckingParameter = setStrictHostKeyChecking;
		}
		if (StringUtils.isNotEmpty(setUserDirIsRoot)) {
			setUserDirIsRootParameter = Boolean.parseBoolean(setUserDirIsRoot);
		}
		if (StringUtils.isNotEmpty(setTimeout)) {
			setTimeoutParameter =Integer.parseInt(setTimeout);
		}
		if (StringUtils.isNotEmpty(setSoTimeout)) {
			setSoTimeoutParameter = Integer.parseInt(setSoTimeout);
		}
		if (StringUtils.isNotEmpty(setPassiveMode)) {
			setPassiveModeParameter = Boolean.parseBoolean(setPassiveMode);
		}

		if (log.isDebugEnabled()) {
			log.debug("File init starts with " + setTimeoutParameter + "," + setPassiveModeParameter + ","
			          + setSoTimeoutParameter + "," + setStrictHostKeyCheckingParameter + ","
			          + setUserDirIsRootParameter);
		}
		FileSystemOptions opts = new FileSystemOptions();

		// Configures the host key checking to use
		try {
			SftpFileSystemConfigBuilder.getInstance().setStrictHostKeyChecking(opts, setStrictHostKeyCheckingParameter);
		} catch (FileSystemException e) {
			throw new SynapseException("Error while configuring setStrictHostKeyChecking", e);
		}
		// Sets whether to use the user directory as root
		SftpFileSystemConfigBuilder.getInstance().setUserDirIsRoot(opts, setUserDirIsRootParameter);
		// Sets the timeout value on Jsch session
		SftpFileSystemConfigBuilder.getInstance().setTimeout(opts, setTimeoutParameter);
		// Enter into passive mode
		FtpFileSystemConfigBuilder.getInstance().setPassiveMode(opts, setPassiveModeParameter);
		FtpsFileSystemConfigBuilder.getInstance().setPassiveMode(opts, setPassiveModeParameter);
		// Sets the socket timeout for the FTP client
		FtpFileSystemConfigBuilder.getInstance().setSoTimeout(opts, setSoTimeoutParameter);

		if (log.isDebugEnabled()) {
			log.debug("FileConnector configuration is completed.");
		}
		return opts;
	}
}