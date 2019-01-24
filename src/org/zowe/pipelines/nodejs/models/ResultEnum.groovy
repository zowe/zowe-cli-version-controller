package org.zowe.pipelines.nodejs.models
// Enumeration for the result
public enum ResultEnum {
    SUCCESS("SUCCESS"),
    NOT_BUILT("NOT_BUILT"),
    UNSTABLE("UNSTABLE"),
    FAILURE("FAILURE"),
    ABORTED("ABORTED");

    ResultEnum(String v) {
        value = v
    }
    private String value
    public String getValue() {
        return value
    }
}
