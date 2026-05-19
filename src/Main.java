import java.util.*;
import java.util.stream.Collectors;
import lexer.YALexRunner;
import parser.*;
import runtime.Token;

/**
 * Main — Punto de entrada de YAPar.
 */
public class Main {

    public static void main(String[] args) throws Exception {

        System.out.println("=== YAPar — Motor SLR(1) con DFA desde autómatas finitos ===\n");

        // ── Casos de prueba ──────────────────────────────────────────────
        testCase("LOW",      LOW_YAL,   LOW_YALP,   "a + b * ( c + d )",  true);
        testCase("LOW-ERR",  LOW_YAL,   LOW_YALP,   "a + + b",            false);

        testCase("MEDIUM",   MED_YAL,   MED_YALP,   "if ( x ) { y = 3 ; }", true);

        testCase("HIGH",     HIGH_YAL,  HIGH_YALP,  "a + b * 2 - c / d",  true);
        testCase("HIGH-ERR", HIGH_YAL,  HIGH_YALP,  "a + * b",            false);

        // ── Validación cruzada ──────────────────────────────────────────
        System.out.println("\n=== Prueba de validación cruzada ===");

        String badYalp = """
            %token PLUS UNKNOWN_TOKEN
            %%
            expr:
                PLUS UNKNOWN_TOKEN
            ;
            """;

        YALexRunner badLexer = YALexRunner.fromContent(LOW_YAL);

        YAParFileParser.ParseResult result =
                YAParFileParser.parseAndValidate(badYalp, badLexer);

        if (result.hasErrors()) {
            System.out.println("✓ Validación correcta — errores detectados:");
            result.tokenValidationErrors.forEach(
                    e -> System.out.println("  " + e)
            );
        }
    }

    private static void testCase(
            String name,
            String yal,
            String yalp,
            String input,
            boolean expectOk
    ) throws Exception {

        System.out.println("── Caso: " + name + " ──────────────────────────────");

        // 1. Lexer
        YALexRunner lexer = YALexRunner.fromContent(yal);

        // 2. Parser grammar
        YAParFileParser.ParseResult pr =
                YAParFileParser.parseAndValidate(yalp, lexer);

        if (pr.hasErrors()) {
            System.out.println("  ERRORES DE VALIDACIÓN:");

            pr.tokenValidationErrors.forEach(
                    e -> System.out.println("  " + e)
            );

            return;
        }

        Grammar g = pr.grammar;

        // 3. LR0 automaton
        LR0Automaton automaton = new LR0Automaton(g);

        // 4. FIRST / FOLLOW
        Map<String, Set<String>> first =
                g.computeFirst();

        Map<String, Set<String>> follow =
                g.computeFollow(first);

        // 5. SLR Table
        SLRTable table =
                SLRTable.build(automaton, g, follow);

        if (!table.conflicts.isEmpty()) {
            System.out.println("  ⚠ Conflictos: " + table.conflicts);
        }

        // 6. Tokenizar
        List<Token> rawTokens = lexer.tokenize(input);

        // FILTRAR TOKENS IGNORADOS (WS)
        List<Token> tokens = rawTokens.stream()
                .filter(t -> !g.ignoreTokens.contains(t.type))
                .collect(Collectors.toList());

        System.out.println("  Tokens: " + tokens);

        // 7. Parsear
        Parser parser = new Parser(table, g);

        parser.parse(tokens);

        if (parser.accepted) {
            System.out.println("  ✓ ACCEPTED — input válido");
        } else {
            System.out.println("  ✗ REJECTED — " + parser.errorMessage);
        }

        boolean ok = parser.accepted == expectOk;

        System.out.println(
                "  Resultado: " +
                        (ok ? "✓ CORRECTO" : "✗ INCORRECTO")
        );

        System.out.println();
    }

    // ─────────────────────────────────────────────────────────────────────
    // LOW
    // ─────────────────────────────────────────────────────────────────────

    static final String LOW_YAL = """
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
        """;

    static final String LOW_YALP = """
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
        """;

    // ─────────────────────────────────────────────────────────────────────
    // MEDIUM
    // ─────────────────────────────────────────────────────────────────────

    static final String MED_YAL = """
        (* Lexer complejidad media *)

        let letter = ['a'-'z' 'A'-'Z']
        let digit  = ['0'-'9']

        rule tokens =
            "if"                        { return IF }
          | "else"                      { return ELSE }
          | "while"                     { return WHILE }
          | letter (letter | digit)*    { return ID }
          | digit+                      { return NUM }
          | '='                         { return ASSIGN }
          | ';'                         { return SEMI }
          | '('                         { return LPAREN }
          | ')'                         { return RPAREN }
          | '{'                         { return LBRACE }
          | '}'                         { return RBRACE }
          | [' ' '\\t' '\\n']+          { return WS }
        """;

    static final String MED_YALP = """
        %token IF ELSE WHILE ASSIGN SEMI
        %token LPAREN RPAREN LBRACE RBRACE
        %token ID NUM
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
        """;

    // ─────────────────────────────────────────────────────────────────────
    // HIGH
    // ─────────────────────────────────────────────────────────────────────

    static final String HIGH_YAL = """
        (* Lexer alta complejidad *)

        let letter = ['a'-'z' 'A'-'Z']
        let digit  = ['0'-'9']

        rule tokens =
            letter (letter | digit)*   { return ID }
          | digit+                     { return NUM }
          | '+'                        { return PLUS }
          | '-'                        { return MINUS }
          | '*'                        { return TIMES }
          | '/'                        { return DIV }
          | '('                        { return LPAREN }
          | ')'                        { return RPAREN }
          | "=="                       { return EQ }
          | "!="                       { return NEQ }
          | '<'                        { return LT }
          | '>'                        { return GT }
          | [' ' '\\t' '\\n']+         { return WS }
        """;

    static final String HIGH_YALP = """
        %token PLUS MINUS TIMES DIV
        %token LPAREN RPAREN
        %token ID NUM
        %token EQ NEQ LT GT
        %token WS

        IGNORE WS

        %%

        cmpexpr:
            expr EQ expr
          | expr NEQ expr
          | expr LT expr
          | expr GT expr
          | expr
        ;

        expr:
            expr PLUS term
          | expr MINUS term
          | term
        ;

        term:
            term TIMES factor
          | term DIV factor
          | factor
        ;

        factor:
            LPAREN expr RPAREN
          | ID
          | NUM
        ;
        """;
}