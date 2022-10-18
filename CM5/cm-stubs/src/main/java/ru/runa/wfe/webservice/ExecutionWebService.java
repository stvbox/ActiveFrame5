package ru.runa.wfe.webservice;

import javax.xml.ws.Service;
import java.net.URL;

public class ExecutionWebService extends Service {
    public ExecutionWebService(URL serviceUrl) {
        super(serviceUrl, null);
    }

    public ExecutionAPI getExecutionAPIPort() {
        return null;
    }
}
