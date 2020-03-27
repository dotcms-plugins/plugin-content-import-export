package org.dotcms.plugins.contentImporter.portlet.ajax;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.dotmarketing.business.APILocator;
import com.dotmarketing.business.CacheLocator;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.portlets.structure.model.Field;
import com.dotmarketing.portlets.structure.model.Structure;
import com.dotmarketing.portlets.workflows.business.WorkflowAPI;
import com.dotmarketing.portlets.workflows.model.WorkflowScheme;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.UtilMethods;

/**
 * This class allow user to get the structures fields that the StructuresAjax class
 * doesn't allow
 * @author Oswaldo
 *
 */
public class ContentExporterAjax {

    private final WorkflowAPI workflowAPI = APILocator.getWorkflowAPI();

	/**
	 * Get a Map with all the structure fields except buttons, line divider and tab dividers
	 * @param contentTypeId
	 * @return
	 */
	public Map<String, Object> getKeyStructureFields(final String contentTypeId) {
		final Structure contentType = CacheLocator.getContentTypeCache().getStructureByInode(contentTypeId);
		final List<Field> fields = contentType.getFields();
        final ArrayList<Map> searchableFields = new ArrayList<> ();
		for (final Field field : fields) {
			if (!field.getFieldType().equals(Field.FieldType.BUTTON.toString()) &&
					!field.getFieldType().equals(Field.FieldType.LINE_DIVIDER.toString()) &&
					!field.getFieldType().equals(Field.FieldType.TAB_DIVIDER.toString())) {
				try {
					Map fieldMap = field.getMap();
					searchableFields.add(fieldMap);
				} catch (Exception e) {
					Logger.error(this, "Error getting the map of properties of a field: " + field.getInode());
				}
			}
		}
        boolean allowImport = true;
		try {
			// If no Workflow Schemes are found for the specified Content Type, the System Workflow Scheme is returned
            final List<WorkflowScheme> schemeList = this.workflowAPI.findSchemesForStruct(contentType);
			if (schemeList.get(0).isMandatory() && !UtilMethods.isSet(schemeList.get(0).getEntryActionId())){
				allowImport = false;
			}
		} catch (final DotDataException e) {
            Logger.error(this, String.format("An error occurred when retrieving key searchable fields from Content " +
                    "Type '%s' / ID = '%s': %s", contentType.getName(), contentTypeId, e.getMessage()));
        }
        final Map<String,Object> result = new HashMap<>();
		result.put("keyStructureFields",searchableFields);
		result.put("allowImport", allowImport);
		return result;
	}

	/**
	 * Get a Map with all the structure fields
	 * @param contentTypeId
	 * @return
	 */
	public Map<String, Object> getAllStructureFields(final String contentTypeId) {
        final Structure contentType = CacheLocator.getContentTypeCache().getStructureByInode(contentTypeId);
        final List<Field> fields = contentType.getFields();
        final ArrayList<Map> searchableFields = new ArrayList<> ();
		for (final Field field : fields) {
			try {
				Map fieldMap = field.getMap();
				searchableFields.add(fieldMap);
			} catch (Exception e) {
				Logger.error(this, "Error getting the map of properties of a field: " + field.getInode());
			}			
		}
        boolean allowImport = true;
		try {
            // If no Workflow Schemes are found for the specified Content Type, the System Workflow Scheme is returned
            final List<WorkflowScheme> schemeList = this.workflowAPI.findSchemesForStruct(contentType);
            if (schemeList.get(0).isMandatory() && !UtilMethods.isSet(schemeList.get(0).getEntryActionId())){
                allowImport = false;
            }
		} catch (final DotDataException e) {
            Logger.error(this, String.format("An error occurred when retrieving all searchable fields from Content " +
                    "Type '%s' / ID = '%s': %s", contentType.getName(), contentTypeId, e.getMessage()));
        }
        final Map<String,Object> result = new HashMap<>();
		result.put("keyStructureFields",searchableFields);
		result.put("allowImport", allowImport);
		return result;
	}

}
