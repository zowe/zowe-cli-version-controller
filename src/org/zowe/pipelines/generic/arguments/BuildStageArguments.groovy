package org.zowe.pipelines.generic.arguments

/**
 * Represents the arguments available to the
 * {@link org.zowe.pipelines.generic.GenericPipeline#buildGeneric(java.util.Map)} method.
 */
class BuildStageArguments extends GenericStageArguments {
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
