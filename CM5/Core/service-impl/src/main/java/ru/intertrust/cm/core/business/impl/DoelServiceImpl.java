package ru.intertrust.cm.core.business.impl;

import java.util.List;

import javax.ejb.Local;
import javax.ejb.Remote;
import javax.ejb.Stateless;
import javax.interceptor.Interceptors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ejb.interceptor.SpringBeanAutowiringInterceptor;

import ru.intertrust.cm.core.business.api.DoelService;
import ru.intertrust.cm.core.business.api.dto.Id;
import ru.intertrust.cm.core.business.api.dto.Value;
import ru.intertrust.cm.core.config.doel.DoelExpression;
import ru.intertrust.cm.core.dao.access.AccessControlService;
import ru.intertrust.cm.core.dao.access.AccessToken;
import ru.intertrust.cm.core.dao.api.CurrentUserAccessor;
import ru.intertrust.cm.core.dao.api.DoelEvaluator;
import ru.intertrust.cm.core.model.SystemException;
import ru.intertrust.cm.core.model.UnexpectedException;

@Stateless(name = "DoelService")
@Local(DoelService.class)
@Remote(DoelService.Remote.class)
@Interceptors(SpringBeanAutowiringInterceptor.class)
public class DoelServiceImpl implements DoelService {

    @Autowired private DoelEvaluator doelEvaluator;
    @Autowired private AccessControlService accessControlService;
    @Autowired private CurrentUserAccessor currentUserAccessor;

    @Override
    public <T extends Value> List<T> evaluate(String expression, Id contextId) {
        try {
            AccessToken accessToken = accessControlService.createCollectionAccessToken(
                    currentUserAccessor.getCurrentUser());
            return doelEvaluator.evaluate(DoelExpression.parse(expression), contextId, accessToken);
        } catch (SystemException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new UnexpectedException("DoelService", "evaluate", "expression:" + expression +
                    ", contextId:" + contextId, ex);
        }
    }

    @Override
    public <T extends Value> T evaluate(String expression, Id contextId, Class<T> valueClass) {
        List<T> values;
        try {
            values = evaluate(expression, contextId);
        } catch (SystemException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new UnexpectedException("DoelService", "evaluate", "expression:" + expression +
                    ", contextId:" + contextId + ", valueClass:" + valueClass, ex);
        }

        switch(values.size()) {
        case 0:
            return null;
        case 1:
            return values.get(0);
        default:
            throw new ClassCastException("Can't cast multiple values to single " + valueClass.getName());
        }
    }

}
