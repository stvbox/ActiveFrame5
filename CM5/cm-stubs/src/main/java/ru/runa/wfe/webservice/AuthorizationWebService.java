package ru.runa.wfe.webservice;

import javax.xml.ws.Service;
import java.net.URL;

public class AuthorizationWebService extends Service {
    public AuthorizationWebService(URL serviceUrl) {
        super(serviceUrl, null);
    }

    public AuthorizationAPI getAuthorizationAPIPort() {
        return null;
    }
}
