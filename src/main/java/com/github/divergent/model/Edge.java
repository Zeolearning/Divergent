package com.github.divergent.model;

import java.util.Objects;

/**
 * structure, control and data dependency
 */
public class Edge extends AbstractEdge<TreeNode> {

    public enum Type {
        IMPORT, // classes declared in other files
        EXTEND, IMPLEMENT, OVERRIDE,
        REFERENCE,  // type reference with import
        DEF_USE,
        METHOD_CALL,
        CONTROL,
        CONTAIN,
        OVERLOAD
    }

    private final Type type;

    public Edge(TreeNode source, TreeNode target, Type type) {
        super(source, target);
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    @Override
    public String getLabel() {
        return type.toString();
    }

    @Override
    public String toString() {
        return String.format("Edge(kind=%s, source=%s, target=%s)", type, source, target);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        Edge edge = (Edge) o;
        return type == edge.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), type);
    }
}
