package lexer;

import java.util.*;

/**
 * RegexToNFA
 *
 * Conversión regex → NFA usando Thompson
 * SIN java.util.regex
 */
public class RegexToNFA {

    private final String src;

    private int pos;

    public RegexToNFA(String src) {

        this.src = src;

        this.pos = 0;
    }

    /**
     * Punto de entrada
     */
    public static NFAFragment build(String regex) {

        RegexToNFA parser =
                new RegexToNFA(regex.trim());

        return parser.parseAlt();
    }

    // ─────────────────────────────────────────────────────────────────────
    // alt → concat ('|' concat)*
    // ─────────────────────────────────────────────────────────────────────

    private NFAFragment parseAlt() {

        NFAFragment left =
                parseConcat();

        while (
                pos < src.length()
                        && src.charAt(pos) == '|'
        ) {

            pos++;

            NFAFragment right =
                    parseConcat();

            left =
                    union(left, right);
        }

        return left;
    }

    // ─────────────────────────────────────────────────────────────────────
    // concat → quant quant*
    // ─────────────────────────────────────────────────────────────────────

    private NFAFragment parseConcat() {

        skipSpaces();

        NFAFragment result =
                parseQuant();

        skipSpaces();

        while (
                pos < src.length()
                        && !isAltOrClose()
        ) {

            result =
                    concat(
                            result,
                            parseQuant()
                    );

            skipSpaces();
        }

        return result;
    }

    private boolean isAltOrClose() {

        char c =
                src.charAt(pos);

        return c == '|'
                || c == ')';
    }

