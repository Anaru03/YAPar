package parser;

import java.util.List;
import java.util.ArrayList;

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
}