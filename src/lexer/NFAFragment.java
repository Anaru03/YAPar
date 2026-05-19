package lexer;

/**
 * Fragmento de NFA: par (inicio, fin) usado durante la construcción de Thompson.
 */
public class NFAFragment {
    public final NFANode start;
    public final NFANode end;

    public NFAFragment(NFANode start, NFANode end) {
        this.start = start;
        this.end = end;
    }
}
