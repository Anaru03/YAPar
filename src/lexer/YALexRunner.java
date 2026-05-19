package lexer;

import java.io.*;
import java.util.*;
import runtime.Token;

/**
 * YALexRunner
 *
 * IMPLEMENTADO CON AUTÓMATAS FINITOS
 * SIN java.util.regex
 */
public class YALexRunner {

    private final List<String[]> rules =
            new ArrayList<>();

    private final Set<String> ignoreTokens =
            new LinkedHashSet<>();

    private List<DFABuilder.DFAState> dfa;

    private DFABuilder.DFAState dfaStart;

    private boolean dfaDirty = true;

    public YALexRunner() {}

    // ─────────────────────────────────────────────────────────────────────
    // API
    // ─────────────────────────────────────────────────────────────────────

    public void addRule(
            String token,
            String regex
    ) {

        rules.add(
                new String[]{token, regex}
        );

        dfaDirty = true;
    }

    public void ignoreToken(String token) {
        ignoreTokens.add(token);
    }

    public Set<String> getIgnoreTokens() {
        return Collections.unmodifiableSet(ignoreTokens);
    }

    public List<String[]> getRules() {
        return Collections.unmodifiableList(rules);
    }

    public List<String> getDeclaredTokenNames() {

        List<String> result =
                new ArrayList<>();

        for (String[] r : rules) {
            result.add(r[0]);
        }

        return result;
    }

    // ─────────────────────────────────────────────────────────────────────
    // CARGA
    // ─────────────────────────────────────────────────────────────────────

    public static YALexRunner fromFile(String path)
            throws IOException {

        StringBuilder sb =
                new StringBuilder();

        try (
                BufferedReader br =
                        new BufferedReader(
                                new FileReader(path)
                        )
        ) {

            String line;

            while ((line = br.readLine()) != null) {

                sb.append(line)
                        .append("\n");
            }
        }

        return fromContent(sb.toString());
    }

    public static YALexRunner fromContent(String content) {

        YALexRunner runner =
                new YALexRunner();

        String cleaned =
                removeComments(content);

        parseYalex(runner, cleaned);

        return runner;
    }

    // ─────────────────────────────────────────────────────────────────────
    // VALIDACIÓN
    // ─────────────────────────────────────────────────────────────────────

    public List<String> validateAgainst(
            Set<String> yalpTokens
    ) {

        Set<String> lexerTokens =
                new LinkedHashSet<>();

        for (String[] r : rules) {
            lexerTokens.add(r[0]);
        }

        List<String> missing =
                new ArrayList<>();

        for (String t : yalpTokens) {

            if (
                    !t.equals("$")
                            && !t.equals("ε")
                            && !lexerTokens.contains(t)
            ) {

                missing.add(t);
            }
        }

        return missing;
    }

    // ─────────────────────────────────────────────────────────────────────
    // PARSE .yal
    // ─────────────────────────────────────────────────────────────────────

