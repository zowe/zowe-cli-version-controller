package org.zowe.pipelines.nodejs.exceptions

class BuildStageException extends StageException {
    BuildStageException(String message, String stageName) {
        super(message, stageName)
    }
}
