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

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.impl.StandardFileSystemManager;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.wso2.carbon.connector.core.AbstractConnector;
import org.wso2.carbon.connector.core.util.ConnectorUtils;
import org.wso2.carbon.connector.util.FileConnectorUtils;
import org.wso2.carbon.connector.util.FileConstants;
import org.wso2.carbon.connector.util.ResultPayloadCreator;

/**
 * This class is used to read file content.
 */
public class FileReadConnector extends AbstractConnector {
	private static final Log log = LogFactory.getLog(FileReadConnector.class);

	/**
	 * Initiate the readFile method.
	 *
	 * @param messageContext The message context that is used in file read mediation flow.
	 */
	public void connect(MessageContext messageContext) {
		readFile(messageContext);
	}

	/**
	 * Read the file content.
	 *
	 * @param messageContext The message context that is generated for processing the read operation.
	 */
	private void readFile(MessageContext messageContext) {
		String source = (String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.FILE_LOCATION);
		String contentType =
				(String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.CONTENT_TYPE);
		String filePattern =
				(String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.FILE_PATTERN);
		boolean streaming = false;
		String enableStreamingParameter =
				(String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.ENABLE_STREAMING);
		if (StringUtils.isNotEmpty(enableStreamingParameter)) {
			streaming = Boolean.parseBoolean(enableStreamingParameter);
		}

		FileObject fileObjectToRead = null;
		StandardFileSystemManager manager = FileConnectorUtils.getManager();
		try {
			FileObject rootFileObject = manager.resolveFile(source, FileConnectorUtils.init(messageContext));
			if (!rootFileObject.exists()) {
				log.error("File/Folder does not exists.");
			}
			if (FileType.FOLDER.equals(rootFileObject.getType())) {
				FileObject[] children = rootFileObject.getChildren();
				if (children == null || children.length == 0) {
					log.error("Empty folder.");
				} else if (StringUtils.isNotEmpty(filePattern)) {
					for (FileObject child : children) {
						if (child.getName().getBaseName().matches(filePattern)) {
							fileObjectToRead = child;
							break;
						}
					}
					if (fileObjectToRead == null) {
						log.error("File does not exists for the mentioned pattern.");
					}
				} else {
					fileObjectToRead = children[0];
				}
			} else if (FileType.FILE.equals(rootFileObject.getType())) {
				fileObjectToRead = rootFileObject;
			} else {
				log.error("File does not exists, or an empty folder");
			}
			ResultPayloadCreator.buildFile(fileObjectToRead, messageContext, contentType, streaming);
			if (log.isDebugEnabled()) {
				log.debug("File read completed." + source);
			}
		} catch (FileSystemException e) {
			throw new SynapseException("Error while reading a file", e);
		} finally {
			try {
				// Close the File system if it is not already closed by the finally block of processFile method
				if (fileObjectToRead != null && fileObjectToRead.getParent() != null &&
				    fileObjectToRead.getParent().getFileSystem() != null) {
					manager.closeFileSystem(fileObjectToRead.getParent().getFileSystem());
				}
				if (fileObjectToRead != null) {
					fileObjectToRead.close();
				}
			} catch (FileSystemException e) {
				log.error("Error while closing the FileObject", e);
			}
		}
	}
}