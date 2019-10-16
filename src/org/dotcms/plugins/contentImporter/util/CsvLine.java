package org.dotcms.plugins.contentImporter.util;

public class CsvLine {

    private final int lineNumber;
    private final String[] line;

    public CsvLine(final int lineNumber, final String [] line) {
        this.lineNumber = lineNumber;
        this.line = line;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String[] getLine() {
        return line;
    }

}
