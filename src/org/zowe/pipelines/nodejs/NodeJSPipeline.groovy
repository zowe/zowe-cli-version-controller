package org.zowe.pipelines.nodejs

import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import org.zowe.pipelines.base.ProtectedBranches
import org.zowe.pipelines.base.models.ResultEnum
import org.zowe.pipelines.base.models.Stage
import org.zowe.pipelines.base.models.StageTimeout
import org.zowe.pipelines.generic.GenericPipeline
import org.zowe.pipelines.generic.exceptions.DeployStageException
import org.zowe.pipelines.nodejs.arguments.NodeJSSetupArguments
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

    static final String TIMEOUT_APPROVE_ID = "[TIMEOUT_APPROVED]"

    /**
     * A map of protected branches.
     *
     * <p>Any branches that are specified as protected will also have concurrent builds disabled. This
     * is to prevent issues with publishing.</p>
     */
    ProtectedBranches<NodeJSProtectedBranch> protectedBranches = new ProtectedBranches<>(NodeJSProtectedBranch.class)

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
     * {@link org.zowe.pipelines.generic.arguments.BuildStageArguments} class.</p>
     *
     * <p>The stage will be created with the {@link org.zowe.pipelines.generic.GenericPipeline#buildGeneric(java.util.Map)}
     * method and will have the following additional operations. <ul>
     *     <li>If {@link org.zowe.pipelines.generic.arguments.BuildStageArguments#buildOperation} is not
     *     provided, the stage will default to executing {@code npm run build}.</li>
     *     <li>After the buildOperation is complete, the stage will use npm pack to generate an
     *     installable artifact. This artifact is archived to the build for later access.</li>
     * </ul>
     *
     * @param arguments A map of arguments to be applied to the {@link org.zowe.pipelines.generic.arguments.BuildStageArguments} used to define
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

            // archive the build
            steps.sh "mkdir temp"

            steps.dir("temp") {
                def json = steps.readJson "../package.json"
                def revision = steps.sh returnStdout: true, script: "git rev-parse HEAD"

                def archiveName = "${json.name}.revision-${revision}.tgz"

                steps.sh "PACK_NAME=\$(npm pack ../ | tail -1) && cp \$PACK_NAME $archiveName "
                steps.archiveArtifacts archiveName
                steps.sh "rm -f $archiveName"
            }
        }])
    }

    void deploy(Map arguments = [:]) {
        if (!arguments.versionArguments) {
            arguments.versionArguments = [:]
        }

        if (!arguments.deployArguments) {
            arguments.deployArguments = [:]
        }

        deploy(arguments.deployArguments, arguments.versionArguments)
    }

    protected void deploy(Map deployArguments, Map versionArguments) {
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

            // Login to the registry
            def npmRegistry = steps.sh returnStdout: true,
                    script: "node -e \"process.stdout.write(require('./package.json').publishConfig.registry)\""
            publishConfig.url = npmRegistry.trim()

            steps.sh "sudo npm config set registry ${publishConfig.url}"

            // Login to the publish registry
            _loginToRegistry(publishConfig)

            NodeJSProtectedBranch branch = protectedBranches.get(_changeInfo.branchName)

            try {
                gitPush()
                steps.sh "npm publish --tag ${branch.tag}"

                sendHtmlEmail(
                    subjectTag: "DEPLOYED",
                    body: "<h3>${steps.env.JOB_NAME}</h3>" +
                        "<p>Branch: <b>${steps.BRANCH_NAME}</b></p>" +
                        "<p>Deployed Package: <b>${steps.env.DEPLOY_PACKAGE}@${steps.env.DEPLOY_VERSION}</b></p>",
                        "<p>Package Tag: <b>${branch.tag}</b></p>" +
                        "<p>Registry: <b>{$publishConfig.url}</b></p>",
                    to: admins.emailList,
                    addProviders: false
                )
            } finally {
                // Logout immediately
                _logoutOfRegistry(publishConfig)

                steps.echo "Deploy Complete, please check this step for errors"
            }
        }

        // Set the version operation for an npm pipeline
        versionArguments.operation = { String stageName ->
            if (versionException) {
                throw versionException
            }

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
                        "No approvers available! Please specify at least one NodeJSPipeline.admin before deploying.",
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

                long startTime = System.currentTimeMillis()
                try {
                    // Sleep for 100 ms to ensure that the timeout catch logic will always work.
                    // This implies that an abort within 100 ms of the timeout will result in
                    // an ignore but who cares at this point.
                    steps.sleep time: 100, unit: TimeUnit.MILLISECONDS

                    steps.timeout(time: timeout.time, unit: timeout.unit) {
                        String bodyText = "<p>Below is the list of versions to choose from:<ul><li><b>${availableVersions.get(0)} [DEFAULT]</b>: " +
                            "This version was derived from the package.json version by only adding/removing a prerelease string as needed.</li>"

                        String versionList = ""
                        List<String> versionText = ["PATCH", "MINOR", "MAJOR"]

                        // Work backwards because of how the versioning works.
                        // patch is always the last element
                        // minor is always the second to last element when present
                        // major is always the third to last element when present
                        // default is always at 0 and there can never be more than 4 items
                        for (int i = availableVersions.size() - 1; i > 0; i--) {
                            String version = versionText.removeAt(0)
                            versionList = "<li><b>${availableVersions.get(i)} [$version]</b>: $version update with any " +
                                "necessary prerelease strings attached.</li>$versionList"
                        }

                        bodyText += "$versionList</ul></p>" +
                            "<p>Versioning information is required before the pipeline can continue. If no input is provided within " +
                            "${timeout.toString()}, the default version (${availableVersions.get(0)}) will be the " +
                            "deployed version. Please provide the required input <a href=\"${steps.RUN_DISPLAY_URL}\">HERE</a></p>"

                        sendHtmlEmail(
                            subjectTag: "APPROVAL REQUIRED",
                            body: "<h3>${steps.env.JOB_NAME}</h3>" +
                                "<p>Branch: <b>${steps.BRANCH_NAME}</b></p>" + bodyText + _getChangeSummary(),
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
                        steps.env.DEPLOY_PACKAGE = packageJSON.name
                        steps.env.DEPLOY_VERSION = inputMap.DEPLOY_VERSION
                    }
                } catch (FlowInterruptedException exception) {
                    /*
                     * Do some bs math to determine if we had a timeout because there is no other way.
                     * Don't even suggest to me that there might be another way unless you can provide
                     * the code that I couldn't find in 5 hours.
                     *
                     * The main problem is that when the timeout step kills the input step, the input
                     * step fires out another FlowInterruptedException. This interrupted exception
                     * takes precedent over the TimeoutException that should be thrown by the timeout step.
                     *
                     * Previously, I had checked to see if the exception.cause[0].user was SYSTEM and
                     * would use that to indicate timeout. However this scenario happens for both a timeout
                     * and a ui abort not on the input step. The consequence of this was that aborting
                     * a build using the stop button would act like a timeout and the deploy would
                     * auto-approve. This is not the desired behavior for an abort.
                     *
                     * It is because of these reasons that I have determined my cheeky timeout check
                     * is the only feasible solution with the current state of Jenkins. If this
                     * changes in the future, the logic can be revisited to adjust.
                     *
                     */
                    if (System.currentTimeMillis() - startTime >= timeout.unit.toMillis(timeout.time)) {
                        steps.env.DEPLOY_APPROVER = TIMEOUT_APPROVE_ID
                        steps.env.DEPLOY_VERSION = availableVersions.get(0)
                    } else {
                        throw exception
                    }
                }
            }
            String approveName = steps.env.DEPLOY_APPROVER == TIMEOUT_APPROVE_ID ? TIMEOUT_APPROVE_ID : admins.get(steps.env.DEPLOY_APPROVER).name

            steps.echo "${steps.env.DEPLOY_VERSION} approved by $approveName"

            sendHtmlEmail(
                    subjectTag: "APPROVED",
                    body: "<h3>${steps.env.JOB_NAME}</h3>" +
                            "<p>Branch: <b>${steps.BRANCH_NAME}</b></p>" +
                            "<p>Approved: <b>${steps.env.DEPLOY_PACKAGE}@${steps.env.DEPLOY_VERSION}</b></p>" +
                            "<p>Approved By: <b>${approveName}</b></p>",
                    to: admins.emailList,
                    addProviders: false
            )

            packageJSON.version = steps.env.DEPLOY_VERSION
            steps.writeJSON file: 'package.json', json: packageJSON, pretty: 2
            steps.sh "git add package.json"
            gitCommit("Bump version to ${steps.env.DEPLOY_VERSION}")
        }

        super.deployGeneric(deployArguments, versionArguments)
    }

    // @TODO doc update
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
    void end(Map options = [:]) {
        List<String> archive = ["/home/jenkins/.npm/_logs"]

        if (options.archiveFolders) {
            options.archiveFolders = archive + options.archiveFolders
        } else {
            options.archiveFolders = archive
        }

        super.endGeneric(options)
    }


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
    void setup(NodeJSSetupArguments timeouts) {
        super.setupGeneric(timeouts)

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
        }, isSkippable: false, timeout: timeouts.installDependencies)
    }

    void setup(Map timeouts = [:]) {
        setup(timeouts as NodeJSSetupArguments)
    }

    /**
     * Creates a stage that will execute tests on your application.
     *
     * <p>Arguments passed to this function will map to the
     * {@link org.zowe.pipelines.generic.arguments.TestStageArguments} class.</p>
     *
     * <p>The stage will be created with the
     * {@link org.zowe.pipelines.generic.GenericPipeline#testGeneric(java.util.Map)} method. If
     * {@link org.zowe.pipelines.generic.arguments.TestStageArguments#testOperation} is not provided, this method
     * will default to executing {@code npm run test}</p>
     *
     *
     * @param arguments A map of arguments to be applied to the {@link org.zowe.pipelines.generic.arguments.TestStageArguments} used to define
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
#npm login command, add whatever command-line arguments are necessary
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
