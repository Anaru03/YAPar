package lexer;

import runtime.Token;
import java.io.*;
import java.util.*;

/**
 * YALexRunner — Analizador léxico basado en reglas .yal.
 *
 * Implementado CON AUTÓMATAS FINITOS (NFA → DFA via construcción de Thompson
 * y construcción de subconjuntos). SIN java.util.regex.
 *
 * Formato .yal soportado (Jalex/YALex):
 *   (* comentario *)
 *   let ident = regexp
 *   rule entrypoint =
 *       regexp   { return TOKEN_NAME }
 *     | regexp   { return TOKEN_NAME }
 *
 * Implementa longest-match con prioridad por orden de definición.
 */
public class YALexRunner {

    /** Regla: nombre del token y su expresión regular (ya expandida con lets) */
    private final List<String[]> rules = new ArrayList<>();
    /** Tokens que se deben ignorar (ej: WS) */
    private final Set<String> ignoreTokens = new LinkedHashSet<>();

    /** DFA combinado (un NFA por regla, combinados en uno, luego → DFA) */
    private List<DFABuilder.DFAState> dfa = null;
    private DFABuilder.DFAState dfaStart = null;
    private boolean dfaDirty = true;

    public YALexRunner() {}

    // ── API pública ───────────────────────────────────────────────────────

    public void addRule(String tokenName, String regexYalex) {
        rules.add(new String[]{tokenName, regexYalex});
        dfaDirty = true;
    }

    public void ignoreToken(String name) {
        ignoreTokens.add(name);
    }

    public Set<String> getIgnoreTokens() {
        return Collections.unmodifiableSet(ignoreTokens);
    }

    public List<String[]> getRules() {
        return Collections.unmodifiableList(rules);
    }

    /** Devuelve los nombres de token declarados (en orden de prioridad) */
    public List<String> getDeclaredTokenNames() {
        List<String> names = new ArrayList<>();
        for (String[] r : rules) names.add(r[0]);
        return names;
    }

    // ── Carga de archivos .yal ────────────────────────────────────────────

