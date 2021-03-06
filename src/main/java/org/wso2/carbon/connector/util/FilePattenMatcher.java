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

package org.wso2.carbon.connector.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validate the file with given pattern.
 */
public class FilePattenMatcher {
	private final Pattern pattern;

	public FilePattenMatcher(String patternStr) {
		pattern = Pattern.compile(patternStr);
	}

	/**
	 * Validate file with regular expression
	 *
	 * @param image file for validation
	 * @return true valid image, false invalid image
	 */
	public boolean validate(final String image) {

		Matcher matcher = pattern.matcher(image);
		return matcher.matches();

	}
}