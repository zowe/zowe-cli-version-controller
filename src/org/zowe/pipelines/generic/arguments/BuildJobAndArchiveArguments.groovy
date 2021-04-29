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
 * {@link org.zowe.pipelines.generic.GenericPipeline#buildJobAndArchive(java.util.Map)} method.
 */
class BuildJobAndArchiveArguments extends GenericStageArguments {
    /**
     * The name of the Stage
     */
    String name = "Remote Job"

    /**
     * The name of the job to be triggered
     */
    String jobName = ""

    /**
     * The parameters to be sent to the job being triggered
     */
    Object jobParms = [:]

    /**
     * Specifies if the results of the remote job should be propagated
     */
    Boolean propagate = false
}
