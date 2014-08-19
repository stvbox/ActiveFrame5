package ru.intertrust.cm.core.business.api.notification;


import ru.intertrust.cm.core.business.api.dto.notification.NotificationContext;

/**
 * Классы имплиментирующие NotificationContextObjectProducer могут на основание
 * существующих объектов в контексте создавать новые и возвращать их в методе getContextObject.
 */
public interface NotificationContextObjectProducer {
    Object getContextObject(NotificationContext context);
}