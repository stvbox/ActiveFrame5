package ru.intertrust.cm.core.dao.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ru.intertrust.cm.core.business.api.dto.SortCriterion;
import ru.intertrust.cm.core.business.api.dto.SortCriterion.Order;
import ru.intertrust.cm.core.business.api.dto.SortOrder;
import ru.intertrust.cm.core.config.model.CollectionConfig;
import ru.intertrust.cm.core.config.model.CollectionFilterConfig;
import ru.intertrust.cm.core.dao.access.AccessToken;
import ru.intertrust.cm.core.dao.impl.access.AccessControlUtility;
import ru.intertrust.cm.core.model.FatalException;

/**
 * Инициализирует запрос для извлечения коллекций, заполняет параметры в конфигурации фильтров, устанавливает порядок сортировки
 * @author atsvetkov
 *
 */
public class CollectionQueryInitializer {

    private static final String PLACEHOLDER_PREFIX = "::";

    private static final String EMPTY_STRING = " ";

    private static final String SQL_DESCENDING_ORDER = "desc";

    private static final String SQL_ASCENDING_ORDER = "asc";

    public static final String QUERY_FILTER_PARAM_DELIMETER = ":";

    public static final String DEFAULT_CRITERIA_CONDITION = "and";

    /**
     * Применение фильтров, сортировки и т.д. к прототипу запроса.
     * @param prototypeQuery прототип запроса
     * @param filledFilterConfigs заполненные фильтры
     * @param sortOrder порядок сортировки
     * @param offset смещение
     * @param limit ограничение количества
     * @return
     */
    public String initializeQuery(CollectionConfig collectionConfig, List<CollectionFilterConfig> filledFilterConfigs,
            SortOrder sortOrder, int offset, int limit, AccessToken accessToken) {
        String prototypeQuery = collectionConfig.getPrototype();
        
        return fillPrototypeQuery(collectionConfig, filledFilterConfigs, sortOrder, offset, limit, accessToken,
                prototypeQuery);

    }

    private String fillPrototypeQuery(CollectionConfig collectionConfig,
            List<CollectionFilterConfig> filledFilterConfigs, SortOrder sortOrder, int offset, int limit,
            AccessToken accessToken, String prototypeQuery) {
        if (prototypeQuery == null || prototypeQuery.trim().length() == 0) {
            throw new FatalException("Prototype query is null and can not be processed");
        }
        StringBuilder collectionQuery = new StringBuilder();
        collectionQuery.append(mergeFilledFilterConfigsInPrototypeQuery(prototypeQuery, filledFilterConfigs));

        if (accessToken.isDeferred()) {
            StringBuilder aclQuery = createAclQueryFilter(collectionConfig, accessToken);

            collectionQuery.append(aclQuery);
        }
        applySortOrder(sortOrder, collectionQuery);       
        applyLimitAndOffset(offset, limit, collectionQuery);
        return collectionQuery.toString();
    }

    private StringBuilder createAclQueryFilter(CollectionConfig collectionConfig, AccessToken accessToken) {
        StringBuilder aclQuery = new StringBuilder();
        if (accessToken.isDeferred()) {            
            String domainObjectAclReadTable = AccessControlUtility.getAclReadTableNameFor(collectionConfig.getDomainObjectType());
            aclQuery.append(" and exists (select r.object_id from ").append(domainObjectAclReadTable).append(" r ");
            aclQuery.append("inner join group_member gm on r.group_id = gm.parent where gm.person_id = :user_id and r.object_id = ");
            aclQuery.append(collectionConfig.getIdField()).append(")");
        }
        return aclQuery;
    }

    /**
     * Применение фильтров, и т.д. к прототипу запроса на количество доменных объектов в коллекции.
     * @param prototypeQuery прототип запроса
     * @param filledFilterConfigs заполненные фильтры
     * @return
     */
    public String initializeCountQuery(CollectionConfig collectionConfig, List<CollectionFilterConfig> filledFilterConfigs, AccessToken accessToken) {        
        String prototypeQuery = collectionConfig.getCountingPrototype();
        return fillPrototypeQuery(collectionConfig, filledFilterConfigs, null, 0, 0, accessToken,
                prototypeQuery);

    }

    private void applyLimitAndOffset(int offset, int limit, StringBuilder collectionQuery) {
        if (limit != 0) {
            collectionQuery.append(" limit ").append(limit).append(" OFFSET ").append(offset);
        }
    }

