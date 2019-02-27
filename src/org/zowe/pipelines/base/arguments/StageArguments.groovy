/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.pipelines.base.arguments

import org.zowe.pipelines.base.enums.ResultEnum
import org.zowe.pipelines.base.models.StageTimeout

/**
 * Arguments available to the {@link org.zowe.pipelines.base.Pipeline#createStage(org.zowe.pipelines.base.arguments.StageArguments)}
 * method.
 */
class StageArguments {
    /**
     * Can the stage ignore a skip all condition.
     *
     * <p>There are some scenarios that will cause the pipeline to internally skip all remaining
     * stages. If this option is set to true, the stage will be allowed to execute under this
     * condition. This will not negate any skip criteria defined by {@link #shouldExecute} or
     * {@link #resultThreshold}</p>
     *
     * @default false
     */
    boolean doesIgnoreSkipAll = false

    /**
     * Environmental variables passed to the {@link #stage}.
     *
     * <p>Environmental variables will be set through the Jenkins withEnv step. Map keys indicate
     * the environment variable name, and values represent the value.</p>
     */
    Map<String, String> environment

    /**
     * Can the stage be skipped via a build parameter.
     *
     * <p>If this option is true, then a build parameter will be created that controls if this
     * stage is skipped for the current run.</p>
     *
     * @default true
     */
    boolean isSkippable = true

    /**
     * The name of the stage.
     *
     * <p>Failure to provide this attribute will lead to unexpected runs of the pipeline.</p>
     *
     * <p>This attribute must be unique across all stages added to the current pipeline. Any
     * duplicates will result in an error.</p>
     */
    String name

    /**
     * Minimum build health needed for this stage to execute.
     *
     * <p>If the current build health is less than the value specified, the stage will be skipped.</p>
     *
     * <p>For more information about the skip precedent, see
     * {@link org.zowe.pipelines.base.Pipeline#createStage(StageArguments)}</p>
     *
     * @default {@link ResultEnum#SUCCESS}
     */
    ResultEnum resultThreshold = ResultEnum.SUCCESS

    /**
     * A custom closure function that has the ability to skip the stage if it returns false.
     *
     * <p>The purpose of this function is to give you more control over how stage execution occurs
     * in your pipeline. If the closure provided evaluates to false, the stage it is applied to will
     * be skipped.</p>
     *
     * <p>For more information about the skip precedent, see
     * {@link org.zowe.pipelines.base.Pipeline#createStage(StageArguments)}
     *
     * @default <pre>{ {@code -> true}}</pre>
     */
    Closure shouldExecute = { -> true }

    /**
     * The operation to execute for the stage.
     *
     * <p>When the closure is called, it will be passed a single parameter. This parameter is the
     * evaluated string name of the stage. This name can be used to obtain the stage object if
     * desired. If the stage object is accessed, avoid making any changes to the internal state.
     * Changes to the internal state may cause instabilities in your pipeline.</p>
     *
     * <p>This operation will be executed inside of a Jenkins stage. Failure to provide this
     * attribute will result in an {@link java.lang.NullPointerException}.</p>
     */
    Closure stage

    /**
     * Amount of time allowed for this stage.
     *
     * <p>If no value is provided, the timeout will be the default values of
     * {@link org.zowe.pipelines.base.models.StageTimeout}</p>
     */
    StageTimeout timeout = [:]
}
