package ru.intertrust.cm.core.business.impl.search;

import java.util.Collection;
import java.util.Collections;

import ru.intertrust.cm.core.business.api.dto.SearchFilter;

public class CustomSearchFieldType implements SearchFieldType {

    private String solrPrefix;

    public CustomSearchFieldType(String solrPrefix) {
        if (!solrPrefix.endsWith("_")) {
            solrPrefix += "_";
        }
        this.solrPrefix = solrPrefix;
    }

    @Override
    public boolean supportsFilter(SearchFilter filter) {
        return true;    // Have no real information about supported filters; try to use with any
    }

    @Override
    public Collection<String> getSolrFieldNames(String field, boolean strict) {
        return Collections.singleton(new StringBuilder()
                .append(solrPrefix)
                .append(field.toLowerCase())
                .toString());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != getClass()) return false;
        CustomSearchFieldType that = (CustomSearchFieldType) obj;
        return this.solrPrefix.equals(that.solrPrefix);
    }

    @Override
    public int hashCode() {
        return solrPrefix.hashCode() ^ 0x5A693C24;
    }

}
