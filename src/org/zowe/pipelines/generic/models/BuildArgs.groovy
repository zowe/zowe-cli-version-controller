package org.zowe.pipelines.generic.models

import groovy.transform.AutoClone

import static groovy.transform.AutoCloneStyle.SIMPLE

/**
 * Represents the arguments available to the
 * {@link org.zowe.pipelines.generic.GenericPipeline#buildGeneric(java.util.Map)} method.
 */
@AutoClone(style = SIMPLE)
class BuildArgs extends GenericArgs {
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
