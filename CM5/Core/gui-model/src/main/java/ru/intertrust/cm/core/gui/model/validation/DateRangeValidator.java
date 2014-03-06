package ru.intertrust.cm.core.gui.model.validation;

import ru.intertrust.cm.core.business.api.dto.Constraint;

/**
 * @author Lesia Puhova
 *         Date: 03.03.14
 *         Time: 14:56
 */
// TODO: [validation] actually compares number of milliseconds - do we have a better way to do this?
public class DateRangeValidator extends  RangeValidator<Long> {

    public DateRangeValidator(Constraint constraint) {
        super(constraint, Constraint.PARAM_RANGE_START_DATE_MS, Constraint.PARAM_RANGE_END_DATE_MS);
    }

    @Override
    Long convert(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException nfe) {
            return null;
        }
    }
}
