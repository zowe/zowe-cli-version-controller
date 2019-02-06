package org.zowe.pipelines.base.models

class EmailOptions {
    String subjectTag
    String body
    String to = ""
    boolean addProviders = true
}
