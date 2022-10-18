package net.sf.jasperreports.engine;

public class JRExporterGridCell extends OccupiedGridCell implements ElementGridCell {
    public static final Object TYPE_OCCUPIED_CELL = "TYPE_OCCUPIED_CELL";
    public static final Object TYPE_ELEMENT_CELL = "TYPE_ELEMENT_CELL";

    public JRLineBox getBox() {
        return null;
    }

    public JRPrintElement getElement() {
        return null;
    }

    public Object getType() {
        return null;
    }

    public int getColSpan() {
        return 0;
    }
}
