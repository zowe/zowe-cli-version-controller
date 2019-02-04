package org.zowe.pipelines.generic.exceptions

import org.zowe.pipelines.base.exceptions.StageException

/**
 * A generic exception that is thrown from within the
 * {@link org.zowe.pipelines.generic.GenericPipeline#testStageGeneric(java.util.Map)} method.
 */
class TestStageException extends StageException {
    /**
     * Create the exception.
     * @param message The exception message
     * @param stageName The name of the stage throwing the exception
     */
    TestStageException(String message, String stageName) {
        super(message, stageName)
    }
}
