package io.github.datakore.jsont.grammar.schema.constraints.text;

import io.github.datakore.jsont.errors.ErrorLocation;
import io.github.datakore.jsont.errors.ValidationError;
import io.github.datakore.jsont.grammar.data.ScalarNode;
import io.github.datakore.jsont.grammar.data.ValueNode;
import io.github.datakore.jsont.grammar.schema.constraints.BaseConstraint;

public class MinLengthConstraint extends BaseConstraint {
    private final int minLength;

    public MinLengthConstraint(ConstraitType constraitType, int minLength) {
        super(constraitType);
        this.minLength = minLength;
    }

    public int minLength() {
        return minLength;
    }

    @Override
    public boolean checkConstraint(ValueNode node) {
        if (node instanceof ScalarNode) {
            ScalarNode scalarNode = (ScalarNode) node;
            return scalarNode.raw().length() >= minLength;
        }
        return true;
    }

    @Override
    public ValidationError makeError(int rowIndex, String fieldName, ValueNode node) {
        if (node instanceof ScalarNode) {
            ScalarNode scalarNode = (ScalarNode) node;
            return new ValidationError(
                    ErrorLocation.withRow("Row ", rowIndex),
                    fieldName,
                    "Field value length is less than minimum length",
                    String.valueOf(minLength),
                    String.valueOf(scalarNode.raw().length()));
        }
        return null;
    }

    @Override
    protected Object constraintValue() {
        return this.minLength;
    }
}
