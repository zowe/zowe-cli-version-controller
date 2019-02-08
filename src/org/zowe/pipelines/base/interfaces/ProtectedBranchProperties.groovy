package org.zowe.pipelines.base.interfaces

/**
 * Required properties of a model used in the {@link org.zowe.pipelines.base.ProtectedBranches}
 * class.
 */
interface ProtectedBranchProperties {
    /**
     * The branch must have a name associated with it.
     * @return The name of the branch
     */
    String getName()
}