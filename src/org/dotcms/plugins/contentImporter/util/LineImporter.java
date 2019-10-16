package org.dotcms.plugins.contentImporter.util;

import com.dotmarketing.beans.Host;
import com.dotmarketing.beans.Identifier;
import com.dotmarketing.beans.Permission;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.business.DotStateException;
import com.dotmarketing.business.FactoryLocator;
import com.dotmarketing.business.PermissionAPI;
import com.dotmarketing.cache.FieldsCache;
import com.dotmarketing.common.model.ContentletSearch;
import com.dotmarketing.exception.DotRuntimeException;
import com.dotmarketing.exception.DotSecurityException;
import com.dotmarketing.portlets.categories.business.CategoryAPI;
import com.dotmarketing.portlets.categories.model.Category;
import com.dotmarketing.portlets.contentlet.business.ContentletAPI;
import com.dotmarketing.portlets.contentlet.business.DotContentletStateException;
import com.dotmarketing.portlets.contentlet.business.DotContentletValidationException;
import com.dotmarketing.portlets.contentlet.business.HostAPI;
import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.dotmarketing.portlets.folders.business.FolderAPI;
import com.dotmarketing.portlets.folders.model.Folder;
import com.dotmarketing.portlets.languagesmanager.business.LanguageAPI;
import com.dotmarketing.portlets.structure.model.ContentletRelationships;
import com.dotmarketing.portlets.structure.model.Field;
import com.dotmarketing.portlets.structure.model.Relationship;
import com.dotmarketing.portlets.structure.model.Structure;
import com.dotmarketing.tag.business.TagAPI;
import com.dotmarketing.util.*;
import com.liferay.portal.language.LanguageUtil;
import com.liferay.portal.model.User;
import org.jetbrains.annotations.NotNull;

