package org.zowe.pipelines.nodejs

import org.zowe.pipelines.base.models.ResultEnum
import org.zowe.pipelines.generic.GenericPipeline
import org.zowe.pipelines.nodejs.models.*
import org.zowe.pipelines.nodejs.exceptions.*

/**
 * A stage executor for a NodeJSPipeline pipeline.
 *
 * <p>This class provides methods that allow you to build, test, and deploy your NodeJSPipeline application.</p>
 *
 * <h5>Basic Usage</h5>
 * <pre>
 * {@code
 * @Library('fill this out according to your setup') import org.zowe.pipelines.nodejs.NodeJSPipeline
 *
 * node('pipeline-node') {
 *     // Create the runner and pass the methods available to the workflow script to the runner
 *     NodeJSPipeline nodejs = new NodeJSPipeline(this)
 *
 *     // Set your config up before calling setup
 *     nodejs.adminEmails = [
 *         "email1@example.com",
 *         "email2@example.com"
 *     ]
 *
 *     nodejs.protectedBranches = [
 *         master: 'daily'
 *     ]
 *
 *     nodejs.gitConfig = [
 *         user: 'robot-user',
 *         email: 'robot-user@example.com',
 *         credentialsId: 'robot-user'
 *     ]
 *
 *     nodejs.publishConfig = [
 *         email: nodejs.gitConfig.email,
 *         credentialsId: 'robot-user'
 *     ]
 *
 *     // MUST BE CALLED FIRST
 *     nodejs.setup()
 *
 *     // Create custom stages for your build like this
 *     nodejs.createStage(name: 'Some Stage", stage: {
 *         echo "This is my stage"
 *     })
 *
 *     // Run a build
 *     nodejs.buildStage()
 *
 *     // Run a test
 *     nodejs.testStage() // Provide required parameters in your pipeline.
 *
 *     // MUST BE CALLED LAST
 *     nodejs.end()
 * }
 * </pre>
 *
 * <p>In the example above, the stages will run on a node labeled {@code 'pipeline-node'}. You must
 * define the node where you pipeline will execute.</p>
 *
 * <p>Stages are not executed until the end stage. This means that the node that
 * {@code nodejs.end()} executes on is where the entire pipeline will execute. You also can't rely
 * that between the buildStage line and the testStage line that the build stage was successful.
 * The stage functions and should skip functions are run in order with the stages. This is where
 * any stage logic should go that is dependent on stage order of execution.
 * </p>
 *
 * <p>
 */
class NodeJSPipeline extends GenericPipeline {
    // @FUTURE part of the deploy story
    /**
     * This is the connection information for the registry where code is published
     * to.
     */
    RegistryConfig publishConfig

    /**
     * An array of registry connection information information for each registry.
     *
     * <p>These login operations will happen before the npm install in setup.</p>
     */
    RegistryConfig[] registryConfig

    /**
     * Constructs the class.
     *
     * <p>When invoking from a Jenkins pipeline script, the NodeJSPipeline must be passed
     * the current environment of the Jenkinsfile to have access to the steps.</p>
     *
     * <h5>Example Setup:</h5>
     * <pre>
     * def nodejs = new NodeJSPipeline(this)
     * </pre>
     *
     * @param steps The workflow steps object provided by the Jenkins pipeline
     */
    NodeJSPipeline(steps) { super(steps) }

