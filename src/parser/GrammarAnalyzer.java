package parser;

import java.util.*;

public class GrammarAnalyzer {

    public static class AnalysisResult {

        public final Set<String> unreachable =
                new LinkedHashSet<>();

        public final Set<String> undefined =
                new LinkedHashSet<>();

        public final List<String> warnings =
                new ArrayList<>();

        public boolean hasIssues() {
            return !unreachable.isEmpty()
                    || !undefined.isEmpty()
                    || !warnings.isEmpty();
        }
    }

    public static AnalysisResult analyze(
            Grammar grammar
    ) {

        AnalysisResult result =
                new AnalysisResult();

        detectUndefined(
                grammar,
                result
        );

        detectUnreachable(
                grammar,
                result
        );

        return result;
    }

    // ─────────────────────────────────────────────

    private static void detectUndefined(
            Grammar g,
            AnalysisResult result
    ) {

        for (Production p : g.productions) {

            for (String sym : p.right) {

                if (
                        sym.equals("ε")
                                || sym.equals("$")
                ) {
                    continue;
                }

                if (
                        !g.terminals.contains(sym)
                                && !g.nonTerminals.contains(sym)
                ) {

                    result.undefined.add(sym);
                }
            }
        }

        for (String s : result.undefined) {

            result.warnings.add(
                    "Undefined symbol: " + s
            );
        }
    }

    // ─────────────────────────────────────────────

    private static void detectUnreachable(
            Grammar g,
            AnalysisResult result
    ) {

        Set<String> reachable =
                new LinkedHashSet<>();

        Queue<String> q =
                new LinkedList<>();

        reachable.add(g.startSymbol);

        q.add(g.startSymbol);

        while (!q.isEmpty()) {

            String nt =
                    q.poll();

            for (Production p : g.getProductions(nt)) {

                for (String sym : p.right) {

                    if (
                            g.nonTerminals.contains(sym)
                                    && !reachable.contains(sym)
                    ) {

                        reachable.add(sym);

                        q.add(sym);
                    }
                }
            }
        }

        for (String nt : g.nonTerminals) {

            if (
                    nt.equals(Grammar.AUG_START)
            ) {
                continue;
            }

            if (!reachable.contains(nt)) {

                result.unreachable.add(nt);

                result.warnings.add(
                        "Unreachable non-terminal: " + nt
                );
            }
        }
    }
}