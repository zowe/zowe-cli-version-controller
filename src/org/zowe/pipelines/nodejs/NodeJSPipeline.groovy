package org.zowe.pipelines.nodejs

import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import org.zowe.pipelines.base.ProtectedBranches
import org.zowe.pipelines.base.models.ResultEnum
import org.zowe.pipelines.base.models.Stage
import org.zowe.pipelines.base.models.StageTimeout
import org.zowe.pipelines.generic.GenericPipeline
import org.zowe.pipelines.generic.exceptions.DeployStageException
import org.zowe.pipelines.nodejs.models.*
import org.zowe.pipelines.nodejs.exceptions.*

import java.util.concurrent.TimeUnit

/**
 * A stage executor for a NodeJSPipeline pipeline.
 *
 * <p>This class provides methods that allow you to build, test, and deploy your NodeJSPipeline application.</p>
 *
 * <h5>Basic Usage</h5>
 * <pre>
 * {@literal @}Library('fill this out according to your setup') import org.zowe.pipelines.nodejs.NodeJSPipeline
 *
 * node('pipeline-node') {
 *     // Create the runner and pass the methods available to the workflow script to the runner
 *     NodeJSPipeline pipeline = new NodeJSPipeline(this)
 *
 *     // Set your config up before calling setup
 *     pipeline.adminEmails = [
 *         "email1@example.com",
 *         "email2@example.com"
 *     ]
 *
 *     pipeline.protectedBranches = [
 *         master: 'daily'
 *     ]
 *
 *     pipeline.gitConfig = [
 *         user: 'robot-user',
 *         email: 'robot-user@example.com',
 *         credentialsId: 'robot-user'
 *     ]
 *
 *     pipeline.publishConfig = [
 *         email: nodejs.gitConfig.email,
 *         credentialsId: 'robot-user'
 *     ]
 *
 *     // MUST BE CALLED FIRST
 *     pipeline.setup()
 *
 *     // Create custom stages for your build like this
 *     pipeline.createStage(name: 'Some Stage", stage: {
 *         echo "This is my stage"
 *     })
 *
 *     // Run a build
 *     pipeline.build()
 *
 *     // Run a test
 *     pipeline.test() // Provide required parameters in your pipeline.
 *
 *     // MUST BE CALLED LAST
 *     pipeline.end()
 * }
 * </pre>
 *
 * <p>In the example above, the stages will run on a node labeled {@code 'pipeline-node'}. You must
 * define the node where your pipeline will execute.</p>
 */
class NodeJSPipeline extends GenericPipeline {
    /**
     * This is the id of the approver saved when the pipeline auto approves the deploy.
     */
    static final String AUTO_APPROVE_ID = "[PIPELINE_AUTO_APPROVE]"

    static final String SYSTEM_ID = "SYSTEM"

    /**
     * This is the connection information for the registry where code is published
     * to.
     *
     * <p>If URL is passed to publish config, it will be ignored in favor of the package.json file's
     * publishConfig.registry property.</p>
     */
    RegistryConfig publishConfig

    /**
     * An array of registry connection information information for each registry.
     *
     * <p>These login operations will happen before the npm install in setup.</p>
     */
    RegistryConfig[] registryConfig

    /**
     * A map of protected branches.
     *
     * <p>Any branches that are specified as protected will also have concurrent builds disabled. This
     * is to prevent issues with publishing.</p>
     */
    ProtectedBranches<NodeJSProtectedBranch> protectedBranches = new ProtectedBranches<>(NodeJSProtectedBranch.class)

    /**
     * Constructs the class.
     *
     * <p>When invoking from a Jenkins pipeline script, the NodeJSPipeline must be passed
     * the current environment of the Jenkinsfile to have access to the steps.</p>
     *
     * <h5>Example Setup:</h5>
     * <pre>
     * def pipeline = new NodeJSPipeline(this)
     * </pre>
     *
     * @param steps The workflow steps object provided by the Jenkins pipeline
     */
    NodeJSPipeline(steps) {
        super(steps)
    }

