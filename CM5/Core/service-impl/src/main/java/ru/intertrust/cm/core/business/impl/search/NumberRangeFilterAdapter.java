package ru.intertrust.cm.core.business.impl.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import ru.intertrust.cm.core.business.api.dto.NumberRangeFilter;
import ru.intertrust.cm.core.business.api.dto.SearchQuery;

public class NumberRangeFilterAdapter implements FilterAdapter<NumberRangeFilter> {

    protected Logger log = LoggerFactory.getLogger(getClass());

    @Autowired private SearchConfigHelper configHelper;

    @Override
    public String getFilterString(NumberRangeFilter filter, SearchQuery query) {
        if (filter.getMin() == null && filter.getMax() == null) {
            log.warn("Empty number search filter for " + filter.getFieldName() + " ignored");
            return null;
        }
        String fieldName = filter.getFieldName();
        Set<SearchFieldType> types = configHelper.getFieldTypes(fieldName, query.getAreas(), query.getTargetObjectTypes());
        if (types.size() == 0) {
            return null;
        }
        ArrayList<String> fields = new ArrayList<>(types.size());
        for (SearchFieldType type : types) {
            if (type.supportsFilter(filter)) {
                for (String field : type.getSolrFieldNames(fieldName)) {
                    fields.add(field +
                            (filter.isMinInclusive() ? ":[" : ":{") +
                            numberToString(filter.getMin()) +
                            " TO " +
                            numberToString(filter.getMax()) +
                            (filter.isMaxInclusive() ? "]" : "}"));
                }
            }
        }
        return SolrUtils.joinStrings("OR", fields);
    }

    @Override
    public boolean isCompositeFilter(NumberRangeFilter filter) {
        return false;
    }

    @Override
    public List<String> getFieldNames(NumberRangeFilter filter, SearchQuery query) {
        String fieldName = filter.getFieldName();
        Set<SearchFieldType> types = configHelper.getFieldTypes(fieldName, query.getAreas(), query.getTargetObjectTypes());
        ArrayList<String> names = new ArrayList<>(types.size());
        if (types.size() == 0) {
            return names;
        }
        for (SearchFieldType type : types) {
            if (type.supportsFilter(filter)) {
                for (String field : type.getSolrFieldNames(fieldName)) {
                    names.add(field);
                }
            }
        }
        return names;
    }

    private static String numberToString(Number number) {
        return number == null ? "*" : number.toString();
    }
}
