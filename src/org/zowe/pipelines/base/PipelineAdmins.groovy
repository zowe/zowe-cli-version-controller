package org.zowe.pipelines.base

import hudson.tasks.Mailer
import org.zowe.pipelines.base.models.PipelineAdmin
import hudson.model.User

/**
 * @TODO DOCUMENT
 */
class PipelineAdmins {
    private final Map<String,PipelineAdmin> _admins = [:]

    void add(String... admins) {
        for (String admin : admins) {
            User u = User.getById(admin, false)
            String emailAddress = u.getProperty(Mailer.UserProperty.class).address
            String name = u.getFullName()

            if (emailAddress) {
                _admins.putAt(admin, new PipelineAdmin(admin, emailAddress, name))
            } else {
                throw new IllegalArgumentException("Email address is null for \"$admin\"")
            }
        }
    }

    PipelineAdmin get(String id) {
        return _admins.get(id)
    }

    String getApproverList() {
        String approverList = ""
        boolean first = true
        _admins.each { key, value ->
            approverList += (!first ? "," : "") + value.userID
            first = false
        }

        return approverList
    }

    String getCCList() {
        _getEmailList("cc")
    }

    String getEmailList() {
        return _getEmailList()
    }

    int size() {
        return _admins.size()
    }

    private String _getEmailList(String prefix = null) {
        String ccList = ""
        boolean first = true
        _admins.each { key, value ->
            ccList += (!first ? "," : "") + "${prefix ? "$prefix: " : ""}${value.email}"
            first = false
        }

        return ccList
    }
}
