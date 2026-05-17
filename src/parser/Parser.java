package parser;

import runtime.Token;
import java.util.*;

public class Parser {
    private SLRTable table;
    private Grammar grammar;
    public List<String> trace = new ArrayList<>();
    public boolean accepted = false;
    public String errorMessage = null;

    public Parser(SLRTable table, Grammar grammar) {
        this.table = table;
        this.grammar = grammar;
    }

    public void parse(List<Token> tokens) {
        Stack<Integer> stateStack = new Stack<>();
        Stack<String> symbolStack = new Stack<>();
        stateStack.push(0);

        int index = 0;
        trace.clear();
        accepted = false;
        errorMessage = null;

        String header = String.format("%-40s %-20s %-12s %s",
                "Stack", "Input", "Action", "Production");
        trace.add(header);
        trace.add("-".repeat(90));

        while (true) {
            int state = stateStack.peek();
            Token token = index < tokens.size() ? tokens.get(index) : new Token("$", "$");

            String tokenType = token.type;
            // Skip ignored tokens
            if (grammar.ignoreTokens.contains(tokenType)) {
                index++;
                continue;
            }

            String action = table.getAction(state, tokenType);

            // Build display strings
            String stackStr = symbolStack.isEmpty() ? "[0]" : symbolStack.toString() + " [" + state + "]";
            String inputStr = buildInputStr(tokens, index);

            if (action == null) {
                String errLine = String.format("%-40s %-20s %-12s %s",
                        truncate(stackStr, 40), truncate(inputStr, 20), "ERROR", "");
                trace.add(errLine);
                errorMessage = "Syntax error at token " + token + (token.line > 0 ? " (line " + token.line + ")" : "");
                return;
            }

            if (action.startsWith("s")) {
                int nextState = Integer.parseInt(action.substring(1));
                String line = String.format("%-40s %-20s %-12s %s",
                        truncate(stackStr, 40), truncate(inputStr, 20), "shift", "");
                trace.add(line);
                symbolStack.push(tokenType);
                stateStack.push(nextState);
                index++;

            } else if (action.startsWith("r")) {
                int prodIdx = Integer.parseInt(action.substring(1));
                Production p = grammar.productions.get(prodIdx);
                String line = String.format("%-40s %-20s %-12s %s",
                        truncate(stackStr, 40), truncate(inputStr, 20), "reduce", p.toString());
                trace.add(line);

                for (int i = 0; i < p.right.size(); i++) {
                    stateStack.pop();
                    symbolStack.pop();
                }

                int top = stateStack.peek();
                Integer gotoState = table.getGoto(top, p.left);
                if (gotoState == null) {
                    errorMessage = "GOTO missing for state " + top + " on " + p.left;
                    return;
                }
                symbolStack.push(p.left);
                stateStack.push(gotoState);

            } else if (action.equals("acc")) {
                String line = String.format("%-40s %-20s %-12s %s",
                        truncate(stackStr, 40), truncate(inputStr, 20), "accept", "");
                trace.add(line);
                accepted = true;
                return;
            }
        }
    }

    private String buildInputStr(List<Token> tokens, int index) {
        StringBuilder sb = new StringBuilder();
        for (int i = index; i < Math.min(index + 4, tokens.size()); i++) {
            sb.append(tokens.get(i).type).append(" ");
        }
        if (index >= tokens.size()) sb.append("$");
        return sb.toString().trim();
    }

    private String truncate(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max - 3) + "...";
    }
}
