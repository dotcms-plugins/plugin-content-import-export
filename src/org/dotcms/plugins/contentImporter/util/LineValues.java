package org.dotcms.plugins.contentImporter.util;

import com.dotmarketing.portlets.categories.model.Category;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class LineValues {

    private final Map<Integer, Object> values;
    private final Set<Category> categories;
    private boolean headersIncludeHostField;

    public LineValues() {
        values = new HashMap<>();
        categories = new HashSet<>();
        headersIncludeHostField = false;
    }

    public Map<Integer, Object> getValues() {
        return values;
    }

    public Set<Category> getCategories() {
        return categories;
    }

    public boolean isHeadersIncludeHostField() {
        return headersIncludeHostField;
    }

    public void setHeadersIncludeHostField(boolean headersIncludeHostField) {
        this.headersIncludeHostField = headersIncludeHostField;
    }

}
