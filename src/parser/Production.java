package parser;

import java.util.List;

public class Production {
    public String left;
    public List<String> right;

    public Production(String left, List<String> right) {
        this.left = left;
        this.right = new java.util.ArrayList<>(right);
    }

    @Override
    public String toString() {
        return left + " -> " + String.join(" ", right);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Production)) return false;
        Production p = (Production) o;
        return left.equals(p.left) && right.equals(p.right);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(left, right);
    }
}