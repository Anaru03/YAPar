// REEMPLAZA COMPLETAMENTE:
// parser/YAParFileParser.java

package parser;

import java.io.*;
import java.util.*;
import lexer.YALexRunner;

public class YAParFileParser {

    public static class ParseResult {

        public final Grammar grammar;

        public final List<String> tokenValidationErrors;

        public final List<String> warnings;

        public ParseResult(
                Grammar grammar,
                List<String> errors,
                List<String> warnings
        ) {

            this.grammar = grammar;
            this.tokenValidationErrors = errors;
            this.warnings = warnings;
        }

        public boolean hasErrors() {
            return !tokenValidationErrors.isEmpty();
        }
    }

    // ─────────────────────────────────────────────────────────

    public static Grammar parse(String filePath)
            throws IOException {

        return parseContent(readFile(filePath));
    }

    public static ParseResult parseAndValidate(
            String yalpContent,
            YALexRunner lexer
    ) {

        Grammar g =
                parseContent(yalpContent);

        List<String> errors =
                validateTokens(g, lexer);

        GrammarAnalyzer.AnalysisResult analysis =
                GrammarAnalyzer.analyze(g);

        return new ParseResult(
                g,
                errors,
                analysis.warnings
        );
    }

    // ─────────────────────────────────────────────────────────
    // VALIDACIÓN CRUZADA REAL
    // ─────────────────────────────────────────────────────────

    public static List<String> validateTokens(
            Grammar grammar,
            YALexRunner lexer
    ) {

        Set<String> lexerTokens =
                new LinkedHashSet<>();

        for (String[] r : lexer.getRules()) {
            lexerTokens.add(r[0]);
        }

        List<String> errors =
                new ArrayList<>();

        // Tokens declarados en YALP pero no definidos en YALEX
        for (String t : grammar.terminals) {

            if (
                    t.equals("$")
                            || t.equals("ε")
            ) {
                continue;
            }

            if (!lexerTokens.contains(t)) {

                errors.add(
                        "[YALP -> YALEX] Token '" + t +
                                "' declarado en .yalp NO existe en .yal"
                );
            }
        }

        // Tokens definidos en YALEX pero nunca usados en YALP
        for (String lt : lexerTokens) {

            if (!grammar.terminals.contains(lt)) {

                errors.add(
                        "[YALEX -> YALP] Token '" + lt +
                                "' definido en .yal PERO no usado en .yalp"
                );
            }
        }

        return errors;
    }

    // ─────────────────────────────────────────────────────────
    // PARSER .YALP
    // ─────────────────────────────────────────────────────────

    public static Grammar parseContent(String content) {

        content = stripBlockComments(content);

        Grammar g = new Grammar();

        String[] parts = content.split("%%", 2);

        if (parts.length < 2) {

            throw new RuntimeException(
                    "Falta separador %% en .yalp"
            );
        }

        // TOKENS

        for (String line : parts[0].split("\\n")) {

            line = line.trim();

            if (line.startsWith("%token")) {

                String[] toks =
                        line.substring(6)
                                .trim()
                                .split("\\s+");

                for (String tok : toks) {

                    if (!tok.isEmpty()) {
                        g.terminals.add(tok);
                    }
                }
            }

            else if (line.startsWith("IGNORE")) {

                String[] toks =
                        line.substring(6)
                                .trim()
                                .split("\\s+");

                for (String tok : toks) {

                    if (!tok.isEmpty()) {
                        g.ignoreTokens.add(tok);
                    }
                }
            }
        }

        // PRODUCCIONES

        String productions =
                parts[1].trim();

        String[] blocks =
                productions.split(";");

        boolean first = true;

        for (String block : blocks) {

            block = block.trim();

            if (block.isEmpty()) {
                continue;
            }

            int colon =
                    block.indexOf(':');

            if (colon < 0) {
                continue;
            }

            String head =
                    block.substring(0, colon)
                            .trim();

            if (head.isEmpty()) {
                continue;
            }

            g.nonTerminals.add(head);

            if (first) {

                g.startSymbol = head;

                first = false;
            }

            String rhs =
                    block.substring(colon + 1);

            String[] alts =
                    rhs.split("\\|");

            for (String alt : alts) {

                alt = alt.trim();

                if (alt.isEmpty()) {
                    continue;
                }

                List<String> symbols =
                        new ArrayList<>();

                for (String s : alt.split("\\s+")) {

                    if (!s.isEmpty()) {
                        symbols.add(s);
                    }
                }

                g.addProduction(head, symbols);
            }
        }

        g.augment();

        return g;
    }

    // ─────────────────────────────────────────────────────────

    private static String stripBlockComments(
            String src
    ) {

        StringBuilder sb =
                new StringBuilder();

        int i = 0;

        while (i < src.length()) {

            if (
                    i + 1 < src.length()
                            && src.charAt(i) == '/'
                            && src.charAt(i + 1) == '*'
            ) {

                i += 2;

                while (
                        i + 1 < src.length()
                                && !(
                                src.charAt(i) == '*'
                                        && src.charAt(i + 1) == '/'
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

    private static String readFile(String path)
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

        return sb.toString();
    }
}