    private void skipSpaces() {

        while (
                pos < src.length()
                        && src.charAt(pos) == ' '
        ) {
            pos++;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // quant → atom (* + ?)*
    // ─────────────────────────────────────────────────────────────────────

    private NFAFragment parseQuant() {

        NFAFragment f =
                parseAtom();

        while (pos < src.length()) {

            char c =
                    src.charAt(pos);

            if (c == '*') {

                pos++;

                f = star(f);
            }

            else if (c == '+') {

                pos++;

                f = plus(f);
            }

            else if (c == '?') {

                pos++;

                f = question(f);
            }

            else {
                break;
            }
        }

        return f;
    }

    // ─────────────────────────────────────────────────────────────────────
    // atom
    // ─────────────────────────────────────────────────────────────────────

    private NFAFragment parseAtom() {

        if (pos >= src.length()) {
            return epsilon();
        }

        char c = src.charAt(pos);

        // ─────────────────────────────────────────
        // GRUPO (...)
        // ─────────────────────────────────────────

        if (c == '(') {

            pos++;

            NFAFragment inside =
                    parseAlt();

            if (
                    pos < src.length()
                            && src.charAt(pos) == ')'
            ) {
                pos++;
            }

            return inside;
        }

        // ─────────────────────────────────────────
        // CHAR CLASS [...]
        // ─────────────────────────────────────────

        if (c == '[') {
            return parseCharClass();
        }

        // ─────────────────────────────────────────
        // CHAR LITERAL
        // ─────────────────────────────────────────

        if (c == '\'') {

            char literal =
                    parseCharLiteral();

            return literal(literal);
        }

        // ─────────────────────────────────────────
        // STRING LITERAL
        // ─────────────────────────────────────────

        if (c == '"') {
            return parseStringLiteral();
        }

        // ─────────────────────────────────────────
        // ANY CHAR
        // ─────────────────────────────────────────

        if (c == '.') {

            pos++;

            return anyChar();
        }

        // ─────────────────────────────────────────
        // ESCAPED CHAR
        // ─────────────────────────────────────────

        if (c == '\\') {

            pos++;

            if (pos >= src.length()) {
                return literal('\\');
            }

            char next =
                    src.charAt(pos++);

            switch (next) {

                case 'n':
                    return literal('\n');

                case 't':
                    return literal('\t');

                case 'r':
                    return literal('\r');

                case '\\':
                    return literal('\\');

                case '\'':
                    return literal('\'');

                case '"':
                    return literal('"');

                case '{':
                    return literal('{');

                case '}':
                    return literal('}');

                case '(':
                    return literal('(');

                case ')':
                    return literal(')');

                case '+':
                    return literal('+');

                case '*':
                    return literal('*');

                case '?':
                    return literal('?');

                case '|':
                    return literal('|');

                case '/':
                    return literal('/');

                case '-':
                    return literal('-');

                default:
                    return literal(next);
            }
        }

        // ─────────────────────────────────────────
        // LITERAL NORMAL
        // ─────────────────────────────────────────

        if ("|)*+?".indexOf(c) < 0) {

            pos++;

            return literal(c);
        }

        return epsilon();
    }

    // ─────────────────────────────────────────────────────────────────────
    // CHAR CLASS [...]
    // ─────────────────────────────────────────────────────────────────────

    private NFAFragment parseCharClass() {

        pos++;

        boolean negated = false;

        if (
                pos < src.length()
                        && src.charAt(pos) == '^'
        ) {

            negated = true;

            pos++;
        }

        Set<Character> chars =
                new LinkedHashSet<>();

        while (
                pos < src.length()
                        && src.charAt(pos) != ']'
        ) {

            if (src.charAt(pos) == ' ') {

                pos++;

                continue;
            }

            // 'x'
            if (src.charAt(pos) == '\'') {

                char c1 =
                        parseCharLiteral();

                int save =
                        pos;

                skipSpaces();

                // rango
                if (
                        pos < src.length()
                                && src.charAt(pos) == '-'
                ) {

                    pos++;

                    skipSpaces();

                    if (
                            pos < src.length()
                                    && src.charAt(pos) == '\''
                    ) {

                        char c2 =
                                parseCharLiteral();

                        for (
                                char r = c1;
                                r <= c2;
                                r++
                        ) {
                            chars.add(r);
                        }

                        continue;
                    }

                    else {
                        pos = save;
                    }
                }

                chars.add(c1);

                continue;
            }

            char ch =
                    src.charAt(pos++);

            if (
                    ch == '\\'
                            && pos < src.length()
            ) {

                ch =
                        unescape(src.charAt(pos++));
            }

            // rango normal
            if (
                    pos < src.length()
                            && src.charAt(pos) == '-'
                            && pos + 1 < src.length()
                            && src.charAt(pos + 1) != ']'
            ) {

                pos++;

                char c2 =
                        src.charAt(pos++);

                if (
                        c2 == '\\'
                                && pos < src.length()
                ) {

                    c2 =
                            unescape(src.charAt(pos++));
                }

                for (
                        char r = ch;
                        r <= c2;
                        r++
                ) {

                    chars.add(r);
                }
            }

            else {

                chars.add(ch);
            }
        }

        if (
                pos < src.length()
                        && src.charAt(pos) == ']'
        ) {
            pos++;
        }

        if (negated) {
            return negatedCharSet(chars);
        }

        return charSet(chars);
    }

    // ─────────────────────────────────────────────────────────────────────
    // CHAR LITERAL
    // ─────────────────────────────────────────────────────────────────────

    private char parseCharLiteral() {

        pos++;

        char c;

        if (
                pos < src.length()
                        && src.charAt(pos) == '\\'
        ) {

            pos++;

            if (pos < src.length()) {

                c =
                        unescape(src.charAt(pos++));

            } else {

                c = '\\';
            }
        }

        else {

            if (pos < src.length()) {

                c = src.charAt(pos++);

            } else {

                c = 0;
            }
        }

        if (
                pos < src.length()
                        && src.charAt(pos) == '\''
        ) {
            pos++;
        }

        return c;
    }

    // ─────────────────────────────────────────────────────────────────────
    // STRING LITERAL
    // ─────────────────────────────────────────────────────────────────────

    private NFAFragment parseStringLiteral() {

        pos++;

        List<Character> chars =
                new ArrayList<>();

        while (
                pos < src.length()
                        && src.charAt(pos) != '"'
        ) {

            char ch =
                    src.charAt(pos++);

            if (
                    ch == '\\'
                            && pos < src.length()
            ) {

                ch =
                        unescape(src.charAt(pos++));
            }

            chars.add(ch);
        }

        if (
                pos < src.length()
                        && src.charAt(pos) == '"'
        ) {
            pos++;
        }

        if (chars.isEmpty()) {
            return epsilon();
        }

        NFAFragment f =
                literal(chars.get(0));

        for (int i = 1; i < chars.size(); i++) {

            f =
                    concat(
                            f,
                            literal(chars.get(i))
                    );
        }

        return f;
    }

    // ─────────────────────────────────────────────────────────────────────
    // THOMPSON
    // ─────────────────────────────────────────────────────────────────────

    private NFAFragment literal(char c) {

        NFANode s =
                new NFANode();

        NFANode e =
                new NFANode();

        s.addTransition(c, e);

        return new NFAFragment(s, e);
    }

    private NFAFragment epsilon() {

        NFANode s =
                new NFANode();

        NFANode e =
                new NFANode();

        s.addEpsilon(e);

        return new NFAFragment(s, e);
    }

    private NFAFragment concat(
            NFAFragment a,
            NFAFragment b
    ) {

        a.end.addEpsilon(b.start);

        return new NFAFragment(
                a.start,
                b.end
        );
    }

    private NFAFragment union(
            NFAFragment a,
            NFAFragment b
    ) {

        NFANode s =
                new NFANode();

        NFANode e =
                new NFANode();

        s.addEpsilon(a.start);
        s.addEpsilon(b.start);

        a.end.addEpsilon(e);
        b.end.addEpsilon(e);

        return new NFAFragment(s, e);
    }

    private NFAFragment star(
            NFAFragment f
    ) {

        NFANode s =
                new NFANode();

        NFANode e =
                new NFANode();

        s.addEpsilon(f.start);
        s.addEpsilon(e);

        f.end.addEpsilon(f.start);
        f.end.addEpsilon(e);

        return new NFAFragment(s, e);
    }

    private NFAFragment plus(
            NFAFragment f
    ) {

        NFANode s =
                new NFANode();

        NFANode e =
                new NFANode();

        s.addEpsilon(f.start);

        f.end.addEpsilon(f.start);
        f.end.addEpsilon(e);

        return new NFAFragment(s, e);
    }

    private NFAFragment question(
            NFAFragment f
    ) {

        NFANode s =
                new NFANode();

        NFANode e =
                new NFANode();

        s.addEpsilon(f.start);
        s.addEpsilon(e);

        f.end.addEpsilon(e);

        return new NFAFragment(s, e);
    }

    private NFAFragment charSet(
            Set<Character> chars
    ) {

        NFANode s =
                new NFANode();

        NFANode e =
                new NFANode();

        for (char c : chars) {
            s.addTransition(c, e);
        }

        return new NFAFragment(s, e);
    }

    private NFAFragment negatedCharSet(
            Set<Character> excluded
    ) {

        NFANode s =
                new NFANode();

        NFANode e =
                new NFANode();

        for (int i = 0; i < 128; i++) {

            char c = (char) i;

            if (!excluded.contains(c)) {

                s.addTransition(c, e);
            }
        }

        return new NFAFragment(s, e);
    }

    private NFAFragment anyChar() {

        NFANode s =
                new NFANode();

        NFANode e =
                new NFANode();

        for (int i = 0; i < 128; i++) {

            char c = (char) i;

            if (c != '\n') {

                s.addTransition(c, e);
            }
        }

        return new NFAFragment(s, e);
    }

    // ─────────────────────────────────────────────────────────────────────
    // ESCAPES
    // ─────────────────────────────────────────────────────────────────────

    private static char unescape(char c) {

        switch (c) {

            case 'n':
                return '\n';

            case 't':
                return '\t';

            case 'r':
                return '\r';

            case '0':
                return '\0';

            case '\\':
                return '\\';

            case '\'':
                return '\'';

            case '"':
                return '"';

            default:
                return c;
        }
    }
}