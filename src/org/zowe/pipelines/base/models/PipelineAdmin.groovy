package org.zowe.pipelines.base.models

final class PipelineAdmin {
    final String userID
    final String email

    PipelineAdmin(String userId, String email) {
        this.userID = userId
        this.email = email
    }
}
