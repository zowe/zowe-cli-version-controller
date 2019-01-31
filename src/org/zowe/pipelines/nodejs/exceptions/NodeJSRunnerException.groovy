package org.zowe.pipelines.nodejs.exceptions

/**
 * A generic exception that can be thrown anywhere in the
 * {@link org.zowe.pipelines.nodejs.NodeJSRunner} class
 */
class NodeJSRunnerException extends Exception {
    /**
     * Construct the exception.
     * @param message The exception message.
     */
    NodeJSRunnerException(String message) {
        super(message)
    }
}
