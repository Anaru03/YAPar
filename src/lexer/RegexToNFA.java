package lexer;

import java.util.*;

/**
 * RegexToNFA — Convierte una expresión regular (formato YALex/OCaml) a un NFA
 * usando la construcción de Thompson. SIN java.util.regex.
 *
 * Operadores soportados:
 *   [a-z]  ['a'-'z']    clases de caracteres
 *   'x'                 literal de carácter
 *   "abc"               literal de cadena
 *   .                   cualquier carácter (excepto \n)
 *   e*  e+  e?          cuantificadores
 *   e1 e2               concatenación
 *   e1 | e2             alternancia
 *   (e)                 agrupación
 *   \n \t \r \\         escapes
 */
public class RegexToNFA {

    private final String src;
    private int pos;

    public RegexToNFA(String src) {
        this.src = src;
        this.pos = 0;
    }

    /** Punto de entrada: convierte la expresión completa a un fragmento NFA. */
    public static NFAFragment build(String regex) {
        return new RegexToNFA(regex.trim()).parseAlt();
    }

    // ── Gramática recursiva descendente ──────────────────────────────────
    // alt   → concat ('|' concat)*
    // concat→ quant quant*
    // quant → atom ('*' | '+' | '?')*
    // atom  → '(' alt ')' | '[' class ']' | '\'' char '\'' | '"' str '"' | '.' | char

    private NFAFragment parseAlt() {
        NFAFragment left = parseConcat();
        while (pos < src.length() && src.charAt(pos) == '|') {
            pos++;
            NFAFragment right = parseConcat();
            left = union(left, right);
        }
        return left;
    }

    private NFAFragment parseConcat() {
        skipSpaces();
        NFAFragment result = parseQuant();
        skipSpaces();
        while (pos < src.length() && !isAltOrClose()) {
            result = concat(result, parseQuant());
            skipSpaces();
        }
        return result;
    }

    /** Salta espacios que no son parte de un literal ni clase de caracteres */
    private void skipSpaces() {
        while (pos < src.length() && src.charAt(pos) == ' ') pos++;
    }

    private boolean isAltOrClose() {
        char c = src.charAt(pos);
        return c == '|' || c == ')';
    }

