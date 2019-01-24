package org.zowe.pipelines.nodejs.models
class Stage {
    String name
    int order // The order of stage execution
    boolean isSkippedByParam = false
    boolean wasExecuted = false
    String endOfStepBuildStatus // The result of the build at the end
    Stage next // The next stage
    StageArgs args
    Closure execute // The closure to execute for the stage
    /**
     * any exception encountered during the stage
     */
    Exception exception
}
