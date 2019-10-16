package org.dotcms.plugins.contentImporter.util;

import com.dotmarketing.portlets.structure.model.Field;
import com.dotmarketing.portlets.structure.model.Relationship;

import java.util.HashMap;
import java.util.Map;

public class ImportFileInfo {

    private final Map<Integer,Field> uniqueFields;
    private final Map<Integer,Field> headers;
    private final Map<Integer,Field> keyFields;
    private final Map<Integer, Relationship> relationships;
    private final Map<Integer,Boolean> onlyParent;
    private final Map<Integer,Boolean> onlyChild;

    private int languageCodeHeaderColumn = -1;
    private int countryCodeHeaderColumn = -1;

    public ImportFileInfo() {

        this.uniqueFields = new HashMap<>();
        this.headers = new HashMap<>();
        this.keyFields = new HashMap<>();
        this.relationships = new HashMap<>();
        this.onlyParent = new HashMap<>();
        this.onlyChild = new HashMap<>();

    }

    public Map<Integer, Field> getUniqueFields() {
        return uniqueFields;
    }

    public Map<Integer, Field> getHeaders() {
        return headers;
    }

    public Map<Integer, Field> getKeyFields() {
        return keyFields;
    }

    public Map<Integer, Relationship> getRelationships() {
        return relationships;
    }

    public Map<Integer, Boolean> getOnlyParent() {
        return onlyParent;
    }

    public Map<Integer, Boolean> getOnlyChild() {
        return onlyChild;
    }

    public int getLanguageCodeHeaderColumn() {
        return languageCodeHeaderColumn;
    }

    public void setLanguageCodeHeaderColumn(int languageCodeHeaderColumn) {
        this.languageCodeHeaderColumn = languageCodeHeaderColumn;
    }

    public int getCountryCodeHeaderColumn() {
        return countryCodeHeaderColumn;
    }

    public void setCountryCodeHeaderColumn(int countryCodeHeaderColumn) {
        this.countryCodeHeaderColumn = countryCodeHeaderColumn;
    }

}
