package org.zowe.pipelines.generic.models

/**
 * Git configuration used to checkout the source.
 */
class GitConfig {
    /**
     * The git email
     */
    String email

    /**
     * The credentials id in Jenkins
     */
    String credentialsId
}
