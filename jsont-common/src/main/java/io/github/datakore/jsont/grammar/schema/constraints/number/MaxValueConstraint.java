package io.github.datakore.jsont.grammar.schema.constraints.number;

import io.github.datakore.jsont.errors.ErrorLocation;
import io.github.datakore.jsont.errors.ValidationError;
import io.github.datakore.jsont.grammar.data.ScalarNode;
import io.github.datakore.jsont.grammar.data.ValueNode;
import io.github.datakore.jsont.grammar.schema.constraints.BaseConstraint;

public class MaxValueConstraint extends BaseConstraint {
    private final double maxValue;

    public MaxValueConstraint(ConstraitType constraitType, double maxValue) {
        super(constraitType);
        this.maxValue = maxValue;
    }

    @Override
    public boolean checkConstraint(ValueNode node) {
        if (node instanceof ScalarNode) {
            ScalarNode scalarNode = (ScalarNode) node;
            return Double.parseDouble(scalarNode.raw()) <= maxValue;
        }
        return true; // For other types, ignore as true
    }

    @Override
    public ValidationError makeError(int rowIndex, String fieldName, ValueNode node) {
        if (node instanceof ScalarNode) {
            ScalarNode scalarNode = (ScalarNode) node;
            return new ValidationError(
                    ErrorLocation.withRow("Row ", rowIndex),
                    fieldName,
                    "Field value is greater than maximum value",
                    String.valueOf(maxValue),
                    scalarNode.raw());
        }
        return null;
    }

    @Override
    protected Object constraintValue() {
        return this.maxValue;
    }
}
