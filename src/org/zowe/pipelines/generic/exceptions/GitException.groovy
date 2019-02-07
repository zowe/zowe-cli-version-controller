package org.zowe.pipelines.generic.exceptions

import org.zowe.pipelines.base.exceptions.PipelineException

class GitException extends PipelineException {
    GitException(String message) {
        super(message)
    }
}
