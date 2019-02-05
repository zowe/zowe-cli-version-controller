package org.zowe.pipelines.generic.models

import org.zowe.pipelines.base.models.StageArgs

/**
 * Represents the arguments available to the
 * {@link org.zowe.pipelines.generic.GenericPipeline#buildGeneric(java.util.Map)} method.
 */
class BuildArgs extends StageArgs {
    /**
     * Custom build operation function.
     *
     * <p>Specifying a closure will overwrite the default build operation</p>
     */
    Closure buildOperation

    /**
     * The name of the build step.
     *
     * <p><b>Default:</b> {@code "Source"}</p>
     */
    String name = "Source"

    /**
     * The build output directory, used to archive the contents of the build.
     *
     * <p><b>Default:</b> {@code "./lib/"}</p>
     */
    String output = "./lib/"
}
