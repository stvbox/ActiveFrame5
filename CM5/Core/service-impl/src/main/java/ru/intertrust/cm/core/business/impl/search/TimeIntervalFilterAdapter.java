package ru.intertrust.cm.core.business.impl.search;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import ru.intertrust.cm.core.business.api.dto.SearchQuery;
import ru.intertrust.cm.core.business.api.dto.TimeIntervalFilter;
import ru.intertrust.cm.core.business.api.util.ThreadSafeDateFormat;

public class TimeIntervalFilterAdapter implements FilterAdapter<TimeIntervalFilter> {

    private static final String DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    private static final TimeZone UTC_TZ = TimeZone.getTimeZone("UTC");
    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Autowired private SearchConfigHelper configHelper;

    @Override
    public String getFilterString(TimeIntervalFilter filter, SearchQuery query) {
        if (filter.getStartTime() == null && filter.getEndTime() == null) {
            log.warn("Empty date search filter for " + filter.getFieldName() + " ignored");
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
                            (filter.isStartInclusive() ? ":[" : ":{") +
                            dateToString(filter.getStartTime()) +
                            " TO " +
                            dateToString(filter.getEndTime()) +
                            (filter.isEndInclusive() ? "]" : "}"));
                }
            }
        }
        return SolrUtils.joinStrings("OR", fields);
    }

    @Override
    public boolean isCompositeFilter(TimeIntervalFilter filter) {
        return false;
    }

    @Override
    public List<String> getFieldNames(TimeIntervalFilter filter, SearchQuery query) {
        String fieldName = filter.getFieldName();
        Set<SearchFieldType> types = configHelper.getFieldTypes(fieldName, query.getAreas(), query.getTargetObjectTypes());
        ArrayList<String> names = new ArrayList<>(types.size());
        for (SearchFieldType type : types) {
            if (type.supportsFilter(filter)) {
                for (String field : type.getSolrFieldNames(fieldName)) {
                    names.add(field);
                }
            }
        }
        return names;

    }

    private static String dateToString(Date time) {
        if (time == null) {
            return "*";
        }
        return ThreadSafeDateFormat.format(time, DATE_PATTERN, UTC_TZ);
    }
}
