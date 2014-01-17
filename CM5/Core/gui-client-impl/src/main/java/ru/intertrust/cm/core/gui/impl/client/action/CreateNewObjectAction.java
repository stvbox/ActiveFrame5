package ru.intertrust.cm.core.gui.impl.client.action;

import ru.intertrust.cm.core.gui.model.ComponentName;
import ru.intertrust.cm.core.gui.model.plugin.FormPluginConfig;
import ru.intertrust.cm.core.gui.model.plugin.FormPluginState;
import ru.intertrust.cm.core.gui.model.plugin.IsDomainObjectEditor;

/**
 * @author Denis Mitavskiy
 *         Date: 23.09.13
 *         Time: 20:03
 */
@ComponentName("create.new.object.action")
public class CreateNewObjectAction extends Action {
    @Override
    public void execute() {
        IsDomainObjectEditor editor = (IsDomainObjectEditor) getPlugin();
        String domainObjectTypeToCreate = editor.getRootDomainObject().getTypeName();
        FormPluginConfig config = new FormPluginConfig(domainObjectTypeToCreate);
        config.setDomainObjectTypeToCreate(domainObjectTypeToCreate);
        final FormPluginState state = editor.getFormPluginState();
        if (state.isToggleEdit() && !state.isEditable()) {
            state.setEditable(true);
        }
        config.setPluginState(editor.getFormPluginState());
        editor.replaceForm(config);
    }

    @Override
    public CreateNewObjectAction createNew() {
        return new CreateNewObjectAction();
    }
}
