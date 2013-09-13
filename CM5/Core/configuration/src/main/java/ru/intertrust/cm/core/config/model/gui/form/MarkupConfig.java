package ru.intertrust.cm.core.config.model.gui.form;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import ru.intertrust.cm.core.business.api.dto.Dto;

/**
 * @author Denis Mitavskiy
 *         Date: 11.09.13
 *         Time: 20:13
 */
@Root(name = "markup")
public class MarkupConfig implements Dto {
    @Element(name = "header")
    private HeaderConfig header;

    @Element(name = "body")
    private BodyConfig body;

    public HeaderConfig getHeader() {
        return header;
    }

    public void setHeader(HeaderConfig header) {
        this.header = header;
    }

    public BodyConfig getBody() {
        return body;
    }

    public void setBody(BodyConfig body) {
        this.body = body;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MarkupConfig that = (MarkupConfig) o;

        if (body != null ? !body.equals(that.body) : that.body != null) {
            return false;
        }
        if (header != null ? !header.equals(that.header) : that.header != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = header != null ? header.hashCode() : 0;
        result = 31 * result + (body != null ? body.hashCode() : 0);
        return result;
    }
}
