package org.zowe.pipelines.generic

import com.cloudbees.groovy.cps.NonCPS
import org.zowe.pipelines.base.Pipeline
import org.zowe.pipelines.base.models.ResultEnum

import org.zowe.pipelines.generic.models.*
import org.zowe.pipelines.generic.exceptions.*

class GenericPipeline extends Pipeline implements Serializable {
    /**
     * Text used for the CI SKIP commit.
     */
    private static final String _CI_SKIP = "[ci skip]"

    /**
     * Shell command that gets the current git revision.
     */
    private static final String _GIT_REVISION_LOOKUP = "git log -n 1 --pretty=format:%h"

    // @FUTURE Only relevant for CD story
    /**
     * Git user configuration, add more documentation in future story
     */
    GitConfig gitConfig

    /**
     * The git commit revision of the build. This is determined at the beginning of
     * the build.
     */
    private String _buildRevision

    /**
     * A boolean that tracks if the build step was run. When false, the build still hasn't completed
     */
    private boolean _didBuild = false

    GenericPipeline(steps) {
        super(steps)
    }

    /**
     * Creates a stage that will build a generic package.
     *
     * <p>Calling this function will add the following stage to your Jenkins pipeline. Arguments passed
     * to this function will map to the {@link BuildArgs} class.</p>
     *
     * <h5>Build: {@link BuildArgs#name}</h5>
     * <p>Runs the build of your application. If {@link BuildArgs#buildOperation} is not provided, the
     * stage will default to executing `npm run build`.</p>
     *
     * <p>The build stage also ignores any {@link BuildArgs#resultThreshold} provided and only runs
     * on {@link org.zowe.pipelines.base.models.ResultEnum#SUCCESS}.</p>
     *
     * <p>After the buildOperation is complete, the stage will continue to archive the contents of the
     * build into a tar file. The folder to archive is specified by arguments.output. In the future,
     * this function will run the npm pack command and archive that tar file instead.</p>
     *
     * <p>This stage will throw a {@link BuildStageException} if called more than once in your pipeline.</p>
     *
     * @param arguments A map of arguments to be applied to the {@link BuildArgs} used to define
     *                  the stage.
     */
    @NonCPS
    void buildStage(Map arguments = [:]) {
        // Force build to only happen on success, this cannot be overridden
        arguments.resultThreshold = ResultEnum.SUCCESS

        BuildArgs args = arguments

        args.name = "Build: ${args.name}"
        args.stage = {
            if (_didBuild) {
                throw new BuildStageException("Only one build step is allowed per pipeline.", args.name)
            }

            _didBuild = true
        }

        createStage(args)
    }

