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
class ChangelogStageArguments extends GenericStageArguments {
    /**
     * The name of the step.
     *
     * @default {@code "Check Changelog"}
     */
    String name = "Check Changelog"

    /**
     * Defines the file that should be checked.
     *
     * @default {@code "CHANGELOG.md"}
     */
    String file = "CHANGELOG.md"

    /**
     * The header to check for in the changelog
     *
     * @default {@code "## Recent Changes"}
     */
     String header = "## Recent Changes"

}
