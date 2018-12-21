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

This sections describes the special variables contained in most Jenkinsfiles on our repositories. Some variables will have a start (**`*`**) next to them indicating it should be parameterized.

* **MASTER_BRANCH`*`**: Contains the branch name that should be treated as the "master" branch of the entire source. This variable could be changed to a development branch in case someone wanted to test specific tasks that Jenkins performs only when it builds the real `master` branch.

* **MASTER_RECIPIENTS`*`**: Contains a list of emails used to notify people about the status of the current build.

* **Test storage locations:** This set of variables contain the location to where various types of tests results will be stored.

  * **UNIT_RESULTS**

  * **INTEGRATION_RESULTS**

  * **SYSTEM_RESULTS**

* **Pipeline Control variables:** The purpose of this set of variables is to manage whether or not the specified step should run. There are two ways of specifying these variables, either one by one with their specific name and value or with special Linux-like options (similar to the [*tar* command](http://man7.org/linux/man-pages/man1/tar.1.html)) passed to a variable called `PIPELINE_CONTROL`. Each variable below has a **generic representation** which is the value used required to be passed in to `PIPELINE_CONTROL`.

  * **PIPELINE_CONTROL_CI_SKIP** 
    * Default: **true**
    * Generic representation: **K**

  * **PIPELINE_CONTROL_BUILD**
    * Default: **true**
    * Generic representation: **B**

  * **PIPELINE_CONTROL_UNIT_TEST**
    * Default: **true**
    * Generic representation: **U**

  * **PIPELINE_CONTROL_INTEGRATION_TEST**
    * Default: **true**
    * Generic representation: **I**

  * **PIPELINE_CONTROL_SYSTEM_TEST**
    * Default: **true**
    * Generic representation: **S**

  * **PIPELINE_CONTROL_DEPLOY**
    * Default: **true**
    * Generic representation: **D**

  * **PIPELINE_CONTROL_SMOKE_TEST**
    * Default: **true**
    * Generic representation: **T**

  * **PIPELINE_CONTROL**
    * Default: **`(empty)`**
    * The purpose of this variable is to minimize the effort of having to specify every control variable separately. A few common values for this variable are:
      * **buis**: Builds and (Unit-, Integration-, System-) tests the code.
      * **bdt**: Builds, Deploys and Smoke tests the code
      * **kbuisdt**: Does it all. (most common setup for the `master` branch)

* **Robot information:** This set of variables contain the information about the robot user that will manage the pipeline in terms of publishing new versions or making documentation updates on GitHub.
  * **ROBOT_NAME**
  * **ROBOT_EMAIL**
  * **ROBOT_CREDENTIAL_ID**

  * **ROBOT_INFO_ID** (special jenkins object that contains all of the above)



## Environment Setup



## Protected Branches



### master



### beta



### latest



### LTS Branches



#### lts-incremental



#### lts-stable



## Tag Migration



## Important Notes

