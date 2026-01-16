package io.github.datakore.jsont.grammar.schema.constraints.number;

import io.github.datakore.jsont.errors.ErrorLocation;
import io.github.datakore.jsont.errors.ValidationError;
import io.github.datakore.jsont.grammar.data.ScalarNode;
import io.github.datakore.jsont.grammar.data.ValueNode;
import io.github.datakore.jsont.grammar.schema.constraints.BaseConstraint;

import java.math.BigDecimal;

public class MaxPrecisionConstraint extends BaseConstraint {
    private final int maxPrecision;

    public MaxPrecisionConstraint(ConstraitType constraitType, int maxPrecision) {
        super(constraitType);
        this.maxPrecision = maxPrecision;
    }

    @Override
    public boolean checkConstraint(ValueNode node) {
        if (node instanceof ScalarNode) {
            ScalarNode scalarNode = (ScalarNode) node;
            try {
                BigDecimal bd = new BigDecimal(scalarNode.raw());
                return bd.scale() <= maxPrecision;
            } catch (NumberFormatException e) {
                return false; // Not a number
            }
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
                    "Field value precision is greater than maximum precision",
                    String.valueOf(maxPrecision),
                    scalarNode.raw());
        }
        return null;
    }

    @Override
    protected Object constraintValue() {
        return this.maxPrecision;
    }
}