    public static YALexRunner fromFile(String path) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
        }
        return fromContent(sb.toString());
    }

    public static YALexRunner fromContent(String content) {
        YALexRunner runner = new YALexRunner();
        String cleaned = removeComments(content);
        parseJalexFormat(runner, cleaned);
        return runner;
    }

    // ── Parser de formato .yal ────────────────────────────────────────────

    private static void parseJalexFormat(YALexRunner runner, String content) {
        Map<String, String> lets = extractLets(content);

        int ruleIdx = content.indexOf("rule ");
        if (ruleIdx < 0) {
            parseSimpleFormat(runner, content);
            return;
        }

        int eq = content.indexOf('=', ruleIdx);
        if (eq < 0) return;
        String ruleSection = content.substring(eq + 1).trim();

        // Quitar trailer { ... } al final si existe
        int trailerBrace = findTrailerBrace(ruleSection);
        if (trailerBrace >= 0) ruleSection = ruleSection.substring(0, trailerBrace).trim();

        List<String> alts = splitAlternatives(ruleSection);

        int priority = 0;
        for (String alt : alts) {
            alt = alt.trim();
            if (alt.isEmpty()) continue;

            // Separar regexp de { acción }
            String action = null;
            String regexp = alt;
            int brace = findTopLevelBrace(alt);
            if (brace >= 0) {
                int closeBrace = findClosingCurly(alt, brace);
                if (closeBrace > brace) {
                    action = alt.substring(brace + 1, closeBrace).trim();
                    regexp = alt.substring(0, brace).trim();
                }
            }
            if (regexp.isEmpty()) continue;

            // Expandir lets
            regexp = expandLets(regexp, lets);

            String tokenName = extractTokenName(action, regexp);
            if (tokenName == null) continue;

            runner.addRule(tokenName, regexp);

            if (action != null && isIgnoreAction(action)) {
                runner.ignoreToken(tokenName);
            }
            priority++;
        }
    }

    private static void parseSimpleFormat(YALexRunner runner, String content) {
        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("let ") || line.startsWith("rule ")
                    || line.startsWith("{") || line.startsWith("}")) continue;
            line = line.replaceAll("^\\|\\s*", "").trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\s+", 2);
            if (parts.length == 2 && parts[0].matches("[A-Z_][A-Z0-9_]*")) {
                runner.addRule(parts[0], parts[1].trim());
            }
        }
    }

    // ── Construcción del DFA combinado ────────────────────────────────────

    /**
     * Construye un único NFA combinado con todas las reglas, luego lo convierte a DFA.
     * El nodo de inicio del NFA combinado tiene transiciones epsilon a cada sub-NFA.
     * Cada sub-NFA marca su estado final con el nombre del token y su prioridad (índice).
     */
    private void buildDFA() {
        NFANode.resetCounter();
        NFANode combinedStart = new NFANode();

        for (int i = 0; i < rules.size(); i++) {
            String tokenName = rules.get(i)[0];
            String regex     = rules.get(i)[1];

            NFAFragment frag;
            try {
                frag = RegexToNFA.build(regex);
            } catch (Exception e) {
                // Si la regexp falla, intentar como literal
                frag = buildLiteralNFA(regex);
            }

            // Marcar estado final con prioridad
            frag.end.acceptToken = tokenName;
            frag.end.priority    = i;

            combinedStart.addEpsilon(frag.start);
        }

        dfa = DFABuilder.build(combinedStart);
        dfaStart = dfa.isEmpty() ? null : dfa.get(0);
        dfaDirty = false;
    }

    private NFAFragment buildLiteralNFA(String literal) {
        if (literal.isEmpty()) {
            NFANode s = new NFANode(); NFANode e = new NFANode(); s.addEpsilon(e);
            return new NFAFragment(s, e);
        }
        NFANode start = new NFANode();
        NFANode prev  = start;
        for (int i = 0; i < literal.length(); i++) {
            NFANode next = new NFANode();
            prev.addTransition(literal.charAt(i), next);
            prev = next;
        }
        return new NFAFragment(start, prev);
    }

    // ── Tokenización (longest-match sobre el DFA) ─────────────────────────

    /**
     * Tokeniza la cadena de entrada usando el DFA.
     * Longest-match: avanza mientras haya transiciones; al quedarse atascado
     * retrocede al último estado de aceptación visitado.
     */
    public List<Token> tokenize(String input) {
        if (dfaDirty) buildDFA();
        if (dfaStart == null) return new ArrayList<>();

        List<Token> tokens = new ArrayList<>();
        int pos  = 0;
        int line = 1;
        int len  = input.length();

        while (pos < len) {
            DFABuilder.DFAState state    = dfaStart;
            int lastAcceptPos            = -1;
            String lastAcceptToken       = null;
            int i                        = pos;

            // Avanzar por el DFA, recordando el último estado de aceptación
            while (i < len) {
                char c = input.charAt(i);
                DFABuilder.DFAState next = state.transitions.get(c);
                if (next == null) break;
                state = next;
                i++;
                if (state.acceptToken != null) {
                    lastAcceptPos   = i;
                    lastAcceptToken = state.acceptToken;
                }
            }

            if (lastAcceptPos > pos) {
                // Tenemos un match
                String lexeme = input.substring(pos, lastAcceptPos);
                if (!ignoreTokens.contains(lastAcceptToken)) {
                    tokens.add(new Token(lastAcceptToken, lexeme, line));
                }
                for (int k = pos; k < lastAcceptPos; k++) {
                    if (input.charAt(k) == '\n') line++;
                }
                pos = lastAcceptPos;
            } else {
                // Error léxico: consumir un carácter
                char c = input.charAt(pos);
                if (c == '\n') line++;
                tokens.add(new Token("LEXER_ERROR", String.valueOf(c), line));
                pos++;
            }
        }
        return tokens;
    }

    // ── Validación cruzada con YAParFileParser ────────────────────────────

    /**
     * Valida que todos los tokens declarados en el .yalp estén definidos en este lexer.
     * Devuelve lista de tokens faltantes (vacía = OK).
     */
    public List<String> validateAgainst(Set<String> yalpTokens) {
        Set<String> lexerTokens = new LinkedHashSet<>();
        for (String[] r : rules) lexerTokens.add(r[0]);

        List<String> missing = new ArrayList<>();
        for (String t : yalpTokens) {
            if (!t.equals("$") && !t.equals("ε") && !lexerTokens.contains(t)) {
                missing.add(t);
            }
        }
        return missing;
    }

    // ── Helpers de parseo .yal ────────────────────────────────────────────

    private static String removeComments(String src) {
        // Eliminar (* ... *) multilinea
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < src.length()) {
            if (i + 1 < src.length() && src.charAt(i) == '(' && src.charAt(i+1) == '*') {
                i += 2;
                while (i + 1 < src.length() && !(src.charAt(i) == '*' && src.charAt(i+1) == ')')) i++;
                i += 2;
            } else {
                sb.append(src.charAt(i++));
            }
        }
        return sb.toString();
    }

    private static Map<String, String> extractLets(String content) {
        Map<String, String> lets = new LinkedHashMap<>();
        int i = 0;
        while (i < content.length()) {
            // Buscar "let "
            int idx = content.indexOf("let ", i);
            if (idx < 0) break;
            // Nombre
            int nameStart = idx + 4;
            while (nameStart < content.length() && content.charAt(nameStart) == ' ') nameStart++;
            int nameEnd = nameStart;
            while (nameEnd < content.length() && isIdentChar(content.charAt(nameEnd))) nameEnd++;
            String name = content.substring(nameStart, nameEnd).trim();
            // =
            int eqIdx = content.indexOf('=', nameEnd);
            if (eqIdx < 0) break;
            // Valor: hasta el siguiente "let", "rule", "{" o fin
            int valStart = eqIdx + 1;
            int valEnd = content.length();
            int[] stops = {
                nextOccurrence(content, "\nlet ", valStart),
                nextOccurrence(content, "\nrule ", valStart),
                // no cortamos en { porque el valor puede tenerlos en clases []
            };
            for (int s : stops) if (s > valStart && s < valEnd) valEnd = s;
            String val = content.substring(valStart, valEnd).trim();
            if (!name.isEmpty()) lets.put(name, val);
            i = valEnd;
        }
        return lets;
    }

    private static int nextOccurrence(String s, String sub, int from) {
        int idx = s.indexOf(sub, from);
        return idx < 0 ? s.length() : idx;
    }

    private static boolean isIdentChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static String expandLets(String regexp, Map<String, String> lets) {
        String result = regexp;
        for (int pass = 0; pass <= lets.size() + 1; pass++) {
            String prev = result;
            for (Map.Entry<String, String> e : lets.entrySet()) {
                String name = e.getKey();
                String val  = "(" + e.getValue() + ")";
                result = replaceWord(result, name, val);
            }
            if (result.equals(prev)) break;
        }
        return result;
    }

    /**
     * Reemplaza ocurrencias de `word` en `src` que no estén dentro de literales
     * de carácter 'x' ni de cadenas "abc", y que estén rodeadas de no-ident.
     */
    private static String replaceWord(String src, String word, String replacement) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < src.length()) {
            char c = src.charAt(i);
            // Saltar literales 'x'
            if (c == '\'') {
                int end = i + 1;
                if (end < src.length() && src.charAt(end) == '\\') end++;
                end++;
                if (end < src.length() && src.charAt(end) == '\'') end++;
                sb.append(src, i, end);
                i = end;
                continue;
            }
            // Saltar cadenas "..."
            if (c == '"') {
                int end = i + 1;
                while (end < src.length() && src.charAt(end) != '"') {
                    if (src.charAt(end) == '\\') end++;
                    end++;
                }
                if (end < src.length()) end++;
                sb.append(src, i, end);
                i = end;
                continue;
            }
            // ¿Empieza la palabra aquí?
            if (src.startsWith(word, i)) {
                boolean prevOk = (i == 0) || !isIdentChar(src.charAt(i - 1));
                int after = i + word.length();
                boolean nextOk = (after >= src.length()) || !isIdentChar(src.charAt(after));
                if (prevOk && nextOk) {
                    sb.append(replacement);
                    i += word.length();
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static int findTrailerBrace(String s) {
        // El trailer es el primer { al nivel top que no es acción de alternativa
        // (simplificado: buscamos el último bloque { } top-level)
        int depth = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            char c = s.charAt(i);
            if (c == '}') { depth++; continue; }
            if (c == '{') { if (depth == 0) return i; depth--; }
        }
        return -1;
    }

    private static List<String> splitAlternatives(String src) {
        List<String> alts = new ArrayList<>();
        int depth = 0, braceDepth = 0;
        StringBuilder cur = new StringBuilder();
        int i = 0;
        while (i < src.length()) {
            char c = src.charAt(i);
            if (c == '\'') {
                cur.append(c); i++;
                if (i < src.length() && src.charAt(i) == '\\') { cur.append(src.charAt(i)); i++; }
                if (i < src.length()) { cur.append(src.charAt(i)); i++; }
                if (i < src.length() && src.charAt(i) == '\'') { cur.append(src.charAt(i)); i++; }
                continue;
            }
            if (c == '[' || c == '(') { depth++; cur.append(c); i++; continue; }
            if (c == ']' || c == ')') { depth--; cur.append(c); i++; continue; }
            if (c == '{') { braceDepth++; cur.append(c); i++; continue; }
            if (c == '}') { braceDepth--; cur.append(c); i++; continue; }
            if (c == '|' && depth == 0 && braceDepth == 0) {
                alts.add(cur.toString().trim()); cur.setLength(0); i++; continue;
            }
            cur.append(c); i++;
        }
        String last = cur.toString().trim();
        if (!last.isEmpty()) alts.add(last);
        return alts;
    }

    private static int findTopLevelBrace(String s) {
        int depth = 0;
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\'') { i++; if (i < s.length() && s.charAt(i) == '\\') i++; i += 2; continue; }
            if (c == '[' || c == '(') { depth++; i++; continue; }
            if (c == ']' || c == ')') { depth--; i++; continue; }
            if (c == '{' && depth == 0) return i;
            i++;
        }
        return -1;
    }

    private static int findClosingCurly(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            if (s.charAt(i) == '{') depth++;
            else if (s.charAt(i) == '}') { depth--; if (depth == 0) return i; }
        }
        return s.length() - 1;
    }

    private static boolean isIgnoreAction(String action) {
        return action.isBlank() || action.equals("skip") || action.contains("lexbuf");
    }

    private static String extractTokenName(String action, String regexp) {
        if (action == null) return deriveFromRegexp(regexp);
        action = action.trim();
        if (action.equals("skip")) return "__SKIP__";

        // Buscar: return TOKEN
        int retIdx = action.indexOf("return");
        if (retIdx >= 0) {
            String after = action.substring(retIdx + 6).trim();
            // Extraer identificador
            int end = 0;
            while (end < after.length() && (Character.isLetterOrDigit(after.charAt(end)) || after.charAt(end) == '_')) end++;
            if (end > 0) {
                String name = after.substring(0, end);
                if (name.equals("lexbuf")) return "__SKIP__";
                return name;
            }
        }
        if (action.contains("raise")) return null;
        if (action.isBlank()) return "__SKIP__";
        return deriveFromRegexp(regexp);
    }

    private static String deriveFromRegexp(String regexp) {
        if (regexp == null || regexp.isBlank()) return null;
        return "TOKEN_" + regexp.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }
}
