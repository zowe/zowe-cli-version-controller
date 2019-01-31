package org.zowe.pipelines.base.exceptions

/**
 * A generic exception that can be thrown during any stage of your Jenkins pipeline.
 */
class StageException extends PipelineException {
    /**
     * The stage where the exception was thrown.
     */
    final String stageName

    /**
     * Construct the exception.
     * @param message The exception message.
     * @param stageName The name of the stage that threw the exception.
     */
    StageException(String message, String stageName) {
        super("${message} (stage = \"${stageName}\")")

        this.stageName = stageName
    }
}
