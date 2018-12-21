# Versioning Pipeline

This article describes what the controller pipeline does as well as how it influences the Zowe CLI and Imperative respective Jenkinsfiles. The topics to be covered in this article explain various aspects that encompass our pipelines and how the versioning scheme is maintained based on tag migrations.

**Table of Contents**

* [GitHub Setup](#github-setup)
* [Environment Setup](#environment-setup)
* [Variables](#variables)
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

## Environment Setup



## Variables



## Protected Branches



### master



### beta



### latest



### LTS Branches



#### lts-incremental



#### lts-stable



## Tag Migration



## Important Notes

