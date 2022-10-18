package net.sf.jasperreports.export;

import java.io.FileOutputStream;
import java.io.Writer;

public class SimpleHtmlExporterOutput implements WriterExporterOutput {
    public SimpleHtmlExporterOutput(FileOutputStream fos) {

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
