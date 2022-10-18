package ru.runa.wfe.webservice;

import javax.xml.ws.Service;
import java.net.URL;

public class ExecutorWebService extends Service {
    public ExecutorWebService(URL serviceUrl) {
        super(serviceUrl, null);
    }

    public ExecutorAPI getExecutorAPIPort() {
        return null;
    }
}
