package org.zowe.pipelines.generic.models

class ChangeInformation {
    final boolean isPullRequest
    final String branchName
    final String baseBranch
    final String changeBranch

    // TODO add build revision

    ChangeInformation(def steps) {
        branchName = steps.BRANCH_NAME

        if (steps.env.CHANGE_BRANCH) {
            isPullRequest = true
            baseBranch = steps.CHANGE_TARGET
            changeBranch = steps.CHANGE_BRANCH
        } else {
            isPullRequest = false
            baseBranch = null
            changeBranch = null
        }
    }
}
