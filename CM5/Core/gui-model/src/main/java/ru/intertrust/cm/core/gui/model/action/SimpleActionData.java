package ru.intertrust.cm.core.gui.model.action;

import ru.intertrust.cm.core.gui.model.plugin.FormPluginData;

/**
 * @author Sergey.Okolot
 *         Created on 02.10.2014 17:52.
 */
public class SimpleActionData extends ActionData {

    private FormPluginData pluginData;

    public FormPluginData getPluginData() {
        return pluginData;
    }

    public void setPluginData(FormPluginData pluginData) {
        this.pluginData = pluginData;
    }
}
