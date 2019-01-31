package org.zowe.pipelines.generic.models

/**
 * Test report archive information.
 */
class TestReport {
    /**
     * The directory containing the report.
     */
    String dir

    /**
     * The index file of the report.
     */
    String files

    /**
     * The name of the report
     *
     * <p><b>Default:</b> {@code "Test Report"}</p>
     */
    String name = "Test Report"
}
