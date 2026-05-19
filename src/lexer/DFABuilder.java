package lexer;

import java.util.*;

/**
 * DFABuilder — Convierte un NFA a DFA usando construcción de subconjuntos.
 * SIN java.util.regex.
 *
 * Cada estado DFA es un conjunto de estados NFA.
 * Los estados de aceptación conservan el token con mayor prioridad
 * (menor número de prioridad = definido primero = mayor prioridad).
 */
public class DFABuilder {

    /** Estado del DFA: conjunto de nodos NFA + info de aceptación */
    public static class DFAState {
        public final int id;
        public final Set<NFANode> nfaStates;
        public final Map<Character, DFAState> transitions = new LinkedHashMap<>();
        public String acceptToken = null;   // null si no es estado de aceptación
        public int priority = Integer.MAX_VALUE;

        public DFAState(int id, Set<NFANode> nfaStates) {
            this.id = id;
            this.nfaStates = nfaStates;
            // Detectar si algún nodo NFA es de aceptación
            for (NFANode n : nfaStates) {
                if (n.acceptToken != null && n.priority < this.priority) {
                    this.acceptToken = n.acceptToken;
                    this.priority    = n.priority;
                }
            }
        }
    }

    private final List<DFAState> states = new ArrayList<>();
    private final Map<Set<Integer>, DFAState> stateMap = new LinkedHashMap<>();
    private int nextId = 0;

    /** Construye el DFA a partir del nodo inicial del NFA. */
    public static List<DFAState> build(NFANode nfaStart) {
        DFABuilder b = new DFABuilder();
        Set<NFANode> init = epsilonClosure(Set.of(nfaStart));
        b.getOrCreate(init);
        Queue<DFAState> worklist = new LinkedList<>(b.states);
        while (!worklist.isEmpty()) {
            DFAState ds = worklist.poll();
            // Recoger todos los símbolos posibles desde este estado DFA
            Set<Character> symbols = new LinkedHashSet<>();
            for (NFANode n : ds.nfaStates) symbols.addAll(n.transitions.keySet());
            for (char c : symbols) {
                Set<NFANode> moved = move(ds.nfaStates, c);
                Set<NFANode> closed = epsilonClosure(moved);
                if (closed.isEmpty()) continue;
                DFAState target = b.getOrCreate(closed);
                boolean isNew = !ds.transitions.containsKey(c);
                ds.transitions.put(c, target);
                if (isNew && !b.states.contains(target)) {
                    b.states.add(target);
                    worklist.add(target);
                }
            }
        }
        return b.states;
    }

    private DFAState getOrCreate(Set<NFANode> nfaSet) {
        Set<Integer> key = new TreeSet<>();
        for (NFANode n : nfaSet) key.add(n.id);
        return stateMap.computeIfAbsent(key, k -> {
            DFAState ds = new DFAState(nextId++, nfaSet);
            states.add(ds);
            return ds;
        });
    }

    /** Cierre epsilon de un conjunto de nodos NFA */
    public static Set<NFANode> epsilonClosure(Set<NFANode> nodes) {
        Set<NFANode> closure = new LinkedHashSet<>(nodes);
        Deque<NFANode> stack = new ArrayDeque<>(nodes);
        while (!stack.isEmpty()) {
            NFANode n = stack.pop();
            for (NFANode e : n.epsilonTransitions) {
                if (closure.add(e)) stack.push(e);
            }
        }
        return closure;
    }

    /** Mueve el conjunto de estados NFA por el símbolo c */
    public static Set<NFANode> move(Set<NFANode> nodes, char c) {
        Set<NFANode> result = new LinkedHashSet<>();
        for (NFANode n : nodes) {
            Set<NFANode> ts = n.transitions.get(c);
            if (ts != null) result.addAll(ts);
        }
        return result;
    }
}
