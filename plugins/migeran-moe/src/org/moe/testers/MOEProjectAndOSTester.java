/*
 * Copyright (C) 2016 Migeran
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.moe.testers;

import org.moe.common.utils.OsUtils;

public class MOEProjectAndOSTester extends MOEProjectTester {

	@Override
	public boolean test(Object o, String property, Object[] args, Object expectedValue) {

		if (!OsUtils.isMac()) {
			return false;
		}

		return super.test(o, property, args, expectedValue);
	}

}
