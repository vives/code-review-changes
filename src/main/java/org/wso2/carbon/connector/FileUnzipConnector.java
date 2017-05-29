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
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.impl.StandardFileSystemManager;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.wso2.carbon.connector.core.AbstractConnector;
import org.wso2.carbon.connector.core.Connector;
import org.wso2.carbon.connector.core.util.ConnectorUtils;
import org.wso2.carbon.connector.util.FileConnectorUtils;
import org.wso2.carbon.connector.util.FileConstants;
import org.wso2.carbon.connector.util.ResultPayloadCreator;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * This class is used to decompress the file.
 */
public class FileUnzipConnector extends AbstractConnector {
	private static final Log log = LogFactory.getLog(FileUnzipConnector.class);

	/**
	 * Initiate the unzip method.
	 *
	 * @param messageContext The message context that is used in file unzip mediation flow.
	 */
	public void connect(MessageContext messageContext) {
		try {
			ResultPayloadCreator.generateResult(messageContext, unzip(messageContext));
		} catch (FileSystemException e) {
			throw new SynapseException("Exception while decompressing the zip file", e);
		}
	}

	/**
	 * Decompress the compressed file into the given directory.
	 *
	 * @param messageContext The message context that is generated for processing unzip operation.
	 * @return true, if zip file successfully extracts and false, if not.
	 * @throws FileSystemException On error parsing the file name, determining if the file exists and creating the
	 * folder.
	 */
	private boolean unzip(MessageContext messageContext) throws FileSystemException {
		String source = (String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.FILE_LOCATION);
		String destination = (String) ConnectorUtils.lookupTemplateParamater(messageContext,
		                                                                     FileConstants.NEW_FILE_LOCATION);
		StandardFileSystemManager manager = FileConnectorUtils.getManager();
		FileSystemOptions opts = FileConnectorUtils.init(messageContext);
		FileObject remoteFile = manager.resolveFile(source, opts);
		FileObject remoteDesFile = manager.resolveFile(destination, opts);

		if (!remoteFile.exists()) {
			log.error("File does not exist.");
			return false;
		}
		if (!remoteDesFile.exists()) {
			//create a folder
			remoteDesFile.createFolder();
		}
		//open the zip file
		ZipInputStream zipIn = new ZipInputStream(remoteFile.getContent().getInputStream());
		try {
			ZipEntry entry = zipIn.getNextEntry();

			// iterates over entries in the zip file
			while (entry != null) {
				String filePath = destination + File.separator + entry.getName();
				// create remote object
				FileObject remoteFilePath = manager.resolveFile(filePath, opts);
				if (log.isDebugEnabled()) {
					log.debug("The created path is " + remoteFilePath.toString());
				}
				try {
					if (!entry.isDirectory()) {
						// if the entry is a file, extracts it
						extractFile(zipIn, remoteFilePath);
					} else {
						 // if the entry is a directory, make the directory
						remoteFilePath.createFolder();
					}
				} finally {
					try {
						zipIn.closeEntry();
						entry = zipIn.getNextEntry();
					} catch (IOException e) {
						log.error("Error while closing the ZipInputStream", e);
					}
				}
			}
		} catch (IOException e) {
			throw new SynapseException("Error while reading the next ZIP file entry", e);
		} finally {
			// close the zip file
			try {
				zipIn.close();
			} catch (IOException e) {
				log.error("Error while closing the ZipInputStream", e);
			}
			// close the StandardFileSystemManager
			manager.close();
			try {
				remoteFile.close();
				remoteDesFile.close();
			}catch (FileSystemException e){
				log.error("Error while closing the FileObject", e);
			}
		}
		if (log.isDebugEnabled()) {
			log.debug("File extracted to" + destination);
		}
		return true;
	}

	/**
	 * Extract each zip entry and write it into file.
	 *
	 * @param zipIn          Zip input stream for reading file in the ZIP file format.
	 * @param remoteFilePath Location of file where zip entry needs to be extracted.
	 */
	private void extractFile(ZipInputStream zipIn, FileObject remoteFilePath) {
		BufferedOutputStream bos = null;
		try {
			// open the zip file
			OutputStream fOut = remoteFilePath.getContent().getOutputStream();
			bos = new BufferedOutputStream(fOut);
			byte[] bytesIn = new byte[FileConstants.BUFFER_SIZE];
			int read;
			while ((read = zipIn.read(bytesIn)) != -1) {
				bos.write(bytesIn, 0, read);
			}
		} catch (IOException e) {
			throw new SynapseException("Unable to write zip entry", e);
		} finally {
			// close the zip file
			if (bos != null) {
				try {
					bos.close();
				} catch (IOException e) {
					log.error("Error while closing the BufferedOutputStream", e);
				}
			}
			try {
				remoteFilePath.close();
			} catch (FileSystemException e) {
				log.error("Error while closing FileObject", e);
			}
		}
	}
}