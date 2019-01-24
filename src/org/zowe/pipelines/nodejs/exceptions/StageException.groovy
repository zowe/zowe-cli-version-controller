package org.zowe.pipelines.nodejs.exceptions

class StageException extends NodeJSRunnerException {
    String stageName

    StageException(String message, String stageName) {
        super("${message} (stage = \"${stageName}\")")

        this.stageName = stageName
    }
}
