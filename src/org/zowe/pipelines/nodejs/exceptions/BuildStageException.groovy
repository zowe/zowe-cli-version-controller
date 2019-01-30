package org.zowe.pipelines.nodejs.exceptions

/**
 * A generic exception that is thrown from within the
 * {@link org.zowe.pipelines.nodejs.NodeJSRunner#buildStage(java.util.Map)} method.
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
