package org.zowe.pipelines.nodejs.exceptions

/**
 * An exception that can be thrown from the {@link org.zowe.pipelines.nodejs.NodeJSPipeline} class
 */
class NodeJSPipelineException extends Exception {
    /**
     * Construct the exception.
     * @param message The exception message.
     */
    NodeJSPipelineException(String message) {
        super(message)
    }
}
