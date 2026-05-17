package runtime;

public class Token {
    public String type;
    public String lexeme;
    public int line;

    public Token(String type, String lexeme, int line) {
        this.type = type;
        this.lexeme = lexeme;
        this.line = line;
    }

    public Token(String type, String lexeme) {
        this(type, lexeme, 0);
    }

    @Override
    public String toString() {
        return type + "(\"" + lexeme + "\")";
    }
}