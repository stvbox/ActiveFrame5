package ru.intertrust.cm.core.business.impl.report;

import net.sf.jasperreports.engine.export.*;
import net.sf.jasperreports.engine.export.ooxml.JRDocxExporter;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.engine.export.ooxml.SochiJRDocxExporter;
import net.sf.jasperreports.export.Exporter;
import net.sf.jasperreports.export.ExporterConfiguration;
import net.sf.jasperreports.export.ExporterInput;
import net.sf.jasperreports.export.ExporterOutput;
import net.sf.jasperreports.export.ReportExportConfiguration;
import org.junit.Test;

import static org.junit.Assert.*;

public class ExporterProviderTest {

    private final DummyExporterConfiguration configuration = new DummyExporterConfiguration();
    private final DummyCsvExporterConfiguration csvConfiguration = new DummyCsvExporterConfiguration();

    @Test
    public void htmlExporterProvider() {
        HtmlExporterProvider htmlExporterProvider = new HtmlExporterProvider();
        Exporter<ExporterInput, ReportExportConfiguration, ExporterConfiguration, ExporterOutput> exporter
                = htmlExporterProvider.getExporter(configuration);

        assertEquals(HtmlExporter.class, exporter.getClass());
        assertEquals(ReportBuilderFormats.HTML_FORMAT.getFormat(), htmlExporterProvider.getExtension());
        assertEquals(ReportBuilderFormats.HTML_FORMAT.getFormat(), htmlExporterProvider.getType());
    }

    @Test
    public void jRDocxExporterProvider() {
        JRDocxExporterProvider provider = new JRDocxExporterProvider();
        Exporter<ExporterInput, ReportExportConfiguration, ExporterConfiguration, ExporterOutput> exporter
                = provider.getExporter(configuration);

        assertEquals(JRDocxExporter.class, exporter.getClass());
        assertEquals(ReportBuilderFormats.DOCX_FORMAT.getFormat(), provider.getExtension());
        assertEquals(ReportBuilderFormats.DOCX_FORMAT.getFormat(), provider.getType());
    }

    @Test
    public void jRPdfExporterProvider() {
        JRPdfExporterProvider provider = new JRPdfExporterProvider();
        Exporter<ExporterInput, ReportExportConfiguration, ExporterConfiguration, ExporterOutput> exporter
                = provider.getExporter(configuration);

        assertEquals(JRPdfExporter.class, exporter.getClass());
        assertEquals(ReportBuilderFormats.PDF_FORMAT.getFormat(), provider.getExtension());
        assertEquals(ReportBuilderFormats.PDF_FORMAT.getFormat(), provider.getType());
    }

    @Test
    public void jRRtfExporterProvider() {
        JRRtfExporterProvider provider = new JRRtfExporterProvider();
        Exporter<ExporterInput, ReportExportConfiguration, ExporterConfiguration, ExporterOutput> exporter
                = provider.getExporter(configuration);

        assertEquals(JRRtfExporter.class, exporter.getClass());
        assertEquals(ReportBuilderFormats.RTF_FORMAT.getFormat(), provider.getExtension());
        assertEquals(ReportBuilderFormats.RTF_FORMAT.getFormat(), provider.getType());
    }

    @Test
    public void jRXlsExporterProvider() {
        JRXlsExporterProvider provider = new JRXlsExporterProvider();
        Exporter<ExporterInput, ReportExportConfiguration, ExporterConfiguration, ExporterOutput> exporter
                = provider.getExporter(configuration);

        assertEquals(JRXlsExporter.class, exporter.getClass());
        assertEquals(ReportBuilderFormats.XLS_FORMAT.getFormat(), provider.getExtension());
        assertEquals(ReportBuilderFormats.XLS_FORMAT.getFormat(), provider.getType());
    }

    @Test
    public void jRXlsxExporterProvider() {
        JRXlsxExporterProvider provider = new JRXlsxExporterProvider();
        Exporter<ExporterInput, ReportExportConfiguration, ExporterConfiguration, ExporterOutput> exporter
                = provider.getExporter(configuration);

        assertEquals(JRXlsxExporter.class, exporter.getClass());
        assertEquals(ReportBuilderFormats.XLSX_FORMAT.getFormat(), provider.getExtension());
        assertEquals(ReportBuilderFormats.XLSX_FORMAT.getFormat(), provider.getType());
    }

    @Test
    public void jRCsvExporterProvider() {
        JRCsvExporterProvider provider = new JRCsvExporterProvider();
        Exporter<ExporterInput, ReportExportConfiguration, ExporterConfiguration, ExporterOutput> exporter
                = provider.getExporter(csvConfiguration);

        assertEquals(JRCsvExporter.class, exporter.getClass());
        assertEquals(ReportBuilderFormats.CSV_FORMAT.getFormat(), provider.getExtension());
        assertEquals(ReportBuilderFormats.CSV_FORMAT.getFormat(), provider.getType());
    }

    @Test
    public void sochiJRDocxExporterProvider() {
        SochiJRDocxExporterProvider provider = new SochiJRDocxExporterProvider();
        Exporter<ExporterInput, ReportExportConfiguration, ExporterConfiguration, ExporterOutput> exporter
                = provider.getExporter(configuration);

        assertEquals(SochiJRDocxExporter.class, exporter.getClass());
        assertEquals(ReportBuilderFormats.DOCX_FORMAT.getFormat(), provider.getExtension());
        assertEquals(ReportBuilderFormats.SOCHI_DOCX_FORMAT.getFormat(), provider.getType());
    }

}