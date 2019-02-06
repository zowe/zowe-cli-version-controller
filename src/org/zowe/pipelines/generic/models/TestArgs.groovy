package org.zowe.pipelines.generic.models

import groovy.transform.AutoClone

import static groovy.transform.AutoCloneStyle.SIMPLE

/**
 * Represents the arguments available to the
 * {@link org.zowe.pipelines.generic.GenericPipeline#testGeneric(java.util.Map)} method.
 */
@AutoClone(style = SIMPLE)
class TestArgs extends GenericArgs {
    /**
     * Default values provided to cobertura.
     *
     * <p>This map will be merged with {@link #cobertura}, preferring cobertura, as the final
     * object passed to the cobertura plugin</p>
     *
     * <p><b>Defaults:</b></p>
     * <pre>
     * {@code
     * [
     *     autoUpdateStability       : true,
     *     classCoverageTargets      : '85, 80, 75',
     *     conditionalCoverageTargets: '70, 65, 60',
     *     failUnhealthy             : false,
     *     failUnstable              : false,
     *     fileCoverageTargets       : '100, 95, 90',
     *     lineCoverageTargets       : '80, 70, 50',
     *     maxNumberOfBuilds         : 20,
     *     methodCoverageTargets     : '80, 70, 50',
     *     onlyStable                : false,
     *     sourceEncoding            : 'ASCII',
     *     zoomCoverageChart         : false
     * ]
     * }
     * </pre>
     */
    public static final Map coberturaDefaults = [
            autoUpdateStability       : true,
            classCoverageTargets      : '85, 80, 75',
            conditionalCoverageTargets: '70, 65, 60',
            failUnhealthy             : false,
            failUnstable              : false,
            fileCoverageTargets       : '100, 95, 90',
            lineCoverageTargets       : '80, 70, 50',
            maxNumberOfBuilds         : 20,
            methodCoverageTargets     : '80, 70, 50',
            onlyStable                : false,
            sourceEncoding            : 'ASCII',
            zoomCoverageChart         : false
    ]

    /**
     * Cobertura report information.
     *
     * <p>Providing this property causes the test stage to capture a cobertura report. The values
     * provided to this map are directly sent to the cobertura plugin. For information about what
     * map options are acceptable, see <a href="https://jenkins.io/doc/pipeline/steps/cobertura/">
     * Jenkins Cobertura Plugin Documentation</a>.</p>
     */
    Map cobertura

    /**
     * Storage location for any coverage reports generated by your test task.
     *
     * <p>If this report is provided and {@link #cobertura} is not, a warning message will be
     * generated by the test task.</p>
     */
    TestReport coverageResults

    /**
     * The location of the generated junit output.
     *
     * <p>This report is required by the test stage. The junit file is used to integrate with
     * Jenkins and mark builds as unstable/failed depending on the test passing status.</p>
     */
    String junitOutput

    /**
     * Should the gnome keyring be unlocked for this test.
     *
     * <p><b>Default:</b> {@code false}</p>
     */
    boolean shouldUnlockKeyring = false

    /**
     * Storage location for the report created by your test task.
     *
     * <p>For example, this would be the html report generated by a jest test that shows the total
     * number of passing and failing tests. If not provided, the test stage will fail.</p>
     */
    TestReport testResults
}
