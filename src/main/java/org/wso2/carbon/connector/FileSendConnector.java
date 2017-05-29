/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.connector;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMOutputFormat;
import org.apache.axis2.AxisFault;
import org.apache.axis2.format.BinaryFormatter;
import org.apache.axis2.format.PlainTextFormatter;
import org.apache.axis2.transport.MessageFormatter;
import org.apache.axis2.transport.base.BaseConstants;
import org.apache.axis2.transport.base.BaseUtils;
import org.apache.axis2.util.MessageProcessorSelector;
import org.apache.commons.io.output.CountingOutputStream;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.impl.StandardFileSystemManager;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.wso2.carbon.connector.core.AbstractConnector;
import org.wso2.carbon.connector.core.util.ConnectorUtils;
import org.wso2.carbon.connector.util.FileConnectorUtils;
import org.wso2.carbon.connector.util.FileConstants;
import org.wso2.carbon.connector.util.ResultPayloadCreator;

import java.io.IOException;

/**
 * This class is used to send the file to specific location.
 */
public class FileSendConnector extends AbstractConnector {
	private static final Log log = LogFactory.getLog(FileSendConnector.class);

	/**
	 * Initiate the sendFile method.
	 *
	 * @param messageContext The message context that is used in file send mediation flow.
	 */
	public void connect(MessageContext messageContext) {
		try {
			ResultPayloadCreator.generateResult(messageContext, sendResponseFile(messageContext));
		} catch (FileSystemException e) {
			throw new SynapseException("Error while sending file to target directory", e);
		}
	}

	/**
	 * Get the formatter for message.
	 *
	 * @param msgContext The message context that is generated for processing the file.
	 * @return messageFormatter to process the send response file operation.
	 */
	private MessageFormatter getMessageFormatter(org.apache.axis2.context.MessageContext msgContext) {
		OMElement firstChild = msgContext.getEnvelope().getBody().getFirstElement();
		if (firstChild != null) {
			if (BaseConstants.DEFAULT_BINARY_WRAPPER.equals(firstChild.getQName())) {
				return new BinaryFormatter();
			} else if (BaseConstants.DEFAULT_TEXT_WRAPPER.equals(firstChild.getQName())) {
				return new PlainTextFormatter();
			}
		}
		try {
			return MessageProcessorSelector.getMessageFormatter(msgContext);
		} catch (AxisFault axisFault) {
			throw new SynapseException("Unable to get the message formatter to use");
		}
	}

	/**
	 * Send the file to the target directory.
	 *
	 * @param messageContext The message context that is used in file send mediation flow.
	 * @return return true, if file is sent successfully.
	 * @throws FileSystemException On error parsing the file name and getting file type.
	 */

	private boolean sendResponseFile(MessageContext messageContext) throws FileSystemException {
		boolean append = false;
		String destination =
				(String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.NEW_FILE_LOCATION);
		String strAppend = (String) ConnectorUtils.lookupTemplateParamater(messageContext, FileConstants.APPEND);
		if (StringUtils.isNotEmpty(strAppend)) {
			append = Boolean.parseBoolean(strAppend);
		}
		StandardFileSystemManager manager = FileConnectorUtils.getManager();
		FileObject fileObjectToSend = null;
		FileObject fileObj = manager.resolveFile(destination, FileConnectorUtils.init(messageContext));
		CountingOutputStream outputStream = null;
		if (log.isDebugEnabled()) {
			log.debug("File sending started to " + destination);
		}
		try {
			org.apache.axis2.context.MessageContext axis2MessageContext =
					((Axis2MessageContext) messageContext).getAxis2MessageContext();

			if (FileType.FOLDER.equals(fileObj.getType())) {
				String destDir = destination.concat(FileConstants.DEFAULT_RESPONSE_FILE);
				fileObjectToSend = manager.resolveFile(destDir, FileConnectorUtils.init(messageContext));
			}else if (FileType.FILE.equals(fileObj.getType())){
				fileObjectToSend = fileObj;
			}
			// Get the message formatter.
			MessageFormatter messageFormatter = getMessageFormatter(axis2MessageContext);
			OMOutputFormat format = BaseUtils.getOMOutputFormat(axis2MessageContext);
			// Creating output stream and give the content to that.
			outputStream = new CountingOutputStream(fileObjectToSend.getContent().getOutputStream(append));
			messageFormatter.writeTo(axis2MessageContext, format, outputStream, true);
			if (log.isDebugEnabled()) {
				log.debug("File send completed to " + destination);
			}
		} catch (AxisFault e) {
			throw new SynapseException("Error while writing the message context", e);
		} finally {
			try {
				fileObjectToSend.close();
			} catch (FileSystemException e) {
				log.error("Error while closing FileObject", e);
			}
			try {
				if (outputStream != null) {
					outputStream.close();
				}
			} catch (IOException e) {
				log.warn("Can not close the output stream");
			}
			manager.close();
		}
		return true;
	}
}