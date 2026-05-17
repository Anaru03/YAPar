package parser;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

public class LR0Item {
    public String left;
    public List<String> right;
    public int dot;

    public LR0Item(String left, List<String> right, int dot) {
        this.left = left;
        this.right = new ArrayList<>(right);
        this.dot = dot;
    }

    public String symbolAfterDot() {
        if (dot < right.size()) return right.get(dot);
        return null;
    }

    public boolean isComplete() {
        return dot >= right.size();
    }

    public LR0Item advance() {
        return new LR0Item(left, right, dot + 1);
    }

    @Override
    public String toString() {
        List<String> r = new ArrayList<>(right);
        r.add(dot, "•");
        return left + " -> " + String.join(" ", r);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LR0Item)) return false;
        LR0Item item = (LR0Item) o;
        return dot == item.dot && left.equals(item.left) && right.equals(item.right);
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, right, dot);
    }
}