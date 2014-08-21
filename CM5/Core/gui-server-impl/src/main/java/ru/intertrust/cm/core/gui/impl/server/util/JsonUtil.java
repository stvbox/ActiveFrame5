package ru.intertrust.cm.core.gui.impl.server.util;

import ru.intertrust.cm.core.config.gui.form.widget.filter.AbstractFilterConfig;
import ru.intertrust.cm.core.config.gui.form.widget.filter.ParamConfig;
import ru.intertrust.cm.core.config.gui.navigation.InitialFilterConfig;
import ru.intertrust.cm.core.config.gui.navigation.InitialFiltersConfig;
import ru.intertrust.cm.core.config.gui.navigation.SortCriteriaConfig;
import ru.intertrust.cm.core.config.gui.navigation.SortCriterionConfig;
import ru.intertrust.cm.core.gui.model.CollectionColumnProperties;
import ru.intertrust.cm.core.gui.model.csv.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Yaroslav Bondarchuk
 *         Date: 09.04.14
 *         Time: 16:15
 */
public class JsonUtil {

    private static CollectionColumnProperties convertToColumnProperties(JsonColumnProperties jsonProperties) {
        CollectionColumnProperties properties = new CollectionColumnProperties();
        String filterName = jsonProperties.getFilterName();
        properties.addProperty(CollectionColumnProperties.SEARCH_FILTER_KEY, filterName);
        String fieldName = jsonProperties.getFieldName();
        properties.addProperty(CollectionColumnProperties.FIELD_NAME, fieldName);
        String fieldType = jsonProperties.getFieldType();
        properties.addProperty(CollectionColumnProperties.TYPE_KEY, fieldType);
        String timeZoneId = jsonProperties.getTimeZoneId();
        properties.addProperty(CollectionColumnProperties.TIME_ZONE_ID, timeZoneId);
        String datePattern = jsonProperties.getDatePattern();
        properties.addProperty(CollectionColumnProperties.DATE_PATTERN, datePattern);
        String timePattern = jsonProperties.getTimePattern();
        properties.addProperty(CollectionColumnProperties.TIME_PATTERN, timePattern);
        String columnName = jsonProperties.getColumnName();
        properties.addProperty(CollectionColumnProperties.NAME_KEY, columnName);
        List<String> initialFilterValues = jsonProperties.getInitialFilterValues();
        properties.addProperty(CollectionColumnProperties.INITIAL_FILTER_VALUES, initialFilterValues);
        return properties;

    }

    public static Map<String, CollectionColumnProperties> convertToColumnPropertiesMap(
            List<JsonColumnProperties> jsonPropertiesList) {
        Map<String, CollectionColumnProperties> columnPropertiesMap = new LinkedHashMap<String, CollectionColumnProperties>();
        for (JsonColumnProperties params : jsonPropertiesList) {
            CollectionColumnProperties properties = convertToColumnProperties(params);
            String fieldName = params.getFieldName();
            columnPropertiesMap.put(fieldName, properties);
        }
        return columnPropertiesMap;
    }

    public static SortCriteriaConfig convertToSortCriteriaConfig(JsonSortCriteria sortCriteria) {
        if (sortCriteria == null) {
            return null;
        }
        List<JsonSortCriterion> jsonSortCriterions = sortCriteria.getCriterions();
        List<SortCriterionConfig> sortCriterionConfigs = new ArrayList<>();
        for (JsonSortCriterion sortCriterion : jsonSortCriterions) {
            SortCriterionConfig sortCriterionConfig = new SortCriterionConfig();
            String field = sortCriterion.getField();
            sortCriterionConfig.setField(field);
            String order = sortCriterion.getOrder();
            sortCriterionConfig.setOrderString(order);
            sortCriterionConfigs.add(sortCriterionConfig);
        }
        SortCriteriaConfig sortCriteriaConfig = new SortCriteriaConfig();
        sortCriteriaConfig.setSortCriterionConfigs(sortCriterionConfigs);
        return sortCriteriaConfig;

    }

    public static InitialFiltersConfig convertToInitialFiltersConfig(JsonInitialFilters jsonInitialFilters) {
        if (jsonInitialFilters == null) {
            return null;
        }
        InitialFiltersConfig initialFiltersConfig = new InitialFiltersConfig();
        String panelState = jsonInitialFilters.getPanelState();
        initialFiltersConfig.setPanelState(panelState);
        List<JsonInitialFilter> initialFilters = jsonInitialFilters.getJsonInitialFilters();
        List<AbstractFilterConfig> initialFilterConfigs = new ArrayList<>();
        for (JsonInitialFilter initialFilter : initialFilters) {
            InitialFilterConfig initialFilterConfig = convertToInitialFilterConfig(initialFilter);
            initialFilterConfigs.add(initialFilterConfig);
        }
        initialFiltersConfig.setAbstractFilterConfigs(initialFilterConfigs);
        return initialFiltersConfig;

    }

    private static InitialFilterConfig convertToInitialFilterConfig(JsonInitialFilter initialFilter) {
        InitialFilterConfig initialFilterConfig = new InitialFilterConfig();
        String filterName = initialFilter.getName();
        initialFilterConfig.setName(filterName);
        List<JsonFilterParam> jsonFilterParams = initialFilter.getFilterParams();
        if (jsonFilterParams != null && !jsonFilterParams.isEmpty()) {
            List<ParamConfig> paramConfigs = new ArrayList<>();
            for (JsonFilterParam jsonFilterParam : jsonFilterParams) {
                ParamConfig paramConfig = new ParamConfig();
                paramConfig.setName(jsonFilterParam.getName());
                paramConfig.setValue(jsonFilterParam.getValue());
                paramConfig.setType(jsonFilterParam.getType());
                paramConfigs.add(paramConfig);
            }
            initialFilterConfig.setParamConfigs(paramConfigs);
        }
        return initialFilterConfig;
    }
}
