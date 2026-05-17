package lexer;

import runtime.Token;
import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 * YALexRunner — Analizador léxico basado en reglas .yal.
 *
 * Compatible con el formato completo de Jalex/YALex:
 *
 *   (* comentario *)
 *   { header }
 *   let ident = regexp
 *   rule entrypoint =
 *       regexp   { return TOKEN_NAME }
 *     | regexp   { return TOKEN_NAME }
 *   { trailer }
 *
 * También soporta el formato simplificado:
 *   TOKEN_NAME   java_regex
 *
 * Implementa longest-match con prioridad por orden de definición.
 */
public class YALexRunner {

    // ── Regla: (tokenName, javaRegexPattern) ──────────────────────────────
    private final List<String[]> rules           = new ArrayList<>();
    private final Set<String>    ignoreTokens    = new LinkedHashSet<>();
    private final List<Pattern>  compiledPatterns = new ArrayList<>();
    private boolean patternsDirty = true;

    public YALexRunner() {}

    // ── API pública ───────────────────────────────────────────────────────

    public void addRule(String tokenName, String regex) {
        rules.add(new String[]{tokenName, regex});
        patternsDirty = true;
    }

    public void ignoreToken(String name) {
        ignoreTokens.add(name);
    }

    public List<String[]> getRules() {
        return Collections.unmodifiableList(rules);
    }

    // ── Parseo de contenido .yal ──────────────────────────────────────────

