package org.dotcms.plugins.contentImporter.util;

import com.dotmarketing.portlets.structure.model.Field;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class UniqueFieldValues {

    private Map<Long, Map<String, Set<Object>>> langMap;

    public UniqueFieldValues() {
        this.langMap = new HashMap<>();
    }

    public boolean existsValue(final String fieldVarName, final Object value, final long language) {
        if (langMap.containsKey(language)) {
            Map<String, Set<Object>> fieldMap = langMap.get(language);
            if (fieldMap.containsKey(fieldVarName)) {
                Set<Object> valuesSet = fieldMap.get(fieldVarName);
                if (valuesSet.contains(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void addValue(String fieldVarName, Object value, long language) {

        Map<String, Set<Object>> fieldMap;
        if (langMap.containsKey(language)) {
            fieldMap = langMap.get(language);
        } else {
            fieldMap = new HashMap<>();
            langMap.put(language, fieldMap);
        }

        Set<Object> valuesSet;
        if (fieldMap.containsKey(fieldVarName)) {
            valuesSet = fieldMap.get(fieldVarName);
        } else {
            valuesSet = new HashSet<>();
            fieldMap.put(fieldVarName, valuesSet);
        }

        valuesSet.add(value);

    }
}
