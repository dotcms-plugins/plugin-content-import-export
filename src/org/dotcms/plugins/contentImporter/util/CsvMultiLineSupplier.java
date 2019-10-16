package org.dotcms.plugins.contentImporter.util;

import com.dotcms.repackage.com.csvreader.CsvReader;
import com.dotmarketing.exception.DotRuntimeException;
import com.dotmarketing.util.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class CsvMultiLineSupplier {

    private class CsvLineSupplier implements Supplier<CsvLine> {

        private int lineNumber;

        public CsvLineSupplier() {
            this.lineNumber = 1;
        }

        @Override
        public CsvLine get() {
            try {
                if (!readCompleted && csvReader.readRecord()) {
                    lineNumber++;
                    Logger.debug(FileImporter.class,
                            "Line " + lineNumber + ": (" + csvReader.getRawRecord() + ").");
                    Logger.info(FileImporter.class,
                            lineNumber + ". (" + csvReader.getRawRecord() + ")");
                    return new CsvLine(lineNumber, csvReader.getValues());
                } else {
                    readCompleted = true;
                    return null;
                }
            } catch (IOException e) {
                Logger.error(this, "Error reading file" + e, e);
                throw new DotRuntimeException("Error reading file: " + e);
            }
        }
    }

    private final CsvReader csvReader;
    private final int linesPerBlock;
    private final CsvLineSupplier csvLineSupplier;
    private boolean readCompleted;

    public CsvMultiLineSupplier(final CsvReader csvReader, final int linesPerBlock) {
        this.csvReader = csvReader;
        this.linesPerBlock = linesPerBlock;
        this.csvLineSupplier = new CsvLineSupplier();
        this.readCompleted = false;
    }

    public List<CsvLine> getNextLineBlock() {

        if (readCompleted) {
            return null;
        }

        final List<CsvLine> lines = new ArrayList<>();
        for (int i = 0; i < linesPerBlock; i++) {
            CsvLine line = csvLineSupplier.get();
            if (line == null) {
                break;
            } else {
                lines.add(line);
            }
        }
        if (lines.isEmpty()) {
            return null;
        } else {
            return lines;
        }

    }

}
