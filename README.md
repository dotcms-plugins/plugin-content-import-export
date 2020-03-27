dotCMS Content Import/Export Plugin
======================================
This plugin allows the configuration of a Quartz Job which imports and exports content automatically.

The plugin contains a portlet that shows the queue of content to be imported and exported in a separate list. It also allows you 
to add, edit and remove import/export quartz jobs. This version includes two options in the import process:

1. Update existing contents without generating new versions.
2. Delete all existing contents in the Content Type before running the import.


Important:
=========
Once you download the code from GitHub, you will need to rename the folder where this plugin will be installed in your 
`${DOTCMS_HOME}/plugins/` directory to: `org.dotcms.plugins.contentImporter`


Configuration:
==============
The following properties on can be configured in the `{$PLUGIN_HOME}/conf/plugin.properties` file:

1. `portlet.role`: The role that a user must belong to in order to add and remove content import tasks in the back-end. 
It's recommended to leave the default value as it is: `portlet.role=Content Importer`
2. `processedFilePath`: Indicates where the files are going to be moved once they have been processed:
`processedFilePath=/DotCMS/Uploads/Processed`
3. `logFile`: Indicates where the log files of each imnport/export file are saved after they have been processed:
`logFile=/DotCMS/Content_Importer_Logs`
4. `exportedFilePath`: Indicates where the exported files are going to be created (this is the default value for the export):
`exportedFilePath=/DotCMS/exported`
5. Add the Content Import/Export portlet to your main menu in dotCMS.


Creating, editing, and deleting an Export Task 
------------------------------------
In the Content Import/Export portlet, enter the following information:

1. The task name.
2. The task description.
3. The task execution properties (when and for how long the task should be executed). Here you could use a cron expression 
or use the configuration properties.
4. The Content Type that you will be exporting.
5. The Language of the content to be exported (single or multi-language).
6. The Fields to export from the Content Type. If no fields are selected, all the fields in the Content Type will be exported. 
If some fields are selected, just those and the system fields mentioned next will be exported to the CSV file: identifier, 
language and country.
7. The File Path where the CSV file(s) zip file will be located: 
7.a. `File in the server's filesystem`: Here you should specify an existing folder in the server running dotCMS where all 
the compressed CSV file will be exported.
7.b. `Content File`: In this case, the exported file will be stored as a File Asset in your dotCMS instance. You will need 
to specify the File Content Type to be assigned to the file and the combination of the Site plus folder where the file will 
be stored.
8. `Overwrite export file?`: When checked, every execution of the job will overwrite the previously generated file. If not, 
a new file will be created on every job execution.
9. The report email. Use this parameter if you want to to receive an email notification with the compressed CSV file every 
time the content export runs. This value can be a comma separated list of emails if more than one person should receive the 
exported file.
10. The CSV Separator Delimiter. This parameter indicates the character that is going to be used to separate the values of the 
fields in each row in the CSV file.
11. The CSV Text Delimiter. Specify if you use a `"`, `'`, or another character to indicate when a text end.


Creating, editing, and deleting an Import Task 
------------------------------------
In the Content Import/Export portlet, enter the following information:

1. The task name.
2. The task description.
3. The task execution properties (when and for how long the task should be executed). You can use a CRON expression or use 
the configuration properties.
4. The Content Type where the content will be imported.
5. The Language of the content to be imported (single or multi-language).
6. The Key Fields of the Content Type, in case you want the import to update existing content.
7. The source from where the content to be imported can be loaded:
7.a `File in the server's filesystem`: In this case, the CSV file will be read as a file in the file system. You will need 
to enter the path of the folder from where any file with extension .CSV will be read.
7.b `Content File`: In this case, the CSV will be a File Asset read as content from your dotCMS instance. You will need to 
enter the File Content Type associated to the file. You can also define a lucene query that will allow you to filter the 
files under the specified Content Type that will be pulled and loaded during the execution of the import.
8. The report email. Use this parameter if you want to to receive a notification every time the content import runs. The email 
will indicate if the process finishes successfully or if there any errors importing the file.
9. The CSV Separator Delimiter. This parameter indicates the character that is used to separate the values of the fields for 
each row in the CSV file.
10. The CSV Text Delimiter. Specify if you use a `"`, `'`, or another character to indicate when a text end.
11. The Publish property. Check it only if you want the process automatically publish the content that will be imported.
12. The Override existing content version? property. Check it, if you want that the import process don't generate a new version 
of the existing content.
13. The Delete Content Type contents? property. Check it, if you want to delete all the contents in the Content Type before 
doing the import.
