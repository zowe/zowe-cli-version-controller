package org.zowe.pipelines.nodejs.models

import org.zowe.pipelines.base.models.ProtectedBranch

/**
 * @see org.zowe.pipelines.base.models.ProtectedBranch
 */
class NodeJSProtectedBranch extends ProtectedBranch {
    /**
     * This is the npm tag in which the branch will be published with. If this
     * property is left null, then the branch will not be published.
     */
    String tag
}

