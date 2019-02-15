/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.pipelines.base.models

import org.zowe.pipelines.base.arguments.StageArguments

/**
 * A stage that will be executed in the Jenkins pipeline.
 */
class Stage {
    /**
     * The arguments passed into the {@link org.zowe.pipelines.base.Pipeline#createStage(org.zowe.pipelines.base.arguments.StageArguments)}
     * method.
     */
    StageArguments args

    /**
     * The current result of the build at the end of stage execution.
     */
    String endOfStepBuildStatus

    /**
     * The first exception thrown by this stage.
     */
    Exception exception

    /**
     * The closure function that represents the complete stage operation.
     *
     * <p>This includes the stage operation provided by a pipeline and any internal operations
     * done by this package.</p>
     */
    Closure execute

    /**
     * Was the stage skipped by a build parameter?
     *
     * <p>If the stage skip build parameter is true for this stage, then this variable will become
     * true before stage execution.</p>
     *
     * @default false
     */
    boolean isSkippedByParam = false

    /**
     * The name of the stage.
     */
    String name

    /**
     * The next stage to execute in the pipeline.
     *
     * <p>If this property is null, then this stage is the last one to execute.</p>
     */
    Stage next

    /**
     * The ordinal of the stage in the pipeline execution flow.
     */
    int order

    /**
     * Was the stage executed?
     *
     * <p>This variable will become true when {@link org.zowe.pipelines.base.Pipeline#createStage(org.zowe.pipelines.base.arguments.StageArguments)}
     * calls the {@link org.zowe.pipelines.base.arguments.StageArguments#stage} operation.</p>
     *
     * @default false
     */
    boolean wasExecuted = false
}
