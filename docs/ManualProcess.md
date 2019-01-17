# Manual Process
This article is intended to describe in detail how to maintain the current versioning scheme without the need of automation. Some steps may be scripted and placed in the [scripts] folder

Here is what you need to do for each repository.
- Create GitHub branches (lts-stable, lts-incremental, latest, beta)
- Manually publish the package located in the corresponding Artifactory and/or add the distribution tags to the corresponding version numbers based on [this document](https://docs.google.com/spreadsheets/d/1PsSn1Yvs6L-uh8Y86D9_P5JMbj_2Lv-AOr5BtjXR3H8/edit?usp=sharing)
- Verify the compatibility at each NPM tag level (e.g. core: `lts-stable` with plug-in XYZ: `lts-stable`)

[scripts]: /scripts/