package org.zowe.pipelines.base.models

import org.zowe.pipelines.base.interfaces.ProtectedBranchProperties

/**
 * Properties of a protected branch
 *
 * <p>A protected branch is usually on that has some restrictions on what code
 * can be published to it. These are typically your release and forward development
 * branches.</p>
 *
 * <p>If a branch is marked as protected, emails will always be sent out to the committers and
 * the list of {@link org.zowe.pipelines.base.Pipeline#adminEmails} provided.</p>
 */
class ProtectedBranch implements ProtectedBranchProperties {
    /**
     * The string name of the branch
     */
    String name
}
