/**
 * $RCSfile$
 * $Revision: 10406 $
 * $Date: 2008-05-19 21:21:54 +0200 (lun, 19 may 2008) $
 *
 * Copyright 2003-2004 Jive Software.
 *
 * All rights reserved. Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.whack.util;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A collection of utility methods for String objects.
 */
public class StringUtils {
	/**
	 * Used to build output as Hex
	 */
	private static final char[] HEX_DIGITS = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

	/**
	 * Calculates the SHA-1 digest and returns the value as a hex string.
	 * 
	 * @param data
	 *            Data to digest
	 * @return SHA-1 digest as a hex string
	 */
	public static final String hash(final String data) {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA");
		} catch (final NoSuchAlgorithmException e) {
			throw new RuntimeException(e.getMessage());
		}

		// Now, compute hash.
		try {
			digest.update(data.getBytes("UTF-8"));
		} catch (final UnsupportedEncodingException e) {
			throw new IllegalStateException(e.getMessage());
		}

		return encodeHex(digest.digest());
	}

	/**
	 * Converts an array of bytes into a String representing the hexadecimal
	 * values of each byte in order. The returned String will be double the
	 * length of the passed array, as it takes two characters to represent any
	 * given byte.
	 * 
	 * @param data
	 *            a byte[] to convert to Hex characters
	 * @return A String containing hexadecimal characters
	 */
	private static final String encodeHex(final byte[] data) {
		final int l = data.length;
		final char[] out = new char[l << 1];
		// two characters form the hex value.
		for (int i = 0, j = 0; i < l; i++) {
			out[j++] = HEX_DIGITS[(0xF0 & data[i]) >>> 4];
			out[j++] = HEX_DIGITS[0x0F & data[i]];
		}
		return new String(out);
	}

}