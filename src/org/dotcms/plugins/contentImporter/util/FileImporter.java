package org.dotcms.plugins.contentImporter.util;

import com.dotcms.repackage.com.csvreader.CsvReader;
import com.dotmarketing.beans.Host;
import com.dotmarketing.beans.Permission;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.business.CacheLocator;
import com.dotmarketing.business.FactoryLocator;
import com.dotmarketing.business.PermissionAPI;
import com.dotmarketing.business.web.WebAPILocator;
import com.dotmarketing.cache.FieldsCache;
import com.dotmarketing.db.HibernateUtil;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.exception.DotRuntimeException;
import com.dotmarketing.exception.DotSecurityException;
import com.dotmarketing.portlets.contentlet.business.ContentletAPI;
import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.dotmarketing.portlets.languagesmanager.business.LanguageAPI;
import com.dotmarketing.portlets.languagesmanager.model.Language;
import com.dotmarketing.portlets.structure.factories.FieldFactory;
import com.dotmarketing.portlets.structure.model.Field;
import com.dotmarketing.portlets.structure.model.Relationship;
import com.dotmarketing.portlets.structure.model.Structure;
import com.dotmarketing.util.Logger;
import com.liferay.portal.language.LanguageException;
import com.liferay.portal.language.LanguageUtil;
import com.liferay.portal.model.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FileImporter {

	public static final String DOTSCHEDULER_DATE = "EEE MMM d hh:mm:ss z yyyy";

	// Final Variables
	private final static String languageCodeHeader = "languageCode";
	private final static String countryCodeHeader = "countryCode";

	//API
	private static PermissionAPI permissionAPI = APILocator.getPermissionAPI();
	private static LanguageAPI langAPI = APILocator.getLanguageAPI();

	private final User systemUser;
	private final User user;

	private final static int commitGranularity = 10;
	private final static int sleepTime = 200;

	// CSV Reader
	private final CsvReader csvReader;
	private final String[] csvHeaders;

	private final ImportParams importParams;
	private final boolean preview;
	private final Structure structure;

	public FileImporter(
			final CsvReader csvReader, final ImportParams importParams,
			final boolean preview, final User user) {

		try {
			this.csvReader = csvReader;
			this.csvHeaders = csvReader.getHeaders();
			this.importParams = importParams;
			this.preview = preview;
			this.systemUser = APILocator.getUserAPI().getSystemUser();
			this.user = user;
			final Host defaultHost = WebAPILocator.getHostWebAPI().findDefaultHost(systemUser, false);
			if (defaultHost == null) {
				Logger.warn(this, "Couldn't find default host");
			}
			structure = CacheLocator.getContentTypeCache().getStructureByInode (importParams.getStructure());

		} catch(Exception ex) {
			throw new DotRuntimeException(ex.getMessage(),ex);
		}

	}

	public HashMap<String, List<String>> importFile()
			throws DotRuntimeException, DotDataException {

		HashMap<String, List<String>> results = new HashMap<String, List<String>>();
		results.put("warnings", new ArrayList<String>());
		results.put("errors", new ArrayList<String>());
		results.put("messages", new ArrayList<String>());
		results.put("results", new ArrayList<String>());
		results.put("counters", new ArrayList<String>());
		results.put("identifiers", new ArrayList<String>());
		results.put("lastInode", new ArrayList<String>());

		//Parsing the file line per line
		try {
			if ((csvHeaders != null) || (csvReader.readHeaders())) {

				//Importing headers from the first file line
				ImportFileInfo importFileInfo = importHeaders(
						(csvHeaders != null ? csvHeaders : csvReader.getHeaders()), results);

				Logger.info(this, "----> Number of Headers: " + importFileInfo.getHeaders().size());
				Logger.info(this, "Headers:" + importFileInfo.getHeaders().values().stream()
						.map(Field::getFieldName).collect(Collectors.toList()));
				Logger.info(this, "Key fields:" + importFileInfo.getKeyFields().values().stream()
						.map(Field::getFieldName).collect(Collectors.toList()));

				//Reading the whole file
				if (importFileInfo.getHeaders().size() > 0) {

					CsvMultiLineSupplier csvLineSupplier =
							new CsvMultiLineSupplier(csvReader, commitGranularity);
					importLines(importFileInfo, csvLineSupplier, results);

				} else {
					results.get("errors").add(LanguageUtil.get(user, "No-headers-found-on-the-file-nothing-will-be-imported"));
				}
			}
		} catch (Exception e) {
			Logger.error(this, String.format("An error occurred while importing data of type " +
					"'%s': %s", structure.getName(), e.getMessage()));
		}

		return results;

	}

	private ImportFileInfo importHeaders(
			final String[] headerLine, final Map<String, List<String>> results)
			throws Exception  {

		ImportFileInfo importFileInfo = new ImportFileInfo();
		int importableFields = 0;

		//Importing headers and storing them in a hashmap to be reused later in the whole import process
		List<Field> fields = FieldsCache.getFieldsByStructureInode(structure.getInode());
		List<Relationship> structureRelationships = FactoryLocator.getRelationshipFactory()
                .byContentType(structure);
		List<String> requiredFields = new ArrayList<String>();
		List<String> headerFields = new ArrayList<String>();

		for(Field field:fields){
			if(field.isRequired()){
				requiredFields.add(field.getVelocityVarName());
			}
		}

		for (int i = 0; i < headerLine.length; ++i) {
			if (headerLine[i].equals(languageCodeHeader))
				importFileInfo.setLanguageCodeHeaderColumn(i);
			if (headerLine[i].equals(countryCodeHeader))
				importFileInfo.setCountryCodeHeaderColumn(i);

			if ((-1 < importFileInfo.getLanguageCodeHeaderColumn()) && (-1 < importFileInfo.getCountryCodeHeaderColumn()))
				break;
		}

		for (int i = 0; i < headerLine.length; i++) {

			boolean found = false;
			String header = headerLine[i].replaceAll("'", "");

			if (header.equalsIgnoreCase("Identifier")) {
				results.get("messages").add(LanguageUtil.get(user, "identifier-field-found-in-import-contentlet-csv-file"));
				results.get("identifiers").add("" + i);
				continue;
			}

			headerFields.add(header);

			for (Field field : fields) {
				if (field.getVelocityVarName().equalsIgnoreCase(header)) {
					if (field.getFieldType().equals(Field.FieldType.BUTTON.toString())){
						found = true;

						results.get("warnings").add(
								LanguageUtil.get(user, "Header")+": \"" + header

								+"\" "+ LanguageUtil.get(user, "matches-a-field-of-type-button-this-column-of-data-will-be-ignored"));
					}
					else if (field.getFieldType().equals(Field.FieldType.BINARY.toString())){
						found = true;
						results.get("warnings").add(
								LanguageUtil.get(user, "Header")+": \"" + header
								+ "\" "+ LanguageUtil.get(user, "matches-a-field-of-type-binary-this-column-of-data-will-be-ignored"));
					}
					else if (field.getFieldType().equals(Field.FieldType.LINE_DIVIDER.toString())){
						found = true;
						results.get("warnings").add(
								LanguageUtil.get(user, "Header")+": \"" + header
								+ "\" "+LanguageUtil.get(user, "matches-a-field-of-type-line-divider-this-column-of-data-will-be-ignored"));
					}
					else if (field.getFieldType().equals(Field.FieldType.TAB_DIVIDER.toString())){
						found = true;
						results.get("warnings").add(
								LanguageUtil.get(user, "Header")+": \"" + header
								+ "\" "+LanguageUtil.get(user, "matches-a-field-of-type-tab-divider-this-column-of-data-will-be-ignored"));
					}
					else {
						found = true;
						importFileInfo.getHeaders().put(i, field);		//Get unique fields for structure
						if(field.isUnique()){
							importFileInfo.getUniqueFields().put(i, field);
						}
						for (String fieldInode : importParams.getFields()) {
							if (fieldInode.equals(field.getInode()))
								importFileInfo.getKeyFields().put(i, field);
						}
						break;
					}
				}
			}

			/*
			 * http://jira.dotmarketing.net/browse/DOTCMS-6409
			 * We gonna delete -RELPARENT -RELCHILD so we can
			 * search for the relation name. No problem as
			 * we put relationships.put(i,relationship) instead
			 * of header.
			 */
			boolean onlyP=false;
			if(header.endsWith("-RELPARENT")) {
				header = header.substring(0,header.lastIndexOf("-RELPARENT"));
				onlyP=true;
			}

			boolean onlyCh=false;
			if(header.endsWith("-RELCHILD")) {
				header = header.substring(0,header.lastIndexOf("-RELCHILD"));
				onlyCh=true;
			}

			//Check if the header is a relationship
			for(Relationship relationship : structureRelationships)
			{
				if(relationship.getRelationTypeValue().equalsIgnoreCase(header))
				{
					found = true;
					importFileInfo.getRelationships().put(i,relationship);
					importFileInfo.getOnlyParent().put(i, onlyP);
					importFileInfo.getOnlyChild().put(i, onlyCh);

					// special case when the relationship has the same structure for parent and child, set only as child
					if(relationship.getChildStructureInode().equals(relationship.getParentStructureInode()) && !onlyCh && !onlyP)
						importFileInfo.getOnlyChild().put(i, true);
				}
			}

			if ((!found) && !(importParams.isMultilanguage() && (header.equals(languageCodeHeader) || header.equals(countryCodeHeader)))) {
				results.get("warnings").add(
						LanguageUtil.get(user, "Header")+": \"" + header
						+ "\""+ " "+ LanguageUtil.get(user, "doesn-t-match-any-structure-field-this-column-of-data-will-be-ignored"));
			}
		}

		requiredFields.removeAll(headerFields);

		for(String requiredField: requiredFields){
			results.get("errors").add(LanguageUtil.get(user, "Field")+": \"" + requiredField+ "\" "+LanguageUtil.get(user, "required-field-not-found-in-header"));
		}

		for (Field field : fields) {
			if (isImportableField(field)){
				importableFields++;
			}
		}

		//Checking keyField selected by the user against the headers
		for (String keyField : importParams.getFields()) {
			boolean found = false;
			for (Field headerField : importFileInfo.getHeaders().values()) {
				if (headerField.getInode().equals(keyField)) {
					found = true;
					break;
				}
			}
			if (!found) {
				results.get("errors").add(
						LanguageUtil.get(user, "Key-field")+": \"" + FieldFactory.getFieldByInode(keyField).getFieldName()
						+ "\" "+LanguageUtil.get(user, "choosen-doesn-t-match-any-of-theh-eaders-found-in-the-file"));
			}
		}

		if (importParams.getFields().size() == 0)
			results.get("warnings").add(
					LanguageUtil.get(user, "No-key-fields-were-choosen-it-could-give-to-you-duplicated-content"));

		if(!importFileInfo.getUniqueFields().isEmpty()){
			for(Field f : importFileInfo.getUniqueFields().values()){
				results.get("warnings").add(LanguageUtil.get(user, "the-structure-field")+ " " + f.getFieldName()+  " " +LanguageUtil.get(user, "is-unique"));
			}
		}

		//Adding some messages to the results
		if (importableFields == importFileInfo.getHeaders().size()) {
			results.get("messages").add(
					LanguageUtil.get(user,  importFileInfo.getHeaders().size() + " "+LanguageUtil.get(user, "headers-match-these-will-be-imported")));
		} else {
			if (importFileInfo.getHeaders().size() > 0)
				results.get("messages").add(importFileInfo.getHeaders().size() + " " + LanguageUtil.get(user, "headers-found-on-the-file-matches-all-the-structure-fields"));
			else
				results
				.get("messages")
				.add(
						LanguageUtil.get(user, "No-headers-found-on-the-file-that-match-any-of-the-structure-fields"));
			results
			.get("warnings")
			.add(LanguageUtil.get(user, "Not-all-the-structure-fields-were-matched-against-the-file-headers-Some-content-fields-could-be-left-empty"));
		}
		//Adding the relationship messages
		if(importFileInfo.getRelationships().size() > 0)
		{
			results.get("messages").add(LanguageUtil.get(user,  importFileInfo.getRelationships().size() + " "+LanguageUtil.get(user, "relationship-match-these-will-be-imported")));
		}

		return importFileInfo;

	}

	private void importLines(
			final ImportFileInfo importFileInfo,
			final CsvMultiLineSupplier csvMultiLineSupplier,
			final Map<String, List<String>> results) throws Exception {

		Logger.info(this, "Start to read lines to import");

		List<Permission> structurePermissions = permissionAPI.getPermissions(structure);
		final LineImporter lineImporter = new LineImporter(importParams,
				structure, structurePermissions, preview, user,
				results, importFileInfo);

		CacheLocator.getContentletCache().clearCache();
		CacheLocator.getIdentifierCache().clearCache();

		List<CsvLine> csvLineList;
		int linesRead = 0;
		int numErrors = 0;
		while ((csvLineList = csvMultiLineSupplier.getNextLineBlock()) != null) {

			boolean transactionRollback;
			do {

				transactionRollback = false;
				if (!preview) {
					HibernateUtil.startTransaction();
				}

				for (int i = 0; i < csvLineList.size(); i++) {

					CsvLine csvLine = csvLineList.get(i);
					try {

						//Importing a line
						if (0 < importParams.getLanguage()) {
							lineImporter.importLine(csvLine.getLine(), csvLine.getLineNumber(), importParams.getLanguage());
						} else {

							final Language dotCMSLanguage = langAPI.getLanguage(
									csvLine.getLine()[importFileInfo.getLanguageCodeHeaderColumn()],
									csvLine.getLine()[importFileInfo.getCountryCodeHeaderColumn()]);

							if (0 < dotCMSLanguage.getId()) {
								lineImporter.importLine(csvLine.getLine(), csvLine.getLineNumber(), dotCMSLanguage.getId());
							} else {
								results.get("errors").add(LanguageUtil.get(user, "Line--") + csvLine.getLineNumber()
										+ LanguageUtil.get(user, "Locale-not-found-for-languageCode") + " ='"
										+ csvLine.getLine()[importFileInfo.getLanguageCodeHeaderColumn()] + "' countryCode='"
										+ csvLine.getLine()[importFileInfo.getCountryCodeHeaderColumn()] + "'");
								numErrors++;
								csvLineList.remove(i);
							}
						}

					} catch (DotRuntimeException ex) {

						if (!preview) {
							HibernateUtil.rollbackTransaction();
							transactionRollback = true;
						}
						String errorMessage = ex.getMessage();
						if (errorMessage.indexOf("Line #") == -1) {
							errorMessage = "Line #" + csvLine.getLineNumber() + " " + errorMessage;
						}
						results.get("errors").add(errorMessage);
						numErrors++;
						Logger.error(FileImporter.class, "Error line: " + linesRead + " (" + csvReader.getRawRecord()
								+ "). Line Ignored.", ex);
						csvLineList.remove(i);
						break;

					}

				}

				if (!preview && !transactionRollback) {
					HibernateUtil.commitTransaction();
					Thread.sleep(sleepTime);
				}

			} while (transactionRollback && csvLineList.size() > 0);

			linesRead+=csvLineList.size();

		}

		Counters counters = lineImporter.getCounters();
		if(!preview){
			results.get("counters").add("lines="+linesRead);
			results.get("counters").add("errors="+numErrors);
			results.get("counters").add("newContent="+counters.getNewContentCounter());
			results.get("counters").add("contentToUpdate="+counters.getContentToUpdateCounter());
		}

		setImportResults(results, lineImporter, linesRead, numErrors, counters);

		Logger.info(FileImporter.class, "Import completed: "
				+ linesRead + " lines read correctly, " + numErrors + " errors found.");

	}

	private void setImportResults(
			final Map<String, List<String>> results, LineImporter lineImporter,
			int linesRead, int numErrors, Counters counters) throws LanguageException {

		String choosenKeyField = lineImporter.getChoosenKeyField();
		results.get("messages").add(linesRead + " "+ LanguageUtil.get(user, "lines-of-data-were-read" ));
		if (numErrors > 0) {
			results.get("errors").add(numErrors + " " + LanguageUtil.get(user, "input-lines-had-errors"));
		}

		if(preview && choosenKeyField.length() > 1) {
			results.get("messages").add(LanguageUtil.get(user, "Fields-selected-as-key")
					+ ": " + choosenKeyField.substring(1) + ".");
		}

		if (counters.getNewContentCounter() > 0) {
			results.get("messages").add(LanguageUtil.get(user, "Attempting-to-create")
					+ " " + (counters.getNewContentCounter()) + " contentlets - "
					+ LanguageUtil.get(user, "check-below-for-errors"));
		}

		if (counters.getContentToUpdateCounter() > 0) {
			results.get("messages").add(LanguageUtil.get(user, "Approximately")
					+ " " + (counters.getContentToUpdateCounter()) + " "
					+ LanguageUtil.get(user, "old-content-will-be-updated"));
		}

		results.get("results").add(counters.getContentCreated() + " "
				+ LanguageUtil.get(user, "new")+" "+"\"" + structure.getName()
				+ "\" "+ LanguageUtil.get(user, "were-created"));
		results.get("results").add(counters.getContentUpdatedDuplicated() + " \"" + structure.getName() + "\" "
				+ LanguageUtil.get(user, "contentlets-updated-corresponding-to")
				+ " " + counters.getContentUpdated() + " "
				+ LanguageUtil.get(user, "repeated-contents-based-on-the-key-provided"));

		if (numErrors > 0) {
			results.get("results").add(numErrors + " " + LanguageUtil.get(user, "contentlets-were-ignored-due-to-invalid-information"));
		}

	}

	/**
	 * This method drop all the content associated to this structure
	 * @param struture structure ID
	 * @param user User with permission
	 * @throws DotDataException
	 * @throws DotSecurityException
	 */
	public void deleteAllContent(String struture, User user) throws DotSecurityException, DotDataException{
		int limit = 200;
		int offset = 0;
		ContentletAPI conAPI=APILocator.getContentletAPI();
		List<Contentlet> contentlets=null;
		Structure st = CacheLocator.getContentTypeCache().getStructureByInode (struture);
		do {
			contentlets = conAPI.findByStructure(st, user, false, limit, offset);
			conAPI.delete(contentlets, user, false);
		} while(contentlets.size()>0);
		conAPI.refresh(st);
	}

	public static boolean isImportableField(Field field) {
		return !(field.getFieldType().equals(Field.FieldType.IMAGE.toString()) ||
				field.getFieldType().equals(Field.FieldType.FILE.toString()) ||
				field.getFieldType().equals(Field.FieldType.BUTTON.toString()) ||
				field.getFieldType().equals(Field.FieldType.LINE_DIVIDER.toString()) ||
				field.getFieldType().equals(Field.FieldType.TAB_DIVIDER.toString()));
	}

}
