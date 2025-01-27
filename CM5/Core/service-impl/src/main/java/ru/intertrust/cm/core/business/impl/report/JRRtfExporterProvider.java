package ru.intertrust.cm.core.business.impl.report;

import net.sf.jasperreports.engine.export.JRRtfExporter;
import net.sf.jasperreports.export.Exporter;
import net.sf.jasperreports.export.ExporterConfiguration;
import net.sf.jasperreports.export.ExporterInput;
import net.sf.jasperreports.export.ExporterOutput;
import net.sf.jasperreports.export.ReportExportConfiguration;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimpleWriterExporterOutput;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;

@Service
public class JRRtfExporterProvider implements ExporterProvider {

    @Override
    public Exporter<ExporterInput, ReportExportConfiguration, ExporterConfiguration, ExporterOutput> getExporter(ExporterConfiguration configuration) {
        return (Exporter) new JRRtfExporter();
    }

    @Override
    public String getType() {
        return ReportBuilderFormats.RTF_FORMAT.getFormat();
    }

    @Override
    public void setExporterOutput(Exporter<ExporterInput, ReportExportConfiguration, ExporterConfiguration, ExporterOutput> exporter, FileOutputStream fos) {
        SimpleWriterExporterOutput output = new SimpleWriterExporterOutput(fos);
        exporter.setExporterOutput(output);
    }

    @Override
    public String getExtension() {
        return ReportBuilderFormats.RTF_FORMAT.getFormat();
    }
}
