package org.zowe.pipelines.base.models

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

    /**
     * Initialize an enum with a value.
     * @param v The string value of the enum.
     */
    private ResultEnum(String v) {
        value = v
    }
    private String value

    /**
     * Get the value of the enum
     * @return The value of the enum.
     */
    String getValue() {
        return value
    }
}
