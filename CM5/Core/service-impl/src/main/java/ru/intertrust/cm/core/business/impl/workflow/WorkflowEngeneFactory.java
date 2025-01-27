package ru.intertrust.cm.core.business.impl.workflow;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import ru.intertrust.cm.core.business.api.workflow.WorkflowEngine;
import ru.intertrust.cm.core.model.FatalException;

/**
 * Фабрика движка workflow
 */
public class WorkflowEngeneFactory {
    private static final Logger logger = Logger.getLogger(WorkflowEngeneFactory.class);

    @Value("${workflow.engine:" + FlowableWorkflowEngineImpl.ENGENE_NAME + "}")
    private String workflowEngene = FlowableWorkflowEngineImpl.ENGENE_NAME;

    public WorkflowEngine getObject() throws Exception {
        WorkflowEngine result = null;
        if (workflowEngene.equals(FlowableWorkflowEngineImpl.ENGENE_NAME)){
            result = new FlowableWorkflowEngineImpl();
        }else if(workflowEngene.equals(RunaWorkflowEngineImpl.ENGENE_NAME)){
            result = new RunaWorkflowEngineImpl();
        }else{
            throw new FatalException("Not support " + workflowEngene + " workflow engine");
        }
        logger.info("Activate " + result.getEngeneName() + " workflow engene");
        return result;
    }
}
