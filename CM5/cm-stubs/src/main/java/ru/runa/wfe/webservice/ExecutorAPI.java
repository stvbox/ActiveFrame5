package ru.runa.wfe.webservice;

import java.util.List;

public class ExecutorAPI {
    public List<Actor> getGroupActors(User user, Group group) {
        return null;
    }

    public boolean isExecutorExist(User user, String name) {
        return false;
    }

    public WfExecutor getExecutorByName(User user, String name) {
        return null;
    }

    public WfExecutor create(User user, WfExecutor executor) {
        return null;
    }

    public List<WfExecutor> getAllExecutorsFromGroup(User user, Group group) {
        return null;
    }

    public void addExecutorsToGroup(User user, List<Long> addExecutors, Object id) {

    }

    public void removeExecutorsFromGroup(User user, List<Long> deleteExecutors, Object id) {

    }

    public Actor getActorCaseInsensitive(String name) {
        return null;
    }

    public void setStatus(User user, Actor actor, boolean active) {

    }

    public void setPassword(User user, Actor actor, String userPassword) {

    }
}
