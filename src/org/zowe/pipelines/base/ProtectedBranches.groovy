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
     * Construct the protected branches object with some default branches.
     * @param branches
     */
    ProtectedBranches(Map<String, T> branches) {
        _protectedBranches.putAll(branches as Map)
    }

    /**
     * Adds a branch object as protected.
     * @param branch The properties of a branch that is protected.
     * @return a reference to this class for chaining.
     * @throws ProtectedBranchException when a branch is already protected.
     */
    ProtectedBranches add(T branch) throws ProtectedBranchException {
        if (_protectedBranches.hasProperty(branch.name)) {
            throw new ProtectedBranchException("${branch.name} already exists as a protected branch.")
        }

        _protectedBranches.put(branch.name, branch)

        return this
    }

    /**
     * Add a branch map into the object.
     * @param branch The branch to add.
     * @return a reference to this class for chaining.
     */
    ProtectedBranches add(Map branch) {
        return add(branch as T)
    }

    /**
     * Gets a protected branch's properties from the map.
     * @param branchName The name of the branch to retrieve
     * @return The object for the branch name or null if there is no branch of the corresponding name.
     */
    T getProtectedBranch(String branchName) {
        return _protectedBranches.get(branchName)
    }

    /**
     * Checks if a given branch name is protected.
     * @param branchName The name of the branch to check.
     * @return True if the branch is protected, false otherwise.
     */
    boolean isProtected(String branchName) {
        return _protectedBranches.containsKey(branchName)
    }

    /**
     * Removes a branch from the protected list.
     * @param branchName The name of the branch to remove.
     * @return The object that was removed or null if none was removed
     */
    T remove(String branchName) {
        return _protectedBranches.remove(branchName)
    }
}
