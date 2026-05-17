package parser;

import java.util.List;

public class Production {
    public String left;
    public List<String> right;

    public Production(String left, List<String> right) {
        this.left = left;
        this.right = new java.util.ArrayList<>(right);
    }
}