package net.sf.jasperreports.engine.export.ooxml;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.export.CutsInfo;

import java.io.Writer;

public abstract class DocxTableHelper {
    public DocxTableHelper(JasperReportsContext jasperReportsContext, Writer writer, CutsInfo xCuts, boolean pageBreak, PrintPageFormat pageFormat, JRPrintElementIndex frameIndex) {

    }

    public abstract void exportRowHeader(int rowHeight, boolean allowRowResize);

    protected void write(String html) {

    }

    public void exportHeader() {
    }

    public void setRowMaxTopPadding(int maxTopPadding) {
    }

    public void exportOccupiedCells(ElementGridCell elementGridCell, boolean startPage, int bookmarkIndex, Object pageAnchor) {
    }

    public void exportEmptyCell(JRExporterGridCell gridCell, int i, boolean startPage, int bookmarkIndex, Object pageAnchor) {
    }

    public void exportRowFooter() {
    }

    public void exportFooter() {
    }

    public FooStub getParagraphHelper() {
        return new FooStub();
    }

    class FooStub {
        public void exportEmptyPage(Object pageAnchor, int bookmarkIndex, boolean twice) {

        }
    }
}
