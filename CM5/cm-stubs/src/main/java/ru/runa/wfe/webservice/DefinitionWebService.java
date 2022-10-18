package ru.runa.wfe.webservice;

import javax.xml.namespace.QName;
import javax.xml.ws.Service;
import java.net.URL;

public class DefinitionWebService extends Service {
    public DefinitionWebService(URL serviceUrl) {
        super(serviceUrl, null);
    }

    public DefinitionAPI getDefinitionAPIPort() {
        return null;
    }
}
