
class StageArgs { // @TODO Stage minimum build health (if build health is >= to this minimum, continue with the stage else skip)
    String name
    Closure stage
    boolean isSkipable = true

    // Setting this to true will cause the stage to ignore the value of _shouldSkipRemainingStages
    boolean doesIgnoreSkipAll = false
    StageTimeout timeout = [:]
    Closure shouldSkip = { -> false }
    Map<String, String> environment

    // The current health of the build must be this or better for the step to execute
    ResultEnum resultThreshold = ResultEnum.SUCCESS
}
