/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.pipelines.nodejs

import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import org.zowe.pipelines.base.ProtectedBranches
import org.zowe.pipelines.base.models.Stage
import org.zowe.pipelines.base.models.StageTimeout
import org.zowe.pipelines.generic.GenericPipeline
import org.zowe.pipelines.generic.arguments.VersionStageArguments
import org.zowe.pipelines.generic.exceptions.*
import org.zowe.pipelines.nodejs.arguments.*
import org.zowe.pipelines.nodejs.models.*
import org.zowe.pipelines.nodejs.exceptions.*

import java.util.concurrent.TimeUnit

/**
 * Extends the functionality available in the {@link org.zowe.pipelines.generic.GenericPipeline} class.
 * This class adds more advanced functionality to build, test, and deploy your application.
 *
 * <dl><dt><b>Required Plugins:</b></dt><dd>
 * The following plugins are required:
 *
 * <ul>
 *     <li>All plugins listed at {@link org.zowe.pipelines.generic.GenericPipeline}</li>
 *     <li><a href="https://plugins.jenkins.io/pipeline-utility-steps">Pipeline Utility Steps</a></li>
 *     <li><a href="https://plugins.jenkins.io/pipeline-input-step">Pipeline: Input Step</a></li>
 * </ul>
 * </dd></dl>
 *
 * <pre>
 * {@literal @}Library('fill this out according to your setup') import org.zowe.pipelines.nodejs.NodeJSPipeline
 *
 * node('pipeline-node') {
 *     // Create the runner and pass the methods available to the workflow script to the runner
 *     NodeJSPipeline pipeline = new NodeJSPipeline(this)
 *
 *     // Set your config up before calling setup
 *     pipeline.admins.add("userid1", "userid2", "userid3")
 *
 *     pipeline.protectedBranches.addMap([
 *         [name: "master", tag: "daily", prerelease: "alpha"],
 *         [name: "beta", tag: "beta", prerelease: "beta"],
 *         [name: "dummy", tag: "dummy", autoDeploy: true],
 *         [name: "latest", tag: "latest"],
 *         [name: "lts-incremental", tag: "lts-incremental", level: SemverLevel.MINOR],
 *         [name: "lts-stable", tag: "lts-stable", level: SemverLevel.PATCH]
 *     ])
 *
 *     pipeline.gitConfig = [
 *         email: 'robot-user@example.com',
 *         credentialsId: 'robot-user'
 *     ]
 *
 *     pipeline.publishConfig = [
 *         email: nodejs.gitConfig.email,
 *         credentialsId: 'robot-user'
 *     ]
 *
 *     pipeline.registryConfig = [
 *         [email: 'email@example.com', credentialsId: 'credentials-id'],
 *         [url: 'https://registry.com', email: 'email@example.com', credentialsId: 'credentials-id']
 *         [url: 'https://registry.com', email: 'email@example.com', credentialsId: 'credentials-id', scope: '@myOrg']
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
 *     pipeline.build()               ///////////////////////////////////////////////////
 *                                    //                                               //
 *     // Run a test                  //                                               //
 *     pipeline.test()                // Provide required parameters in your pipeline. //
 *                                    //                                               //
 *     // Deploy your application     //                                               //
 *     pipeline.deploy()              ///////////////////////////////////////////////////
 *
 *     // MUST BE CALLED LAST
 *     pipeline.end()
 * }
 * </pre>
 *
 * <p>In the example above, the stages will run on a node labeled {@code 'pipeline-node'}. You must
 * define the node where your pipeline will execute. This node must have the ability to execute an
 * <a href="https://en.wikipedia.org/wiki/Expect">Expect Script</a>.</p>
 */
class NodeJSPipeline extends GenericPipeline {
    /**
     * This is the id of the approver saved when the pipeline auto approves the deploy.
     */
    static final String AUTO_APPROVE_ID = "[PIPELINE_AUTO_APPROVE]"

    /**
     * This is the id of the approver saved when the pipeline auto approves the deploy because
     * of a timeout.
     */
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
     * A registry connection information ojbect used by default on protected branches if specified.
     *
     * <p>The login operation will happen before the npm install in setup.</p>
     */
    RegistryConfig releaseRegistryConfig

