package org.dotcms.plugins.contentImporter.util;

import com.dotmarketing.portlets.contentlet.model.Contentlet;

import java.util.ArrayList;
import java.util.List;

class LineExistingContent {

    private final List<Contentlet> contentlets;
    private final StringBuilder conditionValues;
    private String identifier;

    public LineExistingContent() {
        this.contentlets = new ArrayList<>();
        this.conditionValues = new StringBuilder();
        this.identifier = null;
    }

    public List<Contentlet> getContentlets() {
        return contentlets;
    }

    public String getConditionValues() {
        return conditionValues.toString();
    }

    public void addConditionValues(String values) {
        this.conditionValues.append(values);
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

}
