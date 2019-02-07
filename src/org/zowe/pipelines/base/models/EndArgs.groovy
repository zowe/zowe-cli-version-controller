package org.zowe.pipelines.base.models

class EndArgs {
    /**
     * A closure that always is executed pipeline completion
     */
    Closure always

    /**
     * An array of folders to archive.
     *
     * <p>If a specific folder doesn't exist, the
     * build will ignore it and will not modify the current build result. See
     * the notes in the log for the reasoning. If a folder in this array
     * starts with a {@literal `/`}, the stage will copy the folder into a temp directory
     * inside the project (retaining the folder structure). This is due to
     * the fact that folders outside the workspace cannot be archived by
     * Jenkins. The leading {@literal `/`} should be used for any logs that you wish to
     * capture that are outside the workspace. Also if the directory starts
     * with a {@literal ../}, the stage will abort access to that folder. This is because
     * Jenkins cannot archive files outside the workspace.</p>
     */
    String[] archiveFolders
}