    /**
     * Constructs the class.
     *
     * <p>When invoking from a Jenkins pipeline script, the NodeJSPipeline must be passed
     * the current environment of the Jenkinsfile to have access to the steps.</p>
     *
     * @Example
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
     *     <li>If {@link org.zowe.pipelines.generic.arguments.BuildStageArguments#operation} is not
     *     provided, the stage will default to executing {@code npm run build}.</li>
     *     <li>After the operation is complete, the stage will use npm pack to generate an
     *     installable artifact. This artifact is archived to the build for later access.</li>
     * </ul></p>
     *
     * @param arguments A map of arguments to be applied to the {@link org.zowe.pipelines.generic.arguments.BuildStageArguments} used to define
     *                  the stage.
     */
    void build(Map arguments = [:]) {
        buildGeneric(arguments + [operation: { String stageName ->
            // Either use a custom build script or the default npm run build
            if (arguments.operation) {
                arguments.operation(stageName)
            } else {
                steps.sh 'npm run build'
            }

            // archive the build
            steps.sh "mkdir -p temp"

            steps.dir("temp") {
                def packageJSON = steps.readJSON file: "../package.json"
                def revision = steps.sh(returnStdout: true, script: "git rev-parse HEAD").trim()

                // Replace special file character names
                def name = packageJSON.name.replaceAll("@", "")
                    .replaceAll("/", "-")

                def archiveName = "${name}.revision.${revision}.tgz"

                steps.sh "PACK_NAME=\$(npm pack ../ | tail -1) && mv \$PACK_NAME $archiveName "
                steps.archiveArtifacts archiveName
                steps.sh "rm -f $archiveName"

                // Set environment variables needed by later stages
                steps.env.DEPLOY_PACKAGE = packageJSON.name
                steps.env.DEPLOY_VERSION = packageJSON.version
            }
        }])
    }

