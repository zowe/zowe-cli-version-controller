package org.zowe.pipelines.nodejs.models

/**
 * Enumeration of the various build statuses accepted by Jenkins
 */
enum ResultEnum {

    /**
     * Successful build
     */
    SUCCESS("SUCCESS"),

    /**
     * Skipped build
     */
    NOT_BUILT("NOT_BUILT"),

    /**
     * Unstable Build
     */
    UNSTABLE("UNSTABLE"),

    /**
     * Failed Build
     */
    FAILURE("FAILURE"),

    /**
     * Aborted Build
     */
    ABORTED("ABORTED");

    ResultEnum(String v) {
        value = v
    }
    private String value
    public String getValue() {
        return value
    }
}
