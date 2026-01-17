package io.github.datakore.jsont.grammar.schema.constraints;

public abstract class BaseConstraint implements FieldConstraint {

    private final ConstraitType type;

    public BaseConstraint(ConstraitType type) {
        this.type = type;
    }

    protected abstract Object constraintValue();

    @Override
    public String toString() {
        String name = null;
        if (!type.getIdentifier().isEmpty()) {
            name = type.getIdentifier().iterator().next();
        }
        return String.format("%s = %s", name, constraintValue());
    }
}
