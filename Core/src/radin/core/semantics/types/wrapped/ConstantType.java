package radin.core.semantics.types.wrapped;

import radin.core.semantics.TypeEnvironment;
import radin.core.semantics.types.CXType;
import radin.core.semantics.types.ICXWrapper;

public class ConstantType extends CXType implements ICXWrapper {
    
    private CXType subtype;
    
    public ConstantType(CXType subtype) {
        this.subtype = subtype;
    }
    
    @Override
    public String generateCDefinition() {
        return "const " + subtype.generateCDefinition();
    }
    
    @Override
    public String generateCDeclaration(String identifier) {
        return "const " + subtype.generateCDeclaration(identifier);
    }
    
    @Override
    public boolean isValid(TypeEnvironment e) {
        return subtype.isValid(e);
    }
    
    @Override
    public boolean isPrimitive() {
        return subtype.isPrimitive();
    }
    
    @Override
    public long getDataSize(TypeEnvironment e) {
        return subtype.getDataSize(e);
    }
    
    public CXType getSubtype() {
        return subtype;
    }
    
    @Override
    public CXType getWrappedType() {
        return getSubtype();
    }
    
    @Override
    public boolean is(CXType other, TypeEnvironment e, boolean strictPrimitiveEquality) {
        if(other instanceof ConstantType) {
            return e.is(subtype, ((ConstantType) other).getSubtype());
            // return subtype.is(((ConstantType) other).getSubtype(), e);
        }
        return false;
    }
}
