package parser;

import java.util.*;

public class State {
    public int id;
    public Set<LR0Item> items;
    public Map<String, State> transitions = new LinkedHashMap<>();

    public State(int id, Set<LR0Item> items) {
        this.id = id;
        this.items = items;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof State)) return false;
        return items.equals(((State) o).items);
    }

    @Override
    public int hashCode() {
        return items.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("State " + id + ":\n");
        for (LR0Item item : items) sb.append("  ").append(item).append("\n");
        return sb.toString();
    }
}