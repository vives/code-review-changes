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

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.vfs2.*;
import org.apache.commons.vfs2.impl.StandardFileSystemManager;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.wso2.carbon.connector.core.AbstractConnector;
import org.wso2.carbon.connector.core.util.ConnectorUtils;
import org.wso2.carbon.connector.util.*;

/**
 * The class is used to compress the file.
 */
public class FileArchiveConnector extends AbstractConnector {
	private static final Log log = LogFactory.getLog(FileArchiveConnector.class);
	private final byte[] bytes = new byte[FileConstants.BUFFER_SIZE];

	/**
	 * Initiate the fileCompress method.
	 *
	 * @param messageContext The message context that is used in file compress mediation flow.
	 */
	public void connect(MessageContext messageContext) {
		try {
			ResultPayloadCreator.generateResult(messageContext, fileCompress(messageContext));
		} catch (FileSystemException e) {
			throw new SynapseException("Error while archiving the file/folder", e);
		}
	}

	/**
	 * Archive a file/folder.
	 *
	 * @param messageContext The message context that is generated for processing the file.
	 * @return return true, if the file/folder is successfully archived, false, if not.
	 * @throws FileSystemException On error parsing the file name, determining if the file exists and getting file type.
	 */
	private boolean fileCompress(MessageContext messageContext) throws FileSystemException {
		String source = (String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.FILE_LOCATION);
		String destination =
				(String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.NEW_FILE_LOCATION);
		StandardFileSystemManager manager = FileConnectorUtils.getManager();
		FileSystemOptions opts = FileConnectorUtils.init(messageContext);
		FileObject fileObj = manager.resolveFile(source, opts);
		FileObject destObj = manager.resolveFile(destination, opts);
		if (!fileObj.exists()) {
			log.error("The File location does not exist.");
			return false;
		}
		if (FileType.FOLDER.equals(fileObj.getType())) {
			List<FileObject> fileList = new ArrayList<>();
			addAllFilesToList(fileObj, fileList);
			writeZipFiles(fileObj, destObj, fileList);
		} else {
			ZipOutputStream outputStream = null;
			InputStream fileIn = null;
			try {
				outputStream = new ZipOutputStream(destObj.getContent().getOutputStream());
				fileIn = fileObj.getContent().getInputStream();
				ZipEntry zipEntry = new ZipEntry(fileObj.getName().getBaseName());
				outputStream.putNextEntry(zipEntry);
				int length;
				while ((length = fileIn.read(bytes)) != -1) {
					outputStream.write(bytes, 0, length);
				}
			} catch (IOException e) {
				throw new SynapseException("Error while writing an array of bytes to the ZipOutputStream", e);
			} finally {
				try {
					// close the file object
					fileObj.close();
				}catch (FileSystemException e){
					log.error("Error while closing the source FileObject", e);
				}
				try {
					// close the file object
					destObj.close();
				}catch (FileSystemException e){
					log.error("Error while closing the destination FileObject", e);
				}
				try {
					if (outputStream != null) {
						outputStream.close();
					}
				} catch (IOException e) {
					log.error("Error while closing ZipOutputStream", e);
				}
				try {
					if (fileIn != null) {
						fileIn.close();
					}
				} catch (IOException e) {
					log.error("Error while closing InputStream:", e);
				}
				// close the StandardFileSystemManager
				manager.close();
			}
		}
		if (log.isDebugEnabled()) {
			log.debug("File archiving completed." + destination);
		}
		return true;
	}

	/**
	 * Add the all files into List.
	 *
	 * @param dir      The source file/folder directory.
	 * @param fileList List for adding the files.
	 */
	private void addAllFilesToList(FileObject dir, List<FileObject> fileList) {
		try {
			FileObject[] children = dir.getChildren();
			for (FileObject child : children) {
				fileList.add(child);
				if (FileType.FOLDER.equals(child.getType())) {
					addAllFilesToList(child, fileList);
				}
			}
		} catch (FileSystemException e) {
			throw new SynapseException("Unable to add all files into List", e);
		}finally {
			try {
				dir.close();
			} catch (FileSystemException e) {
				log.error("Error while closing the FileObject", e);
			}
		}
	}

	/**
	 * Extract all files to add the zip directory.
	 *
	 * @param fileObj        Source fileObject.
	 * @param directoryToZip Destination fileObject.
	 * @param fileList       List of files to be compressed.
	 * @throws FileSystemException When get the OutputStream, get file type.
	 */
	private void writeZipFiles(FileObject fileObj, FileObject directoryToZip, List<FileObject> fileList)
			throws FileSystemException {
		ZipOutputStream zos = null;
		try {
			zos = new ZipOutputStream(directoryToZip.getContent().getOutputStream());
			for (FileObject file : fileList) {
				if (FileType.FILE.equals(file.getType())) {
					addToZip(fileObj, file, zos);
				}
			}
		} catch (FileSystemException e) {
			throw new SynapseException("Error occurs in writing files", e);
		} finally {
			try {
				if (zos != null) {
					zos.close();
				}
			} catch (IOException e) {
				log.error("Error while closing the ZipOutputStream");
			}
			try {
				fileObj.close();
				directoryToZip.close();
			}catch (FileSystemException e){
				log.error("Error while closing the FileObject", e);
			}
		}
	}

	/**
	 * Add the file to zip directory.
	 *
	 * @param fileObject   The fileObject where zip entry data needs to be written.
	 * @param file         The fileObject which needs to be archived.
	 * @param outputStream ZipOutputStream.
	 */
	private void addToZip(FileObject fileObject, FileObject file, ZipOutputStream outputStream) {
		InputStream fin = null;
		try {
			fin = file.getContent().getInputStream();
			String name = file.getName().toString();
			String entry = name.substring(fileObject.getName().toString().length() + 1, name.length());
			ZipEntry zipEntry = new ZipEntry(entry);
			outputStream.putNextEntry(zipEntry);
			int length;
			while ((length = fin.read(bytes)) != -1) {
				outputStream.write(bytes, 0, length);
			}
		} catch (IOException e) {
			throw new SynapseException("Unable to write an array of bytes to the ZIP entry data", e);
		} finally {
			try {
				outputStream.closeEntry();
				if (fin != null) {
					fin.close();
				}
			} catch (IOException e) {
				log.error("Error while closing InputStream", e);
			}
			try {
				fileObject.close();
				file.close();
			} catch (FileSystemException e) {
				log.error("Error while closing the FileObject", e);
			}
		}
	}
}