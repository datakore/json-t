package io.github.datakore.jsont.grammar.schema.constraints.arrays;

import io.github.datakore.jsont.errors.ErrorLocation;
import io.github.datakore.jsont.errors.ValidationError;
import io.github.datakore.jsont.grammar.data.ArrayNode;
import io.github.datakore.jsont.grammar.data.NullNode;
import io.github.datakore.jsont.grammar.data.ValueNode;
import io.github.datakore.jsont.grammar.schema.constraints.BaseConstraint;

public class MaxNullElementsConstraint extends BaseConstraint {
    private final int maxNullElements;

    public MaxNullElementsConstraint(ConstraitType constraitType, int maxNullElements) {
        super(constraitType);
        this.maxNullElements = maxNullElements;
    }

    @Override
    public boolean checkConstraint(ValueNode node) {
        if (node instanceof ArrayNode) {
            ArrayNode arrayNode = (ArrayNode) node;
            return arrayNode.elements().stream().filter(valueNode -> valueNode instanceof NullNode)
                    .count() <= maxNullElements;
        }
        return true; // For other types, ignore as true
    }

    @Override
    public ValidationError makeError(int rowIndex, String fieldName, ValueNode node) {
        if (node instanceof ArrayNode) {
            ArrayNode arrayNode = (ArrayNode) node;
            return new ValidationError(
                    ErrorLocation.withRow("Row ", rowIndex),
                    fieldName,
                    "Field array contains more null elements than allowed",
                    String.valueOf(maxNullElements),
                    String.valueOf(
                            arrayNode.elements().stream().filter(valueNode -> valueNode instanceof NullNode)
                                    .count()));
        }
        return null;
    }

    @Override
    protected Object constraintValue() {
        return this.maxNullElements;
    }
}
