package org.zowe.pipelines.base.models

import org.zowe.pipelines.base.interfaces.ProtectedBranchProperties

/**
 * A default implementation of the protected branch scheme.
 */
class ProtectedBranch implements ProtectedBranchProperties {
    /**
     * The string name of the branch
     */
    String name
}
