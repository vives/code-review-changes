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

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.vfs2.*;
import org.apache.commons.vfs2.impl.StandardFileSystemManager;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.wso2.carbon.connector.core.AbstractConnector;
import org.wso2.carbon.connector.core.Connector;
import org.wso2.carbon.connector.core.util.ConnectorUtils;
import org.wso2.carbon.connector.util.FileConnectorUtils;
import org.wso2.carbon.connector.util.FileConstants;
import org.wso2.carbon.connector.util.FilePattenMatcher;
import org.wso2.carbon.connector.util.ResultPayloadCreator;

import java.io.File;
import java.io.IOException;

/**
 * This class is used to copy file/folder to target directory.
 */
public class FileCopyConnector extends AbstractConnector {
	private static final Log log = LogFactory.getLog(FileCopyConnector.class);

	/**
	 * Initiate the copyFile method.
	 *
	 * @param messageContext The message context that is used in file copy mediation flow.
	 */
	public void connect(MessageContext messageContext) {
		String source = (String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.FILE_LOCATION);
		FileSystemOptions opts = FileConnectorUtils.init(messageContext);
		ResultPayloadCreator.generateResult(messageContext, copyFile(source, messageContext, opts));
	}

	/**
	 * Copy the file or folder from source to destination.
	 *
	 * @param messageContext The message context that is generated for processing the file.
	 * @param opts           FileSystemOptions.
	 * @return return true, if file/folder is successfully copied.
	 */
	private boolean copyFile(String source, MessageContext messageContext, FileSystemOptions opts) {
		String destination =
				(String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.NEW_FILE_LOCATION);
		String filePattern =
				(String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.FILE_PATTERN);
		String includeParentDir =
				(String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.INCLUDE_PARENT_DIRECTORY);
		boolean includeParentDirectory = FileConstants.DEFAULT_INCLUDE_PARENT_DIRECTORY;

		if (StringUtils.isNotEmpty(includeParentDir)) {
			includeParentDirectory = Boolean.parseBoolean(includeParentDir);
		}
		boolean resultStatus = false;
		StandardFileSystemManager manager = FileConnectorUtils.getManager();
		FileObject souFile = null;
		FileObject destFile = null;
		try {
			souFile = manager.resolveFile(source, opts);
			destFile = manager.resolveFile(destination, opts);
			if (!souFile.exists()) {
				log.error("The File Location does not exist.");
				return false;
			}
			if (StringUtils.isNotEmpty(filePattern)) {
				FileObject[] children = souFile.getChildren();
				for (FileObject child : children) {
					if (FileType.FILE.equals(child.getType())) {
						copy(child, destination, filePattern, opts, manager);
					} else if (FileType.FOLDER.equals(child.getType())) {
						String newSource = source + File.separator + child.getName().getBaseName();
						copyFile(newSource, messageContext, opts);
					}
				}
			} else {
				if (FileType.FILE.equals(souFile.getType())) {
					String name = souFile.getName().getBaseName();
					FileObject outFile = manager.resolveFile(destination + File.separator + name, opts);
					outFile.copyFrom(souFile, Selectors.SELECT_ALL);
				} else if (FileType.FOLDER.equals(souFile.getType())) {
					if (includeParentDirectory) {
						destFile = manager.resolveFile(destination + File.separator + souFile.getName().getBaseName(),
						                               opts);
						destFile.createFolder();
					}
					destFile.copyFrom(souFile, Selectors.SELECT_ALL);
				}
				if (log.isDebugEnabled()) {
					log.debug("File copying completed from " + source + "to" + destination);
				}
			}
		} catch (FileSystemException e) {
			throw new SynapseException("Unable to copy a file/folder", e);
		} finally {
			manager.close();
		}
		try {
			souFile.close();
			destFile.close();
		} catch (FileSystemException e) {
			log.error("Error while closing the FileObject", e);
		}
		return true;
	}

	/**
	 * copy the file for given pattern.
	 *
	 * @param source      The source file object.
	 * @param destination The target file location.
	 * @param filePattern Pattern of the file.
	 * @param opts        Configured file system.
	 */
	private void copy(FileObject source, String destination, String filePattern, FileSystemOptions opts,
	                  StandardFileSystemManager manager) throws FileSystemException {
		FilePattenMatcher patternMatcher = new FilePattenMatcher(filePattern);
		if (patternMatcher.validate(source.getName().getBaseName())) {
			String name = source.getName().getBaseName();
			FileObject outFile = manager.resolveFile(destination + File.separator + name, opts);
			outFile.copyFrom(source, Selectors.SELECT_ALL);
		}
	}
}