package net.sf.jasperreports.repo;

public interface RepositoryService {
    public abstract Resource getResource(String uri);

    public abstract void saveResource(String uri, Resource resource);

    public abstract <K extends Resource> K getResource(String uri, Class<K> resourceType);
}
