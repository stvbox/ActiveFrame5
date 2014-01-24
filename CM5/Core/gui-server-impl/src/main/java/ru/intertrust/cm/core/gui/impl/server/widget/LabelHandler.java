package ru.intertrust.cm.core.gui.impl.server.widget;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import ru.intertrust.cm.core.business.api.ConfigurationService;
import ru.intertrust.cm.core.business.api.dto.DecimalValue;
import ru.intertrust.cm.core.business.api.dto.LongValue;
import ru.intertrust.cm.core.business.api.dto.TimestampValue;
import ru.intertrust.cm.core.business.api.dto.Value;
import ru.intertrust.cm.core.config.FieldConfig;
import ru.intertrust.cm.core.config.gui.form.widget.*;
import ru.intertrust.cm.core.gui.api.server.widget.LabelRenderer;
import ru.intertrust.cm.core.gui.api.server.widget.SingleObjectWidgetHandler;
import ru.intertrust.cm.core.gui.api.server.widget.WidgetContext;
import ru.intertrust.cm.core.gui.model.ComponentName;
import ru.intertrust.cm.core.gui.model.form.FieldPath;
import ru.intertrust.cm.core.gui.model.form.widget.LabelState;
import ru.intertrust.cm.core.gui.model.form.widget.WidgetState;

import java.text.SimpleDateFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Denis Mitavskiy
 *         Date: 14.09.13
 *         Time: 17:04
 */
@ComponentName("label")
public class LabelHandler extends SingleObjectWidgetHandler {

    @Autowired
    protected ConfigurationService configurationService;

    @Autowired
    private ApplicationContext applicationContext;

    @Override
    public LabelState getInitialState(WidgetContext context) {
        FieldPath[] fieldPaths = context.getFieldPaths();
        LabelConfig labelConfig = context.getWidgetConfig();
        LabelState state = new LabelState();
        setStylesFromConfig(labelConfig, state);
        String textFromConfig = labelConfig.getText();
        if (textFromConfig != null) {
            state.setLabel(textFromConfig);
        }
        if (fieldPaths[0] != null) {
            RendererConfig renderer = labelConfig.getRenderer();
            String rendererName = renderer == null ? null : renderer.getValue();
            if (rendererName != null){
                LabelRenderer customLabelRenderer = (LabelRenderer) applicationContext.getBean(rendererName);
                String composedText = customLabelRenderer.composeString(fieldPaths, context);
                state.setLabel(composedText);
            } else {
            AllValuesEmptyMessageConfig allValuesEmptyMessage = labelConfig.getAllValuesEmptyMessage();
            String allValuesEmpty = allValuesEmptyMessage == null ? "" : allValuesEmptyMessage.getValue();
            String formattedString = format(labelConfig.getPattern().getValue(), fieldPaths, context, allValuesEmpty);
            state.setLabel(formattedString);
            }

        }
        state.setRelatedToRequiredField(isRelatedFieldRequired(context, labelConfig));
        return state;
    }

    @Override
    public Value getValue(WidgetState state) {
        return null;
    }

    private String format(String configPattern, FieldPath[] fieldPaths, WidgetContext context, String allValuesEmpty ) {
        final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("dd.MM.yyyy");
        String displayPattern = configPattern == null ? buildDefaultPattern(fieldPaths) : configPattern;
        Pattern pattern = Pattern.compile("\\{[\\w.]+\\}");
        Matcher matcher = pattern.matcher(displayPattern);
        StringBuffer replacement = new StringBuffer();
        boolean allEmpty = true;
        while (matcher.find()) {
            String group = matcher.group();
            FieldPath fieldPath = new FieldPath(group.substring(1, group.length() - 1));
            Value value = context.getValue(fieldPath);
            String displayValueUnescaped = "";
            if (value != null) {
                Object primitiveValue = value.get();
                if (primitiveValue == null) {
                    if (value instanceof LongValue || value instanceof DecimalValue) {
                        displayValueUnescaped = "0";
                    }
                } else {
                    allEmpty = false;
                    if (value instanceof TimestampValue) {
                        displayValueUnescaped = DATE_FORMATTER.format(primitiveValue);
                    } else {
                        displayValueUnescaped = primitiveValue.toString();
                    }
                }
            }
            String displayValue = displayValueUnescaped.replaceAll( "\\\\", "\\\\\\\\").replaceAll("\\$", "\\\\\\$");
            matcher.appendReplacement(replacement, displayValue);
        }
        if (allEmpty) {
            return allValuesEmpty;
        }

        matcher.appendTail(replacement);
        matcher.reset();
        return replacement.toString();
    }

    private String buildDefaultPattern(FieldPath[] fieldPaths) {
        StringBuilder pattern = new StringBuilder(fieldPaths.length * 5);
        for (int i = 0; i < fieldPaths.length; ++i) {
            FieldPath fieldPath = fieldPaths[i];
            if (i != 0) {
                pattern.append(", ");
            }
            pattern.append('{').append(fieldPath.getPath()).append('}');
        }
        return pattern.toString();
    }

    private boolean isRelatedFieldRequired(WidgetContext context, LabelConfig labelConfig) {
        if (labelConfig.getRelatesTo() != null) {
            String relatedWidgetId = labelConfig.getRelatesTo().getWidgetId();
            WidgetConfig relatedConfig = context.getWidgetConfigById(relatedWidgetId);
            FieldPath relatedFieldPath = new FieldPath(relatedConfig.getFieldPathConfig().getValue());
            String field = relatedFieldPath.getFieldName();
            String objectType = context.getFormObjects().getNode(relatedFieldPath.getParentPath()).getType();
            FieldConfig fieldConfig = configurationService.getFieldConfig(objectType, field);
            if (fieldConfig.isNotNull()) {
                return true;
            }
        }
        return false;
    }

    private void setStylesFromConfig(LabelConfig labelConfig, LabelState state) {
        FontStyleConfig fontStyleConfig = labelConfig.getFontStyle();
        FontWeightConfig fontWeightConfig = labelConfig.getFontWeight();
        FontSizeConfig fontSizeConfig = labelConfig.getFontSize();
        String fontSize = fontSizeConfig == null ? null : fontSizeConfig.getValue();
        String fontStyle = fontStyleConfig == null ? null : fontStyleConfig.getValue();
        String fontWeight = fontWeightConfig == null ? null : fontWeightConfig.getValue();
        state.setFontSize(fontSize);
        state.setFontStyle(fontStyle);
        state.setFontWeight(fontWeight);

    }
}
