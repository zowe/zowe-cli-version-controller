package org.zowe.pipelines.base

import org.zowe.pipelines.base.exceptions.ProtectedBranchException
import org.zowe.pipelines.base.interfaces.ProtectedBranch

/**
 * Manages the protected branches of a Pipeline.
 * @param <T> This type ensures that the branch properties implement the {@link ProtectedBranch}
 *            interface and all branches are of the same property.
 */
final class ProtectedBranches<T extends ProtectedBranch> implements Serializable {
    /**
     * The mapping of protected branches
     */
    private HashMap<String, T> _protectedBranches = new HashMap()

    /**
     * Adds a branch object as protected.
     * @param branch The properties of a branch that is protected.
     * @return a reference to this class for chaining.
     * @throws ProtectedBranchException when a branch is already protected.
     */
    ProtectedBranches add(T branch) throws ProtectedBranchException {
        if (_protectedBranches.hasProperty(branch.branchName)) {
            throw new ProtectedBranchException("${branch.branchName} already exists as a protected branch.")
        }

        _protectedBranches.put(branch.branchName, branch)

        return this
    }

    /**
     * Removes a branch from the protected list.
     * @param branchName The name of the branch to remove.
     * @return The object that was removed or null if none was removed
     */
    T remove(String branchName) {
        return _protectedBranches.remove(branchName)
    }

    /**
     * Gets a protected branch's properties from the map.
     * @param branchName The name of the branch to retrieve
     * @return The object for the branch name or null if there is no branch of the corresponding name.
     */
    T getProtectedBranch(String branchName) {
        return _protectedBranches.get(branchName)
    }
}
