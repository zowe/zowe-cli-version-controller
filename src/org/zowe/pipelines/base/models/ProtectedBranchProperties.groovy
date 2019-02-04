package org.zowe.pipelines.base.models

import org.zowe.pipelines.base.interfaces.ProtectedBranch

/**
 * A default implementation of the protected branch scheme.
 */
class ProtectedBranchProperties implements ProtectedBranch {
    /**
     * The string name of the branch
     */
    String name
}
