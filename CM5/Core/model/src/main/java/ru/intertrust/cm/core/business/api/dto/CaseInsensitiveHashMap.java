package ru.intertrust.cm.core.business.api.dto;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Denis Mitavskiy
 *         Date: 25.02.14
 *         Time: 15:05
 */
public class CaseInsensitiveHashMap<T> implements Map<String, T>, Dto {
    private HashMap<String, T> map;

    public CaseInsensitiveHashMap() {
        map = new HashMap<>();
    }

    public CaseInsensitiveHashMap(int initialCapacity) {
        map = new HashMap<>(initialCapacity);
    }

    public CaseInsensitiveHashMap(int initialCapacity, float loadFactor) {
        map = new HashMap<>(initialCapacity, loadFactor);
    }

    public CaseInsensitiveHashMap(Map<? extends String, ? extends T> m) {
        map = new HashMap<>(m);
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return map.containsKey(((String) key).toLowerCase());
    }

    @Override
    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    @Override
    public T get(Object key) {
        return map.get(((String) key).toLowerCase());
    }

    @Override
    public T put(String key, T value) {
        return map.put(key.toLowerCase(), value);
    }

    @Override
    public T remove(Object key) {
        return map.remove(((String) key).toLowerCase());
    }

    @Override
    public void putAll(Map<? extends String, ? extends T> m) {
        map.putAll(m);
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public Set<String> keySet() {
        return map.keySet();
    }

    @Override
    public Collection<T> values() {
        return map.values();
    }

    @Override
    public Set<Entry<String, T>> entrySet() {
        return map.entrySet();
    }
}