import java.io.StringWriter;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class LineImporter {

    private static final SimpleDateFormat DATE_FIELD_FORMAT = new SimpleDateFormat("yyyyMMdd");

    private static final String[] IMP_DATE_FORMATS = new String[] { "d-MMM-yy", "MMM-yy", "MMMM-yy", "d-MMM", "dd-MMM-yyyy",
            "MM/dd/yy hh:mm aa", "MM/dd/yyyy hh:mm aa",	"MM/dd/yy HH:mm", "MM/dd/yyyy HH:mm", "MMMM dd, yyyy", "M/d/y", "M/d",
            "EEEE, MMMM dd, yyyy", "MM/dd/yyyy", "hh:mm:ss aa", "HH:mm:ss", "hh:mm aa" };


    //API
    private final PermissionAPI permissionAPI = APILocator.getPermissionAPI();
    private final CategoryAPI catAPI = APILocator.getCategoryAPI();
    private final HostAPI hostAPI = APILocator.getHostAPI();
    private final ContentletAPI conAPI = APILocator.getContentletAPI();
    private final FolderAPI folderAPI = APILocator.getFolderAPI();
    private final LanguageAPI langAPI = APILocator.getLanguageAPI();

    private final ImportParams importParams;
    private final Structure structure;
    private final List<Permission> structurePermissions;
    private final boolean preview;
    private final User user;
    private final Map<String, List<String>> results;
    private final ImportFileInfo importFileInfo;

    private final StringBuilder choosenKeyField;
    private final Counters counters;
    private final UniqueFieldValues uniqueFieldValues;
    private final Set<String> keyContentUpdated;

    LineImporter(final ImportParams importParams,
                 final Structure structure, final List<Permission> structurePermissions,
                 final boolean preview, final User user,
                 final Map<String, List<String>> results, final ImportFileInfo importFileInfo) {

        this.importParams = importParams;
        this.structure = structure;
        this.structurePermissions = structurePermissions;
        this.preview = preview;
        this.user = user;
        this.results = results;
        this.importFileInfo = importFileInfo;

        this.choosenKeyField = new StringBuilder();
        this.counters = new Counters();
        this.uniqueFieldValues = new UniqueFieldValues();
        this.keyContentUpdated = new HashSet<>();

    }

    public String getChoosenKeyField() {
        return choosenKeyField.toString();
    }

    public Counters getCounters() {
        return counters;
    }

    public void importLine(String[] line, int lineNumber, long language) throws DotRuntimeException {

        try {

            // Building a values HashMap based on the headers/columns position
            final LineValues lineValues = getLineValues(line, lineNumber);

            //Check if line has repeated values for a unique field, if it does then ignore the line
            boolean ignoreLine = checkRepeatedValueForUniqueField(lineNumber, lineValues, language);

            if(!ignoreLine) {

                // Get related content
                final LineRelationships lineRelationships = getLineRelationships(line, lineNumber);

                //Searching existing contentlets to be updated by key fields
                final LineExistingContent lineExistingContent = getExistingContent(line, lineNumber, language, lineValues);
                final List<Contentlet> contentlets = lineExistingContent.getContentlets();

                //Creating/updating content
                final boolean isNew = addNewContentItem(contentlets, lineExistingContent, lineNumber, language);

                for (Contentlet cont : contentlets) {

                    List<Category> categoriesOnWorkingContent = null;
                    if (UtilMethods.isSet(cont.getIdentifier())) {
                        categoriesOnWorkingContent = catAPI.getParents(cont, user, false);
                    }

                    //Fill the new contentlet with the data
                    fillContentletData(lineValues, cont);

                    //DOTCMS-4528 Retaining Categories when content updated with partial imports
                    if (UtilMethods.isSet(cont.getIdentifier())) {
                        retainContentCategories(lineValues, categoriesOnWorkingContent);
                    }


                    //Check the new contentlet with the validator
                    validateContentlet(lineNumber, lineValues, cont);

                    //If not preview save the contentlet
                    if (!preview) {

                        if (!importParams.isSaveWithoutVersions()) {
                            cont.setInode(null);
                        }
                        cont.setLowIndexPriority(true);

                        //Load the old relationShips and add the new ones
                        ContentletRelationships contentletRelationships = conAPI.getAllRelationships(cont);
                        Map<Relationship, List<Contentlet>> relations = addNewContentRelationships(
                                lineRelationships, contentletRelationships);

                        // Save content
                        if (!isNew && importParams.isSaveWithoutVersions()) {
                            cont = conAPI.checkinWithoutVersioning(
                                    cont, relations, new ArrayList<>(lineValues.getCategories()),
                                    structurePermissions, user, false);

                        } else {
                            cont = conAPI.checkin(
                                    cont, contentletRelationships, new ArrayList<>(lineValues.getCategories()),
                                    structurePermissions, user, false);
                        }

                        // Publish content if required
                        if (importParams.isPublishContent()) {
                            Contentlet workingContentlet = conAPI.findContentletByIdentifier(
                                    cont.getIdentifier(),false, language, user, false);
                            if (workingContentlet != null) {
                                conAPI.publish(workingContentlet, user, false);
                            } else {
                                Logger.warn(this, "Line " + lineNumber
                                        + " not published: couldn't get working version");
                            }
                        }

                        // Content tags
                        addContentTags(lineValues, cont);

                        results.get("lastInode").clear();
                        List<String> l = results.get("lastInode");
                        l.add(cont.getInode());
                        results.put("lastInode", l);


                        if (isNew) {
                            counters.setContentCreated(counters.getContentCreated() + 1);
                        } else {
                            final String conditionValues = lineExistingContent.getConditionValues();
                            if (conditionValues.equals("") || !keyContentUpdated.contains(conditionValues)) {
                                counters.setContentUpdated(counters.getContentUpdated() + 1);
                                counters.setContentUpdatedDuplicated(counters.getContentUpdatedDuplicated() + 1);
                                keyContentUpdated.add(conditionValues);
                            } else {
                                counters.setContentUpdatedDuplicated(counters.getContentUpdatedDuplicated() + 1);
                            }

                        }
                    }

                }

            }

        } catch (Exception e) {
            Logger.error(LineImporter.class, "Error importing line # " + lineNumber + ": " + e.getMessage(),e);
            throw new DotRuntimeException("Error importing line: " + e.getMessage());
        }

    }

    private LineValues getLineValues(String[] line, int lineNumber) throws Exception {

        LineValues lineValues = new LineValues();

        for (Integer column : importFileInfo.getHeaders().keySet()) {
            Field field = importFileInfo.getHeaders().get(column);
            if (line.length < column) {
                throw new DotRuntimeException("Incomplete line found, the line #" + lineNumber +
                        " doesn't contain all the required columns.");
            }
            String value = line[column];
            Object valueObj = value;
            if (field.getFieldType().equals(Field.FieldType.DATE.toString())) {
                if (field.getFieldContentlet().startsWith("date")) {
                    if(UtilMethods.isSet(value)) {
                        try { valueObj = parseExcelDate(value) ;} catch (ParseException e) {
                            throw new DotRuntimeException("Line #" + lineNumber + " contains errors, Column: " + field.getFieldName() +
                                    ", value: " + value + ", couldn't be parsed as any of the following supported formats: " +
                                    printSupportedDateFormats());
                        }
                    } else {
                        valueObj = null;
                    }
                }
            } else if (field.getFieldType().equals(Field.FieldType.DATE_TIME.toString())) {
                if (field.getFieldContentlet().startsWith("date")) {
                    if(UtilMethods.isSet(value)) {
                        try { valueObj = parseExcelDate(value) ;} catch (ParseException e) {
                            throw new DotRuntimeException("Line #" + lineNumber + " contains errors, Column: " + field.getFieldName() +
                                    ", value: " + value + ", couldn't be parsed as any of the following supported formats: " +
                                    printSupportedDateFormats());
                        }
                    } else {
                        valueObj = null;
                    }
                }
            } else if (field.getFieldType().equals(Field.FieldType.TIME.toString())) {
                if (field.getFieldContentlet().startsWith("date")) {
                    if(UtilMethods.isSet(value)) {
                        try { valueObj = parseExcelDate(value) ;} catch (ParseException e) {
                            throw new DotRuntimeException("Line #" + lineNumber + " contains errors, Column: " + field.getFieldName() +
                                    ", value: " + value + ", couldn't be parsed as any of the following supported formats: " +
                                    printSupportedDateFormats());
                        }
                    } else {
                        valueObj = null;
                    }
                }
            } else if (field.getFieldType().equals(Field.FieldType.CATEGORY.toString()) || field.getFieldType().equals(Field.FieldType.CATEGORIES_TAB.toString())) {
                valueObj = value;
                if(UtilMethods.isSet(value)) {
                    String[] categoryKeys = value.split(",");
                    for(String catKey : categoryKeys) {
                        Category cat = catAPI.findByKey(catKey.trim(), user, false);
                        if(cat == null)
                            throw new DotRuntimeException("Line #" + lineNumber + " contains errors, Column: " + field.getFieldName() +
                                    ", value: " + value + ", invalid category key found, line will be ignored.");
                        lineValues.getCategories().add(cat);
                    }
                }
            }
            else if (field.getFieldType().equals(Field.FieldType.CHECKBOX.toString()) ||
                    field.getFieldType().equals(Field.FieldType.SELECT.toString()) ||
                    field.getFieldType().equals(Field.FieldType.MULTI_SELECT.toString()) ||
                    field.getFieldType().equals(Field.FieldType.RADIO.toString())
            ) {
                valueObj = value;
                if(UtilMethods.isSet(value))
                {


                    String fieldEntriesString = field.getValues();
                    String[] fieldEntries = fieldEntriesString.split("\n");
                    boolean found = false;
                    for(String fieldEntry : fieldEntries)
                    {
                        String[] splittedValue = fieldEntry.split("\\|");
                        String entryValue = splittedValue[splittedValue.length - 1].trim();

                        if(entryValue.equals(value) || value.contains(entryValue))
                        {
                            found = true;
                            break;
                        }
                    }
                    if(!found)
                    {
                        throw new DotRuntimeException("Line #" + lineNumber + " contains errors, Column: " + field.getFieldName() +
                                ", value: " + value + ", invalid value found, line will be ignored.");
                    }
                }
                else {
                    valueObj = null;
                }
            }
            else if (field.getFieldType().equals(Field.FieldType.TEXT.toString())) {
                if (value.length() > 255)
                    value = value.substring(0, 255);
                //valueObj = UtilMethods.escapeUnicodeCharsForHTML(value);
            }//http://jira.dotmarketing.net/browse/DOTCMS-3232
            else if (field.getFieldType().equals(Field.FieldType.HOST_OR_FOLDER.toString())) {
                Identifier id = null;
                valueObj = null;
                try{
                    id = APILocator.getIdentifierAPI().findFromInode(value);
                }
                catch(DotStateException dse){
                    Logger.debug(LineImporter.class, dse.getMessage());

                }

                if(id != null && InodeUtils.isSet(id.getInode())){
                    valueObj = value;
                    lineValues.setHeadersIncludeHostField(true);
                }else if(value.contains("//")){
                    String hostName=null;
                    StringWriter path = null;

                    String[] arr = value.split("/");
                    path = new StringWriter().append("/");


                    for(String y : arr){
                        if(UtilMethods.isSet(y) && hostName == null){
                            hostName = y;

                        }
                        else if(UtilMethods.isSet(y)){
                            path.append(y);
                            path.append("/");

                        }
                    }
                    Host host = APILocator.getHostAPI().findByName(hostName, user, false);
                    if(UtilMethods.isSet(host)){
                        valueObj=host.getIdentifier();
                        Folder f = APILocator.getFolderAPI().findFolderByPath(path.toString(), host, user, false);
                        if(UtilMethods.isSet(f))
                            valueObj=f.getInode();
                        lineValues.setHeadersIncludeHostField(true);
                    }
                }
                else{
                    Host h = APILocator.getHostAPI().findByName(value, user, false);
                    if(UtilMethods.isSet(h)){
                        valueObj=h.getIdentifier();
                        lineValues.setHeadersIncludeHostField(true);
                    }
                }

                if(valueObj ==null){
                    throw new DotRuntimeException("Line #" + lineNumber + " contains errors, Column: " + field.getFieldName() +
                            ", value: " + value + ", invalid host/folder inode found, line will be ignored.");

                }
            }else if(field.getFieldType().equals(Field.FieldType.IMAGE.toString()) || field.getFieldType().equals(Field.FieldType.FILE.toString())) {
                String filePath = value;
                if(field.getFieldType().equals(Field.FieldType.IMAGE.toString()) && !UtilMethods.isImage(filePath))
                {
                    //Add Warning the File isn't is an image
                    if(UtilMethods.isSet(filePath)){
                        String localLineMessage = LanguageUtil.get(user, "Line--");
                        String noImageFileMessage = LanguageUtil.get(user, "the-file-is-not-an-image");
                        results.get("warnings").add(localLineMessage + lineNumber + ". " + noImageFileMessage);
                    }
                    valueObj = null;
                }
                else
                {
                    //check if the path is relative to this host or not
                    Host fileHost = hostAPI.findDefaultHost(user,false);
                    if(filePath.indexOf(":") > -1)
                    {
                        String[] fileInfo = filePath.split(":");
                        if(fileInfo.length == 2)
                        {
                            Host fileHostAux = hostAPI.findByName(fileInfo[0], user, false);
                            fileHost = (UtilMethods.isSet(fileHostAux) ? fileHostAux : fileHost);
                            filePath = fileInfo[1];
                        }
                    }

                    Identifier id = APILocator.getIdentifierAPI().find(fileHost, filePath);
                    if(id!=null && InodeUtils.isSet(id.getId()) && id.getAssetType().equals("contentlet")){
                        Contentlet cont = APILocator.getContentletAPI().findContentletByIdentifier(id.getId(), true, APILocator.getLanguageAPI().getDefaultLanguage().getId(), user, false);
                        if(cont!=null && InodeUtils.isSet(cont.getInode())){
                            valueObj = cont.getIdentifier();
                        }else{
                            String localLineMessage = LanguageUtil.get(user, "Line--");
                            String noFileMessage = LanguageUtil.get(user, "The-file-has-not-been-found");
                            results.get("warnings").add(localLineMessage + lineNumber + ". " + noFileMessage + ": " + fileHost.getHostname() + ":" + filePath);
                            valueObj = null;
                        }
                    }
                }
            }
            else {
                valueObj = UtilMethods.escapeUnicodeCharsForHTML(value);
            }
            lineValues.getValues().put(column, valueObj);

        }

        return lineValues;

    }

    public LineRelationships getLineRelationships(String[] line, int lineNumber) throws Exception {

        LineRelationships lineRelationships = new LineRelationships();
        for (Integer column : importFileInfo.getRelationships().keySet()) {
            Relationship relationship = importFileInfo.getRelationships().get(column);
            String relatedQuery = line[column];
            List<Contentlet> relatedContentlets = new ArrayList<>();
            boolean error = false;
            if(UtilMethods.isSet(relatedQuery))
            {
                relatedContentlets = conAPI.checkoutWithQuery(relatedQuery, user, false);

                //validate if the contenlet retrieved are from the correct type
                if(FactoryLocator.getRelationshipFactory().isParent(relationship,structure))
                {
                    for(Contentlet contentlet : relatedContentlets)
                    {
                        Structure relatedStructure = contentlet.getStructure();
                        if(!(FactoryLocator.getRelationshipFactory().isChild(relationship,relatedStructure)))
                        {
                            error = true;
                            break;
                        }
                    }
                }
                if(FactoryLocator.getRelationshipFactory().isChild(relationship,structure))
                {
                    for(Contentlet contentlet : relatedContentlets)
                    {
                        Structure relatedStructure = contentlet.getStructure();
                        if(!(FactoryLocator.getRelationshipFactory()
                                .isParent(relationship,relatedStructure)))
                        {
                            error = true;
                            break;
                        }
                    }
                }
            }
            if(!error)
            {
                //If no error add the relatedContentlets
                if(importFileInfo.getOnlyChild().get(column))
                    lineRelationships.getCsvRelationshipRecordsChildOnly().put(relationship, relatedContentlets);
                else if(importFileInfo.getOnlyParent().get(column))
                    lineRelationships.getCsvRelationshipRecordsParentOnly().put(relationship, relatedContentlets);
                else
                    lineRelationships.getCsvRelationshipRecords().put(relationship, relatedContentlets);
            }
            else
            {
                //else add the error message
                String localLineMessage = LanguageUtil.get(user, "Line--");
                String structureDoesNoMatchMessage = LanguageUtil.get(user, "the-structure-does-not-match-the-relationship");
                results.get("warnings").add(localLineMessage + lineNumber + ". " + structureDoesNoMatchMessage);
            }
        }
        return lineRelationships;
    }

    public LineExistingContent getExistingContent(
            String[] line, int lineNumber, long language, LineValues lineValues) throws Exception {

        LineExistingContent lineExistingContent = new LineExistingContent();

        StringBuffer buffy = new StringBuffer();
        int identifierFieldIndex= -1;
        try {
            identifierFieldIndex = Integer.parseInt(results.get("identifiers").get(0));
        } catch (Exception e) {
        }

        if (-1 < identifierFieldIndex) {
            lineExistingContent.setIdentifier(line[identifierFieldIndex]);
        }

        if (importParams.isMultilanguage() || UtilMethods.isSet(lineExistingContent.getIdentifier()))
            buffy.append("+structureName:" + structure.getVelocityVarName() + " +working:true +deleted:false");
        else
            buffy.append("+structureName:" + structure.getVelocityVarName() + " +working:true +deleted:false +languageId:" + language);

        if (UtilMethods.isSet(lineExistingContent.getIdentifier())) {
            buffy.append(" +identifier:" + lineExistingContent.getIdentifier());

            List<ContentletSearch> contentsSearch = conAPI.searchIndex(buffy.toString(), 0, -1, null, user, true);

            if ((contentsSearch == null) || (contentsSearch.size() == 0)) {
                throw new DotRuntimeException("Line #" + lineNumber + ": Content not found with identifier " + lineExistingContent.getIdentifier() + "\n");
            } else {
                Contentlet contentlet;
                for (ContentletSearch contentSearch: contentsSearch) {
                    contentlet = conAPI.find(contentSearch.getInode(), user, true);
                    if ((contentlet != null) && InodeUtils.isSet(contentlet.getInode())) {
                        lineExistingContent.getContentlets().add(contentlet);
                    } else {
                        throw new DotRuntimeException("Line #" + lineNumber + ": Content not found with identifier " + lineExistingContent.getIdentifier() + "\n");
                    }
                }
            }
        } else if (importFileInfo.getKeyFields().size() > 0) {

            for (Integer column : importFileInfo.getKeyFields().keySet()) {
                Field field = importFileInfo.getKeyFields().get(column);
                Object value = lineValues.getValues().get(column);
                String text = null;
                if (value instanceof Date || value instanceof Timestamp) {
                    SimpleDateFormat formatter = null;
                    if(field.getFieldType().equals(Field.FieldType.DATE.toString())){
                        text = DATE_FIELD_FORMAT.format((Date)value);
                    }else if(field.getFieldType().equals(Field.FieldType.DATE_TIME.toString())){
                        DateFormat df = new SimpleDateFormat("MM/dd/yyyy");
                        text = df.format((Date)value);
                    }else if(field.getFieldType().equals(Field.FieldType.TIME.toString())) {
                        DateFormat df = new SimpleDateFormat("HHmmss");
                        text =  df.format((Date)value);
                    } else {
                        formatter = new SimpleDateFormat();
                        text = formatter.format(value);
                        Logger.warn(LineImporter.class,"importLine: field's date format is undetermined.");
                    }
                } else {
                    text = value.toString();
                }
                if(!UtilMethods.isSet(text)){
                    throw new DotRuntimeException("Line #" + lineNumber + " key field "+field.getFieldName()+" is required since it was defined as a key\n");
                }else{
                    if(field.getFieldType().equals(Field.FieldType.HOST_OR_FOLDER.toString()))
                        buffy.append(" +(conhost:" + text + " conFolder:" + text+")");
                    else
                        buffy.append(" +" + structure.getVelocityVarName() + "." + field.getVelocityVarName()
                                + ":" + (escapeLuceneSpecialCharacter(text).contains(" ")?"\""
                                + escapeLuceneSpecialCharacter(text)+"\"": escapeLuceneSpecialCharacter(text)));
                    lineExistingContent.addConditionValues(value + "-");
                }

                if(!field.isUnique()){
                    if(UtilMethods.isSet(choosenKeyField.toString())){
                        int count = 1;
                        String[] chosenArr = choosenKeyField.toString().split(",");
                        for(String chosen : chosenArr){
                            if(UtilMethods.isSet(chosen) && !field.getFieldName().equals(chosen.trim())){
                                count++;
                            }
                        }
                        if(chosenArr.length==count){
                            choosenKeyField.append(", "+field.getFieldName());
                        }
                    }else{
                        choosenKeyField.append(", "+field.getFieldName());
                    }
                }


            }
            List<ContentletSearch> cons = conAPI.searchIndex(buffy.toString(), 0, -1, null, user, true);
            for (ContentletSearch contentletSearch: cons) {
                final Contentlet con = conAPI.find(contentletSearch.getInode(), user, true);
                if ((con != null) && InodeUtils.isSet(con.getInode())) {
                    boolean columnExists = false;
                    for (Integer column : importFileInfo.getKeyFields().keySet()) {
                        Field field = importFileInfo.getKeyFields().get(column);
                        Object value = lineValues.getValues().get(column);
                        Object conValue = conAPI.getFieldValue(con, field);
                        if(field.getFieldType().equals(Field.FieldType.DATE.toString())
                                || field.getFieldType().equals(Field.FieldType.DATE_TIME.toString())
                                || field.getFieldType().equals(Field.FieldType.TIME.toString())){
                            if(field.getFieldType().equals(Field.FieldType.TIME.toString())){
                                DateFormat df = new SimpleDateFormat("HHmmss");
                                conValue = df.format((Date)conValue);
                                value = df.format((Date)value);
                            }else if(field.getFieldType().equals(Field.FieldType.DATE.toString())){
                                value = DATE_FIELD_FORMAT.format((Date)value);
                                conValue = DATE_FIELD_FORMAT.format((Date)conValue);
                            }else{
                                if(conValue instanceof Timestamp){
                                    value = new Timestamp(((Date)value).getTime());
                                }else if(conValue instanceof Date){
                                    DateFormat df = new SimpleDateFormat("MM/dd/yyyy");
                                    value = df.format((Date)value);
                                }
                            }
                            if(conValue.equals(value)){
                                columnExists = true;
                            }else{
                                columnExists = false;
                                break;
                            }
                        }else{
                            if(conValue.toString().equalsIgnoreCase(value.toString())){
                                columnExists = true;
                            }else{
                                columnExists = false;
                                break;
                            }
                        }
                    }
                    if(columnExists) {
                        lineExistingContent.getContentlets().add(con);
                    }
                }
            }
        }

        return lineExistingContent;

    }

    private boolean addNewContentItem(
            List<Contentlet> contentlets, LineExistingContent lineExistingContent,
            int lineNumber, long language) throws Exception {

        boolean isNew = false;

        if (contentlets.size() == 0) {
            counters.setNewContentCounter(counters.getNewContentCounter() + 1);
            isNew = true;
            //if (!preview) {
            Contentlet newCont = new Contentlet();
            newCont.setStructureInode(structure.getInode());
            newCont.setLanguageId(language);
            contentlets.add(newCont);
            //}
        } else {
            if (importParams.isMultilanguage() || UtilMethods.isSet(lineExistingContent.getIdentifier())) {
                List<Contentlet> multilingualContentlets = new ArrayList<Contentlet>();

                for (Contentlet contentlet: contentlets) {
                    if (contentlet.getLanguageId() == language)
                        multilingualContentlets.add(contentlet);
                }

                if (multilingualContentlets.size() == 0) {
                    String lastIdentifier = "" ;
                    isNew = true;
                    for (Contentlet contentlet: contentlets) {
                        if (!contentlet.getIdentifier().equals(lastIdentifier)) {
                            counters.setNewContentCounter(counters.getNewContentCounter() + 1);
                            Contentlet newCont = new Contentlet();
                            newCont.setIdentifier(contentlet.getIdentifier());
                            newCont.setStructureInode(structure.getInode());
                            newCont.setLanguageId(language);
                            multilingualContentlets.add(newCont);

                            lastIdentifier = contentlet.getIdentifier();
                        }
                    }
                }

                contentlets = multilingualContentlets;
            }

            if (!isNew) {
                final String conditionValues = lineExistingContent.getConditionValues();
                if (conditionValues.equals("") || !keyContentUpdated.contains(conditionValues) || importParams.isMultilanguage()) {
                    counters.setContentToUpdateCounter(counters.getContentToUpdateCounter()+contentlets.size());
                    if (preview)
                        keyContentUpdated.add(conditionValues);
                }
                if (contentlets.size() == 1) {//DOTCMS-5204
                    results.get("warnings").add(
                            LanguageUtil.get(user, "Line--") + lineNumber + ". "+ LanguageUtil.get(user, "The-key-fields-chosen-match-one-existing-content(s)")+" - "
                                    + LanguageUtil.get(user, "more-than-one-match-suggests-key(s)-are-not-properly-unique"));
                }else if (contentlets.size() > 1) {
                    results.get("warnings").add(
                            LanguageUtil.get(user, "Line--") + lineNumber + ". "+ LanguageUtil.get(user, "The-key-fields-choosen-match-more-than-one-content-in-this-case")+": "
                                    + " "+ LanguageUtil.get(user, "matches")+": " + contentlets.size() + " " +LanguageUtil.get(user, "different-content-s-looks-like-the-key-fields-choosen")+" " +
                                    LanguageUtil.get(user, "aren-t-a-real-key"));
                }
            }
        }

        return isNew;

    }

    private void fillContentletData(LineValues lineValues, Contentlet cont) throws Exception {

        for (Integer column : importFileInfo.getHeaders().keySet()) {

            Field field = importFileInfo.getHeaders().get(column);
            Object value = lineValues.getValues().get(column);

            if (field.getFieldType().equals(Field.FieldType.HOST_OR_FOLDER.toString())) { // DOTCMS-4484

                //Verify if the value belongs to a Host or to a Folder
                Folder folder = null;
                Host host = hostAPI.find( value.toString(), user, false );
                //If a host was not found using the given value (identifier) it must be a folder
                if ( !UtilMethods.isSet( host ) || !InodeUtils.isSet( host.getInode() ) ) {
                    folder = folderAPI.find( value.toString(), user, false );
                }

                if (folder != null && folder.getInode().equalsIgnoreCase(value.toString())) {

                    if (!permissionAPI.doesUserHavePermission(folder, PermissionAPI.PERMISSION_CAN_ADD_CHILDREN,user)) {
                        throw new DotSecurityException( "User have no Add Children Permissions on selected folder" );
                    }
                    cont.setHost(folder.getHostId());
                    cont.setFolder(value.toString());
                }
                else if(host != null) {
                    if (!permissionAPI.doesUserHavePermission(host,PermissionAPI.PERMISSION_CAN_ADD_CHILDREN,user)) {
                        throw new DotSecurityException("User have no Add Children Permissions on selected host");
                    }
                    cont.setHost(value.toString());
                    cont.setFolder(FolderAPI.SYSTEM_FOLDER);
                }
                continue;
            }

            if(UtilMethods.isSet(field.getDefaultValue()) && (!UtilMethods.isSet(String.valueOf(value)) || value==null)){
                value = field.getDefaultValue();
            }


            if(field.getFieldContentlet().startsWith("integer") || field.getFieldContentlet().startsWith("float")){
                if(!UtilMethods.isSet(String.valueOf(value)) && !field.isRequired()){
                    value = "0";
                }
            }
            try{
                conAPI.setContentletProperty(cont, field, value);
            }catch(DotContentletStateException de){
                if(!field.isRequired() || (value!=null && UtilMethods.isSet(String.valueOf(value)))){
                    throw de;
                }
            }

        }
    }

    private boolean checkRepeatedValueForUniqueField(
            final int lineNumber, final LineValues lineValues, final long language) throws Exception {

        boolean ignoreLine = false;
        for(int column : importFileInfo.getUniqueFields().keySet()) {
            Field field = importFileInfo.getUniqueFields().get(column);
            Object value = lineValues.getValues().get(column);

            if (uniqueFieldValues.existsValue(field.getVelocityVarName(), value, language)) {
                counters.setNewContentCounter(counters.getNewContentCounter() - 1);
                ignoreLine = true;
                results.get("warnings").add(LanguageUtil.get(user, "Line--") + " " + lineNumber +  " "
                        + LanguageUtil.get(user, "contains-duplicate-values-for-structure-unique-field")
                        + " " + field.getFieldName() + " "  +LanguageUtil.get(user, "and-will-be-ignored"));

            } else {
                uniqueFieldValues.addValue(field.getVelocityVarName(), value, language);
            }

        }
        return ignoreLine;

    }

    private void retainContentCategories(
            final LineValues lineValues,
            final List<Category> categoriesOnWorkingContent) throws Exception {

        List<Field> structureFields = FieldsCache.getFieldsByStructureInode(structure.getInode());
        List<Field> categoryFields = new ArrayList<Field>();
        List<Field> nonHeaderCategoryFields = new ArrayList<Field>();
        List<Category> nonHeaderParentCats = new ArrayList<Category>();
        List<Category> categoriesToRetain = new ArrayList<Category>();

        for(Field field : structureFields){
            if(field.getFieldType().equals(Field.FieldType.CATEGORY.toString()) || field.getFieldType().equals(Field.FieldType.CATEGORIES_TAB.toString()))
                categoryFields.add(field);
        }

        categoryFields.stream().filter(field -> {
            for (int column : importFileInfo.getHeaders().keySet()) {
                Field headerField = importFileInfo.getHeaders().get(column);
                if(headerField.getInode().equalsIgnoreCase(field.getInode())){
                    return false;
                }
            }
            return true;
        });

        nonHeaderCategoryFields.addAll(categoryFields);

        for(Field field : nonHeaderCategoryFields){
            nonHeaderParentCats.add(catAPI.find(field.getValues(), user, false));
        }

        for(Category cat : nonHeaderParentCats){
            categoriesToRetain.addAll(catAPI.getChildren(cat,false, user, false));
        }

        for(Category existingCat : categoriesOnWorkingContent){
            for(Category retainCat : categoriesToRetain){
                if(existingCat.compareTo(retainCat) == 0){
                    lineValues.getCategories().add(existingCat);
                }
            }
        }

    }

    private void validateContentlet(int lineNumber, LineValues lineValues, Contentlet cont) {
        try {
            conAPI.validateContentlet(cont,new ArrayList<Category>(lineValues.getCategories()));
        } catch(DotContentletValidationException ex) {
            StringBuffer sb = new StringBuffer("Line #" + lineNumber + " contains errors\n");
            HashMap<String, List<Field>> errors = (HashMap<String,List<Field>>) ex.getNotValidFields();
            Set<String> keys = errors.keySet();
            for(String key : keys)
            {
                sb.append(key + ": ");
                List<Field> fields = errors.get(key);
                int count = 0;
                for(Field field : fields){
                    if(count>0){
                        sb.append(", ");
                    }
                    sb.append(field.getFieldName());
                    count++;
                }
                sb.append("\n");
            }
            throw new DotRuntimeException(sb.toString());
        }
    }

    @NotNull
    private Map<Relationship, List<Contentlet>> addNewContentRelationships(
            LineRelationships lineRelationships, ContentletRelationships contentletRelationships) {

        List<ContentletRelationships.ContentletRelationshipRecords> relationshipRecords =
                contentletRelationships.getRelationshipsRecords();
        Map<Relationship,List<Contentlet>> relations = new HashMap<>();

        for(ContentletRelationships.ContentletRelationshipRecords relationshipRecord : relationshipRecords) {
            List<Contentlet> csvRelatedContentlet = lineRelationships.getCsvRelationshipRecords().get(
                    relationshipRecord.getRelationship());
            if(importParams.isSaveWithoutVersions()){
                if(UtilMethods.isSet(csvRelatedContentlet)) {
                    relations.put(relationshipRecord.getRelationship(), csvRelatedContentlet);
                }
            }else{
                if(UtilMethods.isSet(csvRelatedContentlet)) {
                    relationshipRecord.getRecords().addAll(csvRelatedContentlet);
                }
                csvRelatedContentlet = lineRelationships.getCsvRelationshipRecordsChildOnly().get(
                        relationshipRecord.getRelationship());
                if(UtilMethods.isSet(csvRelatedContentlet) && relationshipRecord.isHasParent()) {
                    relationshipRecord.getRecords().addAll(csvRelatedContentlet);
                }
                csvRelatedContentlet = lineRelationships.getCsvRelationshipRecordsParentOnly().get(
                        relationshipRecord.getRelationship());
                if(UtilMethods.isSet(csvRelatedContentlet) && !relationshipRecord.isHasParent()) {
                    relationshipRecord.getRecords().addAll(csvRelatedContentlet);
                }
            }
        }

        return relations;

    }

    private void addContentTags(LineValues lineValues, Contentlet cont) throws Exception {

        TagAPI tagapi = APILocator.getTagAPI();
        for (Integer column : importFileInfo.getHeaders().keySet()) {
            Field field = importFileInfo.getHeaders().get(column);
            Object value = lineValues.getValues().get(column);
            if (field.getFieldType().equals(Field.FieldType.TAG.toString()) &&
                    value instanceof String) {
                String[] tags = ((String)value).split(",");
                Host host = null;
                String hostId = "";
                if(lineValues.isHeadersIncludeHostField()){
                    //the csv has a Host Or Field Column, with a valid value
                    try{
                        host = APILocator.getHostAPI().find(cont.getHost(), user, true);
                    }catch(Exception e){
                        Logger.error(LineImporter.class, "Unable to get host from content");
                    }
                    if(UtilMethods.isSet(host)){
                        if(host.getIdentifier().equals(Host.SYSTEM_HOST))
                            hostId = Host.SYSTEM_HOST;
                        else
                            hostId = host.getIdentifier();
                    }
                    else{
                        hostId = Host.SYSTEM_HOST;
                    }
                    for (String tag : tags) {
                        tagapi.addContentletTagInode((String)tag.trim(), cont.getInode(), hostId, field.getVelocityVarName());
                    }
                }
                else {
                    for (String tagName : tags)
                        try {
                            tagapi.addContentletTagInode(tagName.trim(), cont.getInode(), Host.SYSTEM_HOST, field.getVelocityVarName());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                }
            }
        }

    }

    private static String printSupportedDateFormats () {
        StringBuffer ret = new StringBuffer("[ ");
        for (String pattern : IMP_DATE_FORMATS) {
            ret.append(pattern + ", ");
        }
        ret.append(" ] ");
        return ret.toString();
    }

    private static Date parseExcelDate (String date) throws ParseException {
        return DateUtil.convertDate(date, IMP_DATE_FORMATS);
    }

    /**
     * Escape lucene reserved characters
     * @param text
     * @return String
     */
    private static String escapeLuceneSpecialCharacter(String text){
        text = text.replaceAll("\\[","\\\\[").replaceAll("\\]","\\\\]");
        text = text.replaceAll("\\{","\\\\{").replaceAll("\\}","\\\\}");
        text = text.replaceAll("\\+","\\\\+").replaceAll(":","\\\\:");
        text = text.replaceAll("\\*","\\\\*").replaceAll("\\?","\\\\?");
        text = text.replaceAll("\\(","\\\\(").replaceAll("\\)","\\\\)");
        text = text.replaceAll("&&","\\\\&&").replaceAll("\\|\\|","\\\\||");
        text = text.replaceAll("!","\\\\!").replaceAll("\\^","\\\\^");
        text = text.replaceAll("-","\\\\-").replaceAll("~","\\\\~");
        text = text.replaceAll("\"","\\\"");

        return text;
    }

}
