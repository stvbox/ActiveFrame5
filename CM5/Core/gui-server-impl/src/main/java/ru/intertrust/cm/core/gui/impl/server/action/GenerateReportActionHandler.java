package ru.intertrust.cm.core.gui.impl.server.action;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import ru.intertrust.cm.core.business.api.dto.Value;
import ru.intertrust.cm.core.config.ConfigurationExplorer;
import ru.intertrust.cm.core.config.gui.form.FormConfig;
import ru.intertrust.cm.core.config.gui.form.widget.WidgetConfig;
import ru.intertrust.cm.core.config.gui.form.widget.WidgetConfigurationConfig;
import ru.intertrust.cm.core.gui.api.server.GuiService;
import ru.intertrust.cm.core.gui.api.server.action.ActionHandler;
import ru.intertrust.cm.core.gui.api.server.widget.WidgetHandler;
import ru.intertrust.cm.core.gui.model.ComponentName;
import ru.intertrust.cm.core.gui.model.action.ActionContext;
import ru.intertrust.cm.core.gui.model.action.GenerateReportActionContext;
import ru.intertrust.cm.core.gui.model.action.GenerateReportActionData;
import ru.intertrust.cm.core.gui.model.form.FieldPath;
import ru.intertrust.cm.core.gui.model.form.FormState;
import ru.intertrust.cm.core.gui.model.form.widget.WidgetState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Lesia Puhova
 *         Date: 25.03.14
 *         Time: 15:05
 */
@ComponentName("generate-report.action")
public class GenerateReportActionHandler extends ActionHandler {

    @Autowired
    private GuiService guiService;
    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private ConfigurationExplorer configurationExplorer;

    @Override
    public GenerateReportActionData executeAction(ActionContext context) {
        GenerateReportActionContext reportContext = (GenerateReportActionContext) context;
        FormState formState = reportContext.getFormState();

        GenerateReportActionData actionData = new GenerateReportActionData();
        actionData.setReportName(reportContext.getReportName());
        actionData.setParams(buildParamsString(formState));
        return actionData;
    }

    private Map<String, Value> buildParamsString(FormState formState) {
        Map<String, Value> params = new HashMap<String, Value>();

        List<WidgetConfig> widgetConfigs = getWidgetConfigs(formState);
        for (WidgetConfig widgetConfig : widgetConfigs) {
            FieldPath[] fieldPaths = FieldPath.createPaths(widgetConfig.getFieldPathConfig().getValue());
            FieldPath firstFieldPath = fieldPaths[0];
            if (firstFieldPath == null) {
                continue;
            }
            String paramName = firstFieldPath.getFieldName();

            WidgetState widgetState = formState.getWidgetState(widgetConfig.getId());
            if (widgetState == null) {
                continue;
            }
            WidgetHandler handler = getWidgetHandler(widgetConfig);
            Value value = handler.getValue(widgetState);

            params.put(paramName, value);
        }
        return params;
    }

    private List<WidgetConfig> getWidgetConfigs(FormState formState) {
        FormConfig formConfig = configurationExplorer.getConfig(FormConfig.class, formState.getName());
        WidgetConfigurationConfig widgetConfigurationConfig = formConfig.getWidgetConfigurationConfig();
        return widgetConfigurationConfig.getWidgetConfigList();
    }

    private WidgetHandler getWidgetHandler(WidgetConfig config) {
        return (WidgetHandler) applicationContext.getBean(config.getComponentName());
    }
}
