package radin.interphase.lexical;

public class Token {

    private TokenType type;
    private String image;
    private int column = -1;
    private int lineNumber = -1;
    
    public Token(TokenType type) {
        this.type = type;
    }
    
    public Token(TokenType type, String image) {
        this.type = type;
        this.image = image;
    }
    
    public int getColumn() {
        return column;
    }
    
    public int getLineNumber() {
        return lineNumber;
    }
    
    public TokenType getType() {
        return type;
    }
    
    public String getImage() {
        return image;
    }
    
    public Token addColumnAndLineNumber(int column, int lineNumber) {
        this.column = column;
        this.lineNumber = lineNumber;
        return this;
    }
    
    @Override
    public String toString() {
        if(image == null) return type.toString();
        return String.format("%s[%s]", type.toString(), image);
    }
    
    public String getRepresentation() {
        if(image != null) return image;
        return type.toString();
    }
}
