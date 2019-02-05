package org.zowe.pipelines.generic.models

import org.zowe.pipelines.base.models.StageArgs

/**
 * Represents the arguments available to the
 * {@link org.zowe.pipelines.generic.GenericPipeline#deployStageGeneric(java.util.Map)} method.
 */
class DeployArgs extends StageArgs{
    /**
     * The deploy operation.
     *
     * <p>This must be passed by any function that calls the {@link org.zowe.pipelines.generic.GenericPipeline#deploy(java.util.Map)}
     * method. This operation is responsible for deploying your code</p>
     */
    Closure deployOperation
}