    private static void parseYalex(
            YALexRunner runner,
            String content
    ) {

        Map<String, String> lets =
                extractLets(content);

        int rulePos =
                content.indexOf("rule ");

        if (rulePos < 0) {
            return;
        }

        int equalPos =
                content.indexOf("=", rulePos);

        if (equalPos < 0) {
            return;
        }

        String rulesSection =
                content.substring(equalPos + 1);

        List<String> alternatives =
                splitAlternatives(rulesSection);

        for (String alt : alternatives) {

            alt = alt.trim();

            if (alt.isEmpty()) {
                continue;
            }

            int braceOpen =
                    alt.indexOf('{');

            int braceClose =
                    alt.lastIndexOf('}');

            if (
                    braceOpen < 0
                            || braceClose < 0
            ) {
                continue;
            }

            String regex =
                    alt.substring(0, braceOpen)
                            .trim();

            String action =
                    alt.substring(
                            braceOpen + 1,
                            braceClose
                    ).trim();

            regex =
                    expandLets(regex, lets);

            String token =
                    extractTokenName(action);

            if (token == null) {
                continue;
            }

            runner.addRule(token, regex);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // TOKENIZE
    // ─────────────────────────────────────────────────────────────────────

    public List<Token> tokenize(String input) {

        if (dfaDirty) {
            buildDFA();
        }

        List<Token> tokens =
                new ArrayList<>();

        int pos = 0;

        int line = 1;

        while (pos < input.length()) {

            DFABuilder.DFAState state =
                    dfaStart;

            int lastAcceptPos = -1;

            String lastAcceptToken = null;

            int i = pos;

            while (i < input.length()) {

                char c =
                        input.charAt(i);

                DFABuilder.DFAState next =
                        state.transitions.get(c);

                if (next == null) {
                    break;
                }

                state = next;

                i++;

                if (state.acceptToken != null) {

                    lastAcceptPos = i;

                    lastAcceptToken =
                            state.acceptToken;
                }
            }

            if (lastAcceptPos > pos) {

                String lexeme =
                        input.substring(
                                pos,
                                lastAcceptPos
                        );

                if (
                        !ignoreTokens.contains(
                                lastAcceptToken
                        )
                ) {

                    tokens.add(
                            new Token(
                                    lastAcceptToken,
                                    lexeme,
                                    line
                            )
                    );
                }

                for (
                        int k = pos;
                        k < lastAcceptPos;
                        k++
                ) {

                    if (input.charAt(k) == '\n') {
                        line++;
                    }
                }

                pos = lastAcceptPos;
            }

            else {

                char c =
                        input.charAt(pos);

                if (
                        c == ' '
                                || c == '\t'
                                || c == '\n'
                                || c == '\r'
                ) {

                    if (c == '\n') {
                        line++;
                    }

                    pos++;

                    continue;
                }

                tokens.add(
                        new Token(
                                "LEXER_ERROR",
                                String.valueOf(c),
                                line
                        )
                );

                pos++;
            }
        }

        return tokens;
    }

    // ─────────────────────────────────────────────────────────────────────
    // BUILD DFA
    // ─────────────────────────────────────────────────────────────────────

    private void buildDFA() {

        NFANode.resetCounter();

        NFANode combinedStart =
                new NFANode();

        for (int i = 0; i < rules.size(); i++) {

            String token =
                    rules.get(i)[0];

            String regex =
                    rules.get(i)[1];

            NFAFragment frag =
                    RegexToNFA.build(regex);

            frag.end.acceptToken =
                    token;

            frag.end.priority =
                    i;

            combinedStart.addEpsilon(
                    frag.start
            );
        }

        dfa =
                DFABuilder.build(combinedStart);

        dfaStart =
                dfa.get(0);

        dfaDirty = false;
    }

    // ─────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────

    private static String removeComments(String src) {

        StringBuilder sb =
                new StringBuilder();

        int i = 0;

        while (i < src.length()) {

            if (
                    i + 1 < src.length()
                            && src.charAt(i) == '('
                            && src.charAt(i + 1) == '*'
            ) {

                i += 2;

                while (
                        i + 1 < src.length()
                                && !(
                                src.charAt(i) == '*'
                                        && src.charAt(i + 1) == ')'
                        )
                ) {
                    i++;
                }

                i += 2;
            }

            else {

                sb.append(src.charAt(i));

                i++;
            }
        }

        return sb.toString();
    }

    private static Map<String, String> extractLets(
            String content
    ) {

        Map<String, String> lets =
                new LinkedHashMap<>();

        String[] lines =
                content.split("\n");

        for (String line : lines) {

            line = line.trim();

            if (!line.startsWith("let ")) {
                continue;
            }

            int eq =
                    line.indexOf('=');

            if (eq < 0) {
                continue;
            }

            String name =
                    line.substring(4, eq)
                            .trim();

            String value =
                    line.substring(eq + 1)
                            .trim();

            lets.put(name, value);
        }

        return lets;
    }

    private static String expandLets(
            String regex,
            Map<String, String> lets
    ) {

        String result = regex;

        boolean changed = true;

        while (changed) {

            changed = false;

            for (
                    Map.Entry<String, String> e
                            : lets.entrySet()
            ) {

                String key =
                        e.getKey();

                String value =
                        "(" + e.getValue() + ")";

                int idx =
                        result.indexOf(key);

                while (idx >= 0) {

                    boolean leftOk =
                            idx == 0
                                    || !Character.isLetterOrDigit(
                                    result.charAt(idx - 1)
                            );

                    int end =
                            idx + key.length();

                    boolean rightOk =
                            end >= result.length()
                                    || !Character.isLetterOrDigit(
                                    result.charAt(end)
                            );

                    if (leftOk && rightOk) {

                        result =
                                result.substring(0, idx)
                                        + value
                                        + result.substring(end);

                        changed = true;

                        idx =
                                result.indexOf(key);
                    }

                    else {

                        idx =
                                result.indexOf(key, end);
                    }
                }
            }
        }

        return result;
    }

    private static List<String> splitAlternatives(
            String src
    ) {

        List<String> result =
                new ArrayList<>();

        StringBuilder current =
                new StringBuilder();

        int parenDepth = 0;
        int bracketDepth = 0;
        int braceDepth = 0;

        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = 0; i < src.length(); i++) {

            char c = src.charAt(i);

            if (c == '\\') {

                current.append(c);

                if (i + 1 < src.length()) {

                    current.append(
                            src.charAt(i + 1)
                    );

                    i++;
                }

                continue;
            }

            if (c == '"' && !inSingleQuote) {

                inDoubleQuote =
                        !inDoubleQuote;

                current.append(c);

                continue;
            }

            if (c == '\'' && !inDoubleQuote) {

                inSingleQuote =
                        !inSingleQuote;

                current.append(c);

                continue;
            }

            if (
                    inSingleQuote
                            || inDoubleQuote
            ) {

                current.append(c);

                continue;
            }

            if (c == '(') parenDepth++;
            else if (c == ')') parenDepth--;

            else if (c == '[') bracketDepth++;
            else if (c == ']') bracketDepth--;

            else if (c == '{') braceDepth++;
            else if (c == '}') braceDepth--;

            if (
                    c == '|'
                            && parenDepth == 0
                            && bracketDepth == 0
                            && braceDepth == 0
            ) {

                String alt =
                        current.toString().trim();

                if (!alt.isEmpty()) {
                    result.add(alt);
                }

                current.setLength(0);

                continue;
            }

            current.append(c);
        }

        String last =
                current.toString().trim();

        if (!last.isEmpty()) {
            result.add(last);
        }

        return result;
    }

    private static String extractTokenName(
            String action
    ) {

        if (action == null) {
            return null;
        }

        int ret =
                action.indexOf("return");

        if (ret < 0) {
            return null;
        }

        String s =
                action.substring(ret + 6)
                        .trim();

        StringBuilder sb =
                new StringBuilder();

        for (int i = 0; i < s.length(); i++) {

            char c = s.charAt(i);

            if (
                    Character.isLetterOrDigit(c)
                            || c == '_'
            ) {

                sb.append(c);
            }

            else {
                break;
            }
        }

        if (sb.length() == 0) {
            return null;
        }

        return sb.toString();
    }
}