    /**
     * Carga un archivo .yal y construye el runner.
     */
    public static YALexRunner fromFile(String path) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
        }
        return fromContent(sb.toString());
    }

    /**
     * Carga reglas desde un String con contenido .yal.
     * Detecta automáticamente el formato (Jalex o simplificado).
     */
    public static YALexRunner fromContent(String content) {
        YALexRunner runner = new YALexRunner();
        String cleaned = removeComments(content);
        if (cleaned.contains("rule ")) {
            parseJalexFormat(runner, cleaned);
        } else {
            parseSimpleFormat(runner, cleaned);
        }
        return runner;
    }

    // ── Formato Jalex completo ────────────────────────────────────────────

    private static void parseJalexFormat(YALexRunner runner, String content) {
        // Extraer lets
        Map<String, String> lets = extractLets(content);

        // Buscar inicio del bloque rule
        int ruleIdx = content.indexOf("rule ");
        if (ruleIdx < 0) return;

        // Saltar "rule entrypoint [args] ="
        int eq = content.indexOf('=', ruleIdx);
        if (eq < 0) return;
        String ruleSection = content.substring(eq + 1).trim();

        // Quitar trailer { ... } al final si existe
        int trailerBrace = findTrailerBrace(ruleSection);
        if (trailerBrace >= 0) {
            ruleSection = ruleSection.substring(0, trailerBrace).trim();
        }

        // Dividir alternativas
        List<String> alts = splitAlternatives(ruleSection);

        for (String alt : alts) {
            alt = alt.trim();
            if (alt.isEmpty()) continue;

            // Extraer acción { ... }
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

            // Obtener nombre del token
            String tokenName = extractTokenName(action, regexp);
            if (tokenName == null) continue;

            // Convertir regexp a Java
            String javaRegex;
            try {
                javaRegex = yalexRegexToJava(regexp);
                // Validar que el patrón compila
                Pattern.compile(javaRegex);
            } catch (Exception e) {
                // Patrón inválido: usar literal si es simple
                javaRegex = Pattern.quote(regexp);
            }

            runner.addRule(tokenName, javaRegex);

            // Si la acción indica ignorar
            if (action != null && isIgnoreAction(action)) {
                runner.ignoreToken(tokenName);
            }
        }
    }

    // ── Formato simplificado ──────────────────────────────────────────────

    private static void parseSimpleFormat(YALexRunner runner, String content) {
        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("let ") || line.startsWith("rule ")) continue;
            if (line.startsWith("{") || line.startsWith("}")) continue;
            line = line.replaceAll("^\\|\\s*", "").trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+", 2);
            if (parts.length == 2 && parts[0].matches("[A-Z_][A-Z0-9_]*")) {
                runner.addRule(parts[0], parts[1].trim());
            }
        }
    }

    // ── Tokenización ──────────────────────────────────────────────────────

    /**
     * Tokeniza la cadena de entrada.
     * Longest-match: elige el patrón que consume más caracteres;
     * en empate gana el que aparece primero (prioridad por orden).
     */
    public List<Token> tokenize(String input) {
        ensurePatterns();
        List<Token> tokens = new ArrayList<>();
        int pos  = 0;
        int line = 1;
        int len  = input.length();

        while (pos < len) {
            int bestLen  = -1;
            int bestRule = -1;

            for (int i = 0; i < rules.size(); i++) {
                Pattern p = compiledPatterns.get(i);
                if (p == null) continue;
                Matcher m = p.matcher(input);
                m.region(pos, len);
                m.useAnchoringBounds(true);
                if (m.lookingAt()) {
                    int mLen = m.group().length();
                    if (mLen > bestLen) {
                        bestLen  = mLen;
                        bestRule = i;
                    }
                }
            }

            if (bestRule >= 0 && bestLen > 0) {
                String tokenName = rules.get(bestRule)[0];
                String lexeme    = input.substring(pos, pos + bestLen);
                if (!ignoreTokens.contains(tokenName)) {
                    tokens.add(new Token(tokenName, lexeme, line));
                }
                for (char c : lexeme.toCharArray()) if (c == '\n') line++;
                pos += bestLen;
            } else {
                char c = input.charAt(pos);
                if (c == '\n') line++;
                tokens.add(new Token("LEXER_ERROR", String.valueOf(c), line));
                pos++;
            }
        }
        return tokens;
    }

    private void ensurePatterns() {
        if (!patternsDirty) return;
        compiledPatterns.clear();
        for (String[] rule : rules) {
            try {
                compiledPatterns.add(Pattern.compile(rule[1]));
            } catch (PatternSyntaxException e) {
                compiledPatterns.add(null);
            }
        }
        patternsDirty = false;
    }

    // ── Helpers de parseo .yal ────────────────────────────────────────────

    private static String removeComments(String src) {
        // Eliminar comentarios (* ... *) incluso multilinea
        return src.replaceAll("(?s)\\(\\*.*?\\*\\)", " ");
    }

    private static Map<String, String> extractLets(String content) {
        Map<String, String> lets = new LinkedHashMap<>();
        // let ident = regexp  (hasta siguiente let / rule / {)
        Pattern p = Pattern.compile(
            "let\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*=\\s*(.*?)(?=\\blet\\b|\\brule\\b|\\{|$)",
            Pattern.DOTALL);
        Matcher m = p.matcher(content);
        while (m.find()) {
            lets.put(m.group(1).trim(), m.group(2).trim());
        }
        return lets;
    }

    private static int findTrailerBrace(String s) {
        // El trailer es un { } al nivel top DESPUÉS de todas las alternativas
        // Solo existe si hay una llave que no sea acción de alternativa
        // Buscamos el último { al nivel top sin un | antes
        int depth = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            char c = s.charAt(i);
            if (c == '}') { depth++; continue; }
            if (c == '{') {
                if (depth == 0) return i;
                depth--;
            }
        }
        return -1;
    }

    private static List<String> splitAlternatives(String src) {
        List<String> alts = new ArrayList<>();
        int depth = 0; // [] y ()
        int braceDepth = 0;
        StringBuilder cur = new StringBuilder();

        int i = 0;
        while (i < src.length()) {
            char c = src.charAt(i);

            if (c == '\'' ) {
                // Literal
                cur.append(c); i++;
                if (i < src.length() && src.charAt(i) == '\\') { cur.append(src.charAt(i)); i++; }
                if (i < src.length()) { cur.append(src.charAt(i)); i++; }
                if (i < src.length() && src.charAt(i) == '\'') { cur.append(src.charAt(i)); i++; }
                continue;
            }
            if (c == '[') { depth++; cur.append(c); i++; continue; }
            if (c == ']') { depth--; cur.append(c); i++; continue; }
            if (c == '(') { depth++; cur.append(c); i++; continue; }
            if (c == ')') { depth--; cur.append(c); i++; continue; }
            if (c == '{') { braceDepth++; cur.append(c); i++; continue; }
            if (c == '}') {
                braceDepth--;
                cur.append(c); i++;
                continue;
            }
            if (c == '|' && depth == 0 && braceDepth == 0) {
                alts.add(cur.toString().trim());
                cur.setLength(0);
                i++;
                continue;
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
            if (c == '\'' ) {
                i++;
                if (i < s.length() && s.charAt(i) == '\\') i++;
                i += 2;
                continue;
            }
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
        return action.equals("skip")
            || action.equals("return lexbuf")
            || action.contains("lexbuf")
            || action.isBlank();
    }

    private static String extractTokenName(String action, String regexp) {
        if (action == null) return deriveNameFromRegexp(regexp);
        action = action.trim();

        // skip sin return
        if (action.equals("skip")) return "_WS_SKIP_";

        // return TOKEN o return TOKEN(lxm)
        Matcher m = Pattern.compile("return\\s+([A-Za-z_][A-Za-z0-9_]*)").matcher(action);
        if (m.find()) {
            String name = m.group(1);
            if (name.equals("int"))    return "INT";
            if (name.equals("float"))  return "FLOAT";
            if (name.equals("string")) return "STRING";
            if (name.equals("lexbuf")) return "__SKIP__";
            return name;
        }

        if (action.contains("raise")) return null;
        return deriveNameFromRegexp(regexp);
    }

    private static String deriveNameFromRegexp(String regexp) {
        if (regexp == null || regexp.isBlank()) return null;
        return "TOKEN_" + regexp.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    private static String expandLets(String regexp, Map<String, String> lets) {
        String result = regexp;
        for (int pass = 0; pass <= lets.size(); pass++) {
            String prev = result;
            for (Map.Entry<String, String> e : lets.entrySet()) {
                String name = e.getKey();
                String val  = "(?:" + e.getValue() + ")";
                result = result.replaceAll(
                    "(?<![a-zA-Z0-9_])" + Pattern.quote(name) + "(?![a-zA-Z0-9_])",
                    Matcher.quoteReplacement(val));
            }
            if (result.equals(prev)) break;
        }
        return result;
    }

    // ── Conversión YALex regexp → Java regex ──────────────────────────────

    /**
     * Convierte una regexp estilo YALex/OCaml a Java regex.
     *
     * Soporta:
     *   ['a'-'z']          → [a-z]
     *   ['a'-'z' 'A'-'Z']  → [a-zA-Z]
     *   digit+             → (si ya expandido) o se pasa como está
     *   '\n' '\t'          → \n \t
     *   eof                → \\z
     */
    static String yalexRegexToJava(String yalex) {
        if (yalex == null) return "";
        yalex = yalex.trim();
        if (yalex.equals("eof")) return "\\z";

        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < yalex.length()) {
            char c = yalex.charAt(i);

            // Clase de caracteres YALex: [...]
            if (c == '[') {
                int end = findMatchingBracket(yalex, i);
                String inner = yalex.substring(i + 1, end);
                sb.append(convertCharClass(inner));
                i = end + 1;
                continue;
            }

            // Literal de carácter: 'x', '\n'
            if (c == '\'') {
                int[] res = readYalCharLiteral(yalex, i);
                char ch = (char) res[0];
                sb.append(escapeJavaRegex(ch));
                i = res[1];
                continue;
            }

            // String literal: "abc"
            if (c == '"') {
                int j = i + 1;
                StringBuilder lit = new StringBuilder();
                while (j < yalex.length() && yalex.charAt(j) != '"') {
                    if (yalex.charAt(j) == '\\') {
                        j++;
                        lit.append(unescapeChar(yalex.charAt(j)));
                    } else {
                        lit.append(yalex.charAt(j));
                    }
                    j++;
                }
                sb.append(Pattern.quote(lit.toString()));
                i = j + 1;
                continue;
            }

            // Operadores de regex: pasar tal cual
            if ("()|*+?".indexOf(c) >= 0) {
                sb.append(c); i++; continue;
            }

            // Punto (cualquier carácter)
            if (c == '.') {
                sb.append('.'); i++; continue;
            }

            // Identificador (let ya expandido o keyword)
            if (Character.isLetter(c) || c == '_') {
                int j = i;
                while (j < yalex.length() && (Character.isLetterOrDigit(yalex.charAt(j)) || yalex.charAt(j) == '_')) j++;
                String word = yalex.substring(i, j);
                switch (word) {
                    case "eof"      -> sb.append("\\z");
                    default         -> sb.append(Pattern.quote(word));
                }
                i = j;
                continue;
            }

            // Backslash: escape Java estándar
            if (c == '\\' && i + 1 < yalex.length()) {
                sb.append('\\').append(yalex.charAt(i + 1));
                i += 2;
                continue;
            }

            // Carácter especial de regex: escapar
            if ("\\.^${}[]|()".indexOf(c) >= 0) {
                sb.append('\\').append(c);
            } else {
                sb.append(c);
            }
            i++;
        }
        return sb.toString();
    }

    private static String convertCharClass(String content) {
        StringBuilder sb = new StringBuilder("[");
        content = content.trim();
        boolean negated = false;
        int i = 0;
        if (i < content.length() && content.charAt(i) == '^') {
            negated = true;
            sb.append('^');
            i++;
        }

        while (i < content.length()) {
            // Saltar espacios separadores (no dentro de literales)
            while (i < content.length() && content.charAt(i) == ' ') i++;
            if (i >= content.length()) break;

            if (content.charAt(i) == '\'') {
                int[] r1 = readYalCharLiteral(content, i);
                char c1 = (char) r1[0];
                int next = r1[1];

                while (next < content.length() && content.charAt(next) == ' ') next++;

                if (next < content.length() && content.charAt(next) == '-') {
                    next++;
                    while (next < content.length() && content.charAt(next) == ' ') next++;
                    if (next < content.length() && content.charAt(next) == '\'') {
                        int[] r2 = readYalCharLiteral(content, next);
                        char c2 = (char) r2[0];
                        sb.append(escapeForClass(c1)).append('-').append(escapeForClass(c2));
                        i = r2[1];
                        continue;
                    }
                }
                sb.append(escapeForClass(c1));
                i = r1[1];
                continue;
            }

            // Otro carácter dentro de clase
            char c = content.charAt(i);
            if ("\\^]-".indexOf(c) >= 0) sb.append('\\');
            sb.append(c);
            i++;
        }
        sb.append(']');
        return sb.toString();
    }

    private static String escapeForClass(char c) {
        return switch (c) {
            case '\\' -> "\\\\";
            case ']'  -> "\\]";
            case '^'  -> "\\^";
            case '-'  -> "\\-";
            case '\n' -> "\\n";
            case '\t' -> "\\t";
            case '\r' -> "\\r";
            default   -> String.valueOf(c);
        };
    }

    private static String escapeJavaRegex(char c) {
        return switch (c) {
            case '\n' -> "\\n";
            case '\t' -> "\\t";
            case '\r' -> "\\r";
            default   -> {
                if ("\\.^${}[]|()*+?".indexOf(c) >= 0)
                    yield "\\" + c;
                yield String.valueOf(c);
            }
        };
    }

    private static int[] readYalCharLiteral(String s, int pos) {
        pos++; // saltar '
        char c;
        if (pos < s.length() && s.charAt(pos) == '\\') {
            pos++;
            c = pos < s.length() ? unescapeChar(s.charAt(pos)) : '\\';
            pos++;
        } else {
            c = pos < s.length() ? s.charAt(pos) : 0;
            pos++;
        }
        if (pos < s.length() && s.charAt(pos) == '\'') pos++;
        return new int[]{c, pos};
    }

    private static char unescapeChar(char c) {
        return switch (c) {
            case 'n'  -> '\n';
            case 't'  -> '\t';
            case 'r'  -> '\r';
            case '0'  -> '\0';
            case '\\' -> '\\';
            case '\'' -> '\'';
            case '"'  -> '"';
            default   -> c;
        };
    }

    private static int findMatchingBracket(String s, int open) {
        int i = open + 1;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\'') {
                i++;
                if (i < s.length() && s.charAt(i) == '\\') i++;
                i += 2;
                continue;
            }
            if (c == ']') return i;
            i++;
        }
        return s.length() - 1;
    }
}
