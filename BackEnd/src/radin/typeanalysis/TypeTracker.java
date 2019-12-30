package radin.typeanalysis;

import radin.interphase.semantics.TypeEnvironment;
import radin.interphase.semantics.exceptions.RedeclareError;
import radin.interphase.semantics.types.CXType;
import radin.interphase.semantics.types.Visibility;
import radin.interphase.semantics.types.compound.CXClassType;
import radin.interphase.semantics.types.compound.CXCompoundType;
import radin.interphase.semantics.types.methods.ParameterTypeList;
import radin.typeanalysis.errors.ClassNotDefinedError;
import radin.typeanalysis.errors.IdentifierDoesNotExistError;
import radin.typeanalysis.errors.RedeclarationError;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class TypeTracker {
    
    public enum EntryStatus {
        OLD,
        NEW,
        FIXED,
    }
    
    public static class TypeTrackerEntry {
        private EntryStatus status;
        private CXType type;
        
        TypeTrackerEntry(EntryStatus status, CXType type) {
            this.status = status;
            this.type = type;
        }
        
        public TypeTrackerEntry(TypeTrackerEntry other) {
            this.status = other.status;
            this.type = other.type;
        }
        
        public EntryStatus getStatus() {
            return status;
        }
        
        void setStatus(EntryStatus status) {
            this.status = status;
        }
        
        public CXType getType() {
            return type;
        }
        
        @Override
        public String toString() {
            return "{" +
                    "status=" + status +
                    ", type=" + type +
                    '}';
        }
    }
    
    private static class CompoundDeclarationKey {
        private CXCompoundType type;
        private String name;
        
        public CompoundDeclarationKey(CXCompoundType type, String name) {
            this.type = type;
            this.name = name;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            
            CompoundDeclarationKey compoundDeclarationKey = (CompoundDeclarationKey) o;
            
            if (!type.equals(compoundDeclarationKey.type)) return false;
            return name.equals(compoundDeclarationKey.name);
        }
        
        @Override
        public int hashCode() {
            int result = type.hashCode();
            result = 31 * result + name.hashCode();
            return result;
        }
        
        @Override
        public String toString() {
            return "{" +
                    "type=" + type +
                    ", name='" + name + '\'' +
                    '}';
        }
    }
    
    private class MethodKey extends CompoundDeclarationKey {
        
        private ParameterTypeList parameterTypeList;
        
        public MethodKey(CXCompoundType type, String name, ParameterTypeList params) {
            super(type, name);
            parameterTypeList = params;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            
            MethodKey methodKey = (MethodKey) o;
            return parameterTypeList.equals(methodKey.parameterTypeList, environment);
        }
        
        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + parameterTypeList.hashCode();
            return result;
        }
    
        @Override
        public String toString() {
            return super.toString() +
                    " parameterTypeList=" + parameterTypeList;
        }
    }
    
    private class ConstructorKey extends MethodKey {
    
        public ConstructorKey(CXCompoundType type, ParameterTypeList params) {
            super(type, "", params);
        }
    }
    
    /**
     * Keep track of available types
     */
    private HashSet<CXCompoundType> trackingTypes;
    // lexical variables
    // these should be demoted
    private HashMap<String, TypeTrackerEntry> variableEntries;
    
    // global availability
    // these should copied
    private HashMap<String, TypeTrackerEntry> functionEntries;
    private HashMap<CompoundDeclarationKey, TypeTrackerEntry> publicMethodEntries;
    private HashMap<CompoundDeclarationKey, TypeTrackerEntry> publicFieldEntries;
    private HashSet<ConstructorKey> publicConstructors;
    
    // internal availability
    // these should be demoted
    private HashMap<CompoundDeclarationKey, TypeTrackerEntry> internalMethodEntries;
    private HashMap<CompoundDeclarationKey, TypeTrackerEntry> internalFieldEntries;
    private HashSet<ConstructorKey> internalConstructors;
    
    // private availability
    // these should be new for every class declaration
    private HashMap<CompoundDeclarationKey, TypeTrackerEntry> privateFieldEntries;
    private HashMap<CompoundDeclarationKey, TypeTrackerEntry> privateMethodEntries;
    private HashSet<ConstructorKey> privateConstructors;
    
    
    private static HashMap<CXClassType, TypeTracker> classTrackers;
    private TypeEnvironment environment;
    
    static {
        classTrackers = new HashMap<>();
    }
    
    public TypeTracker(TypeEnvironment environment) {
        this.environment = environment;
        variableEntries = new HashMap<>();
        functionEntries = new HashMap<>();
        
        publicMethodEntries = new HashMap<>();
        publicFieldEntries = new HashMap<>();
        publicConstructors = new HashSet<>();
        
        internalFieldEntries = new HashMap<>();
        internalMethodEntries = new HashMap<>();
        internalConstructors = new HashSet<>();
        
        privateMethodEntries = new HashMap<>();
        privateFieldEntries = new HashMap<>();
        privateConstructors = new HashSet<>();
        
        trackingTypes = new HashSet<>();
    }
    
    private TypeTracker(TypeTracker old) {
        environment = old.environment;
        trackingTypes = new HashSet<>(old.trackingTypes);
        variableEntries = new HashMap<>();
        demoteEntries(variableEntries, old.variableEntries);
        
        functionEntries = old.functionEntries;
        
        publicMethodEntries = old.publicMethodEntries;
        publicFieldEntries = old.publicFieldEntries;
        publicConstructors = old.publicConstructors;
        
        internalFieldEntries = new HashMap<>();
        internalMethodEntries = new HashMap<>();
        demoteEntries(internalFieldEntries, old.internalFieldEntries);
        demoteEntries(internalMethodEntries, old.internalMethodEntries);
        internalConstructors = new HashSet<>(old.internalConstructors);
        
        
        
        privateMethodEntries = new HashMap<>();
        privateFieldEntries = new HashMap<>();
        demoteEntries(privateFieldEntries, old.privateFieldEntries);
        demoteEntries(privateFieldEntries, old.privateMethodEntries);
        privateConstructors = new HashSet<>();
    }
    
    private TypeTracker(TypeTracker old, CXClassType parentType) {
        this(old);
        
        TypeTracker typeTracker = classTrackers.getOrDefault(parentType, null);
        if(typeTracker == null) throw new ClassNotDefinedError();
        
        demoteEntries(internalFieldEntries, typeTracker.internalFieldEntries);
        demoteEntries(internalMethodEntries, typeTracker.internalMethodEntries);
        
        internalConstructors.addAll(typeTracker.internalConstructors);
    }
    
    /**
     * Moves entries from old set, while also "demoting" them
     * @param entryHashMap the new map
     * @param oldMap the old map
     * @param <T> the key type
     */
    private static <T> void demoteEntries(HashMap<T, TypeTrackerEntry> entryHashMap, HashMap<T,
            TypeTrackerEntry> oldMap) {
        for (Map.Entry<T, TypeTrackerEntry> typeTrackerEntry : oldMap.entrySet()) {
            TypeTrackerEntry newEntry = new TypeTrackerEntry(typeTrackerEntry.getValue());
            if (newEntry.getStatus() == EntryStatus.NEW) {
                newEntry.status = EntryStatus.OLD;
            }
            entryHashMap.put(typeTrackerEntry.getKey(), newEntry);
        }
    }
    
    public TypeTracker createInnerTypeTracker() {
        return new TypeTracker(this);
    }
    
    public TypeTracker createInnerTypeTracker(CXClassType owner) {
        TypeTracker typeTracker;
        if(owner.getParent() != null) {
            typeTracker = new TypeTracker(this, owner.getParent());
        } else {
            typeTracker = new TypeTracker(this);
        }
        //typeTracker.addEntry("this", new PointerType(owner));
        classTrackers.put(owner, typeTracker);
        return typeTracker;
    }
    
    public boolean entryExists(String name) {
        if(variableEntries.containsKey(name)) return true;
        return functionExists(name);
    }
    
    public boolean variableExists(String name) {
        return variableEntries.containsKey(name);
    }
    
    public boolean functionExists(String name) {
        return functionEntries.containsKey(name);
    }
    
    public boolean fieldVisible(CXCompoundType type, String name) {
        
        return isVisible(type, name, publicFieldEntries, internalFieldEntries, privateFieldEntries, null);
    }
    
    public boolean methodVisible(CXCompoundType type, String name, ParameterTypeList typeList) {
        return isVisible(type, name, publicMethodEntries, internalMethodEntries, privateMethodEntries, typeList);
    }
    
    public boolean isVisible(CXCompoundType type, String name, HashMap<CompoundDeclarationKey, TypeTrackerEntry> publicEntries, HashMap<CompoundDeclarationKey, TypeTrackerEntry> internalEntries, HashMap<CompoundDeclarationKey, TypeTrackerEntry> privateEntries, ParameterTypeList params) {
        CompoundDeclarationKey key;
        
        
        if(type instanceof CXClassType) {
            CXClassType cxClassType = (CXClassType) type;
            for (CXClassType inherit : cxClassType.getReverseInheritanceOrder()) {
                if(params == null) {
                    key =new CompoundDeclarationKey(inherit, name);
                } else {
                    key = new MethodKey(inherit, name, params);
                }
                if(publicEntries.containsKey(key) || internalEntries.containsKey(key) || privateEntries.containsKey(key)) return true;
            }
        } else {
            if(params == null) {
                key =new CompoundDeclarationKey(type, name);
            } else {
                key = new MethodKey(type, name, params);
            }
            if(publicEntries.containsKey(key)) return true;
            
        }
        return false;
    }
    
    public void addVariable(String name, CXType type) {
        TypeTrackerEntry typeTrackerEntry = new TypeTrackerEntry(EntryStatus.NEW, type);
        addVariable(name, typeTrackerEntry);
    }
    
    public void addVariableEntry(String name, CXType type) {
        TypeTrackerEntry typeTrackerEntry = new TypeTrackerEntry(EntryStatus.FIXED, type);
        addVariable(name, typeTrackerEntry);
    }
    
    private TypeTrackerEntry getEntry(String name) {
        if(!entryExists(name)) return null;
        if(functionExists(name)) return functionEntries.get(name);
        return variableEntries.get(name);
    }
    
    private void addVariable(String name, TypeTrackerEntry typeTrackerEntry) {
        if(!entryExists(name)) {
            variableEntries.put(name, typeTrackerEntry);
        } else {
            TypeTrackerEntry oldEntry = getEntry(name);
            if(oldEntry.getStatus() != EntryStatus.OLD) {
                throw new RedeclareError(name);
            }
            
            variableEntries.replace(name, typeTrackerEntry);
        }
    }
    
    public void addFunction(String name, CXType type) {
        TypeTrackerEntry typeTrackerEntry = new TypeTrackerEntry(EntryStatus.NEW, type);
        if(functionExists(name)) throw new RedeclareError(name);
        functionEntries.put(name, typeTrackerEntry);
    }
    
    public void addFixedFunction(String name, CXType type) {
        TypeTrackerEntry typeTrackerEntry = new TypeTrackerEntry(EntryStatus.FIXED, type);
        if(functionExists(name)) throw new RedeclareError(name);
        functionEntries.put(name, typeTrackerEntry);
    }
    
    
    private void putVariable(String name, CXType type) {
        TypeTrackerEntry typeTrackerEntry = new TypeTrackerEntry(EntryStatus.NEW, type);
        variableEntries.put(name, typeTrackerEntry);
    }
    
    public void addCompoundTypeField(CXCompoundType parent, String name, CXType type, HashMap<CompoundDeclarationKey, TypeTrackerEntry> publicFieldEntries, HashMap<CompoundDeclarationKey, TypeTrackerEntry> publicMethodEntries) {
        CompoundDeclarationKey key = new CompoundDeclarationKey(parent, name);
        TypeTrackerEntry typeTrackerEntry = new TypeTrackerEntry(EntryStatus.NEW, type);
        
        if(fieldVisible(parent, name)) {
            throw new RedeclareError(name);
        }
        publicFieldEntries.put(key, typeTrackerEntry);
        
    }
    
    public void addIsTracking(CXCompoundType type) {
        trackingTypes.add(type);
    }
    
    public boolean isTracking(CXCompoundType type) {
        return trackingTypes.contains(type);
    }
    
    public void addBasicCompoundType(CXCompoundType type) {
        if(type instanceof CXClassType) return;
        for (CXCompoundType.FieldDeclaration field : type.getFields()) {
            addPublicField(type, field.getName(), field.getType());
            if(field.getType() instanceof CXCompoundType) {
                CXCompoundType fieldType = (CXCompoundType) field.getType();
                if(!isTracking(fieldType)) {
                    addBasicCompoundType(fieldType);
                    addIsTracking(fieldType);
                }
            }
        }
    }
    
    public void removeParentlessStructFields() {
        HashSet<CompoundDeclarationKey> remove = new HashSet<>();
        for (CompoundDeclarationKey compoundDeclarationKey : publicFieldEntries.keySet()) {
            if(!(compoundDeclarationKey.type instanceof CXClassType) && !trackingTypes.contains(compoundDeclarationKey.type)) {
                remove.add(compoundDeclarationKey);
            }
        }
        for (CompoundDeclarationKey compoundDeclarationKey : remove) {
            publicFieldEntries.remove(compoundDeclarationKey);
        }
    }
    
    public void addPublicField(CXCompoundType parent, String name, CXType type) {
        addCompoundTypeField(parent, name, type, publicFieldEntries, publicMethodEntries);
    }
    
    public void addInternalField(CXClassType parent, String name, CXType type) {
        addCompoundTypeField(parent, name, type, internalFieldEntries, internalMethodEntries);
    }
    
    public void addPrivateField(CXClassType parent, String name, CXType type) {
        addCompoundTypeField(parent, name, type, privateFieldEntries, privateMethodEntries);
    }
    
    public void addPublicMethod(CXCompoundType parent, String name, CXType type, ParameterTypeList typeList) {
        addCompoundTypeMethodEntry(parent, name, type, typeList, publicMethodEntries);
    }
    
    public void addInternalMethod(CXClassType parent, String name, CXType type, ParameterTypeList typeList) {
        addCompoundTypeMethodEntry(parent, name, type, typeList, internalMethodEntries);
    }
    
    public void addPrivateMethod(CXClassType parent, String name, CXType type, ParameterTypeList typeList) {
        addCompoundTypeMethodEntry(parent, name, type, typeList, privateMethodEntries);
    }
    
    public void addConstructor(Visibility visibility, CXClassType owner, ParameterTypeList parameterTypeList) {
        ConstructorKey constructorKey = new ConstructorKey(owner, parameterTypeList);
        if(constructorVisible(owner, parameterTypeList)) throw new RedeclarationError("Constructor of type " + owner + " on parameters " + parameterTypeList);
        
        switch (visibility) {
            case _public:
                publicConstructors.add(constructorKey);
            return;
            case internal:
                internalConstructors.add(constructorKey);
                return;
            case _private:
                privateConstructors.add(constructorKey);
            
        }
    }
    
    public boolean constructorVisible(CXClassType owner, ParameterTypeList parameterTypeList) {
        ConstructorKey constructorKey = new ConstructorKey(owner, parameterTypeList);
        return publicConstructors.contains(constructorKey) || internalConstructors.contains(constructorKey) || privateConstructors.contains(constructorKey);
    }
    
    
    public void addCompoundTypeMethodEntry(CXCompoundType parent, String name, CXType type, ParameterTypeList typeList,
                                           HashMap<CompoundDeclarationKey, TypeTrackerEntry> publicMethodEntries) {
        CompoundDeclarationKey key = new MethodKey(parent, name, typeList);
        TypeTrackerEntry typeTrackerEntry = new TypeTrackerEntry(EntryStatus.NEW, type);
        
        if(methodVisible(parent, name, typeList)) {
            assert parent instanceof CXClassType;
            System.out.println(((CXClassType) parent).classInfo());
            throw new RedeclareError(name);
        }
        publicMethodEntries.put(key, typeTrackerEntry);
        
    }
    
    public CXType getFieldType(CXCompoundType owner, String name) {
        if(!fieldVisible(owner, name)) return null;
        CompoundDeclarationKey key = new CompoundDeclarationKey(owner, name);
        if(publicFieldEntries.containsKey(key)) {
            return publicFieldEntries.get(key).getType();
        }
        if(internalFieldEntries.containsKey(key)) {
            return internalFieldEntries.get(key).getType();
        }
        if(privateFieldEntries.containsKey(key)) {
            return privateFieldEntries.get(key).getType();
        }
        return null;
    }
    
    public CXType getFieldType(CXClassType owner, String name) {
        for (CXClassType cxClassType : owner.getLineage()) {
            CXType fieldType = getFieldType((CXCompoundType) cxClassType, name);
            if(fieldType != null) return fieldType;
        }
        return null;
    }
    
    public CXType getMethodType(CXClassType owner, String name, ParameterTypeList typeList) {
        for (CXClassType cxClassType : owner.getReverseInheritanceOrder()) {
            if (!methodVisible(cxClassType, name, typeList)) continue;
            CompoundDeclarationKey key = new MethodKey(cxClassType, name, typeList);
            if (publicMethodEntries.containsKey(key)) {
                return publicMethodEntries.get(key).getType();
            }
            if (internalMethodEntries.containsKey(key)) {
                return internalMethodEntries.get(key).getType();
            }
            if (privateMethodEntries.containsKey(key)) {
                return privateMethodEntries.get(key).getType();
            }
        }
        return null;
    }
    
    
    public CXType getType(String name) {
        if(entryExists(name)) {
            if(functionExists(name)) {
                return functionEntries.get(name).getType();
            }
            return variableEntries.get(name).getType();
        }
        throw new IdentifierDoesNotExistError(name);
        
    }
}
