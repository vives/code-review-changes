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

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMNamespace;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.impl.StandardFileSystemManager;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.wso2.carbon.connector.core.AbstractConnector;
import org.wso2.carbon.connector.core.util.ConnectorUtils;
import org.wso2.carbon.connector.util.FileConnectorUtils;
import org.wso2.carbon.connector.util.FileConstants;
import org.wso2.carbon.connector.util.FilePattenMatcher;
import org.wso2.carbon.connector.util.ResultPayloadCreator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class is used to search file for a given pattern.
 */
public class FileSearchConnector extends AbstractConnector {
	private static final Log log = LogFactory.getLog(FileSearchConnector.class);

	/**
	 * Initiate the searchFile method.
	 *
	 * @param messageContext The message context that is used in file search mediation flow.
	 */
	public void connect(MessageContext messageContext) {

		searchFile(messageContext);
	}

	/**
	 * List the all files of given pattern.
	 *
	 * @param messageContext The message context that is generated for processing the file.
	 */
	private void searchFile(MessageContext messageContext) {
		String source = (String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.FILE_LOCATION);
		String filePattern =
				(String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.FILE_PATTERN);
		boolean enableRecursiveSearch = false;

		String recursiveSearchParameter =
				(String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.RECURSIVE_SEARCH);

		if (StringUtils.isNotEmpty(recursiveSearchParameter)) {
			enableRecursiveSearch = Boolean.parseBoolean(recursiveSearchParameter);
		}
		StandardFileSystemManager manager = FileConnectorUtils.getManager();
		if (StringUtils.isEmpty(filePattern)) {
			throw new SynapseException("FilePattern should not be null");
		}
		try {
			FileSystemOptions opt = FileConnectorUtils.init(messageContext);
			FileObject remoteFile = manager.resolveFile(source, opt);
			if (!remoteFile.exists()) {
				throw new SynapseException("File location does not exist");
			}
			FileObject[] children = remoteFile.getChildren();
			OMFactory factory = OMAbstractFactory.getOMFactory();
			OMNamespace ns = factory.createOMNamespace(FileConstants.FILECON, FileConstants.NAMESPACE);
			OMElement result = factory.createOMElement(FileConstants.RESULT, ns);
			ResultPayloadCreator.preparePayload(messageContext, result);
			FilePattenMatcher fpm = new FilePattenMatcher(filePattern);

			for (FileObject child : children) {
				try {
					if (FileType.FILE.equals(child.getType()) && fpm.validate(child.getName().getBaseName())) {
						String outputResult = child.getName().getPath();
						OMElement messageElement = factory.createOMElement(FileConstants.FILE, ns);
						messageElement.setText(outputResult);
						result.addChild(messageElement);
					} else if (FileType.FOLDER.equals(child.getType()) && enableRecursiveSearch) {
						searchSubFolders(child, filePattern, factory, result, ns);
					}
				} catch (FileSystemException e) {
					throw new SynapseException("Unable to search a file.", e);
				} finally {
					try {
						if (child != null) {
							child.close();
						}
					} catch (IOException e) {
						log.error("Error while closing Directory: " + e.getMessage(), e);
					}
				}
			}
			messageContext.getEnvelope().getBody().addChild(result);
		} catch (FileSystemException e) {
			throw new SynapseException("Unable to search a file for a given pattern.", e);
		} finally {
			manager.close();
		}
	}

	/**
	 * Search the files of given pattern inside the sub directory.
	 *
	 * @param child          Child folder.
	 * @param filePattern    Pattern of the file to be searched.
	 * @param factory        OMFactory.
	 * @param result         OMElement.
	 * @param ns             OMNamespace.
	 * @throws FileSystemException On error getting file type.
	 */
	private void searchSubFolders(FileObject child, String filePattern, OMFactory factory, OMElement result,
	                              OMNamespace ns) throws FileSystemException {
		List<FileObject> fileList = new ArrayList<>();
		Collections.addAll(fileList, child.getChildren());
		FilePattenMatcher fpm = new FilePattenMatcher(filePattern);
		String outputResult;
		try {
			for (FileObject file : fileList) {
				if (FileType.FILE.equals(file.getType())) {
					if (fpm.validate(file.getName().getBaseName().toLowerCase())) {
						outputResult = file.getName().getPath();
						OMElement messageElement = factory.createOMElement(FileConstants.FILE, ns);
						messageElement.setText(outputResult);
						result.addChild(messageElement);
					}
				} else if (FileType.FOLDER.equals(file.getType())) {
					searchSubFolders(file, filePattern, factory, result, ns);
				}
			}
		} catch (FileSystemException e) {
			throw new SynapseException("Unable to search files in sub folder", e);
		} finally {
			try {
				child.close();
			} catch (IOException e) {
				log.error("Error while closing Directory: " + e.getMessage(), e);
			}
		}
	}
}