    private String mergeFilledFilterConfigsInPrototypeQuery(String prototypeQuery, List<CollectionFilterConfig> filledFilterConfigs) {
        
        ReferencePlaceHolderCollector referencePlaceHolderCollector = new ReferencePlaceHolderCollector();
        CriteriaPlaceHolderCollector criteriaPlaceHolderCollector = new CriteriaPlaceHolderCollector();
        
        for (CollectionFilterConfig collectionFilterConfig : filledFilterConfigs) {
            if (collectionFilterConfig.getFilterReference() != null
                    && collectionFilterConfig.getFilterReference().getPlaceholder() != null) {
                String placeholder = collectionFilterConfig.getFilterReference().getPlaceholder();
                String value = collectionFilterConfig.getFilterReference().getValue();
                referencePlaceHolderCollector.addPlaceholderValue(placeholder, value);
            }

            if (collectionFilterConfig.getFilterCriteria() != null
                    && collectionFilterConfig.getFilterCriteria().getPlaceholder() != null) {
                String placeholder = collectionFilterConfig.getFilterCriteria().getPlaceholder();
                String value = collectionFilterConfig.getFilterCriteria().getValue();
                criteriaPlaceHolderCollector.addPlaceholderValue(placeholder, value);
            }
        }
        
        for (String placeholder : referencePlaceHolderCollector.getPlaceholders()) {
            String placeholderValue = referencePlaceHolderCollector.getPlaceholderValue(placeholder);
            prototypeQuery = prototypeQuery.replace(PLACEHOLDER_PREFIX + placeholder, placeholderValue);
        }

        for (String placeholder : criteriaPlaceHolderCollector.getPlaceholders()) {
            String placeholderValue = criteriaPlaceHolderCollector.getPlaceholderValue(placeholder);
            if (placeholderValue == null) {
                placeholderValue = EMPTY_STRING;
            }

            prototypeQuery = prototypeQuery.replace(PLACEHOLDER_PREFIX + placeholder, placeholderValue);
        }

        prototypeQuery = removeUnFilledPlaceholders(prototypeQuery);

        return prototypeQuery;
    }

    /**
     * Удаляет не заполненные placeholders в прототипе запроса.
     * @param prototypeQuery исходный запрос
     * @return измененный запрос
     */
    private String removeUnFilledPlaceholders(String prototypeQuery) {
        while(prototypeQuery.indexOf(PLACEHOLDER_PREFIX) > 0){
            int startPlaceHolderIndex = prototypeQuery.indexOf(PLACEHOLDER_PREFIX);
            int endPlaceHolderIndex = prototypeQuery.indexOf(EMPTY_STRING, startPlaceHolderIndex);
            if (endPlaceHolderIndex < 0) {
                endPlaceHolderIndex = prototypeQuery.length();
            }
            String placeHolder = prototypeQuery.substring(startPlaceHolderIndex, endPlaceHolderIndex);
            
            prototypeQuery = prototypeQuery.replaceAll(placeHolder, "");
            
        }
        return prototypeQuery;
    }
    
    private String applySortOrder(SortOrder sortOrder, StringBuilder prototypeQuery) {
        boolean hasSortEntry = false;
        if (sortOrder != null && sortOrder.size() > 0) {
            for (SortCriterion criterion : sortOrder) {
                if (hasSortEntry) {
                    prototypeQuery.append(", ");
                }
                prototypeQuery.append(" order by ").append(criterion.getField()).append("  ").append(getSqlSortOrder(criterion.getOrder()));
                hasSortEntry = true;
            }
        }
        return prototypeQuery.toString();
    }

    private String getSqlSortOrder(SortCriterion.Order order) {
        if (order == Order.ASCENDING) {
            return SQL_ASCENDING_ORDER;
        } else if (order == Order.DESCENDING) {
            return SQL_DESCENDING_ORDER;
        } else {
            return SQL_ASCENDING_ORDER;
        }
    }
    
    /**
     * Группирует фильтры после кл. слова from по названию placeholder.
     * @author atsvetkov
     */
    private class ReferencePlaceHolderCollector {

        private Map<String, String> placeholdersMap = new HashMap<>();

        public void addPlaceholderValue(String placeholder, String value) {
            String placeholderValue = placeholdersMap.get(placeholder);

            if (placeholderValue != null) {
                placeholderValue += value;
            } else {
                placeholderValue = value;
            }
            placeholdersMap.put(placeholder, placeholderValue);

        }

        public String getPlaceholderValue(String placeholder) {
            return placeholdersMap.get(placeholder);
        }

        public Set<String> getPlaceholders() {
            return placeholdersMap.keySet();
        }
    }
 
    /**
     * Группирует все фильтры после слова where по названию placeholder. Т.е. для каждого placeholder составляет запрос
     * из заполненных фильтров. По умолчанию все фильтры соединяются через условие AND ({@link CollectionQueryInitializer#DEFAULT_CRITERIA_CONDITION})
     * @author atsvetkov
     */
    private class CriteriaPlaceHolderCollector {

        private Map<String, String> placeholdersMap = new HashMap<>();

        public void addPlaceholderValue(String placeholder, String value) {
            String placeholderValue = placeholdersMap.get(placeholder);
            
            if (placeholderValue != null) {
                placeholderValue += createCriteriaValue(value);
            } else {
                placeholderValue = createCriteriaValue(value);
            }
            placeholdersMap.put(placeholder, placeholderValue);

        }

        private String createCriteriaValue(String value) {
            String condition = DEFAULT_CRITERIA_CONDITION;
            StringBuilder criteriaValue = new StringBuilder();
            criteriaValue.append(EMPTY_STRING).append(condition).append(EMPTY_STRING).append(value);            
            return criteriaValue.toString();
        }

        public String getPlaceholderValue(String placeholder) {
            return placeholdersMap.get(placeholder);
        }

        public Set<String> getPlaceholders() {
            return placeholdersMap.keySet();
        }
    }
    
}
