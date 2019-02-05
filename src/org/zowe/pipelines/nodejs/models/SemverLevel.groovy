package org.zowe.pipelines.nodejs.models

/**
 * The max semver level that the package can move.
 */
enum SemverLevel {
    /**
     * In x.y.z, allows x, y, and z to increment
     */
    MAJOR,

    /**
     * In x.y.z, allows only y and z to increment
     */
    MINOR,

    /**
     * In x.y.z, allows only z to increment
     */
    PATCH
}