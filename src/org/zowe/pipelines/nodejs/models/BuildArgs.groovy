package org.zowe.pipelines.nodejs.models

/**
 * Represents the arguments available to the
 * {@link org.zowe.pipelines.nodejs.NodeJSRunner#buildStage(java.util.Map)} method.
 */
class BuildArgs extends StageArgs { //@TODO figure out why the groovydoc inheritance breaks.
    /**
     * The build output directory, used to archive the contents of the build.
     *
     * <p><b>Default:</b> {@code "./lib/"}</p>
     */
    String output = "./lib/"

    /**
     * The name of the build step.
     *
     * <p><b>Default:</b> {@code "Source"}</p>
     */
    String name = "Source"

    /**
     * Custom build operation function.
     *
     * <p>Specifying a closure will overwrite the default build operation</p>
     */
    Closure buildOperation
}
