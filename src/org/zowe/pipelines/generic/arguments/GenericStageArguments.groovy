package org.zowe.pipelines.generic.arguments

import org.zowe.pipelines.base.arguments.StageArguments

//TODO change the documentation
/**
 * Represents the arguments available to the
 * {@link org.zowe.pipelines.generic.GenericPipeline#deployGeneric(java.util.Map)} method.
 */
class GenericStageArguments extends StageArguments{
    /**
     * The deploy operation.
     *
     * <p>This must be passed by any function that calls the {@link org.zowe.pipelines.generic.GenericPipeline#deployGeneric(java.util.Map, jave.util.Map)}
     * method. This operation is responsible for deploying your code</p>
     */
    Closure operation
}