    /**
     * Manage versions of a NodeJSPipeline package.
     *
     * <p>Arguments passed to this function will map to the
     * {@link org.zowe.pipelines.generic.arguments.VersionStageArguments} class.</p>
     *
     * <p>The stage will be created with the {@link org.zowe.pipelines.generic.GenericPipeline#versionGeneric(java.util.Map)}
     * method.</p>
     *
     * <p>In a Node JS Pipeline, this stage will always be executed on a protected branch. When in this stage,
     * the build will determine the possible versions based on the {@link NodeJSProtectedBranch#prerelease} and
     * {@link NodeJSProtectedBranch#level} properties.</p>
     *
     * <p>If the branch is set to {@link NodeJSProtectedBranch#autoDeploy}, then the default version will be used to publish
     * (with any prerelease strings needed/removed). Otherwise, an email will be sent out asking what the new version
     * should be. This email will list the possible versions and give a link back to the build. The build will wait
     * for one of the {@link #admins} to open the link and select the version. If the wait period expires, the build will
     * continue as if it was autoDeployed and will use the default version.</p>
     *
     * <p>If the build is approved, this new version will be committed to <b>package.json</b> and the build will continue.
     * Otherwise, the build will stop at this step.</p>
     *
     * @Exceptions
     * <p>
     *     The following exceptions will be thrown if there is an error.
     *
     *     <dl>
     *         <dt><b>{@link IllegalArgumentException}</b></dt>
     *         <dd>When versionArguments.operation is provided. This is an invalid parameter.</dd>
     *         <dt><b>{@link org.zowe.pipelines.generic.exceptions.VersionStageException}</b></dt>
     *         <dd>
     *             When no pipeline admins are defined and auto deploy is false. Pipeline admins are used as approvers
     *             for the build. If there are none, the build can never be approved.
     *         </dd>
     *         <dd>
     *             A timeout of 1 Minute or less was specified. This would result in no wait for the version input
     *             and is most likely a programming mistake.
     *         </dd>
     *     </dl>
     * </p>
     *
     * @param arguments A map of arguments to be applied to the {@link org.zowe.pipelines.generic.arguments.VersionStageArguments} used to
     *                  define the stage.
     */
    void version(Map arguments = [:]) {
        IllegalArgumentException versionException

        if (arguments.operation) {
            versionException = new IllegalArgumentException("operation is an invalid map object for versionArguments")
        }

        // Set the version operation for an npm pipeline
        arguments.operation = { String stageName ->
            if (versionException) {
                throw versionException
            }

            // Extract the base version
            def baseVersion = steps.env.DEPLOY_VERSION.split("-")[0]

            // Extract the raw version
            def rawVersion = baseVersion.split("\\.")

            NodeJSProtectedBranch branch = protectedBranches.get(changeInfo.branchName)

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
            } else if (admins.size == 0) {
                steps.echo "ERROR"
                throw new VersionStageException("No approvers available! Please specify at least one NodeJSPipeline.admin before deploying.", stageName)
            } else {
                Stage currentStage = getStage(stageName)

                // Add a timeout of one minute less than the available stage execution time
                // This will allow the versioning task at least 1 minute to update the files and
                // move on to the next step.
                StageTimeout timeout = currentStage.args.timeout.subtract(time: 1, unit: TimeUnit.MINUTES)

                if (timeout.time <= 0) {
                    throw new VersionStageException("Unable to wait for input! Timeout for $stageName, must be greater than 1 minute." +
                            "Timeout was ${currentStage.args.timeout.toString()}", stageName)
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
                                submitter: admins.commaSeparated, submitterParameter: "DEPLOY_APPROVER",
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

            String approveName =
                    steps.env.DEPLOY_APPROVER == TIMEOUT_APPROVE_ID ? TIMEOUT_APPROVE_ID :
                            steps.env.DEPLOY_APPROVER == AUTO_APPROVE_ID ? AUTO_APPROVE_ID : admins.get(steps.env.DEPLOY_APPROVER).name

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

            // reset working directory before versioning
            steps.sh "git reset --hard"
            steps.sh "npm version ${steps.env.DEPLOY_VERSION} --allow-same-version -m \"Release ${steps.env.DEPLOY_VERSION} to ${branch.tag}\""
            gitCommit("Bump version to ${steps.env.DEPLOY_VERSION}", true)
            gitPush(arguments.gitTag ? arguments.gitTag : true)
        }

        super.versionGeneric(arguments)
    }

    /**
     * Creates a stage that will execute a vulnerability check
     *
     * <p>Calling this function will add the following stage to your Jenkins pipeline. Arguments passed
     * to this function will map to the {@link CheckVulnerabilitiesStageArguments} class.
     *
     * @Stages
     * This method adds the following stage to your build:
     * <dl>
     *     <dt><b>Check Vulnerabilities</b></dt>
     *     <dd>This stage is responsible for npm auditing your application source.</dd>
     * </dl>
     *
     * @Conditions
     *
     * <p>
     *     NONE
     * </p>
     *
     * @Exceptions
     *
     * <p>
     *     The following exceptions will be thrown if there is an error.
     *
     *     <dl>
     *         <dt><b>{@link NodeJSPipelineException}</b></dt>
     *         <dd>When stage is provided as an argument.</dd>
     *         <dd>When operation is provided as an argument.</dd>
     *     </dl>
     * </p>
     *
     * @param arguments A map of arguments to be applied to the {@link CheckVulnerabilitiesStageArguments} used to define the stage.
     */
    void checkVulnerabilities(CheckVulnerabilitiesStageArguments arguments = [:]) {

        NodeJSPipelineException preSetupException

        if (arguments.stage) {
            preSetupException = new NodeJSPipelineException("arguments.stage is an invalid option for checkVulnerabilities", arguments.name)
        }
        if (arguments.operation) {
            preSetupException = new NodeJSPipelineException("arguments.operation is an invalid option for checkVulnerabilities", arguments.name)
        }

        arguments.stage = { String stageName ->
            // If there were any exceptions during the setup, throw them here so proper email notifications can be sent.
            if (preSetupException) {
                throw preSetupException
            }

            def packageJSON = steps.readJSON file: 'package.json'
            def devDeps = packageJSON.devDependencies

            if (!arguments.dev) {
                // Clean the work space
                steps.sh "rm npm-shrinkwrap.json || exit 0"
                steps.sh "rm package-lock.json || exit 0"

                packageJSON.devDependencies = [:]
                steps.writeJSON file: 'package.json', json: packageJSON

                // Create an production ready environment
                steps.sh "npm prune --production --no-package-lock"
                steps.sh "npm shrinkwrap"
            }

            steps.sh "npm audit${arguments.registry != "" ? " --registry ${arguments.registry}" : ""}"

            packageJSON.devDependencies = devDeps
            steps.writeJSON file: 'package.json', json: packageJSON
        }

        // Create the stage and ensure that the first one is the stage of reference
        Stage checkVuln = createStage(arguments)
        if (!_control.checkVuln) {
            _control.checkVuln = checkVuln
        }
    }

    /**
     * Deploy a Node JS package.
     *
     * <dl>
     *     <dt><b>Argument Map:</b></dt>
     *     <dd>
     *         The following map objects are valid options for the arguments map.
     *         <dl>
     *             <dt><b>versionArguments</b></dt>
     *             <dd>A map of {@link org.zowe.pipelines.generic.arguments.GenericStageArguments} to be
     *             provided to the version command.</dd>
     *             <dt><b>deployArguments</b></dt>
     *             <dd>A map of {@link org.zowe.pipelines.generic.arguments.GenericStageArguments} to be
     *                 provided to the deploy command.</dd>
     *         </dl>
     *     </dd>
     * </dl>
     *
     * @Exceptions
     * <p>
     *     The following exceptions will be thrown from the Setup stage if there is an error.
     *
     *     <dl>
     *         <dt><b>{@link IllegalArgumentException}</b></dt>
     *         <dd>When an invalid map option is sent to this method.</dd>
     *     </dl>
     * </p>
     *
     * @param arguments The arguments map.
     *
     * @see #deploy(java.util.Map, java.util.Map)
     */
    void deploy(Map arguments = [:]) {
        if (!arguments.versionArguments) {
            arguments.versionArguments = [:]
        }

        if (!arguments.deployArguments) {
            arguments.deployArguments = [:]
        }

        // If the size is > than 2 at this point, that means there are invalid keys
        // in the map. Gather them and print them out. Also only run if we don't
        // already have a first failing stage.
        if (arguments.size() > 2 && !_stages.firstFailingStage) {
            String badArgs = arguments.collectMany { key, value ->
                if (key != "versionArguments" && key != "deployArguments") {
                    return [key]
                } else {
                    return []
                }
            }.join(",")

            _stages.firstFailingStage = _stages.getStage(_SETUP_STAGE_NAME)
            _stages.firstFailingStage.exception =
                new IllegalArgumentException("Unsupported arguments for deploy(Map): [$badArgs]")
        }

        deploy(arguments.deployArguments, arguments.versionArguments)
    }

    /**
     * Deploy a Node JS package.
     *
     * @Stages
     * This will extend the stages provided by the {@link org.zowe.pipelines.generic.GenericPipeline#deployGeneric(java.util.Map, java.util.Map)}
     * method.
     *
     * <dl>
     *     <dt><b>Versioning</b></dt>
     *     <dd>
     *          <p>In a Node JS Pipeline, this stage will always be executed on a protected branch.
     *          When in this stage, the build will determine the possible versions based on the
     *          {@link NodeJSProtectedBranch#prerelease} and {@link NodeJSProtectedBranch#level}
     *          properties.</p>
     *
     *          <p>If the branch is set to {@link NodeJSProtectedBranch#autoDeploy}, then
     *          the default version will be used to publish (with any prerelease strings needed/removed).
     *          Otherwise, an email will be sent out asking what the new version should be. This
     *          email will list the possible versions and give a link back to the build. The build
     *          will wait for one of the {@link #admins} to open the link and select the version.
     *          If the wait period expires, the build will continue as if it was autoDeployed and
     *          will use the default version.</p>
     *
     *          <p>If the build is approved, this new version will be committed to <b>package.json</b>
     *          and the build will continue. Otherwise, the build will stop at this step.</p>
     *
     *          <dl><dt><b>Exceptions:</b></dt><dd>
     *          <dl>
     *              <dt><b>{@link IllegalArgumentException}</b></dt>
     *              <dd>When versionArguments.operation is provided. This is an invalid parameter.</dd>
     *              <dt><b>{@link org.zowe.pipelines.generic.exceptions.DeployStageException}</b></dt>
     *              <dd>
     *                  When no pipeline admins are defined and auto deploy is false. Pipeline admins
     *                  are used as approvers for the build. If there are none, the build can never be
     *                  approved.
     *              </dd>
     *              <dd>
     *                  A timeout of 1 Minute or less was specified. This would result in no wait for the
     *                  version input and is most likely a programming mistake.
     *              </dd>
     *          </dl></dd>
     *     </dd>
     *     <dt><b>Deploy</b></dt>
     *     <dd>
     *         <p>In a Node JS Pipeline, this stage will always be executed on a protected branch.
     *         This stage will execute after the version bump has been completed and is tasked
     *         with doing an {@code npm publish} to the publish registry.</p>
     *
     *         <p>The publish registry is determined by looking in <b>package.json</b> for the
     *         publishConfig.registry property. If this is absent, the deploy will fail. After the
     *         registry is loaded, the pipeline will attempt to login using the specified
     *         {@link #publishConfig}.</p>
     *
     *         <p>Prior to executing the deploy, changes will be pushed to
     *         the remote server. If the pipeline is behind the branch's remote, the push will
     *         fail and the deploy will stop. After changes are successfully pushed, the npm
     *         publish command will be executed with the {@link NodeJSProtectedBranch#tag} specified.
     *         On successful deploy, an email will be sent out to the {@link #admins}.</p>
     *
     *         <p>Note that the local npmrc configuration file will not affect publishing in any way.
     *         This step only considers the configuration parameters provided in {@link #publishConfig}.</p>
     *
     *         <dl><dt><b>Exceptions:</b></dt><dd>
     *         <dl>
     *             <dt><b>{@link IllegalArgumentException}</b></dt>
     *             <dd>When versionArguments.operation is provided. This is an invalid parameter.</dd>
     *         </dl></dd>
     *     </dd>
     * </dl>
     *
     * @param deployArguments The arguments for the Deploy stage.
     * @param versionArguments The arguments for the Version stage.
     */
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

            if (deployArguments.customLogin) {
                deployArguments.customLogin()
            } else {
                def tempRegistry = publishConfig
                // Use releaseRegistryConfig IFF this is not a PR, the branch is protected AND there is no prerelease set for it
                // releaseRegistryConfig hass to be set for us to use it
                if (releaseRegistryConfig && !changeInfo.isPullRequest && protectedBranches.isProtected(changeInfo.branchName) && !protectedBranches.get(changeInfo.branchName).prerelease?.trim()) {
                    tempRegistry = releaseRegistryConfig
                }

                steps.sh "sudo npm config set registry ${tempRegistry.scope ? "${tempRegistry.scope}:" : ""}${tempRegistry.url}"

                // Login to the publish registry
                _loginToRegistry(tempRegistry)
            }

            NodeJSProtectedBranch branch = protectedBranches.get(changeInfo.branchName)

            try {
                // Prevent npm publish from being affected by the local npmrc file
                steps.sh "rm -f .npmrc || exit 0"

                // Clean the work space && Create an production ready environment
                steps.sh "rm npm-shrinkwrap.json || exit 0"
                steps.sh "rm package-lock.json || exit 0"
                steps.sh "npm prune --production --no-package-lock"
                steps.sh "npm shrinkwrap"

                // Install devDependencies to prevent any prepublishOnly from failing
                _processDeps(branch.devDependencies, true)
                steps.sh "npm install --only=dev --no-shrinkwrap"
                steps.sh "npm publish --tag ${branch.tag}"

                sendHtmlEmail(
                    subjectTag: "DEPLOYED",
                    body: "<h3>${steps.env.JOB_NAME}</h3>" +
                        "<p>Branch: <b>${steps.BRANCH_NAME}</b></p>" +
                        "<p>Deployed Package: <b>${steps.env.DEPLOY_PACKAGE}@${steps.env.DEPLOY_VERSION}</b></p>" +
                        "<p>Package Tag: <b>${branch.tag}</b></p>" +
                        "<p>Registry: <b>$publishConfig.url</b></p>",
                    to: admins.emailList,
                    addProviders: false
                )
            } finally {
                // Apply alias tags, even if no new version was published
                try {
                    for (String tag in branch.aliasTags) {
                        steps.sh "npm dist-tag add ${steps.env.DEPLOY_PACKAGE}@${steps.env.DEPLOY_VERSION} ${tag}"
                    }
                } catch (Exception e) {
                    // Do nothing
                }

                // Logout immediately
                _logoutOfRegistry(publishConfig)
                steps.echo "Deploy Complete, please check this step for errors"
            }
        }

