package gui;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import lexer.YALexRunner;
import parser.*;
import runtime.Token;

public class YAParGUI extends JFrame {

    // ── COLORS ──────────────────────────────────────────────
    private final Color BG      = new Color(13, 15, 20);
    private final Color BG2     = new Color(19, 22, 29);
    private final Color BG3     = new Color(26, 30, 40);
    private final Color BORDER  = new Color(46, 53, 72);
    private final Color TEXT    = new Color(232, 234, 245);
    private final Color TEXT2   = new Color(144, 153, 184);
    private final Color TEXT3   = new Color(92, 100, 128);
    private final Color ACCENT  = new Color(91, 108, 249);
    private final Color GREEN   = new Color(62, 207, 142);
    private final Color RED     = new Color(240, 79, 90);
    private final Color YELLOW  = new Color(245, 200, 66);
    private final Color BLUE    = new Color(124, 139, 250);

    // ── TABS ────────────────────────────────────────────────
    private final CardLayout tabsLayout = new CardLayout();
    private final JPanel     tabsPanel  = new JPanel(tabsLayout);

    // ── EDITORS ─────────────────────────────────────────────
    private final JTextArea lowYal = new JTextArea(), lowYalp = new JTextArea(), lowTxt = new JTextArea();
    private final JTextArea medYal = new JTextArea(), medYalp = new JTextArea(), medTxt = new JTextArea();
    private final JTextArea highYal= new JTextArea(), highYalp= new JTextArea(), highTxt= new JTextArea();
    private final JTextArea validateYal = new JTextArea(), validateYalp = new JTextArea();

    // ── RESULTS per tab ─────────────────────────────────────
    private final JTextArea lowOutput = new JTextArea(),  lowTokens = new JTextArea();
    private final JLabel    lowStatus = new JLabel("PENDING");
    private final JTable    lowSLR    = new JTable(),     lowTrace   = new JTable();

    private final JTextArea medOutput = new JTextArea(),  medTokens = new JTextArea();
    private final JLabel    medStatus = new JLabel("PENDING");
    private final JTable    medSLR    = new JTable(),     medTrace   = new JTable();

    private final JTextArea highOutput= new JTextArea(),  highTokens= new JTextArea();
    private final JLabel    highStatus= new JLabel("PENDING");
    private final JTable    highSLR   = new JTable(),     highTrace  = new JTable();

    private final JTextArea validateOutput = new JTextArea();
    private final JLabel    validateStatus = new JLabel("PENDING");
    private final JLabel    globalStatus   = new JLabel("Sin análisis aún.");

    // ── CONSTRUCTOR ─────────────────────────────────────────
    public YAParGUI() {
        setTitle("YAPar — Analizador SLR(1)");
        setSize(1700, 980);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        initUI();
        loadLow(); loadMedium(); loadHigh();
        loadValidation();
    }

