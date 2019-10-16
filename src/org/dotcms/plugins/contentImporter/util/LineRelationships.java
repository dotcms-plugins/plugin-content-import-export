package org.dotcms.plugins.contentImporter.util;

import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.dotmarketing.portlets.structure.model.Relationship;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class LineRelationships {

    private Map<Relationship, List<Contentlet>> csvRelationshipRecordsParentOnly;
    private Map<Relationship, List<Contentlet>> csvRelationshipRecordsChildOnly;
    private Map<Relationship, List<Contentlet>> csvRelationshipRecords;

    public LineRelationships() {
        csvRelationshipRecordsParentOnly = new HashMap<>();
        csvRelationshipRecordsChildOnly = new HashMap<>();
        csvRelationshipRecords = new HashMap<>();
    }

    public Map<Relationship, List<Contentlet>> getCsvRelationshipRecordsParentOnly() {
        return csvRelationshipRecordsParentOnly;
    }

    public Map<Relationship, List<Contentlet>> getCsvRelationshipRecordsChildOnly() {
        return csvRelationshipRecordsChildOnly;
    }

    public Map<Relationship, List<Contentlet>> getCsvRelationshipRecords() {
        return csvRelationshipRecords;
    }

}
