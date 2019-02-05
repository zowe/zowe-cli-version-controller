package org.zowe.pipelines.generic.exceptions

import org.zowe.pipelines.base.exceptions.StageException

/**
 * A generic exception that is thrown from within the
 * {@link org.zowe.pipelines.generic.GenericPipeline#buildGeneric(java.util.Map)} method.
 */
class BuildStageException extends StageException {
    /**
     * Create the exception.
     * @param message The exception message
     * @param stageName The name of the stage throwing the exception
     */
    BuildStageException(String message, String stageName) {
        super(message, stageName)
    }
}
