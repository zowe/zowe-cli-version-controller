package org.zowe.pipelines.generic.models

import org.zowe.pipelines.base.models.StageArgs

/**
 * Represents the arguments available to the
 * {@link org.zowe.pipelines.generic.GenericPipeline#deployGeneric(java.util.Map)} method.
 */
class DeployArgs extends StageArgs{
    /**
     * The deploy operation.
     *
     * <p>This must be passed by any function that calls the {@link org.zowe.pipelines.generic.GenericPipeline#deployGeneric(java.util.Map)}
     * method. This operation is responsible for deploying your code</p>
     */
    Closure deployOperation

    /**
     * The versioning operation.
     *
     * <p>This method will perform a version bump on the code if needed. Omitting this closure will
     * not result in a failed build.</p>
     */
    Closure versioningOperation
}
