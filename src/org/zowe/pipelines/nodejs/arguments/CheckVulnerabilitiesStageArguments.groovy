/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.pipelines.nodejs.arguments

import org.zowe.pipelines.generic.arguments.GenericStageArguments

/**
 * Represents the arguments available to the
 * {@link org.zowe.pipelines.nodejs.NodeJSPipeline#checkVulnerabilities(java.util.Map, java.util.Map)} method.
 */
class CheckVulnerabilitiesStageArguments extends GenericStageArguments {
    /**
     * The name of the "Check Vulnerabilities" step.
     *
     * @default {@code "Check Vulnerabilities"}
     */
    String name = "Check Vulnerabilities"

    /**
     * The registry to npm-audit against.
     */
    String registry = ""

    /**
     * The audit level allowed on npm-audit.
     */
    String auditLevel = "moderate"

    /**
     * Indicates if the pipeline should include {@code "dev"} dependencies when checking for vulnerabilities.
     *
     * @default {@code "false"}
     */
    Boolean dev = false
}
