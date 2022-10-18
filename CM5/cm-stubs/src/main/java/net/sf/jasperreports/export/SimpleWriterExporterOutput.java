package net.sf.jasperreports.export;

import java.io.FileOutputStream;
import java.io.Writer;

public class SimpleWriterExporterOutput implements WriterExporterOutput {
    public SimpleWriterExporterOutput(FileOutputStream fos) {

    }

    @Override
    public String getEncoding() {
        return null;
    }

    @Override
    public Writer getWriter() {
        return null;
    }

    @Override
    public void close() {

    }
}