    private void initUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        setContentPane(root);
        root.add(buildHeader(),    BorderLayout.NORTH);
        root.add(buildMain(),      BorderLayout.CENTER);
        root.add(buildInfoStrip(), BorderLayout.SOUTH);
    }

    // ── HEADER ──────────────────────────────────────────────
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG2);
        header.setBorder(BorderFactory.createMatteBorder(0,0,1,0,BORDER));
        header.setPreferredSize(new Dimension(100,70));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left,BoxLayout.X_AXIS));
        left.setBorder(new EmptyBorder(12,20,12,20));

        JPanel icon = new JPanel(new GridBagLayout());
        icon.setBackground(ACCENT);
        icon.setPreferredSize(new Dimension(44,44));
        icon.setMaximumSize(new Dimension(44,44));
        icon.setMinimumSize(new Dimension(44,44));
        JLabel iconLabel = new JLabel("SLR");
        iconLabel.setForeground(Color.WHITE);
        iconLabel.setFont(monoBold(13));
        icon.add(iconLabel);

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel,BoxLayout.Y_AXIS));
        JLabel title = new JLabel("YAPar");
        title.setForeground(TEXT); title.setFont(new Font("SansSerif",Font.BOLD,26));
        JLabel subtitle = new JLabel("Motor SLR(1)  ·  Autómatas Finitos  ·  DLP 2026");
        subtitle.setForeground(TEXT3); subtitle.setFont(mono(12));
        textPanel.add(title); textPanel.add(subtitle);

        left.add(icon); left.add(Box.createHorizontalStrut(14)); left.add(textPanel);
        header.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new GridBagLayout());
        right.setOpaque(false); right.setBorder(new EmptyBorder(0,0,0,20));
        globalStatus.setForeground(TEXT2); globalStatus.setFont(mono(12));
        right.add(globalStatus);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    // ── MAIN ────────────────────────────────────────────────
    private JPanel buildMain() {
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(BG);
        main.add(buildSidebar(), BorderLayout.WEST);
        main.add(buildContent(), BorderLayout.CENTER);
        return main;
    }

    // ── SIDEBAR ─────────────────────────────────────────────
    private JPanel buildSidebar() {
        JPanel side = new JPanel();
        side.setBackground(BG2);
        side.setPreferredSize(new Dimension(270,100));
        side.setLayout(new BoxLayout(side,BoxLayout.Y_AXIS));
        side.setBorder(BorderFactory.createMatteBorder(0,0,0,1,BORDER));

        side.add(Box.createVerticalStrut(20));
        side.add(sideTitle("ARCHIVOS DE PRUEBA"));
        side.add(sideButton("● low.yal / low.yalp / low.txt",          YELLOW, ()-> { loadLow();    loadValidationFrom("LOW");    showTab("LOW");    }));
        side.add(sideButton("● medium.yal / medium.yalp / medium.txt", BLUE,   ()-> { loadMedium(); loadValidationFrom("MEDIUM"); showTab("MEDIUM"); }));
        side.add(sideButton("● high.yal / high.yalp / high.txt",       RED,    ()-> { loadHigh();   loadValidationFrom("HIGH");   showTab("HIGH");   }));

        side.add(Box.createVerticalStrut(24));
        side.add(sideTitle("ARCHIVOS CON ERROR"));
        side.add(sideButton("✕ low_error.txt",    RED, ()-> { lowTxt.setText("a + + b");        showTab("LOW");    }));
        side.add(sideButton("✕ medium_error.txt", RED, ()-> { medTxt.setText("if x { y = 3 ; }"); showTab("MEDIUM"); }));
        side.add(sideButton("✕ high_error.txt",   RED, ()-> { highTxt.setText("a + * b");       showTab("HIGH");   }));

        side.add(Box.createVerticalStrut(24));
        side.add(sideTitle("ARCHIVOS MODIFICADOS"));
        side.add(sideButton("◆ low (modificado)",    GREEN, ()-> { loadLowModified();    showTab("LOW");    }));
        side.add(sideButton("◆ medium (modificado)", GREEN, ()-> { loadMediumModified(); showTab("MEDIUM"); }));
        side.add(sideButton("◆ high (modificado)",   GREEN, ()-> { loadHighModified();   showTab("HIGH");   }));

        side.add(Box.createVerticalGlue());
        return side;
    }

    // ── CONTENT ─────────────────────────────────────────────
    private JPanel buildContent() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(BG);
        content.add(buildTabsBar(), BorderLayout.NORTH);
        tabsPanel.setBackground(BG);
        tabsPanel.add(buildValidationPanel(), "VALIDATE");
        tabsPanel.add(buildParserPanel("LOW",    lowYal,  lowYalp,  lowTxt,  "Complejidad Baja — Expresiones Aritméticas",  lowOutput,  lowTokens,  lowStatus,  lowSLR,  lowTrace),  "LOW");
        tabsPanel.add(buildParserPanel("MEDIUM", medYal,  medYalp,  medTxt,  "Complejidad Media — Sentencias if/while",     medOutput,  medTokens,  medStatus,  medSLR,  medTrace),  "MEDIUM");
        tabsPanel.add(buildParserPanel("HIGH",   highYal, highYalp, highTxt, "Complejidad Alta — Comparaciones",            highOutput, highTokens, highStatus, highSLR, highTrace), "HIGH");
        content.add(tabsPanel, BorderLayout.CENTER);
        return content;
    }

    private JPanel buildTabsBar() {
        JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEFT,8,8));
        tabs.setBackground(BG2);
        tabs.setBorder(BorderFactory.createMatteBorder(0,0,1,0,BORDER));
        tabs.add(tabButton("✓  Validación", GREEN,  "VALIDATE"));
        tabs.add(tabButton("●  Baja",       YELLOW, "LOW"));
        tabs.add(tabButton("●  Media",      BLUE,   "MEDIUM"));
        tabs.add(tabButton("●  Alta",       RED,    "HIGH"));
        return tabs;
    }

    // ── VALIDATION PANEL ────────────────────────────────────
    private JPanel buildValidationPanel() {
        JPanel root = basePanel();
        root.setLayout(new BorderLayout(15,15));

        JLabel heading = new JLabel("Validación Cruzada — .yal vs .yalp");
        heading.setForeground(TEXT3); heading.setFont(mono(12));
        heading.setBorder(new EmptyBorder(0,0,10,0));
        root.add(heading, BorderLayout.NORTH);

        configureEditor(validateYal); configureEditor(validateYalp);
        JPanel editors = new JPanel(new GridLayout(1,2,15,0));
        editors.setOpaque(false);
        editors.add(editorCard("Archivo .yal (YALex)",  validateYal,  YELLOW));
        editors.add(editorCard("Archivo .yalp (YAPar)", validateYalp, BLUE));

        configureEditor(validateOutput); validateOutput.setEditable(false);
        validateStatus.setOpaque(true); validateStatus.setForeground(Color.WHITE);
        validateStatus.setBackground(BG3); validateStatus.setFont(monoBold(12));
        validateStatus.setBorder(new EmptyBorder(6,14,6,14));

        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusRow.setOpaque(false);
        JButton run = actionButton("▶  Ejecutar Validación Cruzada", ACCENT);
        run.addActionListener(e -> runValidation());
        statusRow.add(run); statusRow.add(Box.createHorizontalStrut(12)); statusRow.add(validateStatus);

        JPanel outputPanel = resultBox("RESULTADO DE LA VALIDACIÓN", darkScroll(validateOutput));
        JPanel bottom = new JPanel(new BorderLayout(0,10));
        bottom.setOpaque(false);
        bottom.add(statusRow,   BorderLayout.NORTH);
        bottom.add(outputPanel, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editors, bottom);
        split.setResizeWeight(0.55); split.setBorder(null); split.setDividerSize(8);
        split.setBackground(BG); split.setOpaque(false);
        root.add(split, BorderLayout.CENTER);
        return root;
    }

    // ── PARSER PANEL ────────────────────────────────────────
    private JPanel buildParserPanel(
            String mode,
            JTextArea yal, JTextArea yalp, JTextArea txt, String title,
            JTextArea outputArea, JTextArea tokensArea, JLabel parseStatus,
            JTable slrTable, JTable traceTable) {

        JPanel root = basePanel();
        root.setLayout(new BorderLayout(0,15));

        JLabel label = new JLabel(title);
        label.setForeground(TEXT3); label.setFont(mono(12));
        label.setBorder(new EmptyBorder(0,0,4,0));
        root.add(label, BorderLayout.NORTH);

        configureEditor(yal); configureEditor(yalp); configureEditor(txt);
        JPanel editors = new JPanel(new GridLayout(1,3,12,0));
        editors.setOpaque(false);
        editors.add(editorCard(mode.toLowerCase()+".yal",  yal,  YELLOW));
        editors.add(editorCard(mode.toLowerCase()+".yalp", yalp, BLUE));
        editors.add(editorCard(mode.toLowerCase()+".txt",  txt,  GREEN));
        editors.setPreferredSize(new Dimension(0,220));

        parseStatus.setOpaque(true); parseStatus.setForeground(Color.WHITE);
        parseStatus.setBackground(BG3); parseStatus.setFont(monoBold(12));
        parseStatus.setBorder(new EmptyBorder(9,16,9,16));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));
        actions.setOpaque(false); actions.setBorder(new EmptyBorder(4,0,4,0));

        JButton parseBtn = actionButton("▶  Parsear", ACCENT);
        parseBtn.addActionListener(e -> runParser(yal,yalp,txt,outputArea,tokensArea,parseStatus,slrTable,traceTable));
        actions.add(parseBtn);

        JButton clearBtn = actionButton("✕  Limpiar", BG3);
        // FIX: border visible sobre fondo oscuro
        clearBtn.setBorder(new CompoundBorder(new LineBorder(BORDER,1), new EmptyBorder(9,17,9,17)));
        clearBtn.addActionListener(e -> {
            outputArea.setText(""); tokensArea.setText("");
            setStatus(parseStatus,"PENDING",BG3);
            slrTable.setModel(new DefaultTableModel());
            traceTable.setModel(new DefaultTableModel());
        });
        actions.add(Box.createHorizontalStrut(10));
        actions.add(clearBtn);
        actions.add(Box.createHorizontalStrut(14));
        actions.add(parseStatus);

        JPanel topSection = new JPanel(new BorderLayout(0,8));
        topSection.setOpaque(false);
        topSection.add(editors, BorderLayout.CENTER);
        topSection.add(actions, BorderLayout.SOUTH);

        JPanel results = buildResultsPanel(outputArea,tokensArea,slrTable,traceTable);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topSection, results);
        split.setResizeWeight(0.30); split.setBorder(null); split.setDividerSize(8); split.setBackground(BG);
        root.add(split, BorderLayout.CENTER);
        return root;
    }

    // ── RESULTS PANEL ───────────────────────────────────────
    private JPanel buildResultsPanel(JTextArea outputArea, JTextArea tokensArea, JTable slrTable, JTable traceTable) {
        configureEditor(tokensArea); tokensArea.setEditable(false);
        configureEditor(outputArea); outputArea.setEditable(false);
        styleTable(slrTable); styleTable(traceTable);

        JPanel topRow = new JPanel(new GridLayout(1,2,12,0));
        topRow.setOpaque(false);
        topRow.add(resultBox("TOKENS GENERADOS",  darkScroll(tokensArea)));
        topRow.add(resultBox("SALIDA DEL PARSER",  darkScroll(outputArea)));

        JPanel botRow = new JPanel(new GridLayout(1,2,12,0));
        botRow.setOpaque(false);
        botRow.add(resultBox("TABLA SLR(1)",     darkScroll(slrTable)));
        botRow.add(resultBox("TRAZA DEL PARSER", darkScroll(traceTable)));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topRow, botRow);
        split.setResizeWeight(0.35); split.setBorder(null); split.setDividerSize(8); split.setBackground(BG);
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false); wrapper.add(split, BorderLayout.CENTER);
        return wrapper;
    }

    // ── PARSER RUNNER ───────────────────────────────────────
    private void runParser(
            JTextArea yalArea, JTextArea yalpArea, JTextArea inputArea,
            JTextArea outputArea, JTextArea tokensArea, JLabel parseStatus,
            JTable slrTable, JTable traceTable) {

        outputArea.setText(""); tokensArea.setText("");
        slrTable.setModel(new DefaultTableModel());
        traceTable.setModel(new DefaultTableModel());

        try {
            String yal   = yalArea.getText().trim();
            String yalp  = yalpArea.getText().trim();
            String input = inputArea.getText().trim();

            if (yal.isEmpty() || yalp.isEmpty()) {
                setStatus(parseStatus,"ERROR",RED);
                outputArea.setText("⚠ Los archivos .yal y .yalp no pueden estar vacíos.");
                return;
            }

            YALexRunner lexer = YALexRunner.fromContent(yal);
            YAParFileParser.ParseResult pr = YAParFileParser.parseAndValidate(yalp, lexer);

            if (pr.hasErrors()) {
                setStatus(parseStatus,"REJECTED",RED);
                for (String err : pr.tokenValidationErrors) outputArea.append("⚠ "+err+"\n");
                setGlobal("✗ Error de validación", RED);
                return;
            }

            Grammar grammar = pr.grammar;
            LR0Automaton automaton = new LR0Automaton(grammar);
            Map<String,Set<String>> first  = grammar.computeFirst();
            Map<String,Set<String>> follow = grammar.computeFollow(first);
            SLRTable table = SLRTable.build(automaton, grammar, follow);

            List<Token> rawTokens = lexer.tokenize(input);
            List<Token> tokens = rawTokens.stream()
                    .filter(t -> !grammar.ignoreTokens.contains(t.type))
                    .collect(Collectors.toList());

            // FIX: usar t.value (campo correcto del Token)
            StringBuilder tokenSb = new StringBuilder();
            for (Token t : tokens) tokenSb.append(String.format("%-14s  %s%n", t.type, t.value));
            tokensArea.setText(tokenSb.toString());

            Parser parser = new Parser(table, grammar);
            parser.parse(tokens);

            if (parser.accepted) {
                setStatus(parseStatus,"✓ ACCEPTED",new Color(20,130,70));
                outputArea.append("✓  ACCEPTED\n\n");
                outputArea.append("El texto fue analizado correctamente.\n");
                outputArea.append("Tokens procesados: "+tokens.size()+"\n");
                if (!table.conflicts.isEmpty()) {
                    outputArea.append("\n⚠ Conflictos SLR detectados:\n");
                    for (String c : table.conflicts) outputArea.append("  "+c+"\n");
                }
                setGlobal("✓ Parse exitoso", GREEN);
            } else {
                setStatus(parseStatus,"✗ REJECTED",new Color(130,30,40));
                outputArea.append("✗  REJECTED\n\n");
                outputArea.append(parser.errorMessage+"\n");
                setGlobal("✗ Error sintáctico", RED);
            }

            buildSLRTable(slrTable, table, automaton, grammar);
            buildTraceTable(traceTable, parser.trace);

        } catch (Exception ex) {
            setStatus(parseStatus,"ERROR",RED);
            outputArea.setText("ERROR:\n\n"+ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void setStatus(JLabel label, String text, Color bg) { label.setText(text); label.setBackground(bg); }
    private void setGlobal(String text, Color c) { globalStatus.setText(text); globalStatus.setForeground(c); }

    // ── VALIDATION RUNNER ───────────────────────────────────
    private void runValidation() {
        validateOutput.setText("");
        try {
            String yal  = validateYal.getText().trim();
            String yalp = validateYalp.getText().trim();
            if (yal.isEmpty() || yalp.isEmpty()) {
                setStatus(validateStatus,"ERROR",RED);
                validateOutput.setText("⚠ Ambos archivos son requeridos.");
                return;
            }
            YALexRunner lexer = YALexRunner.fromContent(yal);
            YAParFileParser.ParseResult pr = YAParFileParser.parseAndValidate(yalp, lexer);
            if (pr.hasErrors()) {
                setStatus(validateStatus,"INVÁLIDO",RED);
                for (String err : pr.tokenValidationErrors) validateOutput.append("✗  "+err+"\n");
            } else {
                setStatus(validateStatus,"✓ VÁLIDO",new Color(20,130,70));
                validateOutput.append("✓  Validación cruzada exitosa.\n\n");
                validateOutput.append("Todos los tokens declarados en .yalp existen en .yal.\n");
                validateOutput.append("No hay inconsistencias detectadas.\n");
            }
        } catch (Exception ex) {
            setStatus(validateStatus,"ERROR",RED);
            validateOutput.setText("ERROR:\n\n"+ex.getMessage());
        }
    }

    // ── TABLE BUILDERS ──────────────────────────────────────
    private void buildSLRTable(JTable slrTable, SLRTable table, LR0Automaton automaton, Grammar grammar) {
        List<String> terms    = new ArrayList<>(grammar.terminals);
        List<String> nonTerms = new ArrayList<>(grammar.nonTerminals);
        terms.add("$");
        nonTerms.remove(Grammar.AUG_START);

        Vector<String> cols = new Vector<>();
        cols.add("Estado"); cols.addAll(terms); cols.add("|"); cols.addAll(nonTerms);

        Vector<Vector<Object>> data = new Vector<>();
        for (State s : automaton.states) {
            Vector<Object> row = new Vector<>();
            row.add(s.id);
            for (String t : terms)    { String v=table.getAction(s.id,t);  row.add(v==null?"":v); }
            row.add("|");
            for (String nt : nonTerms){ Integer g=table.getGoto(s.id,nt);  row.add(g==null?"":g); }
            data.add(row);
        }

        slrTable.setModel(new DefaultTableModel(data,cols){ @Override public boolean isCellEditable(int r,int c){return false;} });

        final Color BG_CELL=new Color(18,22,34), BG_SEL=new Color(50,70,120);
        slrTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer(){
            @Override public Component getTableCellRendererComponent(JTable t,Object val,boolean sel,boolean foc,int row,int col){
                super.getTableCellRendererComponent(t,val,sel,foc,row,col);
                setBackground(BG_CELL); setForeground(Color.WHITE);
                String s=val==null?"":val.toString();
                if(s.startsWith("s"))      setForeground(BLUE);
                else if(s.startsWith("r")) setForeground(GREEN);
                else if(s.equals("acc"))   setForeground(YELLOW);
                else if(s.equals("|"))     { setForeground(BORDER); setBackground(BG3); }
                if(col==0) { setForeground(TEXT2); setFont(monoBold(12)); }
                if(sel) setBackground(BG_SEL);
                return this;
            }
        });
        // auto-size columns
        for (int c=0; c<slrTable.getColumnCount(); c++) slrTable.getColumnModel().getColumn(c).setPreferredWidth(c==0?60:56);
    }

    private void buildTraceTable(JTable traceTable, List<String> trace) {
        boolean hasColumns = trace.stream().anyMatch(s -> s.contains("\t"));
        Vector<String> cols = new Vector<>();
        Vector<Vector<Object>> data = new Vector<>();
        if (hasColumns) {
            cols.add("#"); cols.add("PILA"); cols.add("ENTRADA"); cols.add("ACCIÓN"); cols.add("PRODUCCIÓN");
            int step=1;
            for (String s : trace) {
                Vector<Object> row = new Vector<>();
                row.add(step++);
                String[] parts = s.split("\t");
                row.add(parts.length>0?parts[0].trim():"");
                row.add(parts.length>1?parts[1].trim():"");
                row.add(parts.length>2?parts[2].trim():"");
                row.add(parts.length>3?parts[3].trim():"");
                data.add(row);
            }
        } else {
            cols.add("#"); cols.add("TRAZA");
            int step=1;
            for (String s : trace) { Vector<Object> row=new Vector<>(); row.add(step++); row.add(s); data.add(row); }
        }

        traceTable.setModel(new DefaultTableModel(data,cols){ @Override public boolean isCellEditable(int r,int c){return false;} });

        final Color R1=new Color(18,22,34), R2=new Color(22,27,40), SEL=new Color(50,70,120);
        traceTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer(){
            @Override public Component getTableCellRendererComponent(JTable t,Object val,boolean sel,boolean foc,int row,int col){
                super.getTableCellRendererComponent(t,val,sel,foc,row,col);
                setBackground(row%2==0?R1:R2); setForeground(TEXT);
                // color de acción
                if (col==3 && val!=null) {
                    String v=val.toString().trim();
                    if(v.startsWith("shift"))  setForeground(BLUE);
                    else if(v.startsWith("reduce")) setForeground(GREEN);
                    else if(v.startsWith("accept")) setForeground(YELLOW);
                    else if(v.startsWith("ERROR"))  setForeground(RED);
                }
                if(col==0) { setForeground(TEXT3); setFont(mono(11)); }
                if(sel) setBackground(SEL);
                return this;
            }
        });
        // column widths
        int[] widths = hasColumns ? new int[]{40,300,160,80,260} : new int[]{40,600};
        for(int c=0;c<Math.min(widths.length,traceTable.getColumnCount());c++)
            traceTable.getColumnModel().getColumn(c).setPreferredWidth(widths[c]);
    }

    // ── HELPERS ─────────────────────────────────────────────
    private void showTab(String tab) { tabsLayout.show(tabsPanel, tab); }

    private JPanel basePanel() {
        JPanel p=new JPanel(new BorderLayout());
        p.setBackground(BG); p.setBorder(new EmptyBorder(18,18,18,18)); return p;
    }

    private JPanel resultBox(String title, Component c) {
        JPanel panel=new JPanel(new BorderLayout());
        panel.setBackground(BG2);
        panel.setBorder(new CompoundBorder(new LineBorder(BORDER),new EmptyBorder(10,10,10,10)));
        JLabel label=new JLabel(title);
        label.setForeground(TEXT2); label.setFont(monoBold(12));
        label.setBorder(new EmptyBorder(0,0,10,0));
        panel.add(label,BorderLayout.NORTH); panel.add(c,BorderLayout.CENTER); return panel;
    }

    private JPanel editorCard(String title, JTextArea area, Color dot) {
        JPanel card=new JPanel(new BorderLayout());
        card.setBackground(BG2); card.setBorder(new LineBorder(BORDER));
        JPanel top=new JPanel(new BorderLayout());
        top.setBackground(BG3); top.setBorder(BorderFactory.createMatteBorder(0,0,1,0,BORDER));
        JLabel label=new JLabel("● "+title);
        label.setForeground(dot); label.setFont(monoBold(12)); label.setBorder(new EmptyBorder(9,14,9,14));
        top.add(label);
        card.add(top,BorderLayout.NORTH); card.add(darkScroll(area),BorderLayout.CENTER); return card;
    }

    private JButton actionButton(String text, Color bg) {
        JButton btn=new JButton(text);
        btn.setBackground(bg); btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false); btn.setBorderPainted(false);
        btn.setBorder(new EmptyBorder(10,18,10,18));
        btn.setFont(monoBold(12)); btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter(){
            @Override public void mouseEntered(MouseEvent e){ btn.setBackground(bg.brighter()); }
            @Override public void mouseExited(MouseEvent e) { btn.setBackground(bg); }
        });
        return btn;
    }

    private JButton tabButton(String text, Color accentColor, String tab) {
        JButton btn=new JButton(text);
        btn.setBackground(BG3); btn.setForeground(accentColor);
        btn.setFocusPainted(false);
        btn.setBorder(new CompoundBorder(new LineBorder(BORDER),new EmptyBorder(10,18,10,18)));
        btn.setFont(monoBold(13)); btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter(){
            @Override public void mouseEntered(MouseEvent e){ btn.setBackground(new Color(36,42,58)); }
            @Override public void mouseExited(MouseEvent e) { btn.setBackground(BG3); }
        });
        btn.addActionListener(e -> showTab(tab)); return btn;
    }

    private JPanel sideButton(String text, Color dot, Runnable action) {
        JPanel wrapper=new JPanel(new BorderLayout());
        wrapper.setOpaque(false); wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE,42));
        wrapper.setBorder(new EmptyBorder(2,8,2,8));
        JButton btn=new JButton(text);
        btn.setBackground(BG2); btn.setForeground(TEXT2);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setFocusPainted(false);
        btn.setBorder(new CompoundBorder(new LineBorder(BORDER,1,true),new EmptyBorder(6,12,6,12)));
        btn.setFont(mono(12)); btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter(){
            @Override public void mouseEntered(MouseEvent e){ btn.setBackground(BG3); btn.setForeground(dot); }
            @Override public void mouseExited(MouseEvent e) { btn.setBackground(BG2); btn.setForeground(TEXT2); }
        });
        btn.addActionListener(e -> action.run()); wrapper.add(btn); return wrapper;
    }

    private JPanel sideTitle(String text) {
        JPanel wrap=new JPanel(new BorderLayout());
        wrap.setOpaque(false); wrap.setBorder(new EmptyBorder(4,20,6,20));
        JLabel label=new JLabel(text);
        label.setForeground(TEXT3); label.setFont(monoBold(10));
        wrap.add(label); return wrap;
    }

    private JScrollPane darkScroll(Component c) {
        JScrollPane scroll=new JScrollPane(c);
        scroll.setBorder(null); scroll.getViewport().setBackground(BG2);
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        scroll.getHorizontalScrollBar().setUnitIncrement(14); return scroll;
    }

    private void configureEditor(JTextArea area) {
        area.setBackground(BG2); area.setForeground(TEXT); area.setCaretColor(TEXT);
        area.setBorder(new EmptyBorder(12,12,12,12)); area.setFont(mono(13));
        area.setLineWrap(false); area.setTabSize(4);
    }

    private void styleTable(JTable table) {
        table.setBackground(new Color(18,22,34)); table.setForeground(Color.WHITE);
        table.setGridColor(BORDER); table.setRowHeight(28); table.setFont(mono(12));
        table.setSelectionBackground(new Color(50,70,120)); table.setSelectionForeground(Color.WHITE);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        JTableHeader header=table.getTableHeader();
        header.setBackground(new Color(28,34,48)); header.setForeground(TEXT);
        header.setFont(monoBold(12)); header.setBorder(new LineBorder(BORDER));
        header.setReorderingAllowed(false);
    }

    private JPanel buildInfoStrip() {
        JPanel strip=new JPanel(new FlowLayout(FlowLayout.LEFT,25,8));
        strip.setBackground(BG2); strip.setBorder(BorderFactory.createMatteBorder(1,0,0,0,BORDER));
        strip.add(info("Motor:","SLR(1)")); strip.add(info("Lexer:","NFA → DFA"));
        strip.add(info("Sin Regex:","✓ Autómatas")); strip.add(info("Runtime:","✓ Java"));
        return strip;
    }

    private JPanel info(String label, String value) {
        JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,5,0)); p.setOpaque(false);
        JLabel l1=new JLabel(label); l1.setForeground(TEXT3); l1.setFont(mono(11));
        JLabel l2=new JLabel(value); l2.setForeground(TEXT);  l2.setFont(monoBold(11));
        p.add(l1); p.add(l2); return p;
    }

    private Font mono(int size)     { return new Font("JetBrains Mono", Font.PLAIN, size); }
    private Font monoBold(int size) { return new Font("JetBrains Mono", Font.BOLD,  size); }

    // ── PRESETS ─────────────────────────────────────────────
    private void loadLow() {
        lowYal.setText("""
let letter = ['a'-'z' 'A'-'Z']
let digit  = ['0'-'9']

rule tokens =
    letter (letter | digit)* { return ID }
  | '+' { return PLUS }
  | '*' { return TIMES }
  | '(' { return LPAREN }
  | ')' { return RPAREN }
  | [' ' '\\t' '\\n']+ { return WS }
""");
        lowYalp.setText("""
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
        lowTxt.setText("a + b * ( c + d )");
    }

    private void loadMedium() {
        medYal.setText("""
let letter = ['a'-'z' 'A'-'Z']
let digit  = ['0'-'9']

rule tokens =
    "if"                       { return IF }
  | "else"                     { return ELSE }
  | "while"                    { return WHILE }
  | letter (letter | digit)*  { return ID }
  | digit+                    { return NUM }
  | '='  { return ASSIGN }
  | ';'  { return SEMI }
  | '('  { return LPAREN }
  | ')'  { return RPAREN }
  | '{'  { return LBRACE }
  | '}'  { return RBRACE }
  | [' ' '\\t' '\\n']+ { return WS }
""");
        medYalp.setText("""
%token IF ELSE WHILE ASSIGN SEMI LPAREN RPAREN LBRACE RBRACE ID NUM
%token WS

IGNORE WS

%%

stmt:
      IF LPAREN expr RPAREN stmt
    | IF LPAREN expr RPAREN stmt ELSE stmt
    | WHILE LPAREN expr RPAREN stmt
    | ID ASSIGN expr SEMI
    | LBRACE stmtlist RBRACE
;

stmtlist:
      stmtlist stmt
    | stmt
;

expr:
      ID
    | NUM
;
""");
        medTxt.setText("if ( x ) { y = 3 ; }");
    }

    private void loadHigh() {
        highYal.setText("""
let letter = ['a'-'z' 'A'-'Z']
let digit  = ['0'-'9']

rule tokens =
    letter (letter | digit)*  { return ID }
  | digit+                    { return NUM }
  | '+'   { return PLUS }
  | '-'   { return MINUS }
  | '*'   { return TIMES }
  | '/'   { return DIV }
  | '('   { return LPAREN }
  | ')'   { return RPAREN }
  | "=="  { return EQ }
  | "!="  { return NEQ }
  | '<'   { return LT }
  | '>'   { return GT }
  | [' ' '\\t' '\\n']+ { return WS }
""");
        highYalp.setText("""
%token PLUS MINUS TIMES DIV LPAREN RPAREN EQ NEQ LT GT ID NUM
%token WS

IGNORE WS

%%

cmpexpr:
      expr EQ  expr
    | expr NEQ expr
    | expr LT  expr
    | expr GT  expr
    | expr
;

expr:
      expr PLUS  term
    | expr MINUS term
    | term
;

term:
      term TIMES factor
    | term DIV   factor
    | factor
;

factor:
      LPAREN expr RPAREN
    | ID
    | NUM
;
""");
        highTxt.setText("a + b * 2 - c / d");
    }

    private void loadLowModified()    { lowTxt.setText("a + b * ( c + d ) + a * b"); }
    private void loadMediumModified() { medTxt.setText("while ( x ) { y = 3 ; }"); }
    private void loadHighModified()   { highTxt.setText("a + b * 2 - c / d"); }

    private void loadValidation() { loadValidationFrom("LOW"); }

    private void loadValidationFrom(String level) {
        switch (level) {
            case "MEDIUM" -> {
                validateYal.setText(medYal.getText());
                validateYalp.setText(medYalp.getText());
            }
            case "HIGH" -> {
                validateYal.setText(highYal.getText());
                validateYalp.setText(highYalp.getText());
            }
            default -> {   // LOW
                validateYal.setText(lowYal.getText());
                validateYalp.setText(lowYalp.getText());
            }
        }
        validateOutput.setText("");
        setStatus(validateStatus, "PENDING", BG3);
    }
}