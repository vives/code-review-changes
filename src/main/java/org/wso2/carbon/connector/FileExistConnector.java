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
package org.wso2.carbon.connector;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.impl.StandardFileSystemManager;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.wso2.carbon.connector.core.AbstractConnector;
import org.wso2.carbon.connector.core.util.ConnectorUtils;
import org.wso2.carbon.connector.util.FileConnectorUtils;
import org.wso2.carbon.connector.util.FileConstants;
import org.wso2.carbon.connector.util.ResultPayloadCreator;

/**
 * This class is used to check file/folder exists or not.
 */
public class FileExistConnector extends AbstractConnector {
	private static final Log log = LogFactory.getLog(FileExistConnector.class);

	/**
	 * Initiate the isFileExist method.
	 *
	 * @param messageContext The message context that is used in file exist mediation flow.
	 */
	public void connect(MessageContext messageContext) {
		ResultPayloadCreator.generateResult(messageContext, isFileExist(messageContext));
	}

	/**
	 * Determine if file/folder exists.
	 *
	 * @param messageContext The message context that is generated for processing the file.
	 * @return true, if the file/folder exists.
	 */
	private boolean isFileExist(MessageContext messageContext) {
		String source = (String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.FILE_LOCATION);

		StandardFileSystemManager manager = FileConnectorUtils.getManager();
		FileObject remoteFile = null;
		try {
			FileSystemOptions opt = FileConnectorUtils.init(messageContext);
			// create remote fileObject
			remoteFile = manager.resolveFile(source, opt);
			if (!remoteFile.exists()) {
				return false;
			}
			if (log.isDebugEnabled()) {
				log.debug("File exist completed with. " + source);
			}
		} catch (FileSystemException e) {
			throw new SynapseException("Error while processing a file.", e);
		} finally {
			// close the StandardFileSystemManager
			manager.close();
			try {
				if (remoteFile != null) {
					// close the file object
					remoteFile.close();
				}
			} catch (FileSystemException e) {
				log.error("Error while closing the FileObject", e);
			}
		}
		return true;
	}
}