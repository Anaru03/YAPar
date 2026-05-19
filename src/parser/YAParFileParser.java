package parser;

import java.io.*;
import java.util.*;
import lexer.YALexRunner;

public class YAParFileParser {

    /**
     * Resultado del parse + validación
     */
    public static class ParseResult {

        public final Grammar grammar;

        public final List<String> tokenValidationErrors;

        public ParseResult(
                Grammar grammar,
                List<String> errors
        ) {
            this.grammar = grammar;
            this.tokenValidationErrors = errors;
        }

        public boolean hasErrors() {
            return !tokenValidationErrors.isEmpty();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // API
    // ─────────────────────────────────────────────────────────────────────

    public static Grammar parse(String filePath)
            throws IOException {

        return parseContent(readFile(filePath));
    }

    public static ParseResult parseAndValidate(
            String yalpContent,
            YALexRunner lexer
    ) {

        Grammar g = parseContent(yalpContent);

        List<String> errors =
                validateTokens(g, lexer);

        return new ParseResult(g, errors);
    }

    public static List<String> validateTokens(
            Grammar grammar,
            YALexRunner lexer
    ) {

        List<String> missing =
                lexer.validateAgainst(grammar.terminals);

        List<String> errors = new ArrayList<>();

        for (String t : missing) {

            errors.add(
                    "ERROR: Token '" + t +
                            "' declarado en .yalp no está definido en .yal"
            );
        }

        return errors;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Parser .yalp
    // ─────────────────────────────────────────────────────────────────────

    public static Grammar parseContent(String content) {

        content = stripBlockComments(content);

        Grammar g = new Grammar();

        String[] parts = content.split("%%", 2);

        if (parts.length < 2) {
            throw new RuntimeException(
                    "Falta separador %% en el archivo .yalp"
            );
        }

        // ── TOKENS ───────────────────────────────────────────────────────

        for (String line : parts[0].split("\\n")) {

            line = line.trim();

            if (line.startsWith("%token")) {

                String[] toks =
                        line.substring(6).trim().split("\\s+");

                for (String tok : toks) {

                    if (!tok.isEmpty()) {
                        g.terminals.add(tok);
                    }
                }
            }

            else if (line.startsWith("IGNORE")) {

                String[] toks =
                        line.substring(6).trim().split("\\s+");

                for (String tok : toks) {

                    if (!tok.isEmpty()) {
                        g.ignoreTokens.add(tok);
                    }
                }
            }
        }

        // ── PRODUCCIONES ────────────────────────────────────────────────

        String productions =
                parts[1].trim();

        String[] blocks =
                productions.split(";");

        boolean first = true;

        for (String block : blocks) {

            block = block.trim();

            if (block.isEmpty()) continue;

            int colon = block.indexOf(':');

            if (colon < 0) continue;

            String head =
                    block.substring(0, colon).trim();

            if (head.isEmpty()) continue;

            g.nonTerminals.add(head);

            if (first) {
                g.startSymbol = head;
                first = false;
            }

            String rhsSection =
                    block.substring(colon + 1);

            String[] alternatives =
                    rhsSection.split("\\|");

            for (String alt : alternatives) {

                alt = alt.trim();

                if (alt.isEmpty()) continue;

                List<String> symbols =
                        new ArrayList<>();

                for (String sym : alt.split("\\s+")) {

                    if (!sym.isEmpty()) {
                        symbols.add(sym);
                    }
                }

                g.addProduction(head, symbols);
            }
        }

        g.augment();

        return g;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    private static String stripBlockComments(String src) {

        StringBuilder sb = new StringBuilder();

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
                                && !(src.charAt(i) == '*'
                                && src.charAt(i + 1) == '/')
                ) {
                    i++;
                }

                i += 2;
            }

            else {
                sb.append(src.charAt(i++));
            }
        }

        return sb.toString();
    }

    private static String readFile(String path)
            throws IOException {

        StringBuilder sb = new StringBuilder();

        try (BufferedReader br =
                     new BufferedReader(new FileReader(path))) {

            String line;

            while ((line = br.readLine()) != null) {

                sb.append(line).append("\n");
            }
        }

        return sb.toString();
    }
}