        // Prevent versioning stage to be created by deployGeneric if no versionArguments were specified
        if (versionArguments.size() <= 0) {
            super.deployGeneric(deployArguments)
        } else {
            version(versionArguments)
            super.deployGeneric(deployArguments, [:])
        }
    }

    /**
     * Signal that no more stages will be added and begin pipeline execution.
     *
     * <p>The following locations are always archived:</p>
     *
     * <dl>
     *     <dt><b>{@literal /home/jenkins/.npm/_logs}</b></dt>
     *     <dd>This is the log output directory for any npm debug logs.</dd>
     * </dl>
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
     * @Stages
     * This method adds one stage to the build:
     *
     * <dl>
     *     <dt><b>Install Node Package Dependencies</b></dt>
     *     <dd>
     *         <p>
     *             This step will install all your package dependencies via `npm install`. Prior to install
     *             the stage will login to any registries specified in the {@link #registryConfig} array. On
     *             exit, the step will try to logout of the registries specified in {@link #registryConfig}.
     *         </p>
     *         <dl>
     *             <dt><b>Exceptions:</b></dt>
     *             <dd>
     *                 <dl>
     *                     <dt><b>{@link NodeJSPipelineException}</b></dt>
     *                     <dd>
     *                         When two default registries, a registry that omits a url, are specified.
     *                     </dd>
     *                     <dd>
     *                         When a login to a registry fails. <b>Note:</b> Failure to logout of a
     *                         registry will not result in a failed build.
     *                     </dd>
     *                     <dt><b>{@link Exception}</b></dt>
     *                     <dd>
     *                         When a failure to install dependencies occurs.
     *                     </dd>
     *                 </dl>
     *             </dd>
     *         </dl>
     *     </dd>
     * </dl>
     */
    void setup(NodeJSSetupArguments timeouts) {
        super.setupGeneric(timeouts)

        createStage(name: 'Install Node Package Dependencies', stage: {
            try {
                // Keep track of when the default registry is used since it is only allowed to be used once
                def didUseDefaultRegistry = false

                steps.echo "Login to registries"

                if (releaseRegistryConfig) {
                    if (!releaseRegistryConfig.url) {
                        didUseDefaultRegistry = true
                    }
                    _loginToRegistry(releaseRegistryConfig)
                }

                if (registryConfig) {
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
                if (changeInfo.isPullRequest) {
                    branch = changeInfo.baseBranch
                }
                // Otherwise we are checking if the current branch is protected
                else {
                    branch = changeInfo.branchName
                }

                if (protectedBranches.isProtected(branch)) {
                    def branchProps = protectedBranches.get(branch)
                    _processDeps(branchProps.dependencies, false)
                    _processDeps(branchProps.devDependencies, true)

                    // Commits will be avoided on PRs
                    if (!changeInfo.isPullRequest) {
                        // Add package and package lock to the commit tree. This will not fail if
                        // unable to add an item for any reasons.
                        steps.sh "git add package.json package-lock.json --ignore-errors || exit 0"
                        gitCommit("Updating dependencies")
                    }
                }
            } finally {
                // Always try to logout regardless of errors
                steps.echo "Logout of registries"
                if (releaseRegistryConfig) {
                    _logoutOfRegistry(releaseRegistryConfig)
                }
                if (registryConfig) {
                    for (int i = 0; i < registryConfig.length; i++) {
                        _logoutOfRegistry(registryConfig[i])
                    }
                }
            }
        }, isSkippable: false, timeout: timeouts.installDependencies)
    }

    /**
     * Initialize the pipeline.
     *
     * @param timeouts A map that can be instantiated as {@link NodeJSSetupArguments}
     * @see #setup(NodeJSSetupArguments)
     */
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
     * {@link org.zowe.pipelines.generic.GenericPipeline#testGeneric(java.util.Map)} method and will
     * have the following additional operations: <ul>
     *     <li>If {@link org.zowe.pipelines.generic.arguments.TestStageArguments#operation} is not
     *     provided, this method will default to executing {@code npm run test}</li>
     * </ul>
     * </p>
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
     * Process provided dependencies in different approaches depending on the data.
     *
     * @param depName Map containing all dependencies to be processed
     * @param isDevDep Specifies if the function is processing regualr dependencies or devDependencies
     *
     * @Note Dependencies can be processed in two different ways
     * <ul>
     *     <li>Simple format: <code>["@my-org/my-pkg" : "<version-number-OR-pkg-tag>"]</code></li>
     *     <li>Structured format: <code>["my-pkg-description": ["name":"@my-org/my-pkg", "version": "<version-number-OR-pkg-tag>", "registry?":"https://my-registry-URL"]]</code></li>
     * </ul>
     */
    protected void _processDeps(Map<String, Object> deps, Boolean isDevDep) {
        deps.each { depName, depInfo ->
            steps.echo "Installing: ${depName}"
            if (depInfo instanceof CharSequence) {
                // Since this is a string, we just need to do what we did before
                steps.sh "npm install --save${isDevDep ? '-dev' : ''} --save-exact $depName@$depInfo"
            } else {
                // Let's parse the object we got
                def depScope = "${depInfo.name.indexOf('/') >= 0 ? depInfo.name.substring(0, depInfo.name.indexOf('/')) : ''}"
                def depReg = depScope ? "--$depScope:registry=$depInfo.registry" : "--registry=$depInfo.registry"
                steps.sh "npm install --save${isDevDep ? '-dev' : ''} --save-exact $depInfo.name@$depInfo.version ${depInfo.registry ? depReg : ''}"
            }
        }
    }

    /**
     * Login to the specified registry.
     *
     * @param registry The registry to login to
     * @throw {@link NodeJSPipelineException} when either the email address or credentials property
     *         is missing from the specified registry.
     */
    protected void _loginToRegistry(RegistryConfig registry) throws NodeJSPipelineException {
        if (!registry.email) {
            throw new NodeJSPipelineException("Missing email address for registry: ${registry.url ? registry.url : "default"}")
        }
        if (!registry.credentialsId) {
            throw new NodeJSPipelineException("Missing credentials for registry: ${registry.url ? registry.url : "default"}")
        }

        if (!registry.url) {
            steps.echo "Attempting to login to the default registry${registry.scope ? " under the scope: ${registry.scope}" : ""}"
        } else {
            steps.echo "Attempting to login to the ${registry.url} registry${registry.scope ? " under the scope: ${registry.scope}" : ""}"
        }

        // Bad formatting but this is probably the cleanest way to do the expect script
        def expectCommand = """/usr/bin/expect <<EOD
set timeout 60
#npm login command, add whatever command-line arguments are necessary
spawn npm login ${registry.url ? "--registry ${registry.url}" : ""}${registry.scope ? " --scope=${registry.scope}" : ""}
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
            steps.echo "Attempting to logout of the default registry${registry.scope ? " under the scope: ${registry.scope}" : ""}"
        } else {
            steps.echo "Attempting to logout of the ${registry.url} registry${registry.scope ? " under the scope: ${registry.scope}" : ""}"
        }

        try {
            // If the logout fails, don't blow up. Coded this way because a failed
            // logout doesn't mean we've failed. It also doesn't stop any other
            // logouts that might need to be done.
            steps.sh "npm logout ${registry.url ? "--registry ${registry.url}" : ""}${registry.scope ? " --scope=${registry.scope}" : ""}"
        } catch (e) {
            steps.echo "Failed logout but will continue"
        }
    }
}
