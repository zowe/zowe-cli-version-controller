# Manual Process
This article is intended to describe in detail how to maintain the current versioning scheme without the need of automation.

Here is what you need to do for each repository as the first step for going GA.
- Create GitHub branches (lts-stable, lts-incremental, latest, beta)
- Manually publish the package located in the corresponding Artifactory and/or add the distribution tags to the corresponding version numbers based on [this document](https://docs.google.com/spreadsheets/d/1PsSn1Yvs6L-uh8Y86D9_P5JMbj_2Lv-AOr5BtjXR3H8/edit?usp=sharing)
- Verify the compatibility at each NPM tag level (e.g. core: `lts-stable` with plug-in XYZ: `lts-stable`)

Here is how to maintain the versioning scheme:
- When: A PR gets merged into the master branch
  - Publish a `@daily` version with a pre-release string of `-alpha.<timestamp>` off of master.
- When: End of sprint
  - Take the previously published `@beta` version number, remove the pre-release string and tag it `@latest`
  - Take a snapshot of the master branch (or the last version tagged `@daily`) and publish a `@beta` version with a pre-release string of `-beta.<timestamp>`
- When: PM/PO decides to port features and fixes to LTS branches
  - Port the specified code to the corresponding LTS branch (e.g. features only to `lts-incremental`, fixes to `lts-incremental` and `lts-stable`)
  - Bump the version number accordingly.
    - If features get merged into the `lts-incremental` branch, then the `minor` version will increase by one
    - If only fixes are merged to either `lts-stable` or `lts-incremental`, then only the `patch` number should increment by one
  - Publish the new version of the product with the corresponding tag (e.g. `@lts-incremental`, `@lts-stable`)
    - There may be a scenario where the package version has already been publish, in which case you only need to update/move the tags around.

