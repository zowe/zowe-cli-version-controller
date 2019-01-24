package org.zowe.pipelines.nodejs.exceptions

class TestStageException extends StageException {
    TestStageException(String message, String stageName) {
        super(message, stageName)
    }
}
