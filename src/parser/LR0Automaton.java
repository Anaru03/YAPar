package parser;

import java.util.*;

public class LR0Automaton {
    public List<State> states = new ArrayList<>();
    private Grammar grammar;

    public LR0Automaton(Grammar grammar) {
        this.grammar = grammar;
        build();
    }

    public static Set<LR0Item> closure(Set<LR0Item> items, Grammar g) {
        Set<LR0Item> closure = new LinkedHashSet<>(items);
        boolean changed;
        do {
            changed = false;
            Set<LR0Item> newItems = new LinkedHashSet<>();
            for (LR0Item item : closure) {
                String sym = item.symbolAfterDot();
                if (sym != null && g.isNonTerminal(sym)) {
                    for (Production p : g.getProductions(sym)) {
                        LR0Item ni = new LR0Item(p.left, p.right, 0);
                        if (!closure.contains(ni)) { newItems.add(ni); changed = true; }
                    }
                }
            }
            closure.addAll(newItems);
        } while (changed);
        return closure;
    }

    public static Set<LR0Item> goTo(Set<LR0Item> items, String symbol, Grammar g) {
        Set<LR0Item> moved = new LinkedHashSet<>();
        for (LR0Item item : items) {
            if (symbol.equals(item.symbolAfterDot())) {
                moved.add(item.advance());
            }
        }
        return closure(moved, g);
    }

    private void build() {
        // Initial item: S' -> •startSymbol
        Production augProd = grammar.productions.get(0); // S' -> startSymbol
        Set<LR0Item> initItems = new LinkedHashSet<>();
        initItems.add(new LR0Item(augProd.left, augProd.right, 0));
        Set<LR0Item> initClosure = closure(initItems, grammar);

        State s0 = new State(0, initClosure);
        states.add(s0);

        Queue<State> worklist = new LinkedList<>();
        worklist.add(s0);

        while (!worklist.isEmpty()) {
            State state = worklist.poll();
            Set<String> symbols = new LinkedHashSet<>();
            for (LR0Item item : state.items) {
                String sym = item.symbolAfterDot();
                if (sym != null) symbols.add(sym);
            }
            for (String sym : symbols) {
                Set<LR0Item> nextItems = goTo(state.items, sym, grammar);
                if (nextItems.isEmpty()) continue;
                State existing = findState(nextItems);
                if (existing == null) {
                    State newState = new State(states.size(), nextItems);
                    states.add(newState);
                    worklist.add(newState);
                    state.transitions.put(sym, newState);
                } else {
                    state.transitions.put(sym, existing);
                }
            }
        }
    }

    private State findState(Set<LR0Item> items) {
        for (State s : states) {
            if (s.items.equals(items)) return s;
        }
        return null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("LR(0) Automaton:\n");
        for (State s : states) {
            sb.append(s);
            for (Map.Entry<String, State> e : s.transitions.entrySet()) {
                sb.append("  --[").append(e.getKey()).append("]--> State ").append(e.getValue().id).append("\n");
            }
        }
        return sb.toString();
    }
}
