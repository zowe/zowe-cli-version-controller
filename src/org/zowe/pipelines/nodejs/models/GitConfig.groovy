package org.zowe.pipelines.nodejs.models

/**
 * Git configuration used to checkout the source.
 */
class GitConfig {
    /**
     * The git username
     *
     * @todo extract this from the credentialsId
     */
    String user

    /**
     * The git email
     */
    String email

    /**
     * The credentials id in Jenkins
     */
    String credentialsId
}
