package net.sf.jasperreports.extensions;

import java.util.List;

public interface ExtensionsRegistry {
    <T> List<T> getExtensions(Class<T> extensionType);
}
