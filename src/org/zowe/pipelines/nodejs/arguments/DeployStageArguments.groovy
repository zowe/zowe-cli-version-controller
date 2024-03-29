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
 * {@link org.zowe.pipelines.nodejs.NodeJSPipeline#deploy(java.util.Map, java.util.Map)} method.
 */
class DeployStageArguments extends GenericStageArguments {
    /**
     * The name of the Deploy step.
     *
     * @default {@code "Package"}
     */
    String name = "Package"

    /**
     * If true, an automatic install test will be performed after deploying a new package.
     *
     * @default {@code true}
     */
    Boolean smokeTest = true

    /**
     * If specified, the deploy stage will run in this project subdirectory.
     */
    String inDir

    /**
     * The custom login operation.
     *
     * <p>This closure is used by the deploy stage method to perform any required login operations.
     * Additional documentation for this argument will be provided in each command.</p>
     */
    Closure customLogin

    /**
     * The custom smoke test operation. If specified, this will override the
     * default smoke test that runs when `smokeTest = true`.
     */
    Closure customSmokeTest
}
