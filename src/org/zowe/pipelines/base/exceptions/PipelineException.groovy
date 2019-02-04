package org.zowe.pipelines.base.exceptions

/**
 * An exception that can be thrown from the {@link org.zowe.pipelines.base.Pipeline} class
 */
class PipelineException extends Exception {
    /**
     * Construct the exception.
     * @param message The exception message.
     */
    PipelineException(String message) {
        super(message)
    }
}
