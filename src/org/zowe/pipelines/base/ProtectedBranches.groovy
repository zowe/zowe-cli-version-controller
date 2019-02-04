package org.zowe.pipelines.base

import org.zowe.pipelines.base.exceptions.ProtectedBranchException
import org.zowe.pipelines.base.interfaces.ProtectedBranchProperties

/**
 * Manages the protected branches of a Pipeline.
 * @param <T> This type ensures that the branch properties implement the {@link ProtectedBranchProperties}
 *            interface and all branches are of the same property.
 */
final class ProtectedBranches<T extends ProtectedBranchProperties> implements Serializable {
    /**
     * The mapping of protected branches
     */
    private HashMap<String, T> _protectedBranches = new HashMap()

    /**
     * Adds a branch object as protected.
     * @param branch The properties of a branch that is protected.
     * @return The object that was added.
     * @throws ProtectedBranchException when a branch is already protected.
     */
    T add(T branch) throws ProtectedBranchException {
        if (_protectedBranches.hasProperty(branch.name)) {
            throw new ProtectedBranchException("${branch.name} already exists as a protected branch.")
        }

        return _protectedBranches.put(branch.name, branch)
    }

    /**
     * Add a branch map into the object. This map must follow the syntax of the Groovy Map Object
     * Constructor.
     * @param branch The branch to add.
     * @return The object that was added
     */
    T add(Map branch) {
        return add(branch as T)
    }

    /**
     * Adds a list of branches to the map.
     * @param branches The branches to add as protected.
     */
    void addList(List<T> branches) {
        for (T branch : branches) {
            add(branch)
        }
    }

    /**
     * Adds a list of branches to the protected maps. The elements of the list must follow the syntax
     * of the Groovy Map Object Constructor.
     * @param branches The branches to add as protected.
     */
    void addListMap(List<Map> branches) {
        for (Map branch : branches) {
            add(branch)
        }
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
