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

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.vfs2.*;
import org.apache.commons.vfs2.impl.StandardFileSystemManager;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.wso2.carbon.connector.core.AbstractConnector;
import org.wso2.carbon.connector.core.util.ConnectorUtils;
import org.wso2.carbon.connector.util.FileConnectorUtils;
import org.wso2.carbon.connector.util.FileConstants;
import org.wso2.carbon.connector.util.ResultPayloadCreator;

/**
 * This class is used to delete an existing file/folder.
 */
public class FileDeleteConnector extends AbstractConnector {
	private static final Log log = LogFactory.getLog(FileDeleteConnector.class);

	/**
	 * Initiate the deleteFile method.
	 *
	 * @param messageContext TThe message context that is used in file delete mediation flow.
	 */
	public void connect(MessageContext messageContext) {
		ResultPayloadCreator.generateResult(messageContext, deleteFile(messageContext));
	}

	/**
	 * Delete an existing file/folder.
	 *
	 * @param messageContext The message context that is generated for processing the file.
	 * @return true, if the file/folder is successfully deleted.
	 */
	private boolean deleteFile(MessageContext messageContext) {
		String source = (String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.FILE_LOCATION);
		StandardFileSystemManager manager = FileConnectorUtils.getManager();
		FileObject remoteFile = null;
		try {
			// create remote fileObject
			 remoteFile = manager.resolveFile(source, FileConnectorUtils.init(messageContext));
			if (!remoteFile.exists()) {
				log.error("The file does not exist.");
				return false;
			}
			if (FileType.FILE.equals(remoteFile.getType())) {
				// delete a file
				remoteFile.delete();
			} else if (FileType.FOLDER.equals(remoteFile.getType())) {
				String filePattern =
						(String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.FILE_PATTERN);

				if (StringUtils.isNotEmpty(filePattern) && !"*".equals(filePattern)) {
					FileObject[] children = remoteFile.getChildren();
					for (FileObject child : children) {
						if (child.getName().getBaseName().matches(filePattern)) {
							child.delete();
						}
					}
				} else {
					// delete folder
					remoteFile.delete(Selectors.SELECT_ALL);
				}
			}
			if (log.isDebugEnabled()) {
				log.debug("File delete completed with " + source);
			}
		} catch (FileSystemException e) {
			throw new SynapseException("Error while deleting file/folder", e);
		} finally {
			// close the StandardFileSystemManager
			manager.close();
			try {
				if(remoteFile != null) {
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