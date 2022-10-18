package net.sf.jasperreports.export;

import java.io.Writer;

public interface WriterExporterOutput {
    String getEncoding();

    Writer getWriter();

    void close();
}
