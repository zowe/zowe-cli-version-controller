package org.zowe.pipelines.nodejs.models
class BuildArgs extends StageArgs {
    String output = "./lib/"
    String name = "Source"
    Closure buildOperation
}
