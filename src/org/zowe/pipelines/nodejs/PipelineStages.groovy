package org.zowe.pipelines.nodejs

import org.zowe.pipelines.nodejs.models.*
import org.zowe.pipelines.nodejs.exceptions.*

public class PipelineStages {
    private HashMap<String, Stage> _stages = new HashMap()

    private Stage _lastAddedStage
    private Stage _firstStageToExecute
    private Stage _firstFailingStage

    public Stage getFirstStageToExecute() {
        return _firstStageToExecute
    }

    public Stage getFirstFailingStage() {
        return _firstFailingStage
    }

    public void setFirstFailingStage(Stage stage) {
        // Only set the first failing stage if it is currently empty. All other calls
        // will be treated as nothing happened
        if (!_firstFailingStage) {
            if (!_stages.containsValue(stage)) {
                throw new StageException("First failing stage cannot be set to a stage absent from the map.", stage.name)
            }

            _firstFailingStage = stage
        }
    }
    

    public PipelineStages add(Stage stage) {
        // First validate if the stage name doesn't exist
        if (_stages.containsKey(stage.name)) {
            if (_firstStageToExecute == null) {
                // This is a condition that indicates that our logic is most likely broken
                throw new StageException("First stage was not set but stages already had values in the map", stage.name)
            } else if (!_firstFailingStage){
                // The first stage should be setup, othewise a stage exception will be
                // thrown before we get into here. So in setup, we should create the exception
                // to be thrown later.
                _firstFailingStage = _firstStageToExecute
                _firstFailingStage.exception = new StageException("Duplicate stage name: \"${stage.name}\"", _firstFailingStage.name)
            } 
        } else {
            // Add stage to map
            _stages.put(stage.name, stage)
        }

        // Set the next stage from the current stage
        if (_lastAddedStage) {
            _lastAddedStage.next = stage
        }

        // If the first stage hasn't been created yet, set it here
        if (!_firstStageToExecute) {
            _firstStageToExecute = stage
        }

        // Set the new current stage to this stage
        _lastAddedStage = stage

        // Return this for chaining
        return this
    }

    public getStage(String stageName) {
        return _stages.get(stageName)
    }

    public execute() {
        Stage stage = _firstStageToExecute

        // Loop while we have a stage
        while (stage) {
            // Execute the stage
            stage.execute()

            // Move to the next stage
            stage = stage.next
        }
    }

    public int size() {
        return _stages.size()
    }
}