    private NFAFragment parseQuant() {
        NFAFragment f = parseAtom();
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '*') { pos++; f = star(f); }
            else if (c == '+') { pos++; f = plus(f); }
            else if (c == '?') { pos++; f = question(f); }
            else break;
        }
        return f;
    }

    private NFAFragment parseAtom() {
        if (pos >= src.length()) return epsilon();
        char c = src.charAt(pos);

        if (c == '(') {
            pos++;
            NFAFragment f = parseAlt();
            if (pos < src.length() && src.charAt(pos) == ')') pos++;
            return f;
        }

        if (c == '[') {
            return parseCharClass();
        }

        if (c == '\'') {
            return literal(parseCharLiteral());
        }

        if (c == '"') {
            return parseStringLiteral();
        }

        if (c == '.') {
            pos++;
            return anyChar();
        }

        if (c == '\\') {
            pos++;
            char esc = pos < src.length() ? src.charAt(pos++) : '\\';
            return literal(unescape(esc));
        }

        // Carácter normal (pero no operadores)
        if ("*+?|)".indexOf(c) < 0) {
            pos++;
            return literal(c);
        }

        // Si llegamos aquí es un operador suelto, epsilon
        return epsilon();
    }

    // ── Clase de caracteres [...]  ─────────────────────────────────────────
    private NFAFragment parseCharClass() {
        pos++; // saltar '['
        boolean negated = false;
        if (pos < src.length() && src.charAt(pos) == '^') { negated = true; pos++; }

        Set<Character> chars = new LinkedHashSet<>();

        while (pos < src.length() && src.charAt(pos) != ']') {
            // Saltar espacios separadores
            if (src.charAt(pos) == ' ') { pos++; continue; }

            if (src.charAt(pos) == '\'') {
                char c1 = parseCharLiteral();
                // ¿Rango 'a'-'z'?
                int save = pos;
                // Saltar espacios
                while (pos < src.length() && src.charAt(pos) == ' ') pos++;
                if (pos < src.length() && src.charAt(pos) == '-') {
                    pos++;
                    while (pos < src.length() && src.charAt(pos) == ' ') pos++;
                    if (pos < src.length() && src.charAt(pos) == '\'') {
                        char c2 = parseCharLiteral();
                        for (char r = c1; r <= c2; r++) chars.add(r);
                        continue;
                    } else {
                        pos = save; // no era rango
                    }
                } else {
                    pos = save;
                }
                chars.add(c1);
                continue;
            }

            // carácter Java normal dentro de []
            char ch = src.charAt(pos++);
            if (ch == '\\' && pos < src.length()) ch = unescape(src.charAt(pos++));
            // rango ch-ch2
            if (pos < src.length() && src.charAt(pos) == '-' && pos + 1 < src.length() && src.charAt(pos+1) != ']') {
                pos++;
                char c2 = src.charAt(pos++);
                if (c2 == '\\' && pos < src.length()) c2 = unescape(src.charAt(pos++));
                for (char r = ch; r <= c2; r++) chars.add(r);
            } else {
                chars.add(ch);
            }
        }
        if (pos < src.length()) pos++; // saltar ']'

        if (negated) {
            return negatedCharSet(chars);
        }
        return charSet(chars);
    }

    // ── Literal de carácter YALex: 'x' o '\n' ────────────────────────────
    private char parseCharLiteral() {
        pos++; // saltar '
        char c;
        if (pos < src.length() && src.charAt(pos) == '\\') {
            pos++;
            c = pos < src.length() ? unescape(src.charAt(pos++)) : '\\';
        } else {
            c = pos < src.length() ? src.charAt(pos++) : 0;
        }
        if (pos < src.length() && src.charAt(pos) == '\'') pos++;
        return c;
    }

    // ── Literal de cadena: "abc" ──────────────────────────────────────────
    private NFAFragment parseStringLiteral() {
        pos++; // saltar "
        List<Character> chars = new ArrayList<>();
        while (pos < src.length() && src.charAt(pos) != '"') {
            char ch = src.charAt(pos++);
            if (ch == '\\' && pos < src.length()) ch = unescape(src.charAt(pos++));
            chars.add(ch);
        }
        if (pos < src.length()) pos++; // saltar "
        if (chars.isEmpty()) return epsilon();
        NFAFragment f = literal(chars.get(0));
        for (int i = 1; i < chars.size(); i++) f = concat(f, literal(chars.get(i)));
        return f;
    }

    // ── Operaciones NFA de Thompson ───────────────────────────────────────

    private NFAFragment literal(char c) {
        NFANode s = new NFANode();
        NFANode e = new NFANode();
        s.addTransition(c, e);
        return new NFAFragment(s, e);
    }

    private NFAFragment charSet(Set<Character> chars) {
        NFANode s = new NFANode();
        NFANode e = new NFANode();
        for (char c : chars) s.addTransition(c, e);
        return new NFAFragment(s, e);
    }

    /** Cualquier char de 0..127 excepto los excluidos */
    private NFAFragment negatedCharSet(Set<Character> excluded) {
        NFANode s = new NFANode();
        NFANode e = new NFANode();
        for (int i = 0; i < 128; i++) {
            char c = (char) i;
            if (!excluded.contains(c)) s.addTransition(c, e);
        }
        return new NFAFragment(s, e);
    }

    /** Cualquier carácter imprimible (excepto \n) */
    private NFAFragment anyChar() {
        NFANode s = new NFANode();
        NFANode e = new NFANode();
        for (int i = 0; i < 128; i++) {
            if ((char) i != '\n') s.addTransition((char) i, e);
        }
        return new NFAFragment(s, e);
    }

    private NFAFragment epsilon() {
        NFANode s = new NFANode();
        NFANode e = new NFANode();
        s.addEpsilon(e);
        return new NFAFragment(s, e);
    }

    private NFAFragment concat(NFAFragment a, NFAFragment b) {
        a.end.addEpsilon(b.start);
        return new NFAFragment(a.start, b.end);
    }

    private NFAFragment union(NFAFragment a, NFAFragment b) {
        NFANode s = new NFANode();
        NFANode e = new NFANode();
        s.addEpsilon(a.start);
        s.addEpsilon(b.start);
        a.end.addEpsilon(e);
        b.end.addEpsilon(e);
        return new NFAFragment(s, e);
    }

    private NFAFragment star(NFAFragment f) {
        NFANode s = new NFANode();
        NFANode e = new NFANode();
        s.addEpsilon(f.start);
        s.addEpsilon(e);
        f.end.addEpsilon(f.start);
        f.end.addEpsilon(e);
        return new NFAFragment(s, e);
    }

    private NFAFragment plus(NFAFragment f) {
        NFANode s = new NFANode();
        NFANode e = new NFANode();
        s.addEpsilon(f.start);
        f.end.addEpsilon(f.start);
        f.end.addEpsilon(e);
        return new NFAFragment(s, e);
    }

    private NFAFragment question(NFAFragment f) {
        NFANode s = new NFANode();
        NFANode e = new NFANode();
        s.addEpsilon(f.start);
        s.addEpsilon(e);
        f.end.addEpsilon(e);
        return new NFAFragment(s, e);
    }

    private static char unescape(char c) {
        return switch (c) {
            case 'n'  -> '\n';
            case 't'  -> '\t';
            case 'r'  -> '\r';
            case '0'  -> '\0';
            case '\\' -> '\\';
            case '\'' -> '\'';
            case '"'  -> '"';
            default   -> c;
        };
    }
}
