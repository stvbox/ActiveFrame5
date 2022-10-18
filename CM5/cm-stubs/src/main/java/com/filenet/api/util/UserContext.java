package com.filenet.api.util;

import com.filenet.api.core.Connection;

import javax.security.auth.Subject;

public class UserContext {
    public static UserContext get() {
        return null;
    }

    public static Subject createSubject(Connection conn, String login, String password, Object o) {
        return null;
    }

    public void popSubject() {
    }

    public void pushSubject(Subject subject) {
    }
}
