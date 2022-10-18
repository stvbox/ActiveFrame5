package ru.runa.wfe.webservice;

import java.util.List;

public class DefinitionAPI {
    public List<WfDefinition> getProcessDefinitionHistory(User user, String processName) {
        return null;
    }

    public WfDefinition deployProcessDefinition(User user, byte[] processDefinition, List<String> singletonList) {
        return null;
    }

    public WfDefinition getLatestProcessDefinition(User user, String processName) {
        return null;
    }

    public WfDefinition redeployProcessDefinition(User user, boolean id, byte[] processDefinition, List<String> singletonList) {
        return null;
    }

    public void undeployProcessDefinition(User user, String processDefinitionId, Object version) {

    }

    public List<WfDefinition> getProcessDefinitions(User user, Object o, boolean b) {
        return null;
    }
}
