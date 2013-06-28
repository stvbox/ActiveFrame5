package ru.intertrust.cm.core.dao.exception;

import ru.intertrust.cm.core.business.api.dto.Id;

/**
 * Представляет случай когда не существует объектов с данным  идентификатором
 * @author skashanski
 *
 */
public class ObjectNotFoundException extends DaoException {

    private final Id id;

    public ObjectNotFoundException(Id id) {
        super();
        this.id = id;
    }

}
