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

import java.io.File;
import java.io.IOException;

/**
 * This class is used to move file/folder to target directory.
 */
public class FileMoveConnector extends AbstractConnector {
	private static final Log log = LogFactory.getLog(FileMoveConnector.class);

	/**
	 * Initiate the move method.
	 *
	 * @param messageContext The message context that is used in file move mediation flow.
	 */
	public void connect(MessageContext messageContext) {
		try {
			ResultPayloadCreator.generateResult(messageContext, move(messageContext));
		} catch (FileSystemException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Move the file/folder from source to destination directory.
	 *
	 * @param messageContext The message context that is generated for processing the move operation.
	 * @return true, if the file/folder is successfully moved.
	 */
	private boolean move(MessageContext messageContext) throws FileSystemException {
		String source = (String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.FILE_LOCATION);
		String destination =
				(String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.NEW_FILE_LOCATION);
		String filePattern =
				(String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.FILE_PATTERN);
		String includeParentDirectory =
				(String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.INCLUDE_PARENT_DIRECTORY);
		boolean includeParentDirectoryParameter = FileConstants.DEFAULT_INCLUDE_PARENT_DIRECTORY;

		if (StringUtils.isNotEmpty(includeParentDirectory)) {
			includeParentDirectoryParameter = Boolean.parseBoolean(includeParentDirectory);
		}

		StandardFileSystemManager manager = FileConnectorUtils.getManager();
		FileSystemOptions opts = FileConnectorUtils.init(messageContext);
		// Create remote object
		FileObject remoteFile = manager.resolveFile(source, opts);
		try {
			if (!remoteFile.exists()) {
				log.error("The file/folder location does not exist.");
				return false;
			}
			if (FileType.FILE.equals(remoteFile.getType())) {
				moveFile(destination, remoteFile, manager, opts);
			} else {
				moveFolder(source, destination, filePattern, includeParentDirectoryParameter, manager, opts);
			}
			if (log.isDebugEnabled()) {
				log.debug("File move completed from " + source + " to " + destination);
			}
		} catch (FileSystemException e) {
			throw new SynapseException("Unable to move a file/folder.", e);
		} finally {
			// close the StandardFileSystemManager
			manager.close();
			try {
				remoteFile.close();
			}catch (FileSystemException e){
				log.error("Error while closing the FileObject", e);
			}
		}
		return true;
	}

	/**
	 * Move the file to the target directory.
	 *
	 * @param destination The target location of the folder to be moved.
	 * @param remoteFile  Location of the remote file.
	 * @param manager     Standard file system manager.
	 * @param opts        Configured file system options.
	 * @throws FileSystemException On error parsing the file name, determining if the file exists and creating the
	 *                             file/folder.
	 */
	private void moveFile(String destination, FileObject remoteFile, StandardFileSystemManager manager,
	                      FileSystemOptions opts) throws FileSystemException {
		FileObject file = manager.resolveFile(destination, opts);
		if (FileConnectorUtils.isFolder(file)) {
			if (!file.exists()) {
				file.createFolder();
			}
			file = manager.resolveFile(destination + File.separator + remoteFile.getName().getBaseName(), opts);
		} else if (!file.exists()) {
			file.createFile();
		}
		remoteFile.moveTo(file);
	}

	/**
	 * Move the folder to the target directory.
	 *
	 * @param destination            The target location of the folder to move folder.
	 * @param source                 Location of the source folder.
	 * @param includeParentDirectory Boolean type to include the parent directory.
	 * @param manager                Standard file system manager.
	 * @param opts                   Configured file system options.
	 */
	private void moveFolder(String source, String destination, String filePattern, boolean includeParentDirectory,
	                        StandardFileSystemManager manager, FileSystemOptions opts) throws FileSystemException {
		FileObject remoteFile = manager.resolveFile(source, opts);
		FileObject file = manager.resolveFile(destination, opts);
		if (StringUtils.isNotEmpty(filePattern)) {
			FileObject[] children = remoteFile.getChildren();
			for (FileObject child : children) {
				if (FileType.FILE.equals(child.getType())) {
					moveFileWithPattern(child, destination, filePattern, manager, opts);
				} else if (FileType.FOLDER.equals(child.getType())) {
					String newSource = source + File.separator + child.getName().getBaseName();
					moveFolder(newSource, destination, filePattern, includeParentDirectory, manager, opts);
				}
			}
		} else if (includeParentDirectory) {
			FileObject destFile =
					manager.resolveFile(destination + File.separator + remoteFile.getName().getBaseName(), opts);
			destFile.createFolder();
			remoteFile.moveTo(destFile);
		} else {
			if (!file.exists()) {
				file.createFolder();
			}
			remoteFile.moveTo(file);
			remoteFile.createFolder();
		}
	}

	/**
	 * Move the file for given pattern.
	 *
	 * @param remoteFile  Location of the remote file.
	 * @param destination Location of the target folder.
	 * @param filePattern Pattern of the file.
	 * @param manager     Standard file system manager.
	 * @param opts        Configured file system options.
	 */
	private void moveFileWithPattern(FileObject remoteFile, String destination, String filePattern,
	                                 StandardFileSystemManager manager, FileSystemOptions opts) {
		FilePattenMatcher patternMatcher = new FilePattenMatcher(filePattern);
		try {
			if (patternMatcher.validate(remoteFile.getName().getBaseName())) {
				FileObject destFile = manager.resolveFile(destination, opts);
				if (!destFile.exists()) {
					destFile.createFolder();
				}
				FileObject newDestFile = manager.resolveFile(destination + File.separator +
				                                             remoteFile.getName().getBaseName(), opts);
				remoteFile.moveTo(newDestFile);
			}
		} catch (FileSystemException e) {
			throw new SynapseException("Error occurred while moving a file for a given pattern", e);
		}
	}
}