package com.github.divergent.model;

import java.util.Objects;

public abstract class AbstractEdge<N> {
    protected final N source;
    protected final N target;

    protected AbstractEdge(N source, N target) {
        this.source = source;
        this.target = target;
    }

    public N getSource() {
        return source;
    }

    public N getTarget() {
        return target;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        AbstractEdge<?> that = (AbstractEdge<?>) o;
        return Objects.equals(source, that.source) && Objects.equals(target, that.target);
    }

    @Override
    public int hashCode() {
        return source.hashCode() * 31 + target.hashCode();
    }

    @Override
    public String toString() {
        return String.format("<%s -> %s>", source, target);
    }

    public abstract String getLabel();
}
