package org.zowe.pipelines.nodejs.models

/**
 * Represents the arguments available to the
 * {@link org.zowe.pipelines.nodejs.NodeJSRunner#createStage(org.zowe.pipelines.nodejs.models.StageArgs)}
 * method.
 */
class StageArgs {
    /**
     * Can the stage ignore a skip all condition.
     *
     * <p>There are some scenarios that will cause the pipeline to internally skip all remaining
     * stages. If this option is set to true, the stage will be allowed to execute under this
     * condition. This will not negate any skip criteria defined by {@link #shouldSkip} or
     * {@link #resultThreshold}</p>
     *
     * <p><b>Default:</b> {@code false}</p>
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
     * <p><b>Default:</b> {@code true}</p>
     */
    boolean isSkipable = true

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
     * <p><b>Default:</b> {@link ResultEnum#SUCCESS}</p>
     *
     * <p>For more information about the skip precedent, see
     * {@link org.zowe.pipelines.nodejs.NodeJSRunner#createStage(org.zowe.pipelines.nodejs.models.StageArgs)}
     */
    ResultEnum resultThreshold = ResultEnum.SUCCESS

    /**
     * A custom closure function that has the ability to skip the stage.
     *
     * <p>The purpose of this function is to give you more control over how stage execution occurs
     * in your pipeline. If the closure provided evaluates to true, the stage it is applied to will
     * be skipped.</p>
     *
     * <p>For more information about the skip precedent, see
     * {@link org.zowe.pipelines.nodejs.NodeJSRunner#createStage(org.zowe.pipelines.nodejs.models.StageArgs)}
     */
    Closure shouldSkip = { -> false }

    /**
     * The operation to execute for the stage.
     *
     * <p>This operation will be executed inside of a Jenkins stage. Failure to provide this
     * attribute will result in an {@link java.lang.NullPointerException}.
     */
    Closure stage

    /**
     * The timeout options for the stage.
     */
    StageTimeout timeout = [:]
}