    /**
     * Creates a stage that will build a NodeJSPipeline package.
     *
     * <p>Arguments passed to this function will map to the
     * {@link org.zowe.pipelines.generic.models.BuildArgs} class.</p>
     *
     * <p>The stage will be created with the {@link org.zowe.pipelines.generic.GenericPipeline#buildGeneric(java.util.Map)}
     * method and will have the following additional operations. <ul>
     *     <li>If {@link org.zowe.pipelines.generic.models.BuildArgs#buildOperation} is not
     *     provided, the stage will default to executing {@code npm run build}.</li>
     *     <li>After the buildOperation is complete, the stage will use npm pack to generate an
     *     installable artifact. This artifact is archived to the build for later access.</li>
     * </ul>
     *
     * @param arguments A map of arguments to be applied to the {@link org.zowe.pipelines.generic.models.BuildArgs} used to define
     *                  the stage.
     */
    void build(Map arguments = [:]) {
        // Force build to only happen on success, this cannot be overridden
        arguments.resultThreshold = ResultEnum.SUCCESS

        buildGeneric(arguments + [operation: { String stageName ->
            // Either use a custom build script or the default npm run build
            if (arguments.operation) {
                arguments.operation(stageName)
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
    }

    void deploy(Map deployArguments = [:], Map versionArguments = [:]) {
        IllegalArgumentException deployException
        IllegalArgumentException versionException

        if (deployArguments.operation) {
            deployException = new IllegalArgumentException("operation is an invalid map object for deployArguments")
        }

        if (versionArguments.operation) {
            versionException = new IllegalArgumentException("operation is an invalid map object for versionArguments")
        }

        // Set the deploy operation for an npm pipeline
        deployArguments.operation = { String stageName ->
            if (deployException) {
                throw deployException
            }

            gitPush()

            // Login to the registry
            def npmRegistry = steps.sh returnStdout: true,
                    script: "node -e \"process.stdout.write(require('./package.json').publishConfig.registry)\""
            publishConfig.url = npmRegistry.trim()

            steps.sh "sudo npm config set registry ${publishConfig.url}"

            // Login to the publish registry
            _loginToRegistry(publishConfig)

            steps.sh "npm publish --tag ${protectedBranches.get(_changeInfo.branchName).tag} --dry-run"

            // Logout immediately
            _logoutOfRegistry(publishConfig)
        }

        // Set the version operation for an npm pipeline
        versionArguments.operation = { String stageName ->
            if (versionException) {
                throw versionException
            }

            steps.echo "TODO Fill this out"

            // Get the package.json
            def packageJSON = steps.readJSON file: 'package.json'

            // Extract the base version
            def baseVersion = packageJSON.version.split("-")[0]

            // Extract the raw version
            def rawVersion = baseVersion.split("\\.")

            NodeJSProtectedBranch branch = protectedBranches.get(_changeInfo.branchName)

            // Format the prerelease to be applied to every item
            String prereleaseString = branch.prerelease ? "-${branch.prerelease}." + new Date().format("yyyyMMddHHmm", TimeZone.getTimeZone("UTC")) : ""

            List<String> availableVersions = ["$baseVersion$prereleaseString"]

            // closure function to make semver increment easier
            Closure addOne = {String number ->
                return Integer.parseInt(number) + 1
            }

            // This switch case has every statement fallthrough. This is so that we can add all the versions based
            // on whichever has the lowest restriction
            switch(branch.level) {
                case SemverLevel.MAJOR:
                    availableVersions.add("${addOne(rawVersion[0])}.0.0$prereleaseString")
                    // falls through
                case SemverLevel.MINOR:
                    availableVersions.add("${rawVersion[0]}.${addOne(rawVersion[1])}.0$prereleaseString")
                    // falls through
                case SemverLevel.PATCH:
                    availableVersions.add("${rawVersion[0]}.${rawVersion[1]}.${addOne(rawVersion[2])}$prereleaseString")
                    break
            }

            if (branch.autoDeploy) {
                steps.env.DEPLOY_VERSION = availableVersions.get(0)
                steps.env.DEPLOY_APPROVER = AUTO_APPROVE_ID
            } else if (admins.size() == 0) {
                steps.echo "ERROR"
                throw new DeployStageException(
                        "No approvers available! Please specify at least one approver in NodeJSPipeline.approverIds",
                        stageName
                )
            } else {
                Stage currentStage = getStage(stageName)

                // Add a timeout of one minute less than the available stage execution time
                // This will allow the versioning task at least 1 minute to update the files and
                // move on to the next step.
                StageTimeout timeout = currentStage.args.timeout.subtract(time: 1, unit: TimeUnit.MINUTES)

                if (timeout.time <= 0) {
                    throw new DeployStageException(
                            "Unable to wait for input! Timeout for $stageName, must be greater than 1 minute." +
                                    " Timeout was ${currentStage.args.timeout.toString()}", stageName
                    )
                }

                try {
                    steps.timeout(time: timeout.time, unit: timeout.unit) {
                        sendHtmlEmail(
                                subjectTag: "APPROVAL REQUIRED",
                                body: "<h3>${steps.env.JOB_NAME}</h3>" +
                                        "<p>Branch: <b>${steps.BRANCH_NAME}</b></p>" +
                                        "<p>Versioning information is required before the pipeline can continue. Please" +
                                        " provide the required input <a href=\"${steps.RUN_DISPLAY_URL}\">HERE</a></p>",
                                to: admins.emailList,
                                addProviders: false
                        )

                        def inputMap = steps.input message: "Version Information Required", ok: "Publish",
                                submitter: admins.approverList, submitterParameter: "DEPLOY_APPROVER",
                                parameters: [
                                        steps.choice(
                                                name: "DEPLOY_VERSION",
                                                choices: availableVersions,
                                                description: "What version should be used?"
                                        )
                                ]

                        steps.env.DEPLOY_APPROVER = inputMap.DEPLOY_APPROVER
                        steps.env.DEPLOY_VERSION = inputMap.DEPLOY_VERSION
                    }
                } catch (FlowInterruptedException exception) {
                    if (exception.causes[0].user.toString() == SYSTEM_ID) {
                        steps.env.DEPLOY_APPROVER = SYSTEM_ID
                        steps.env.DEPLOY_VERSION = availableVersions.get(0)
                    } else {
                        // If the system didn't cancel the request, propogate the exception up the
                        // callstack
                        throw exception
                    }
                }
            }

            String approveName = steps.env.DEPLOY_APPROVER == SYSTEM_ID ? SYSTEM_ID : admins.get(steps.env.DEPLOY_APPROVER).name

            steps.echo "${steps.env.DEPLOY_VERSION} approved by $approveName"

            sendHtmlEmail(
                    subjectTag: "APPROVED",
                    body: "<h3>${steps.env.JOB_NAME}</h3>" +
                            "<p>Branch: <b>${steps.BRANCH_NAME}</b></p>" +
                            "<p>Approved: <b>${packageJSON.name}@${packageJSON.version}</b></p>" +
                            "<p>Approved By: <b>${approveName}</b></p>",
                    to: admins.emailList,
                    addProviders: false
            )

            packageJSON.version = steps.env.DEPLOY_VERSION
            steps.writeJSON file: 'package.json', json: packageJSON, pretty: 2
            steps.sh "git add package.json"
            gitCommit("Bump version to ${steps.env.DEPLOY_VERSION}")

            // @TODO commit version bump to deploy step fail if any changes
            // @TODO send out confirmation email of deploy success in deploy step
        }

        super.deployGeneric(deployArguments, versionArguments)
    }

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

        endBase(archive)
    }


    // @FUTURE a super class could define this method for setup and checkout and the nodejs
    // @FUTURE class can extend it to add the npm install stuff
    /**
     * Calls {@link org.zowe.pipelines.generic.GenericPipeline#setupGeneric()} to setup the build.
     *
     * <p>Additionally, this method adds the following stage to the build:</p>
     *
     * <h4>Install Node Package Dependencies</h4>
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

                // Get the branch that will be used to install dependencies for
                String branch

                // If this is a pull request, then we will be checking if the base branch is protected
                if (_changeInfo.isPullRequest) {
                    branch = _changeInfo.baseBranch
                }
                // Otherwise we are checking if the current branch is protected
                else {
                    branch = _changeInfo.branchName
                }

                if (protectedBranches.isProtected(branch)) {
                    def branchProps = protectedBranches.get(branch)

                    def depInstall = "npm install"
                    def devInstall = "npm install"

                    // If this is a pull request, we don't want to make any commits
                    if (_changeInfo.isPullRequest) {
                        depInstall += " --no-save"
                        devInstall += " --no-save"
                    }
                    // Otherwise we need to save the version properly
                    else {
                        depInstall += " --no-save"
                        devInstall += " --save-dev"
                    }

                    branchProps.dependencies.each { npmPackage, version -> steps.sh "$depInstall $npmPackage@$version" }
                    branchProps.devDependencies.each { npmPackage, version -> steps.sh "$devInstall $npmPackage@$version" }

                    if (!_changeInfo.isPullRequest) {
                        // Add package and package lock to the commit tree. This will not fail if
                        // unable to add an item for any reasons.
                        steps.sh "git add package.json package-lock.json --ignore-errors || exit 0"
                        gitCommit("Updating dependencies")
                        gitPush() // @TODO REMOVE THIS LINE BECAUSE I AM TESTING RIGHT NOW
                    }
                }
            } finally {
                // Always try to logout regardless of errors
                if (registryConfig) {
                    steps.echo "Logout of registries"

                    for (int i = 0; i < registryConfig.length; i++) {
                        _logoutOfRegistry(registryConfig[i])
                    }
                }
            }
        }, isSkippable: false, timeout: [time: 10, unit: TimeUnit.MINUTES])
    }

    /**
     * Creates a stage that will execute tests on your application.
     *
     * <p>Arguments passed to this function will map to the
     * {@link org.zowe.pipelines.generic.models.TestArgs} class.</p>
     *
     * <p>The stage will be created with the
     * {@link org.zowe.pipelines.generic.GenericPipeline#testGeneric(java.util.Map)} method. If
     * {@link org.zowe.pipelines.generic.models.TestArgs#testOperation} is not provided, this method
     * will default to executing {@code npm run test}</p>
     *
     *
     * @param arguments A map of arguments to be applied to the {@link org.zowe.pipelines.generic.models.TestArgs} used to define
     *                  the stage.
     */
    void test(Map arguments = [:]) {
        if (!arguments.operation) {
            arguments.operation = {
                steps.sh "npm run test"
            }
        }

        super.testGeneric(arguments)
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
            steps.sh "npm logout ${registry.url ? "--registry ${registry.url}" : ""}"
        } catch (e) {
            steps.echo "Failed logout but will continue"
        }
    }
}
