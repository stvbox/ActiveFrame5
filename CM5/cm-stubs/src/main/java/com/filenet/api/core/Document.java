package com.filenet.api.core;

import com.filenet.api.collection.ContentElementList;

import java.util.jar.Attributes;

public class Document {
    public void delete() {
    }

    public void save(Object noRefresh) {
    }

    public ContentElementList get_ContentElements() {
        return null;
    }

    public void checkin(Object doNotAutoClassify, Object majorVersion) {
    }

    public void set_ContentElements(ContentElementList contentList) {
    }

    public Object get_Reservation() {
        return null;
    }

    public Attributes getProperties() {
        return null;
    }

    public Document createInstance(ObjectStore os, Object document) {
        return null;
    }

    public Object getClassName() {
        return null;
    }

    public void checkout(Object exclusive, Object o, Object className, Attributes properties) {
    }

    public Document fetchInstance(ObjectStore os, String path, Object o) {
        return null;
    }

    public Document getInstance(ObjectStore os, Object document, String path) {
        return null;
    }
}
