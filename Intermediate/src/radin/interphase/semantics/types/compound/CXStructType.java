package radin.interphase.semantics.types.compound;

import java.util.List;

public class CXStructType extends CXBasicCompoundType {
    
    public CXStructType(List<FieldDeclaration> fields) {
        super(fields);
    }
    
    public CXStructType(FieldDeclaration f1, FieldDeclaration... fields) {
        super(f1, fields);
    }
    
    public CXStructType(String name, List<FieldDeclaration> fields) {
        super(name, fields);
    }
    
    public CXStructType(String name, FieldDeclaration f1, FieldDeclaration... fields) {
        super(name, f1, fields);
    }
    
    
    
    @Override
    public String generateCDefinition() {
        return "struct" + super.generateCDefinition();
        
    }
    
}
