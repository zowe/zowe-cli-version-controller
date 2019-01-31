# Jenkins Pipeline

This article describes what the controller pipeline does as well as how it influences the Zowe CLI and Imperative respective Jenkinsfiles. The topics to be covered in this article explain various aspects that encompass our pipelines and how the versioning scheme is maintained based on tag migrations.

**Table of Contents**

* [GitHub Setup](#github-setup)
* [Variables](#variables)
* [Environment Setup](#environment-setup)
* [Protected Branches](#protected-branches)
  * [master](#master)
  * [beta](#beta)
  * [latest](#latest)
  * [LTS Branches](#lts-branches)
    * [lts-incremental](#lts-incremental)
    * [lts-stable](#lts-stable)
* [Tag Migration](#tag-migration)
* [Important Notes](#important-notes)

## GitHub Setup

This section is intended to explain at a high level what is required, in terms of Source Control Management (SCM), so that our versioning scheme functions properly.

Zowe has generic users for automation, also known as Robots. For our pipelines to work, we need to give these users administrator-level authority to the repositories we want them to control. Don't forget to give your team write-access to the repository too.

In an attempt to prevent human error while maintaining a proper versioning workflow, we need to protect certain branches that the automation server (Jenkins in our case) uses for the builds that produce deliverables. However, we still want our Robots to directly publish to such branches in order to make version updates or auto-generated documentation changes (if applicable). Please see the [Protected Branches](#protected-branches) section for more information.

Jenkins will also need to know when there is a code change so it can trigger the builds. However, we want to prevent the automation server from using what is known as the polling system. In order to do this, we need to configure the appropriate webhooks for GitHub to tell Jenkins that it is ready to build after a push event occurs. For example, a code change is merged into a protected branch. In Jenkins, there is a plug-in for GitHub ([GitHub Plugin](https://wiki.jenkins.io/display/JENKINS/GitHub+Plugin)) which allows said configuration.

## Variables

This section describes the special variables contained in most Jenkinsfiles on our repositories. Some variables will have a star (**`*`**) next to them indicating it should be parameterized.

* **MASTER_BRANCH`*`**: Contains the branch name that should be treated as the "master" branch of the entire source. This variable could be changed to a development branch in case someone wanted to test specific tasks that Jenkins performs only when it builds the real `master` branch.

* **MASTER_RECIPIENTS`*`**: Contains a list of emails used to notify people about the status of the current build.

* **Test storage locations:** This set of variables contain the location to where various types of tests results will be stored.

  * **UNIT_RESULTS**

  * **INTEGRATION_RESULTS**

  * **SYSTEM_RESULTS**

* **Pipeline Control variables`*`:** The purpose of this set of variables is to manage whether or not the specified step should run. There are two ways of specifying these variables, either one by one with their specific name and value or with special Linux-like options (similar to the [*tar* command](http://man7.org/linux/man-pages/man1/tar.1.html)) passed to a variable called `PIPELINE_CONTROL`. Each variable below has a **generic representation** which is the value required to be passed in to `PIPELINE_CONTROL`.

  * **PIPELINE_CONTROL_CI_SKIP`*`** 
    * Default: **true**
    * Generic representation: **K**

  * **PIPELINE_CONTROL_BUILD`*`**
    * Default: **true**
    * Generic representation: **B**

  * **PIPELINE_CONTROL_UNIT_TEST`*`**
    * Default: **true**
    * Generic representation: **U**

  * **PIPELINE_CONTROL_INTEGRATION_TEST`*`**
    * Default: **true**
    * Generic representation: **I**

  * **PIPELINE_CONTROL_SYSTEM_TEST`*`**
    * Default: **true**
    * Generic representation: **S**

  * **PIPELINE_CONTROL_DEPLOY`*`**
    * Default: **true**
    * Generic representation: **D**

  * **PIPELINE_CONTROL_SMOKE_TEST`*`**
    * Default: **true**
    * Generic representation: **T**

  * **PIPELINE_CONTROL`*`**
    * Default: **`(empty)`**
    * The purpose of this variable is to minimize the effort of having to specify every control variable separately. A few common values for this variable are:
      * **buis**: Builds and (Unit-, Integration-, System-) tests the code.
      * **bdt**: Builds, Deploys and Smoke tests the code
      * **kbuisdt**: Does it all. (most common setup for the `master` branch)

* **Robot information:** This set of variables contain information about the robot user that will manage the pipeline in terms of publishing new versions or making documentation updates on GitHub.
  * **ROBOT_NAME`*`**
  * **ROBOT_EMAIL`*`**
  * **ROBOT_CREDENTIAL_ID`*`**

  * **ROBOT_INFO_ID`*`** (special jenkins object that contains all of the above)

* **Git Repository URL:`*`** This variable contains the URL to the corresponding git project. This variable could be removed in case this information can be extracted from a runtime environmental variable.

* **Git Revision Lookup:** This variable contains the *git* command used to verify if the current commit matches with what was previously built by the pipeline. This git command can be hard-coded into a common function.

* **Registry Administrator information`*`** This set of variables contain information about the user with publishing permissions to the desired registry.
  * **REGISTRY_URL:`*`** The URL of the registry used for publishing. Usually specified in the publishConfig property of the package.json file.
  * **REGISTRY_ADMIN_CREDENTIAL_ID:`*`** The ID used by Jenkins to store the admin user credentials on the registry. 
  * **REGISTRY_ADMIN_EMAIL:`*`** The user email used for publishing to the registry.

## Environment Setup

This section cover the basic aspects of how the pipeline environment should be structured. First and foremost, almost all variables should be parameterized to allow flexibility on each step for all pipelines using this scheme and all defaults should be provided by the pipelines themselves.

Since memory could become an issue given that we plan to release the beta version frequently, we want to limit the number of builds kept in memory for the release branches as well as any other branch on a given repository. For release branches, e.g. `master`, `beta`, `latest`, we will keep only twenty (20) builds and will disable concurrent builds to avoid any issues and complications that can arise because of this. For regular branches, such as fixes or enhancements/features, we will keep only five (5) builds and concurrent builds should be allowed to provide a smooth developing experience in terms of Continuous Integration (CI).

Also, we will implement a containerization system based on Docker. This allows pipelines to run in controlled environments and possibly perform destructive operations without harming the host machine.

## Protected Branches

This section explains in details the purpose of each branch and their corresponding build strategy. The following branches are considered protected as only administrators and robots can and will have the authority to push changes directly to them in order to guarantee a successful Continuous Integration / Continuous Delivery (CI/CD) cycle. Please refer to the [tag names](https://github.com/zowe/zowe-cli/blob/master/docs/MaintainerVersioning.md#npm-tag-names) for more information about the specific NPM tags that will be mentioned in this section.

### master

The `master` branch is the first step in our versioning scheme. All code goes through this branch at some point in time. Since it's protected, only a approved Pull Request (PR) can modify its source. Every PR that gets merged will finally be published with an `@daily` tag. The version number should **not** be manually changed or else the PR will be rejected since the version is controlled by an email mechanism described in the [Tag Migration](#tag-migration) section.

### beta

The `beta` branch is the next step in the cycle. It shall build every two weeks with the intention to send out emails to ask for what the appropriate coming beta version should be. For instance, if the current `@beta` version is `3.5.1-alpha.TIME1`, an email will be sent asking what the version number should be, `3.5.2-beta.TIME2`, `3.6.0-beta.TIME2`, or `4.0.0-beta.TIME2`, and the user will determine and take action based on the desired version to be published.

### latest

The `latest` branch is the container of the most recent Community Edition (CE) version of the given project. The controller will also trigger these builds every two weeks after the beta builds with the intention to publish what was previously marked as `@beta` before the new version is specified via email. Following the previous example, we will publish a `@latest` version of `3.5.1`. Notice that there is no pre-release string on the latest versions since this is what the final user and even plug-in developers will want to use.

### LTS Branches

The purpose of the Long Term Support (LTS) branches is to provide a more stable releases of the product. The following branches have been created to house the said releases.

#### lts-incremental

The `lts-incremental` branch is where **only** non-breaking backward-compatible enhancements and bug fixes are allowed. This provides users with the ability to receive new functionality while maintaining a version that guarantees zero negative effects on what they previously had. For example, the version could `3.5.1`  which corresponds to the NPM tag `lts-incremental-v3` and should only increment the `minor` and `patch` level.

The build is triggered any time code gets merged into the branch and since it's a protected branch, we enforce the rule that only approved PRs can add changes. When a PR is opened, the major version of the package are compared against `<major>` in the most recent `@lts-incremental-v<major>` NPM tag and fail the PR if the major version has changed. This will ensure that the major version never increases, thus ensuring no breaking changes are introduced into the branch. The PR will also fail when the version specified has already been published.

#### lts-stable

The `lts-stable` branch is where **only** bug fixes are allowed. This provides users with a non-changing version that guarantees that no new features are applied but only patches. For example, the version `3.5.1` corresponds to the NPM tag `lts-stable-v3.5` and should only increment the `patch` level.

Similar to `lts-incremental`, the build is triggered any time changes are merged into the branch. This branch also enforces approved PRs as the only way to make changes. When a PR is opened, the major and minor versions of the package are compared against `<major>` and `<minor>` in the most recent `@lts-stable-v<major>.<minor>` NPM tag and fail the PR if the major or minor versions have changed. This will ensure that the major or minor version never increase, thus ensuring no features are introduced into the branch.

Since this branch only accepts bug fixes (or patch increments), PRs will also fail if the patch version is changed manually. The version will automatically bump after a successful build.

## Tag Migration

This section covers how the tags corresponding to each protected branch gets moved into the next level of delivery. There will be an email mechanism that will notify and ask users for action on what version number to publish. The email may contain various options as described in the `beta` branch section. After the user replies or clicks a link/button, the controller receives the action and the CD process begins.

First, we take the most current snapshot contained on the `beta` branch (which is tagged `@beta`) and move the code to the `latest` branch, which then triggers a build generating a new `latest` release (also known as CE). Then we move the most current snapshot of `master` and move it to `beta`, triggering the beta deployment as well.

In terms of LTS branches, the tag migration works a little different given that the release cycle is at Product Management discretion and they decide when should the next incremental or stable version be available. There will be a build button in the jenkins machine to facilitate the release effort once the decision is made. The build process executes the following steps:
  1. Rename the `lts-stable` branch to `lts-stable-v<major>.<minor>.<patch>` in case we need to go back to that specific stable release. It can be deleted later if needed.
  2. Rename the `lts-incremental` branch to `lts-stable`. This will trigger CI/CD on the lts-stable branch. The CD build will be skipped if the version already exists.
  3. Move the `@lts-stable` NPM tag to `@lts-incremental` tag version. This is needed because the version that `@lts-incremental` is pointing to, has already been published to the registry and cannot be republished.
  4. Create a new `lts-incremental` branch based off of `latest`. This will trigger CI/CD on the lts-incremental branch. The CD build will be skipped if the version already exists.
  5. Move the `@lts-incremental` NPM tag to `@latest` tag version. Similar to number 3, this is needed because the version that `@latest` is pointing to, has already been published and cannot be republished.

The tag migration process for the LTS branches should require additional authorization to complete. This is to prevent any accidental trigger of such process. Jenkins will send an email out to a list of approvers and **ALL** or **x%** (a given percentage) must confirm the action before the pipeline continues. Anyone on the list can **DENY** the action which will cancel the LTS tag migration regardless of the number of approvers.

## Template Steps

TODO: This section will be modified as we work on [issue 139 of Zowe CLI](https://github.com/zowe/zowe-cli/issues/139)

## Important Notes

- Test storage locations should be sent in as a default from each script
  - We should account for someone that doesn't use JEST for example.

- Pipeline should not call any special scripts (e.g jest something or typedoc or gulp).
  - All scripts should be defined in an npm run script.
  - This allows the template steps to call well-structured scripts on the project.

- Handle Zowe CLI/imperative builds
  - Zowe requires npm tag `@<imperative-git-branch-name>` so builds of the protected branches should always install and save to the package json of this version.
  - We need to do npm install then npm install imperative@tag --save and commit that before publishing when in protected branches.
  - This implies that the biweekly builds have knowledge about both builds and first builds imperative then Zowe CLI.
  
