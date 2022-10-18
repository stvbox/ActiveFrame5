package net.sf.jasperreports.export;

public interface CsvExporterConfiguration extends ExporterConfiguration {
    public abstract String getFieldDelimiter();

    public abstract String getFieldEnclosure();

    public abstract Boolean getForceFieldEnclosure();

    public abstract String getRecordDelimiter();

    public abstract Boolean isWriteBOM();
}
