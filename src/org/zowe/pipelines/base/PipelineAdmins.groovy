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
            String emailAddress = u?.getProperty(Mailer.UserProperty.class)?.address

            if (emailAddress) {
                _admins.add(new PipelineAdmin(admin, emailAddress))
            } else {
                throw new IllegalArgumentException("Email address is null for \"$admin\"")
            }
        }
    }

    String getCCList() {
        _getEmailList("cc")
    }

    String getEmailList() {
        return _getEmailList()
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

        // Could be more efficient about the comma. Come back to this later, maybe?
        return ccList
    }
}
