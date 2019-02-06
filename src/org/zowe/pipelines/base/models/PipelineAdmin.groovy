package org.zowe.pipelines.base.models

final class PipelineAdmin {
    final String userID
    final String email
    final String name

    PipelineAdmin(String userId, String email, String name) {
        this.userID = userId
        this.email = email
        this.name = name
    }
}
