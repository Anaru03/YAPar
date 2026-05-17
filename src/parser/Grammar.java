package parser;

import java.util.*;

/**
 * Grammar — Gramática Libre de Contexto.
 *
 * Reglas de clasificación según el formato .yalp (PDF YAPar):
 *   - Los NO-TERMINALES son los nombres de producciones: siempre en minúsculas.
 *   - Los TERMINALES son los tokens declarados con %token: siempre en MAYÚSCULAS.
 *   - Un símbolo es terminal si fue declarado explícitamente con %token,
 *     O si comienza con mayúscula y NO fue declarado como no-terminal.
 *   - El símbolo especial "ε" (epsilon) representa producción vacía.
 *   - El símbolo "$" es el marcador de fin de entrada.
 */
public class Grammar {

    public List<Production>      productions  = new ArrayList<>();
    public String                startSymbol;
    public Set<String>           terminals    = new LinkedHashSet<>();
    public Set<String>           nonTerminals = new LinkedHashSet<>();
    public Set<String>           ignoreTokens = new LinkedHashSet<>();

    /** Símbolo de inicio aumentado S' → startSymbol */
    public static final String AUG_START = "S'";

    // ── Construcción ──────────────────────────────────────────────────────

    /**
     * Agrega una producción.
     * Clasifica cada símbolo del lado derecho como terminal o no-terminal.
     */
    public void addProduction(String left, List<String> right) {
        productions.add(new Production(left, right));
        nonTerminals.add(left);
        for (String sym : right) {
            if (sym.equals("ε") || sym.equals("$")) continue;
            // Si NO está ya en no-terminales y comienza con mayúscula → terminal
            if (!nonTerminals.contains(sym) && isTerminalSymbol(sym)) {
                terminals.add(sym);
            } else if (!isTerminalSymbol(sym)) {
                nonTerminals.add(sym);
            }
        }
    }

    /**
     * Aumenta la gramática: agrega S' → startSymbol como primera producción.
     * Se debe llamar DESPUÉS de agregar todas las producciones.
     */
    public void augment() {
        // Re-clasificar: cualquier símbolo que sea no-terminal deja de ser terminal
        for (String nt : nonTerminals) terminals.remove(nt);

        Production aug = new Production(AUG_START, Arrays.asList(startSymbol));
        productions.add(0, aug);
        nonTerminals.add(AUG_START);
    }

    // ── Consultas ─────────────────────────────────────────────────────────

    public List<Production> getProductions(String nonTerminal) {
        List<Production> result = new ArrayList<>();
        for (Production p : productions) {
            if (p.left.equals(nonTerminal)) result.add(p);
        }
        return result;
    }

    public boolean isNonTerminal(String sym) {
        return nonTerminals.contains(sym);
    }

    public boolean isTerminal(String sym) {
        return terminals.contains(sym) || sym.equals("$");
    }

    /**
     * Determina si un símbolo es terminal según el formato .yalp:
     *   - Empieza con mayúscula → terminal (token)
     *   - Empieza con minúscula → no-terminal (producción)
     *   - "$" → terminal especial (EOF)
     */
    private static boolean isTerminalSymbol(String sym) {
        if (sym.equals("$") || sym.equals("ε")) return false;
        return Character.isUpperCase(sym.charAt(0));
    }

    // ── FIRST sets ────────────────────────────────────────────────────────

    /**
     * Calcula los conjuntos FIRST para todos los símbolos.
     * FIRST(X) = conjunto de terminales que pueden aparecer al inicio
     *            de una cadena derivada de X.
     */
    public Map<String, Set<String>> computeFirst() {
        Map<String, Set<String>> first = new LinkedHashMap<>();

        // Inicializar
        for (String t : terminals)    first.put(t, new LinkedHashSet<>(Arrays.asList(t)));
        first.put("$", new LinkedHashSet<>(Arrays.asList("$")));
        for (String nt : nonTerminals) first.put(nt, new LinkedHashSet<>());

        boolean changed;
        do {
            changed = false;
            for (Production p : productions) {
                Set<String> f = first.computeIfAbsent(p.left, k -> new LinkedHashSet<>());

                if (p.right.isEmpty()) {
                    if (f.add("ε")) changed = true;
                    continue;
                }

                // Agregar FIRST de la secuencia (sin ε)
                boolean allEps = true;
                for (String sym : p.right) {
                    Set<String> fs = first.getOrDefault(sym, Collections.emptySet());
                    for (String s : fs) {
                        if (!s.equals("ε") && f.add(s)) changed = true;
                    }
                    if (!fs.contains("ε")) { allEps = false; break; }
                }
                if (allEps && f.add("ε")) changed = true;
            }
        } while (changed);

        return first;
    }

    /**
     * Calcula FIRST de una secuencia de símbolos.
     */
    public Set<String> firstOf(List<String> symbols, Map<String, Set<String>> firstSets) {
        Set<String> result = new LinkedHashSet<>();
        if (symbols.isEmpty()) {
            result.add("ε");
            return result;
        }
        for (String sym : symbols) {
            Set<String> fs = firstSets.getOrDefault(sym, Collections.emptySet());
            for (String s : fs) { if (!s.equals("ε")) result.add(s); }
            if (!fs.contains("ε")) return result;
        }
        result.add("ε");
        return result;
    }

    // ── FOLLOW sets ───────────────────────────────────────────────────────

    /**
     * Calcula los conjuntos FOLLOW para todos los no-terminales.
     * FOLLOW(A) = conjunto de terminales que pueden aparecer inmediatamente
     *             después de A en alguna forma sentencial.
     */
    public Map<String, Set<String>> computeFollow(Map<String, Set<String>> firstSets) {
        Map<String, Set<String>> follow = new LinkedHashMap<>();
        for (String nt : nonTerminals) follow.put(nt, new LinkedHashSet<>());

        // FOLLOW(S') = {$}
        follow.computeIfAbsent(AUG_START, k -> new LinkedHashSet<>()).add("$");

        boolean changed;
        do {
            changed = false;
            for (Production p : productions) {
                for (int i = 0; i < p.right.size(); i++) {
                    String sym = p.right.get(i);
                    if (!isNonTerminal(sym)) continue;

                    Set<String> fl = follow.computeIfAbsent(sym, k -> new LinkedHashSet<>());
                    List<String> beta = p.right.subList(i + 1, p.right.size());
                    Set<String> firstBeta = firstOf(beta, firstSets);

                    // Agregar FIRST(beta) - {ε} a FOLLOW(sym)
                    for (String s : firstBeta) {
                        if (!s.equals("ε") && fl.add(s)) changed = true;
                    }

                    // Si β ⟹* ε → agregar FOLLOW(left) a FOLLOW(sym)
                    if (firstBeta.contains("ε") || beta.isEmpty()) {
                        Set<String> followLeft = follow.getOrDefault(p.left, Collections.emptySet());
                        for (String s : followLeft) {
                            if (fl.add(s)) changed = true;
                        }
                    }
                }
            }
        } while (changed);

        return follow;
    }
}
