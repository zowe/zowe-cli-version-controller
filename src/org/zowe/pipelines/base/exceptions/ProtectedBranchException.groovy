package org.zowe.pipelines.base.exceptions

/**
 * An exception class for errors that occur within the {@link org.zowe.pipelines.base.ProtectedBranches}
 * class.
 */
class ProtectedBranchException extends Exception {
    /**
     * Construct the exception.
     * @param message The exception message.
     */
    ProtectedBranchException(String message) {
        super(message)
    }
}
