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
import java.io.OutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
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
 * This class is used to append file content.
 */
public class FileAppendConnector extends AbstractConnector {
	private static final Log log = LogFactory.getLog(FileAppendConnector.class);

	/**
	 * Initiate the appendFile method.
	 *
	 * @param messageContext The message context that is used in file append mediation flow.
	 */
	public void connect(MessageContext messageContext) {
		try {
			ResultPayloadCreator.generateResult(messageContext, appendFile(messageContext));
		} catch (FileSystemException e) {
			throw new SynapseException("Error while appending the content", e);
		}
	}

	/**
	 * Add the content into file.
	 *
	 * @param messageContext The message context that is generated for processing the file.
	 * @return true, if the content is successfully appended.
	 * @throws FileSystemException On error parsing the file name, determining if the file exists and creating the
	 * file.
	 */
	private boolean appendFile(MessageContext messageContext) throws FileSystemException {
		String destination =
				(String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.NEW_FILE_LOCATION);
		String content = (String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.CONTENT);
		String encoding = (String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.ENCODING);
		if (StringUtils.isEmpty(encoding)) {
			encoding = FileConstants.DEFAULT_ENCODING;
		}
		FileSystemOptions opts = FileConnectorUtils.init(messageContext);
		OutputStream out = null;
		FileObject fileObj = null;
		StandardFileSystemManager manager = FileConnectorUtils.getManager();
		try {
			fileObj = manager.resolveFile(destination, opts);
			if (!fileObj.exists()) {
				fileObj.createFile();
			}
			// True, if the content should be appended.
			out = fileObj.getContent().getOutputStream(true);
			IOUtils.write(content, out, encoding);
			if (log.isDebugEnabled()) {
				log.debug("File appending completed. " + destination);
			}
		} catch (IOException e) {
			throw new SynapseException("Error while appending content", e);
		} finally {
			try {
				if (fileObj != null) {
					// close the file object
					fileObj.close();
				}
			} catch (FileSystemException e) {
				log.error("Error while closing FileObject", e);
			}
			try {
				if (out != null) {
					// close the output stream
					out.close();
				}
			} catch (IOException e) {
				log.error("Error while closing OutputStream", e);
			}
			// close the StandardFileSystemManager
			manager.close();
		}
		return true;
	}
}