package ru.runa.wfe.webservice;

import javax.xml.namespace.QName;
import javax.xml.ws.Service;
import java.net.URL;

public class AuthenticationWebService extends Service {
    public AuthenticationWebService(URL serviceUrl) {
        super(serviceUrl, null);
    }

    public AuthenticationAPI getAuthenticationAPIPort() {
        return null;
    }
}
