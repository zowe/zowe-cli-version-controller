package org.zowe.pipelines.base.models

/**
 * A stage that will be executed in the Jenkins pipeline.
 */
class Stage {
    /**
     * The arguments passed into the {@link org.zowe.pipelines.nodejs.NodeJSPipeline#createStage(org.zowe.pipelines.nodejs.models.StageArgs)}
     * method.
     */
    StageArgs args

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
     * <p><b>Default:</b> {@code false}</p>
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
     * <p>This variable will become true when {@link org.zowe.pipelines.nodejs.NodeJSPipeline#createStage(org.zowe.pipelines.nodejs.models.StageArgs)}
     * calls the {@link org.zowe.pipelines.nodejs.models.StageArgs#stage} operation.</p>
     *
     * <p><b>Default:</b> {@code false}</p>
     */
    boolean wasExecuted = false
}
