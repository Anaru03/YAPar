package gui;

import lexer.YALexRunner;
import parser.*;
import runtime.Token;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

public class YAParGUI extends JFrame {

    private final JTextArea yalArea = new JTextArea();
    private final JTextArea yalpArea = new JTextArea();
    private final JTextArea inputArea = new JTextArea();

    private final JTextArea outputArea = new JTextArea();

    private final JTable slrTable = new JTable();

    private final JTable traceTable = new JTable();

    public YAParGUI() {

        setTitle("YAPar — Analizador SLR(1)");

        setSize(1500, 900);

        setDefaultCloseOperation(EXIT_ON_CLOSE);

        setLocationRelativeTo(null);

        setLayout(new BorderLayout());

        JPanel editors = new JPanel(new GridLayout(1, 3, 10, 10));

        editors.setBorder(new EmptyBorder(10, 10, 10, 10));

        configureEditor(yalArea);
        configureEditor(yalpArea);
        configureEditor(inputArea);

        editors.add(createPanel("Archivo .yal", yalArea));
        editors.add(createPanel("Archivo .yalp", yalpArea));
        editors.add(createPanel("Input", inputArea));

        add(editors, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(1, 2));

        outputArea.setFont(new Font("JetBrains Mono", Font.PLAIN, 13));
        outputArea.setEditable(false);

        center.add(createPanel("Salida", new JScrollPane(outputArea)));

        JPanel right = new JPanel(new GridLayout(2, 1));

        right.add(createPanel("Tabla SLR", new JScrollPane(slrTable)));

        right.add(createPanel("Traza Parse", new JScrollPane(traceTable)));

        center.add(right);

        add(center, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton lowBtn = new JButton("LOW");
        JButton mediumBtn = new JButton("MEDIUM");
        JButton highBtn = new JButton("HIGH");
        JButton parseBtn = new JButton("PARSEAR");

        lowBtn.addActionListener(e -> loadLow());
        mediumBtn.addActionListener(e -> loadMedium());
        highBtn.addActionListener(e -> loadHigh());

        parseBtn.addActionListener(e -> runParser());

        actions.add(lowBtn);
        actions.add(mediumBtn);
        actions.add(highBtn);
        actions.add(parseBtn);

        add(actions, BorderLayout.SOUTH);

        loadLow();
    }

    private JPanel createPanel(String title, Component c) {

        JPanel p = new JPanel(new BorderLayout());

        p.setBorder(BorderFactory.createTitledBorder(title));

        p.add(c, BorderLayout.CENTER);

        return p;
    }

    private void configureEditor(JTextArea area) {

        area.setFont(new Font("JetBrains Mono", Font.PLAIN, 13));

        area.setLineWrap(false);
    }

    private void runParser() {

        try {

            String yal = yalArea.getText();
            String yalp = yalpArea.getText();
            String input = inputArea.getText();

            outputArea.setText("");

            // LEXER
            YALexRunner lexer =
                    YALexRunner.fromContent(yal);

            // PARSE GRAMMAR
            YAParFileParser.ParseResult pr =
                    YAParFileParser.parseAndValidate(
                            yalp,
                            lexer
                    );

            if (pr.hasErrors()) {

                for (String err : pr.tokenValidationErrors) {

                    outputArea.append(err + "\n");
                }

                return;
            }

            Grammar g = pr.grammar;

            // ANALYSIS
            GrammarAnalyzer.AnalysisResult analysis =
                    GrammarAnalyzer.analyze(g);

            for (String w : analysis.warnings) {

                outputArea.append("WARNING: " + w + "\n");
            }

            // LR0
            LR0Automaton automaton =
                    new LR0Automaton(g);

            // FIRST/FOLLOW
            Map<String, Set<String>> first =
                    g.computeFirst();

            Map<String, Set<String>> follow =
                    g.computeFollow(first);

            // SLR TABLE
            SLRTable table =
                    SLRTable.build(
                            automaton,
                            g,
                            follow
                    );

            if (!table.conflicts.isEmpty()) {

                outputArea.append("\nCONFLICTOS:\n");

                for (String c : table.conflicts) {

                    outputArea.append(c + "\n");
                }
            }

            // TOKENIZE
            List<Token> rawTokens =
                    lexer.tokenize(input);

            List<Token> tokens =
                    rawTokens.stream()
                            .filter(t ->
                                    !g.ignoreTokens.contains(t.type)
                            )
                            .collect(Collectors.toList());

            outputArea.append("\nTOKENS:\n");

            for (Token t : tokens) {

                outputArea.append(t + "\n");
            }

            // PARSER
            Parser parser =
                    new Parser(table, g);

            parser.parse(tokens);

            if (parser.accepted) {

                outputArea.append("\n✓ ACCEPTED\n");
            }

            else {

                outputArea.append("\n✗ REJECTED\n");

                outputArea.append(parser.errorMessage + "\n");
            }

            buildSLRTable(table, automaton, g);

            buildTraceTable(parser.trace);
        }

        catch (Exception ex) {

            ex.printStackTrace();

            outputArea.setText(
                    "ERROR:\n" + ex.getMessage()
            );
        }
    }

    private void buildSLRTable(
            SLRTable table,
            LR0Automaton automaton,
            Grammar grammar
    ) {

        List<String> terms =
                new ArrayList<>(grammar.terminals);

        terms.add("$");

        List<String> nonTerms =
                new ArrayList<>(grammar.nonTerminals);

        nonTerms.remove(Grammar.AUG_START);

        Vector<String> cols = new Vector<>();

        cols.add("STATE");

        cols.addAll(terms);

        cols.add("|");

        cols.addAll(nonTerms);

        Vector<Vector<Object>> data = new Vector<>();

        for (State s : automaton.states) {

            Vector<Object> row = new Vector<>();

            row.add(s.id);

            for (String t : terms) {

                String v =
                        table.getAction(s.id, t);

                row.add(v == null ? "" : v);
            }

            row.add("|");

            for (String nt : nonTerms) {

                Integer g =
                        table.getGoto(s.id, nt);

                row.add(g == null ? "" : g);
            }

            data.add(row);
        }

        slrTable.setModel(
                new DefaultTableModel(data, cols)
        );
    }

    private void buildTraceTable(
            List<String> trace
    ) {

        Vector<String> cols = new Vector<>();

        cols.add("TRACE");

        Vector<Vector<Object>> data = new Vector<>();

        for (String s : trace) {

            Vector<Object> row = new Vector<>();

            row.add(s);

            data.add(row);
        }

        traceTable.setModel(
                new DefaultTableModel(data, cols)
        );
    }

    // ─────────────────────────────────────────
    // PRESETS
    // ─────────────────────────────────────────

    private void loadLow() {

        yalArea.setText("""
(* Lexer baja complejidad *)

let letter = ['a'-'z' 'A'-'Z']
let digit  = ['0'-'9']

rule tokens =
    letter (letter | digit)*   { return ID }
  | '+'                        { return PLUS }
  | '*'                        { return TIMES }
  | '('                        { return LPAREN }
  | ')'                        { return RPAREN }
  | [' ' '\\t' '\\n']+         { return WS }
""");

        yalpArea.setText("""
%token PLUS TIMES LPAREN RPAREN ID
%token WS

IGNORE WS

%%

expr:
    expr PLUS term
  | term
;

term:
    term TIMES factor
  | factor
;

factor:
    LPAREN expr RPAREN
  | ID
;
""");

        inputArea.setText(
                "a + b * ( c + d )"
        );
    }

    private void loadMedium() {

        inputArea.setText(
                "if ( x ) { y = 3 ; }"
        );
    }

    private void loadHigh() {

        inputArea.setText(
                "a + b * 2 - c / d"
        );
    }
}