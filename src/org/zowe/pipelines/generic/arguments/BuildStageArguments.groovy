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
 * {@link org.zowe.pipelines.generic.GenericPipeline#buildGeneric(java.util.Map)} method.
 */
class BuildStageArguments extends GenericStageArguments {
    /**
     * The name of the build step.
     *
     * @default {@code "Source"}
     */
    String name = "Source"

    /**
     * The archive operation.
     *
     * This closure is used by the the user to perform their own archiving operation
     */
    Closure archiveOperation
}
