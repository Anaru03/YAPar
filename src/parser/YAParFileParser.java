package parser;

import lexer.YALexRunner;
import java.io.*;
import java.util.*;

public class YAParFileParser {

    /** Resultado del parse + validación */
    public static class ParseResult {
        public final Grammar grammar;
        public final List<String> tokenValidationErrors;

        public ParseResult(Grammar grammar, List<String> errors) {
            this.grammar = grammar;
            this.tokenValidationErrors = errors;
        }

        public boolean hasErrors() {
            return !tokenValidationErrors.isEmpty();
        }
    }

    // ── Métodos principales ───────────────────────────────────────────────

    public static Grammar parse(String filePath) throws IOException {
        return parseContent(readFile(filePath));
    }

    /**
     * Parsea el .yalp y valida sus tokens contra el lexer .yal provisto.
     * Si algún token del .yalp no está en el .yal, lanza RuntimeException
     * con el listado de tokens faltantes.
     */
    public static ParseResult parseAndValidate(String yalpContent, YALexRunner lexer) {
        Grammar g = parseContent(yalpContent);
        List<String> errors = validateTokens(g, lexer);
        return new ParseResult(g, errors);
    }

    /**
     * Valida que todos los tokens declarados en la gramática .yalp
     * existan como reglas en el lexer .yal.
     * Devuelve lista de mensajes de error (vacía = OK).
     */
    public static List<String> validateTokens(Grammar grammar, YALexRunner lexer) {
        List<String> missing = lexer.validateAgainst(grammar.terminals);
        List<String> errors  = new ArrayList<>();
        for (String t : missing) {
            errors.add("ERROR: Token '" + t + "' declarado en .yalp no está definido en .yal");
        }
        return errors;
    }

    // ── Parser del formato .yalp ──────────────────────────────────────────

    public static Grammar parseContent(String content) {
        // Eliminar comentarios /* ... */
        content = stripBlockComments(content);

        Grammar g = new Grammar();
        String[] parts = content.split("%%", 2);
        if (parts.length < 2) throw new RuntimeException("Falta separador %% en el archivo .yalp");

        // Sección de tokens
        for (String line : parts[0].split("\\n")) {
            line = line.trim();
            if (line.startsWith("%token")) {
                for (String tok : line.substring(6).trim().split("\\s+")) {
                    if (!tok.isEmpty()) g.terminals.add(tok);
                }
            } else if (line.startsWith("IGNORE")) {
                for (String t : line.substring(6).trim().split("\\s+")) {
                    if (!t.isEmpty()) g.ignoreTokens.add(t);
                }
            }
        }

        // Sección de producciones
        String prodSection = parts[1].trim();
        String[] blocks    = prodSection.split(";");
        boolean first      = true;

        for (String block : blocks) {
            block = block.trim();
            if (block.isEmpty()) continue;

            int colonIdx = block.indexOf(':');
            if (colonIdx < 0) continue;

            String head = block.substring(0, colonIdx).trim();
            if (head.isEmpty()) continue;

            g.nonTerminals.add(head);
            if (first) {
                g.startSymbol = head;
                first = false;
            }

            for (String alt : block.substring(colonIdx + 1).split("\\|")) {
                alt = alt.trim();
                if (alt.isEmpty()) continue;
                List<String> symbols = new ArrayList<>();
                for (String sym : alt.split("\\s+")) {
                    if (!sym.isEmpty()) symbols.add(sym);
                }
                g.addProduction(head, symbols);
            }
        }

        g.augment();
        return g;
    }

    // ── Utilidades ────────────────────────────────────────────────────────

    private static String stripBlockComments(String src) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < src.length()) {
            if (i + 1 < src.length() && src.charAt(i) == '/' && src.charAt(i+1) == '*') {
                i += 2;
                while (i + 1 < src.length() && !(src.charAt(i) == '*' && src.charAt(i+1) == '/')) i++;
                i += 2;
            } else {
                sb.append(src.charAt(i++));
            }
        }
        return sb.toString();
    }

    private static String readFile(String path) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
        }
        return sb.toString();
    }
}
