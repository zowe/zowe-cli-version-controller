/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.pipelines.generic.models

/**
 * Test report archive information.
 */
class TestReport {
    /**
     * The directory containing the report.
     */
    String dir

    /**
     * The index file of the report.
     */
    String files

    /**
     * The name of the report
     *
     * @default {@code "Test Report"}
     */
    String name = "Test Report"
}
