package org.zowe.pipelines.base

import hudson.tasks.Mailer
import org.zowe.pipelines.base.models.PipelineAdmin
import hudson.model.User

/**
 * @TODO DOCUMENT
 */
class PipelineAdmins {
    private final List<PipelineAdmin> _admins = []

    void add(String... admins) {
        for (String admin : admins) {
            User u = User.getById(admin, false)
            String emailAddress = u.getProperty(Mailer.UserProperty.class).address
            String name = u.getFullName()

            if (emailAddress) {
                _admins.add(new PipelineAdmin(admin, emailAddress, name))
            } else {
                throw new IllegalArgumentException("Email address is null for \"$admin\"")
            }
        }
    }

    String getApproverList() {
        String approverList = ""
        for (PipelineAdmin admin : _admins) {
            approverList += admin.userID

            // If the current iteration isn't the last element, add a comma separator
            if (!admin.is(_admins.last())) {
                approverList += ","
            }
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
        for (PipelineAdmin admin : _admins) {
            ccList += "${prefix ? "$prefix: " : ""}${admin.email}"

            // If the current iteration isn't the last element, add a comma separator
            if (!admin.is(_admins.last())) {
                ccList += ","
            }
        }

        return ccList
    }
}
