package io.github.datakore.jsont.grammar.schema.constraints.text;

import io.github.datakore.jsont.errors.ErrorLocation;
import io.github.datakore.jsont.errors.ValidationError;
import io.github.datakore.jsont.grammar.data.ScalarNode;
import io.github.datakore.jsont.grammar.data.ValueNode;
import io.github.datakore.jsont.grammar.schema.constraints.BaseConstraint;

public class RegexPatternConstraint extends BaseConstraint {
    private final String pattern;

    public RegexPatternConstraint(ConstraitType type, String pattern) {
        super(type);
        this.pattern = pattern;
    }

    @Override
    public boolean checkConstraint(ValueNode node) {
        if (node instanceof ScalarNode) {
            ScalarNode scalarNode = (ScalarNode) node;
            return scalarNode.raw().matches(pattern);
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
                    "Field value does not match the pattern",
                    pattern,
                    scalarNode.raw());
        }
        return null;
    }

    @Override
    protected Object constraintValue() {
        return this.pattern;
    }
}
