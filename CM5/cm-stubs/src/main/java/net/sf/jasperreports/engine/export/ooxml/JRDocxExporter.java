package net.sf.jasperreports.engine.export.ooxml;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.export.*;

import java.io.Writer;

public class JRDocxExporter extends Exporter {
    protected static final Object EXCEPTION_MESSAGE_KEY_COLUMN_COUNT_OUT_OF_RANGE = "EXCEPTION_MESSAGE_KEY_COLUMN_COUNT_OUT_OF_RANGE";

    protected long pageIndex;
    protected long endPageIndex;
    protected boolean emptyPageState;

    protected JasperReportsContext jasperReportsContext;
    protected Writer docWriter;
    protected PrintPageFormat pageFormat;
    protected DeployReportData exporterInput;
    protected long startPageIndex;
    protected int reportIndex;
    protected Object pageAnchor;
    protected int bookmarkIndex;
    protected boolean startPage;

    protected FooStub getCurrentItemConfiguration() {
        return new FooStub();
    }

    protected void exportGenericElement(DocxTableHelper tableHelper, JRGenericPrintElement element, JRExporterGridCell gridCell) {

    }

    protected void exportEllipse(DocxTableHelper tableHelper, JRPrintEllipse element, JRExporterGridCell gridCell) {

    }

    protected void exportLine(DocxTableHelper tableHelper, JRPrintLine element, JRExporterGridCell gridCell) {

    }

    protected void exportImage(DocxTableHelper tableHelper, JRPrintImage element, JRExporterGridCell gridCell) {
    }

    protected void exportText(DocxTableHelper tableHelper, JRPrintText element, JRExporterGridCell gridCell) {

    }

    protected void exportRectangle(DocxTableHelper tableHelper, JRPrintRectangle element, JRExporterGridCell gridCell) {

    }

    protected void exportFrame(DocxTableHelper tableHelper, JRPrintFrame element, JRExporterGridCell gridCell) {

    }

    protected void exportGrid(JRGridLayout gridLayout, JRPrintElementIndex frameIndex) throws JRException {

    }

    class FooStub {
        public boolean isFlexibleRowHeight() {
            return false;
        }
    }
}
