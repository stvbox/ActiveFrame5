package ru.intertrust.cm.core.gui.impl.client.action;

import com.google.gwt.user.client.Window;
import ru.intertrust.cm.core.business.api.dto.Value;
import ru.intertrust.cm.core.business.api.util.ValueUtil;
import ru.intertrust.cm.core.gui.api.client.Component;
import ru.intertrust.cm.core.gui.impl.client.plugins.report.ReportPlugin;
import ru.intertrust.cm.core.gui.model.ComponentName;
import ru.intertrust.cm.core.gui.model.action.ActionContext;
import ru.intertrust.cm.core.gui.model.action.ActionData;
import ru.intertrust.cm.core.gui.model.action.GenerateReportActionContext;
import ru.intertrust.cm.core.gui.model.action.GenerateReportActionData;
import ru.intertrust.cm.core.gui.model.form.FormState;

import java.util.Map;

/**
 * @author Lesia Puhova
 *         Date: 25.03.14
 *         Time: 14:38
 */
@ComponentName("generate-report.action")
public class GenerateReportAction extends SimpleServerAction {

    private static final String SEPARATOR = "~";

    @Override
    public void execute() {
        ReportPlugin plugin = (ReportPlugin) getPlugin();
        plugin.updateFormState();

        super.execute();
    }

    @Override
    protected GenerateReportActionContext appendCurrentContext(ActionContext initialContext) {
        ReportPlugin plugin = (ReportPlugin) getPlugin();

        FormState formState = plugin.getFormState();
        GenerateReportActionContext context = (GenerateReportActionContext) initialContext;
        context.setFormState(formState);
        context.setReportName(plugin.getReportName());
        return context;
    }


    @Override
    protected void onSuccess(ActionData result) {
        GenerateReportActionData actionData = (GenerateReportActionData) result;

        String reportName = actionData.getReportName();
        String paramString = buildParamsString(reportName, actionData.getParams());

        String query = com.google.gwt.core.client.GWT.getHostPageBaseURL() + "generate-report?" + paramString;
        Window.open(query, reportName, "");
    }

    private static String buildParamsString(String reportName, Map<String, Value> params) {
        StringBuilder sb = new StringBuilder();
        sb.append("report_name=").append(reportName);
        if (params != null && !params.isEmpty()) {
            for (Map.Entry<String, Value> entry : params.entrySet()) {
                String fieldName = entry.getKey();
                String value = ValueUtil.valueToString(entry.getValue());
                String fieldType = entry.getValue().getFieldType().name();
                sb.append("&")
                        .append(fieldName)
                        .append("=")
                        .append(value)
                        .append(SEPARATOR)
                        .append(fieldType);
            }
        }
        return sb.toString();
    }

    @Override
    public Component createNew() {
        return new GenerateReportAction();
    }
}
