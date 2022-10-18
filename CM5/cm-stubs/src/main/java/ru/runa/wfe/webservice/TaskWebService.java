package ru.runa.wfe.webservice;

import javax.xml.ws.Service;
import java.net.URL;

public class TaskWebService extends Service {
    public TaskWebService(URL serviceUrl) {
        super(serviceUrl, null);
    }

    public TaskAPI getTaskAPIPort() {
        return null;
    }
}
