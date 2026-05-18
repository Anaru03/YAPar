package lexer;

import java.util.*;

/**
 * Nodo de un NFA (autómata finito no-determinista).
 * Usado internamente por Thompson construction.
 */
public class NFANode {
    private static int counter = 0;
    public final int id;

    /** Transiciones por carácter concreto (char -> set of NFANode) */
    public final Map<Character, Set<NFANode>> transitions = new LinkedHashMap<>();
    /** Transiciones epsilon */
    public final Set<NFANode> epsilonTransitions = new LinkedHashSet<>();
    /** Si es estado de aceptación, guarda el nombre del token y su prioridad */
    public String acceptToken = null;
    public int priority = Integer.MAX_VALUE;

    public NFANode() {
        this.id = counter++;
    }

    public static void resetCounter() {
        counter = 0;
    }

    public void addTransition(char c, NFANode target) {
        transitions.computeIfAbsent(c, k -> new LinkedHashSet<>()).add(target);
    }

    public void addEpsilon(NFANode target) {
        epsilonTransitions.add(target);
    }
}
