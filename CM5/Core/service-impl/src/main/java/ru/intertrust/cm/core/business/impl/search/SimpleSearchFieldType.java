package ru.intertrust.cm.core.business.impl.search;

import java.util.Collection;
import java.util.Collections;

import ru.intertrust.cm.core.business.api.dto.BooleanSearchFilter;
import ru.intertrust.cm.core.business.api.dto.DatePeriodFilter;
import ru.intertrust.cm.core.business.api.dto.EmptyValueFilter;
import ru.intertrust.cm.core.business.api.dto.FieldType;
import ru.intertrust.cm.core.business.api.dto.NumberRangeFilter;
import ru.intertrust.cm.core.business.api.dto.OneOfListFilter;
import ru.intertrust.cm.core.business.api.dto.SearchFilter;
import ru.intertrust.cm.core.business.api.dto.TimeIntervalFilter;

public class SimpleSearchFieldType implements SearchFieldType {

    public enum Type {
        //TEXT("t_", "ts_"),
        DATE("dt_", "dts_"),
        LONG("l_", "ls_"),
        DOUBLE("d_", "ds_"),
        REF("r_", "rs_"),
        BOOL("b_", "bs_");

        final String infixSingle;
        final String infixMultiple;

        Type(String infixSingle, String infixMultiple) {
            this.infixSingle = infixSingle;
            this.infixMultiple = infixMultiple;
        }
    }

    protected final Type type;
    protected final boolean multiValued;

    public SimpleSearchFieldType(Type type) {
        this.type = type;
        this.multiValued = false;
    }

    public SimpleSearchFieldType(Type type, boolean multiValued) {
        this.type = type;
        this.multiValued = multiValued;
    }

    @Override
    public Collection<String> getSolrFieldNames(String field, boolean strict) {
        return Collections.singleton(new StringBuilder()
                .append(SolrFields.FIELD_PREFIX)
                .append(multiValued ? type.infixMultiple : type.infixSingle)
                .append(field.toLowerCase())
                .toString());
    }

    @Override
    public boolean supportsFilter(SearchFilter filter) {
        if (filter instanceof EmptyValueFilter) {
            return true;
        }
        switch(type) {
        case DATE:
            return filter instanceof TimeIntervalFilter || filter instanceof DatePeriodFilter;
        case LONG:
        case DOUBLE:
            return filter instanceof NumberRangeFilter;
        case REF:
            return filter instanceof OneOfListFilter;
        case BOOL:
            return filter instanceof BooleanSearchFilter;
        default:
            return false;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != getClass()) return false;
        SimpleSearchFieldType that = (SimpleSearchFieldType) obj;
        return this.type == that.type && this.multiValued == that.multiValued;
    }

    @Override
    public int hashCode() {
        int hash = type.name().hashCode();
        if (multiValued) {
            hash ^= 0x5A5A5A5A;
        }
        return hash;
    }

    public static Type byFieldType(FieldType type) {
        switch(type) {
        /*case STRING:
        case TEXT:
            return Type.TEXT;*/
        case DATETIME:
        case DATETIMEWITHTIMEZONE:
        case TIMELESSDATE:
            return Type.DATE;
        case LONG:
            return Type.LONG;
        case DECIMAL:
            return Type.DOUBLE;
        case REFERENCE:
            return Type.REF;
        case BOOLEAN:
            return Type.BOOL;
        default:
            return null;
        }
    }
}
