/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.pipelines.generic.arguments

/**
 * Represents the arguments available to the
 * {@link org.zowe.pipelines.generic.GenericPipeline#versionGeneric(java.util.Map)} method.
 */
class VersionStageArguments extends GenericStageArguments {
    /**
     * The name of the Versioning step.
     *
     * @default {@code "Package"}
     */
    String name = "Package"

    /**
     * Specifies whether we should push the tags to the repo or not.
     *
     * @default {@code true}
     */
    Boolean gitTag = true

    /**
     * Indicates if the pipeline should allow for a passive versioning strategy.
     * Passive strategy: User manually updates the package.json version
     * Active strategy (default): Pipeline admins need to approve a version.
     *
     * @default {@code "false"}
     */
    Boolean passive = false
}