    /**
     * Creates a stage that will build a NodeJSPipeline package.
     *
     * <p>Calling this function will add the following stage to your Jenkins pipeline. Arguments passed
     * to this function will map to the {@link org.zowe.pipelines.generic.models.BuildArgs} class.</p>
     *
     * <h5>Build: {@link org.zowe.pipelines.generic.models.BuildArgs#name}</h5>
     * <p>Runs the build of your application. If {@link org.zowe.pipelines.generic.models.BuildArgs#buildOperation} is not provided, the
     * stage will default to executing `npm run build`.</p>
     *
     * <p>The build stage also ignores any {@link org.zowe.pipelines.generic.models.BuildArgs#resultThreshold} provided and only runs
     * on {@link org.zowe.pipelines.base.models.ResultEnum#SUCCESS}.</p>
     *
     * <p>After the buildOperation is complete, the stage will continue to archive the contents of the
     * build into a tar file. The folder to archive is specified by arguments.output. In the future,
     * this function will run the npm pack command and archive that tar file instead.</p>
     *
     * <p>This stage will throw a {@link org.zowe.pipelines.generic.exceptions.BuildStageException} if called more than once in your pipeline.</p>
     *
     * @param arguments A map of arguments to be applied to the {@link org.zowe.pipelines.generic.models.BuildArgs} used to define
     *                  the stage.
     */
    void buildStage(Map arguments = [:]) {
        // Force build to only happen on success, this cannot be overridden
        arguments.resultThreshold = ResultEnum.SUCCESS

        buildStageGeneric(arguments + [buildOperation: {
            // Either use a custom build script or the default npm run build
            if (arguments.buildOperation) {
                arguments.buildOperation()
            } else {
                steps.sh 'npm run build'
            }

            steps.sh "npm pack"
            // determine the file name of the produced .tgz file
            def buildArchiveName = steps.sh(
                    script: 'ls *.tgz',
                    returnStdout: true
            )
            steps.archiveArtifacts "${buildArchiveName}"
            steps.sh "rm -f ${buildArchiveName}"
        }])

    // Npm logs will always be archived
    /**
     * Call to inform the runner that no more stages are to be added and execution can begin.
     *
     * <p>The following locations are always archived:</p>
     *
     * <ul>
     * <li>{@literal /home/jenkins/.npm/_logs}</li>
     * </ul>
     *
     * @param archiveFolders An array of folders to archive. If a specific folder doesn't exist, the
     *                       build will ignore it and will not modify the current build result. See
     *                       the notes in the log for the reasoning. If a folder in this array
     *                       starts with a {@literal `/`}, the stage will copy the folder into a temp directory
     *                       inside the project (retaining the folder structure). This is due to
     *                       the fact that folders outside the workspace cannot be archived by
     *                       Jenkins. The leading {@literal `/`} should be used for any logs that you wish to
     *                       capture that are outside the workspace. Also if the directory starts
     *                       with a {@literal ../}, the stage will abort access to that folder. This is because
     *                       Jenkins cannot archive files outside the workspace.
     */
    void end(String[] archiveFolders) {
        String[] archive = ["/home/jenkins/.npm/_logs"]

        if (archiveFolders) {
            archive = archive + archiveFolders
        }

        endBasic(archive)
    }


    // @FUTURE a super class could define this method for setup and checkout and the nodejs
    // @FUTURE class can extend it to add the npm install stuff
    /**
     * Creates the pipeline setup stages.
     *
     * <h5>Install Node Package Dependencies</h5>
     *
     * <p>This step will install all your package dependencies via `npm install`. Prior to install
     * the stage will login to any registries specified in the {@link #registryConfig} array. On
     * exit, the step will try to logout of the registries specified in {@link #registryConfig}.</p>
     *
     * <ul>
     * <li>If two default registries, a registry that omits a url, are specified, this stage will fail</li>
     * <li>Failure to login to a registry or install dependencies will result in a failed build.</li>
     * <li>Failure to logout of a registry will not fail the build.</li>
     * </ul>
     */
    void setup() {
        // @TODO all timeouts should be configurable do as part of next story
        // @FUTURE Fail if version was manually changed (allow for an override if we need to for some reason) for DEPLOY
        super.setupGeneric()

        createStage(name: 'Install Node Package Dependencies', stage: {
            try {
                if (registryConfig) {
                    // Only one is allowed to use the default registry
                    // This will keep track of that
                    def didUseDefaultRegistry = false

                    steps.echo "Login to registries"

                    for (int i = 0; i < registryConfig.length; i++) {
                        def registry = registryConfig[i]

                        if (!registry.url) {
                            if (didUseDefaultRegistry) {
                                throw new NodeJSPipelineException("No registry specified for registryConfig[${i}] and was already logged into the default")
                            }
                            didUseDefaultRegistry = true
                        }

                        _loginToRegistry(registry)
                    }
                }

                steps.sh "npm install"
            } finally {
                // Always try to logout regardless of errors
                if (registryConfig) {
                    steps.echo "Logout of registries"

                    for (int i = 0; i < registryConfig.length; i++) {
                        _logoutOfRegistry(registryConfig[i])
                    }
                }
            }
        }, isSkipable: false, timeout: [time: 5, unit: 'MINUTES'])
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
    void testStage(Map arguments = [:]) {
        if (!arguments.testOperation) {
            arguments.testOperation = {
                steps.sh "npm run test"
            }
        }

        super.testStageGeneric(arguments)
    }

    /**
     * Login to the specified registry.
     *
     * @param registry The registry to login to
     * @throws NodeJSPipelineException when either the email address or credentials property is missing
     *                               from the specified registry.
     */
    protected void _loginToRegistry(RegistryConfig registry) throws NodeJSPipelineException {
        if (!registry.email) {
            throw new NodeJSPipelineException("Missing email address for registry: ${registry.url ? registry.url : "default"}")
        }
        if (!registry.credentialsId) {
            throw new NodeJSPipelineException("Missing credentials for registry: ${registry.url ? registry.url : "default"}")
        }

        if (!registry.url) {
            steps.echo "Attempting to login to the default registry"
        } else {
            steps.echo "Attempting to login to the ${registry.url} registry"
        }

        // Bad formatting but this is probably the cleanest way to do the expect script
        def expectCommand = """/usr/bin/expect <<EOD
set timeout 60
#npm login command, add whatever command-line args are necessary
spawn npm login ${registry.url ? "--registry ${registry.url}" : ""}
match_max 100000

expect "Username"
send "\$EXPECT_USERNAME\\r"

expect "Password"
send "\$EXPECT_PASSWORD\\r"

expect "Email"
send "\$EXPECT_EMAIL\\r"

expect {
   timeout      exit 1
   eof
}
"""
        // Echo the command that was run
        steps.echo expectCommand

        steps.withCredentials([
                steps.usernamePassword(
                        credentialsId: registry.credentialsId,
                        usernameVariable: 'EXPECT_USERNAME',
                        passwordVariable: 'EXPECT_PASSWORD'
                )
        ]) {
            steps.withEnv(["EXPECT_EMAIL=${registry.email}"]) {
                steps.sh expectCommand
            }
        }
    }

    /**
     * Logout of the specified registry.
     *
     * @param registry The registry to logout of.
     */
    protected void _logoutOfRegistry(RegistryConfig registry) {
        if (!registry.url) {
            steps.echo "Attempting to logout of the default registry"
        } else {
            steps.echo "Attempting to logout of the ${registry.url} registry"
        }

        try {
            // If the logout fails, don't blow up. Coded this way because a failed
            // logout doesn't mean we've failed. It also doesn't stop any other
            // logouts that might need to be done.
            steps.sh "npm logout ${registry.url ? "--registry registry.url" : ""}"
        } catch (e) {
            steps.echo "Failed logout but will continue"
        }
    }
}
