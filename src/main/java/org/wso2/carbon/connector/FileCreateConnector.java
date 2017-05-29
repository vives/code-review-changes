/*
* Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.impl.StandardFileSystemManager;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.wso2.carbon.connector.core.AbstractConnector;
import org.wso2.carbon.connector.core.util.ConnectorUtils;
import org.wso2.carbon.connector.util.FileConnectorUtils;
import org.wso2.carbon.connector.util.FileConstants;
import org.wso2.carbon.connector.util.ResultPayloadCreator;

import java.io.IOException;
import java.io.OutputStream;

/**
 * This class is used to create a new file/folder.
 */
public class FileCreateConnector extends AbstractConnector {
	private static final Log log = LogFactory.getLog(FileCreateConnector.class);

	/**
	 * Initiate the createFile method.
	 *
	 * @param messageContext The message context that is used in file create mediation flow.
	 */
	public void connect(MessageContext messageContext) {
		try {
			ResultPayloadCreator.generateResult(messageContext, createFile(messageContext));
		} catch (FileSystemException e) {
			throw new SynapseException("Error while creating a file", e);
		}
	}

	/**
	 * Create a new file/folder.
	 *
	 * @param messageContext The message context that is generated for processing the file.
	 * @return true, if the file/folder is successfully created.
	 */
	private boolean createFile(MessageContext messageContext) throws FileSystemException {
		String source = (String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.FILE_LOCATION);
		String content = (String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.CONTENT);
		String encoding = (String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.ENCODING);
		if (StringUtils.isEmpty(encoding)) {
			encoding = FileConstants.DEFAULT_ENCODING;
		}
		StandardFileSystemManager manager = FileConnectorUtils.getManager();
		FileObject sourceFile = manager.resolveFile(source, FileConnectorUtils.init(messageContext));
		if (FileConnectorUtils.isFolder(sourceFile)) {
			sourceFile.createFolder();
		} else {
			if (StringUtils.isEmpty(content)) {
				sourceFile.createFile();
			} else {
				OutputStream out = sourceFile.getContent().getOutputStream();
				try {
					IOUtils.write(content, out, encoding);
				} catch (IOException e) {
					throw new SynapseException("Error while writing the file content", e);
				} finally {
					try {
						// close the file object
						sourceFile.close();
					} catch (FileSystemException e) {
						log.error("Error while closing FileObject", e);
					}
					try {
						if (out != null) {
							out.close();
						}
					} catch (IOException e) {
						log.error("Error while closing OutputStream", e);
					}
					// close the StandardFileSystemManager
					manager.close();
				}
			}
			if (log.isDebugEnabled()) {
				log.debug("File creation completed with " + source);
			}
		}
		return true;
	}
}