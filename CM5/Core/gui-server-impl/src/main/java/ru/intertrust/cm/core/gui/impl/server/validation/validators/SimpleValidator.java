package ru.intertrust.cm.core.gui.impl.server.validation.validators;

import ru.intertrust.cm.core.business.api.dto.Constraint;
import ru.intertrust.cm.core.business.api.dto.Dto;
import ru.intertrust.cm.core.business.api.dto.Value;
import ru.intertrust.cm.core.gui.model.action.ActionContext;
import ru.intertrust.cm.core.gui.model.validation.ValidationResult;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Lesia Puhova
 *         Date: 06.03.14
 *         Time: 14:44
 */
public class SimpleValidator implements ServerValidator {

    private final String pattern;
    private final String messageKey;

    public SimpleValidator(Constraint constraint) {
        String wordOrPattern = constraint.param(Constraint.PARAM_PATTERN);
        String pattern = wordsToPatterns.get(wordOrPattern);
        String messageKey = wordOrPattern;
        if (pattern == null) {
            pattern = wordOrPattern;
            messageKey = "validate.pattern";
        }
        this.pattern = pattern;
        this.messageKey = messageKey;
    }

    private static final Map<String, String> wordsToPatterns = new HashMap<String, String>();

    static {
        wordsToPatterns.put(Constraint.KEYWORD_NOT_EMPTY, "^.*[^\\s]+.*$");
        wordsToPatterns.put(Constraint.KEYWORD_INTEGER, "^([-+]?\\d+)?$");
        wordsToPatterns.put(Constraint.KEYWORD_POSITIVE_INT, "^([+]?\\d+)?$");
        wordsToPatterns.put(Constraint.KEYWORD_DECIMAL, "^([-+]?[0-9]+(\\.[0-9]+)?)?$");
        wordsToPatterns.put(Constraint.KEYWORD_POSITIVE_DEC, "^([+]?[0-9]+(\\.[0-9]+)?)?$");
    }

    @Override
    public ValidationResult validate(Dto dtoToValidate) {
        ValidationResult validationResult = new ValidationResult();
        if (dtoToValidate instanceof Value) {
            Value value = (Value)dtoToValidate;
            if (value.get() != null) {
                if (pattern!= null && !value.get().toString().matches(pattern)) {
                    validationResult.addError(messageKey);
                }
            }
        }
        return validationResult;
    }

    @Override
    public void init(ActionContext context) {
        // do nothing
    }

    @Override
    public String toString() {
        return "Server simple validator: " + pattern;
    }
}