    /**
     * Creates a stage that will execute tests on your application.
     *
     * <p>Calling this function will add the following stage to your Jenkins pipeline. Arguments passed
     * to this function will map to the {@link org.zowe.pipelines.generic.models.TestArgs} class.</p>
     *
     * <h5>Test: {@link org.zowe.pipelines.generic.models.TestArgs#name}</h5>
     *
     * <p>Runs one of your application tests. If {@link org.zowe.pipelines.generic.models.TestArgs#testOperation}, the stage will execute
     * `npm run test` as the default operation. If the test operation throws an error, that error is
     * ignored and  will be assumed to be caught in the junit processing. Some test functions may
     * exit with a non-zero return code on a test failure but may still capture junit output. In
     * this scenario, it is assumed that the junit report is either missing or contains failing
     * tests. In the case that it is missing, the build will fail on this report and relevant
     * exceptions are printed. If the junit report contains failing tests, the build will be marked
     * as unstable and a report of failing tests can be viewed.</p>
     *
     * <p>The following reports can be captured:</p>
     *
     * <h6>Test Results HTML Report (REQUIRED)</h6>
     *
     * <p>This is an html report that contains the result of the build. The report must be defined to
     * the method in the {@link org.zowe.pipelines.generic.models.TestArgs#testResults} variable.</p>
     *
     * <h6>Code Coverage HTML Report</h6>
     *
     * <p>This is an HTML report generated from code coverage output from your build. The report can
     * be omitted by omitting {@link org.zowe.pipelines.generic.models.TestArgs#coverageResults}</p>
     *
     * <h6>JUnit report (REQUIRED)</h6>
     *
     * <p>This report feeds Jenkins the data about the current test run. It can be used to mark a build
     * as failed or unstable. The report location must be present in {@link org.zowe.pipelines.generic.models.TestArgs#junitOutput}</p>
     *
     * <h6>Cobertura Report</h6>
     *
     * <p>This report feeds Jenkins the data about the coverage results for the current test run. If
     * no Cobertura options are passed, then no coverage data will be collected. For more
     * information, see {@link org.zowe.pipelines.generic.models.TestArgs#cobertura}</p>
     *
     * <p>The test stage will execute by default if the current build result is greater than or
     * equal to {@link ResultEnum#UNSTABLE}. If a different status is passed, that will take
     * precedent.</p>
     *
     * <p>After the test is complete, the stage will continue to collect the JUnit Report and the Test
     * Results HTML Report. The stage will fail if either of those are missing. If specified, the
     * Code Coverage HTML Report and the Cobertura Report are then captured. The build will fail if
     * these reports are to be collected and were missing.</p>
     *
     * <p>Some tests may also require the use of the gnome-keyring. The stage can be configured to
     * unlock the keyring prior to the tests by passing {@link org.zowe.pipelines.generic.models.TestArgs#shouldUnlockKeyring} as true.</p>
     *
     * <h6>Stage Exceptions</h6>
     *
     * <p>The test stage can throw a {@link org.zowe.pipelines.generic.exceptions.TestStageException} under any of the following
     * circumstances:</p>
     *
     * <ul>
     * <li>A test stage was created before a call to {@link #buildStage(Map)}</li>
     * <li>{@link org.zowe.pipelines.generic.models.TestArgs#testResults} was missing</li>
     * <li>Invalid options specified for {@link org.zowe.pipelines.generic.models.TestArgs#testResults}</li>
     * <li>{@link org.zowe.pipelines.generic.models.TestArgs#coverageResults} was provided but had an invalid format</li>
     * <li>{@link org.zowe.pipelines.generic.models.TestArgs#junitOutput} is missing.</li>
     * </ul>
     *
     * @param arguments A map of arguments to be applied to the {@link org.zowe.pipelines.generic.models.TestArgs} used to define
     *                  the stage.
     */
    @NonCPS
    void testStage(Map arguments = [:]) {
        // Default the resultThreshold to unstable for tests,
        // if a custom value is passed then that will be used instead
        if (!arguments.resultThreshold) {
            arguments.resultThreshold = ResultEnum.UNSTABLE
        }

        TestArgs args = arguments

        // @TODO one must happen before deploy
        args.name = "Test: ${args.name}"
        args.stage = {
            if (!_didBuild) {
                throw new TestStageException("Tests cannot be run before the build has completed", args.name)
            }

            steps.echo "Processing Arguments"

            if (!args.testResults) {
                throw new TestStageException("Test Results HTML Report not provided", args.name)
            } else {
                _validateReportInfo(args.testResults, "Test Results HTML Report", args.name)
            }

            if (!args.coverageResults) {
                steps.echo "Code Coverage HTML Report not provided...report ignored"
            } else {
                _validateReportInfo(args.coverageResults, "Code Coverage HTML Report", args.name)
            }

            if (!args.junitOutput) {
                throw new TestStageException("JUnit Report not provided", args.name)
            }

            // Unlock the keyring for dbus
            if (args.shouldUnlockKeyring) {
                steps.sh "echo 'jenkins' | gnome-keyring-daemon --unlock"
            }

            try {
                args.testOperation()
            } catch (e) {
                steps.echo "Exception: ${e.getMessage()}"
            }

            // Collect junit report
            steps.junit args.junitOutput

            // Collect Test Results HTML Report
            steps.publishHTML(target: [
                    allowMissing         : false,
                    alwaysLinkToLastBuild: true,
                    keepAll              : true,
                    reportDir            : args.testResults.dir,
                    reportFiles          : args.testResults.files,
                    reportName           : args.testResults.name
            ])

            // Collect coverage if applicable
            if (args.coverageResults) {
                steps.publishHTML(target: [
                        allowMissing         : false,
                        alwaysLinkToLastBuild: true,
                        keepAll              : true,
                        reportDir            : args.coverageResults.dir,
                        reportFiles          : args.coverageResults.files,
                        reportName           : args.coverageResults.name
                ])
            }

            // Collect cobertura coverage if specified
            if (args.cobertura) {
                steps.cobertura(TestArgs.coberturaDefaults + args.cobertura)
            } else if (args.coverageResults) {
                steps.echo "WARNING: Cobertura file not detected, skipping"
            }
        }

        createStage(args)
    }

    /**
     * Validates that a test report has the required options.
     *
     * @param report The report to validate
     * @param reportName The name of the report being validated
     * @param stageName The name of the stage that is executing.
     *
     * @throws TestStageException when any of the report properties are invalid.
     */
    private static void _validateReportInfo(TestReport report, String reportName, String stageName) {
        if (!report.dir) {
            throw new TestStageException("${reportName} is missing property `dir`", stageName)
        }

        if (!report.files) {
            throw new TestStageException("${reportName} is missing property `files`", stageName)
        }

        if (!report.name) {
            throw new TestStageException("${reportName} is missing property `name`", stageName)
        }
    }

    /**
     * <h5>Check for CI Skip</h5>
     *
     * <p>Checks that the build commit doesn't contain the CI Skip indicator. If the pipeline finds
     * the skip commit, all remaining steps (except those explicitly set to ignore this condition)
     * will also be skipped. The build will also be marked as not built in this scenario.</p>
     */
    @NonCPS
    void setup() {
        // Call setup from the super class
        super.setup()

        createStage(name: 'Check for CI Skip', stage: {
            // We need to keep track of the current commit revision. This is to prevent the condition where
            // the build starts on master and another branch gets merged to master prior to version bump
            // commit taking place. If left unhandled, the version bump could be done on latest master branch
            // code which would already be ahead of this build.
            _buildRevision = steps.sh returnStatus: true, script: _GIT_REVISION_LOOKUP

            // This checks for the [ci skip] text. If found, the status code is 0
            def result = steps.sh returnStatus: true, script: 'git log -1 | grep \'.*\\[ci skip\\].*\''
            if (result == 0) {
                steps.echo "\"${_CI_SKIP}\" spotted in the git commit. Aborting."
                _shouldSkipRemainingStages = true
                setResult(ResultEnum.NOT_BUILT)
            }
        }, timeout: [time: 1, unit: 'MINUTES'])
    }
}
