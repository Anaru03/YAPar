package parser;

import java.util.*;

public class SLRTable {
    public Map<String, String> action = new LinkedHashMap<>();
    public Map<String, Integer> goTo = new LinkedHashMap<>();
    public List<String> conflicts = new ArrayList<>();

    public void setAction(int state, String symbol, String value) {
        String key = state + "," + symbol;
        if (action.containsKey(key) && !action.get(key).equals(value)) {
            conflicts.add("CONFLICT at state " + state + " on '" + symbol + "': " + action.get(key) + " vs " + value);
        }
        action.put(key, value);
    }

    public String getAction(int state, String symbol) {
        return action.get(state + "," + symbol);
    }

    public void setGoto(int state, String symbol, int next) {
        goTo.put(state + "," + symbol, next);
    }

    public Integer getGoto(int state, String symbol) {
        return goTo.get(state + "," + symbol);
    }

    /** Build SLR table from LR(0) automaton + follow sets */
    public static SLRTable build(LR0Automaton automaton, Grammar grammar,
                                 Map<String, Set<String>> followSets) {
        SLRTable table = new SLRTable();

        for (State state : automaton.states) {
            int id = state.id;

            // Transitions → shift or goto
            for (Map.Entry<String, State> e : state.transitions.entrySet()) {
                String sym = e.getKey();
                int nextId = e.getValue().id;
                if (grammar.isNonTerminal(sym)) {
                    table.setGoto(id, sym, nextId);
                } else {
                    table.setAction(id, sym, "s" + nextId);
                }
            }

            // Complete items → reduce
            for (LR0Item item : state.items) {
                if (item.isComplete()) {
                    String head = item.left;
                    if (head.equals(Grammar.AUG_START)) {
                        table.setAction(id, "$", "acc");
                    } else {
                        // Find production index
                        int prodIdx = -1;
                        for (int i = 0; i < grammar.productions.size(); i++) {
                            Production p = grammar.productions.get(i);
                            if (p.left.equals(item.left) && p.right.equals(item.right)) {
                                prodIdx = i;
                                break;
                            }
                        }
                        if (prodIdx >= 0) {
                            Set<String> follow = followSets.getOrDefault(head, new HashSet<>());
                            for (String terminal : follow) {
                                table.setAction(id, terminal, "r" + prodIdx);
                            }
                        }
                    }
                }
            }
        }
        return table;
    }

    public String toDisplayString(LR0Automaton automaton, Grammar grammar) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-8s", "State"));
        List<String> terms = new ArrayList<>(grammar.terminals);
        terms.add("$");
        List<String> nonTerms = new ArrayList<>(grammar.nonTerminals);
        nonTerms.remove(Grammar.AUG_START);

        for (String t : terms) sb.append(String.format("%-10s", t));
        sb.append("| ");
        for (String nt : nonTerms) sb.append(String.format("%-10s", nt));
        sb.append("\n").append("-".repeat(8 + terms.size()*10 + 2 + nonTerms.size()*10)).append("\n");

        for (State s : automaton.states) {
            sb.append(String.format("%-8d", s.id));
            for (String t : terms) {
                String val = getAction(s.id, t);
                sb.append(String.format("%-10s", val != null ? val : ""));
            }
            sb.append("| ");
            for (String nt : nonTerms) {
                Integer g = getGoto(s.id, nt);
                sb.append(String.format("%-10s", g != null ? g : ""));
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
