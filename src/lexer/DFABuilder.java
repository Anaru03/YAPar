package lexer;

import java.util.*;

public class DFABuilder {

    public static class DFAState {

        public final int id;

        public final Set<NFANode> nfaStates;

        public final Map<Character, DFAState> transitions =
                new LinkedHashMap<>();

        public String acceptToken = null;

        public int priority = Integer.MAX_VALUE;

        public int matchLength = -1;

        public DFAState(
                int id,
                Set<NFANode> nfaStates
        ) {

            this.id = id;

            this.nfaStates = nfaStates;

            for (NFANode n : nfaStates) {

                if (n.acceptToken != null) {

                    // PRIORIDAD:
                    // 1. longest match
                    // 2. menor prioridad

                    if (n.priority < priority) {

                        priority =
                                n.priority;

                        acceptToken =
                                n.acceptToken;
                    }
                }
            }
        }
    }

    private final List<DFAState> states =
            new ArrayList<>();

    private final Map<Set<Integer>, DFAState> stateMap =
            new LinkedHashMap<>();

    private int nextId = 0;

    // ─────────────────────────────────────────────

    public static List<DFAState> build(
            NFANode nfaStart
    ) {

        DFABuilder b =
                new DFABuilder();

        Set<NFANode> init =
                epsilonClosure(Set.of(nfaStart));

        DFAState start =
                b.getOrCreate(init);

        Queue<DFAState> worklist =
                new LinkedList<>();

        worklist.add(start);

        Set<DFAState> visited =
                new LinkedHashSet<>();

        visited.add(start);

        while (!worklist.isEmpty()) {

            DFAState ds =
                    worklist.poll();

            Set<Character> symbols =
                    new LinkedHashSet<>();

            for (NFANode n : ds.nfaStates) {

                symbols.addAll(
                        n.transitions.keySet()
                );
            }

            for (char c : symbols) {

                Set<NFANode> moved =
                        move(ds.nfaStates, c);

                Set<NFANode> closed =
                        epsilonClosure(moved);

                if (closed.isEmpty()) {
                    continue;
                }

                DFAState target =
                        b.getOrCreate(closed);

                ds.transitions.put(c, target);

                if (!visited.contains(target)) {

                    visited.add(target);

                    worklist.add(target);
                }
            }
        }

        return b.states;
    }

    // ─────────────────────────────────────────────

    private DFAState getOrCreate(
            Set<NFANode> nfaSet
    ) {

        Set<Integer> key =
                new TreeSet<>();

        for (NFANode n : nfaSet) {
            key.add(n.id);
        }

        DFAState existing =
                stateMap.get(key);

        if (existing != null) {
            return existing;
        }

        DFAState ds =
                new DFAState(
                        nextId++,
                        nfaSet
                );

        stateMap.put(key, ds);

        states.add(ds);

        return ds;
    }

    // ─────────────────────────────────────────────

    public static Set<NFANode> epsilonClosure(
            Set<NFANode> nodes
    ) {

        Set<NFANode> closure =
                new LinkedHashSet<>(nodes);

        Deque<NFANode> stack =
                new ArrayDeque<>(nodes);

        while (!stack.isEmpty()) {

            NFANode n =
                    stack.pop();

            for (NFANode e : n.epsilonTransitions) {

                if (closure.add(e)) {
                    stack.push(e);
                }
            }
        }

        return closure;
    }

    // ─────────────────────────────────────────────

    public static Set<NFANode> move(
            Set<NFANode> nodes,
            char c
    ) {

        Set<NFANode> result =
                new LinkedHashSet<>();

        for (NFANode n : nodes) {

            Set<NFANode> ts =
                    n.transitions.get(c);

            if (ts != null) {
                result.addAll(ts);
            }
        }

        return result;
    }
}