package org.dotcms.plugins.contentImporter.util;

import java.util.ArrayList;
import java.util.List;

public class ImportParams {

    private boolean saveWithoutVersions;
    private boolean deleteAllContent;
    private boolean publishContent;
    private String csvTextDelimiter;
    private String csvSeparatorDelimiter;
    private long language;
    private String structure;
    private List<String> fields;

    public ImportParams() {
        this.fields = new ArrayList<>();
    }

    public boolean isSaveWithoutVersions() {
        return saveWithoutVersions;
    }

    public void setSaveWithoutVersions(boolean saveWithoutVersions) {
        this.saveWithoutVersions = saveWithoutVersions;
    }

    public boolean isDeleteAllContent() {
        return deleteAllContent;
    }

    public void setDeleteAllContent(boolean deleteAllContent) {
        this.deleteAllContent = deleteAllContent;
    }

    public boolean isPublishContent() {
        return publishContent;
    }

    public void setPublishContent(boolean publishContent) {
        this.publishContent = publishContent;
    }

    public String getCsvTextDelimiter() {
        return csvTextDelimiter;
    }

    public void setCsvTextDelimiter(String csvTextDelimiter) {
        this.csvTextDelimiter = csvTextDelimiter;
    }

    public String getCsvSeparatorDelimiter() {
        return csvSeparatorDelimiter;
    }

    public void setCsvSeparatorDelimiter(String csvSeparatorDelimiter) {
        this.csvSeparatorDelimiter = csvSeparatorDelimiter;
    }

    public long getLanguage() {
        return language;
    }

    public void setLanguage(long language) {
        this.language = language;
    }

    public boolean isMultilanguage() {
        return this.language == -1;
    }


    public String getStructure() {
        return structure;
    }

    public void setStructure(String structure) {
        this.structure = structure;
    }

    public List<String> getFields() {
        return fields;
    }

    public void setFields(List<String> fields) {
        this.fields = fields;
    }


}
