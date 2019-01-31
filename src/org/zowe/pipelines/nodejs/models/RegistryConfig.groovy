package org.zowe.pipelines.nodejs.models

/**
 * Registry connection information.
 */
class RegistryConfig {
    /**
     * The url of the registry.
     *
     * <p>If this is null, the default registry (per npm commands) will be referenced</p>
     */
    String url

    /**
     * The email address of the user
     */
    String email

    /**
     * ID of credentials in the Jenkins secure credential store.
     *
     * <p>The username and password should be stored under this ID</p>
     */
    String credentialsId
}
