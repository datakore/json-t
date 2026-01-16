package io.github.datakore.jsont.grammar.schema.constraints.general;

import io.github.datakore.jsont.errors.ErrorLocation;
import io.github.datakore.jsont.errors.ValidationError;
import io.github.datakore.jsont.grammar.data.NullNode;
import io.github.datakore.jsont.grammar.data.ValueNode;
import io.github.datakore.jsont.grammar.schema.constraints.BaseConstraint;

public class MandatoryFieldConstraint extends BaseConstraint {
    private final boolean mandatory;

    public MandatoryFieldConstraint(ConstraitType constraitType, boolean mandatory) {
        super(constraitType);
        this.mandatory = mandatory;
    }

    @Override
    public boolean checkConstraint(ValueNode node) {
        return !mandatory || !(node instanceof NullNode);
    }

    @Override
    public ValidationError makeError(int rowIndex, String fieldName, ValueNode node) {
        return new ValidationError(
                ErrorLocation.withRow("Row ", rowIndex),
                fieldName,
                "Mandatory field is null",
                "non-null",
                "null");
    }

    @Override
    protected Object constraintValue() {
        return this.mandatory;
    }
}
