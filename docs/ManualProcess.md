# Manual Process
This article describes how to maintain your versioning scheme without using automation.

## Prepare the Repositories

Complete the following tasks before you release a GA version of your product:

- Create the following Github branches:
    - `lts-stable`
    - `lts-incremental`
    - `latest`
    - `beta`
- Manaully publish the package that is located in the corresponding Artifactory. Add the required distribution tags to the corresponding version numbers. Use the guidelines for packages that are described in the following document: [Versions numbers](https://docs.google.com/spreadsheets/d/1PsSn1Yvs6L-uh8Y86D9_P5JMbj_2Lv-AOr5BtjXR3H8/edit?usp=sharing).
- Verify the compatibility at each NPM tag level. For example, core: `lts-stable` with plug-in *XYZ*: `lts-stable`.

## Maintain Versioning Schemes

Use the following guidelines to maintain your versioning scheme:

- When you merge a pull request into the master branch, publish an `@daily` version with a pre-release string of `-alpha.<timestamp>` off the master branch.
- At the end of sprints, complete the following tasks:
  - Take the previously published `@beta` version number, remove the pre-release string and tag it with `@latest`.
  - Take a snapshot of the master branch (or the last version tagged `@daily`) and publish an `@beta` version with a pre-release string of `-beta.<timestamp>`.
- When your PM/PO decides to port features and fixes to LTS branches, complete the following tasks:
  - Port the specified code into the corresponding LTS branch. For example, port features to only `lts-incremental` and fixes to only `lts-incremental` and `lts-stable`).
  - Increase the version number accordingly.
    - When you merge features into the `lts-incremental` branch, the `minor` version increases by one.
    - When you merge fixes into the `lts-stable` or `lts-incremental` branch, increment only the `patch` number by one.
  - Publish the new version of the product with the corresponding tag. For example, `@lts-incremental`, `@lts-stable`.

    **Note:** You might encounter situations where the package version was published previously. When this situation occurs, you need to only update or move the tags around.