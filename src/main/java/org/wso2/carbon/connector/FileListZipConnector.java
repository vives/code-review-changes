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

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMNamespace;
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

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * This class is used to listAllFiles all the files inside zip file file content.
 */
public class FileListZipConnector extends AbstractConnector {
	private static final Log log = LogFactory.getLog(FileListZipConnector.class);

	/**
	 * Initiate the listZip method.
	 *
	 * @param messageContext The message context that is used in listAllFiles zip file mediation flow.
	 */
	public void connect(MessageContext messageContext) {
		try {
			listAllFiles(messageContext);
		} catch (FileSystemException e) {
			throw new SynapseException("Error while listing all files of zip file", e);
		}
	}

	/**
	 * List all the files inside zip file.
	 *
	 * @param messageContext The message context that is generated for processing the file.
	 */
	private void listAllFiles(MessageContext messageContext) throws FileSystemException {
		String source = (String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.FILE_LOCATION);
		StandardFileSystemManager manager = FileConnectorUtils.getManager();
		FileObject remoteFile = manager.resolveFile(source, FileConnectorUtils.init(messageContext));
		if (!remoteFile.exists()) {
			log.error("Zip file location does not exist.");
		}
		// open the zip file
		InputStream input = remoteFile.getContent().getInputStream();
		ZipInputStream zip = new ZipInputStream(input);
		OMFactory factory = OMAbstractFactory.getOMFactory();
		OMNamespace ns = factory.createOMNamespace(FileConstants.FILECON, FileConstants.NAMESPACE);
		OMElement result = factory.createOMElement(FileConstants.RESULT, ns);
		ZipEntry zipEntry;
		try {
			while ((zipEntry = zip.getNextEntry()) != null && !zipEntry.isDirectory()) {
				// add the entries
				String outputResult = zipEntry.getName();
				OMElement messageElement = factory.createOMElement(FileConstants.FILE, ns);
				messageElement.setText(outputResult);
				result.addChild(messageElement);
			}
		} catch (IOException e) {
			throw new SynapseException("Error while reading the next ZIP file entry", e);
		} finally {
			try {
				zip.close();
			} catch (IOException e) {
				log.error("Error while closing ZipInputStream");
			}
			try {
				remoteFile.close();
			} catch (FileSystemException e) {
				log.error("Error while closing the FileObject", e);
			}
			// close the StandardFileSystemManager
			manager.close();
		}
		ResultPayloadCreator.preparePayload(messageContext, result);
		if (log.isDebugEnabled()) {
			log.debug("The envelop body with the read files path is " + messageContext.getEnvelope().getBody().
					toString());
		}
	}
}