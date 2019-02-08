package org.zowe.pipelines.base.arguments

/**
 * Arguments available to the {@link org.zowe.pipelines.base.Pipeline#sendHtmlEmail(EmailArguments)}
 * method.
 */
class EmailArguments {
    /**
     * Should the recipient providers be added?
     */
    boolean addProviders = true

    /**
     * HTML message content.
     */
    String body

    /**
     * A tag that will be added in the subject of the email.
     *
     * <p>This value will be the first text content in the subject formatted as: {@code "[$subjectTag]"}</p>
     */
    String subjectTag = "BUILD"

    /**
     * A list of additional emails, separated by a comma, that will receive the email.
     *
     * <p>The string provided follows the convention of the <a href="https://jenkins.io/doc/pipeline/steps/email-ext/">
     * Extended Email Plugin</a></p>
     */
    String to = ""
}
