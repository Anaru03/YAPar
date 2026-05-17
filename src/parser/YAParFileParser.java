package parser;

import java.io.*;
import java.util.*;

public class YAParFileParser {

    public static Grammar parse(String filePath) throws IOException {
        String content = readFile(filePath);
        return parseContent(content);
    }

    public static Grammar parseContent(String content) {
        content = content.replaceAll("/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/", " ");

        Grammar g = new Grammar();
        String[] parts = content.split("%%", 2);
        if (parts.length < 2) throw new RuntimeException("Missing %% separator in .yalp file");

        String tokenSection = parts[0];
        for (String line : tokenSection.split("\\n")) {
            line = line.trim();
            if (line.startsWith("%token")) {
                String[] tokens = line.substring(6).trim().split("\\s+");
                for (String tok : tokens) {
                    if (!tok.isEmpty()) g.terminals.add(tok);
                }
            } else if (line.startsWith("IGNORE")) {
                String[] toks = line.substring(6).trim().split("\\s+");
                for (String t : toks) if (!t.isEmpty()) g.ignoreTokens.add(t);
            }
        }

        String prodSection = parts[1].trim();
        String[] blocks = prodSection.split(";");
        boolean first = true;
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

            String alts = block.substring(colonIdx + 1);
            String[] alternatives = alts.split("\\|");
            for (String alt : alternatives) {
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

    private static String readFile(String path) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
        }
        return sb.toString();
    }
}