package radin.core.output.backend.interpreter;

import radin.core.JodinLogger;
import radin.core.SymbolTable;
import radin.core.errorhandling.CompilationError;
import radin.core.lexical.Token;
import radin.core.lexical.TokenType;
import radin.core.output.midanalysis.MethodTASNTracker;
import radin.core.output.midanalysis.TypeAugmentedSemanticNode;
import radin.core.output.tags.ArrayWithSizeTag;
import radin.core.output.tags.ConstructorCallTag;
import radin.core.output.tags.MultiDimensionalArrayWithSizeTag;
import radin.core.output.tags.PriorConstructorTag;
import radin.core.semantics.ASTNodeType;
import radin.core.semantics.TypeEnvironment;
import radin.core.semantics.exceptions.InvalidPrimitiveException;
import radin.core.semantics.types.CXIdentifier;
import radin.core.semantics.types.CXType;
import radin.core.semantics.types.ICXWrapper;
import radin.core.semantics.types.TypedAbstractSyntaxNode;
import radin.core.semantics.types.compound.CXClassType;
import radin.core.semantics.types.compound.CXCompoundType;
import radin.core.semantics.types.compound.ICXCompoundType;
import radin.core.semantics.types.methods.CXConstructor;
import radin.core.semantics.types.methods.CXMethod;
import radin.core.semantics.types.methods.ParameterTypeList;
import radin.core.semantics.types.primitives.*;
import radin.core.utility.ICompilationSettings;

import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class Interpreter {
    
    private JodinLogger logger = ICompilationSettings.ilog;
    
    public abstract class Instance <T extends CXType> {
        
        private T type;
        
        public Instance(T type) {
            this.type = type;
        }
        
        public T getType() {
            return type;
        }
        
        public PointerInstance<T> toPointer() {
            return new PointerInstance<>(getType(), this);
        }
        
        @Override
        public String toString() {
            return "Instance{" +
                    "type=" + type +
                    '}';
        }
        
        abstract void copyFrom(Instance<?> other);
        
        abstract Instance<T> copy();
        
        abstract Instance<?> castTo(CXType castingTo) throws InvalidPrimitiveException;
    
        
        public NullableInstance<T, ? extends Instance<T>> toNullable() {
            return new NullableInstance<>(getType(), this);
        }
    }
    
    public class PrimitiveInstance <R, P extends AbstractCXPrimitiveType> extends Instance<P> {
        
        private R backingValue;
        private boolean unsigned;
        
        public PrimitiveInstance(P type, R backingValue, boolean unsigned) {
            super(type);
            this.backingValue = backingValue;
            if (backingValue == null) logger.warning("Shouldn't set backing value to null");
            this.unsigned = unsigned;
        }
        
        public R getBackingValue() {
            return backingValue;
        }
        
        public void setBackingValue(R backingValue) {
            this.backingValue = backingValue;
        }
        
        @Override
        public String toString() {
            return "(" + getType() + ") " + backingValue;
        }
        
        @Override
        void copyFrom(Instance<?> other) {
            assert other instanceof PrimitiveInstance;
            
            this.backingValue = ((PrimitiveInstance<R, P>) other).backingValue;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PrimitiveInstance<?, ?> that = (PrimitiveInstance<?, ?>) o;
            return unsigned == that.unsigned &&
                    Objects.equals(backingValue, that.backingValue);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(backingValue, unsigned);
        }
        
        @Override
        Instance<P> copy() {
            return new PrimitiveInstance<R, P>(getType(), backingValue, unsigned);
        }
        
        @Override
        Instance<?> castTo(CXType castingTo) throws InvalidPrimitiveException {
            if(backingValue instanceof Number) {
                if (castingTo.equals(CXPrimitiveType.INTEGER)) {
                    return new PrimitiveInstance<>(CXPrimitiveType.INTEGER, (int) ((Number) backingValue).intValue(),
                            false);
                } else if (castingTo.equals(CXPrimitiveType.DOUBLE)) {
                    return new PrimitiveInstance<>(CXPrimitiveType.DOUBLE, (double) ((Number) backingValue).doubleValue(),
                            false);
                } else if (castingTo.equals(CXPrimitiveType.FLOAT)) {
                    return new PrimitiveInstance<>(CXPrimitiveType.DOUBLE, ((Number) backingValue).floatValue(),
                            false);
                } else if (castingTo.equals(CXPrimitiveType.CHAR)) {
                    return new PrimitiveInstance<>(CXPrimitiveType.CHAR, (char) ((Number) backingValue).intValue(),
                            false);
                } else if (castingTo.equals(CXPrimitiveType.VOID)) {
                    return null;
                } else if (castingTo instanceof LongPrimitive) {
                    return new PrimitiveInstance<>(LongPrimitive.create(), (long) ((Number) backingValue).longValue(), false);
                } else if (castingTo instanceof ShortPrimitive) {
                    return new PrimitiveInstance<>(new ShortPrimitive((CXPrimitiveType) getType()),
                            (short) ((Number) backingValue).shortValue(),
                            false);
                } else if (castingTo instanceof UnsignedPrimitive) {
                    UnsignedPrimitive to = (UnsignedPrimitive) castingTo;
                    PrimitiveInstance<Number, AbstractCXPrimitiveType> primitiveInstance =
                            (PrimitiveInstance<Number, AbstractCXPrimitiveType>) castTo(to.getPrimitiveCXType());
                    return new PrimitiveInstance<>(primitiveInstance.getType(), backingValue, true);
                } else if (castingTo instanceof PointerType &&
                        ((Number) this.backingValue).intValue() == 0
                ) {
                    return new PointerInstance<>(((PointerType) castingTo).getSubType(),
                            new ArrayList<>(Collections.singletonList(null)), 0);
                }
            } else if(backingValue instanceof Character) {
                if (castingTo.equals(CXPrimitiveType.INTEGER)) {
                    return new PrimitiveInstance<>(CXPrimitiveType.INTEGER,
                            (int) ((Character) backingValue).charValue(),
                            false);
                } else if (castingTo.equals(CXPrimitiveType.DOUBLE)) {
                    return new PrimitiveInstance<>(CXPrimitiveType.DOUBLE, (double) ((Character) backingValue).charValue(),
                            false);
                } else if (castingTo.equals(CXPrimitiveType.FLOAT)) {
                    return new PrimitiveInstance<>(CXPrimitiveType.DOUBLE, (float)  ((Character) backingValue).charValue(),
                            false);
                } else if (castingTo.equals(CXPrimitiveType.CHAR)) {
                    return new PrimitiveInstance<>(CXPrimitiveType.CHAR, (char) ((Character) backingValue).charValue(),
                            false);
                } else if (castingTo.equals(CXPrimitiveType.CHAR)) {
                    return new PrimitiveInstance<>(CXPrimitiveType.CHAR, (char) ((Character) backingValue).charValue(),
                            false);
                } else if (castingTo.equals(CXPrimitiveType.VOID)) {
                    return null;
                } else if (castingTo instanceof LongPrimitive) {
                    return new PrimitiveInstance<>(LongPrimitive.create(), (long) ((Character) backingValue).charValue(), false);
                } else if (castingTo instanceof ShortPrimitive) {
                    return new PrimitiveInstance<>(new ShortPrimitive((CXPrimitiveType) getType()), (short)  ((Character) backingValue).charValue(),
                            false);
                } else if (castingTo instanceof UnsignedPrimitive) {
                    UnsignedPrimitive to = (UnsignedPrimitive) castingTo;
                    PrimitiveInstance<Number, AbstractCXPrimitiveType> primitiveInstance =
                            (PrimitiveInstance<Number, AbstractCXPrimitiveType>) castTo(to.getPrimitiveCXType());
                    return new PrimitiveInstance<>(primitiveInstance.getType(), backingValue, true);
                } else if (castingTo instanceof PointerType &&
                        ((Number) this.backingValue).intValue() == 0
                ) {
                    return new PointerInstance<>(((PointerType) castingTo).getSubType(),
                            new ArrayList<>(Collections.singletonList(null)), 0);
                }
            }
            
            throw new IllegalStateException("Can't type cast " + getType() + " to " + castingTo);
        }
    
        
    }
    
    public class SegmentationFault extends Error {
        
        public SegmentationFault() {
        }
        
        public SegmentationFault(String message) {
            super(message);
        }
        
        public SegmentationFault(String message, Throwable cause) {
            super(message, cause);
        }
        
        public SegmentationFault(Throwable cause) {
            super(cause);
        }
    }
    
    public class NullableInstance<R extends CXType, I extends Instance<R>> extends Instance<R> {
        
        private I value;
        
        
        public NullableInstance(R type) {
            super(type);
            value = null;
        }
        
        public NullableInstance(R type, Instance<R> instance) {
            super(type);
            if(instance instanceof NullableInstance) {
                value = ((NullableInstance<R, I>) instance).getValue();
            }else {
                value = (I) instance;
            }
        }
        
        
        @Override
        void copyFrom(Instance<?> other) {
            if(value== null) value = (I) other;
            else value.copyFrom(other);
        }
        
        @Override
        Instance<R> copy() {
            return new NullableInstance<>(getType(), value);
        }
        
        @Override
        Instance<?> castTo(CXType castingTo) throws InvalidPrimitiveException {
            return new NullableInstance<R, Instance<R>>((R) castingTo, value);
        }
        
        @Override
        public String toString() {
            if(value == null) return "null value";
            return "Nullable<" + value + ">";
        }
        
        public I getValue() {
            if(value != null && value instanceof NullableInstance) return ((NullableInstance<R, I>) value).getValue();
            return value;
        }
        
        public void setValue(I value) {
            if(value instanceof NullableInstance) {
                this.value = ((NullableInstance<R, I>) value).value;
            }
            this.value = value;
        }
    
        @Override
        public NullableInstance<R, ? extends Instance<R>> toNullable() {
            return this;
        }
    }
    
    
    /**
     *
     * @param <R> Type of subtype
     * @param <T> Type of Array
     */
    public class ArrayInstance <R extends CXType, T extends ArrayType> extends PrimitiveInstance<ArrayList<NullableInstance<R, Instance<R>>>,
            T> {
        
        private int size;
        private R subType;
        
        public ArrayInstance(T type, R subtype, int size) {
            super(type, new ArrayList<>(size), true);
            this.subType = subtype;
            for (int i = 0; i < size; i++) {
                getBackingValue().add(new NullableInstance<>(subtype));
            }
            this.size = size;
        }
        
        public ArrayInstance(T type, R subType, ArrayList<? extends Instance<R>> other) {
            super(type,
                    new ArrayList<NullableInstance<R, Instance<R>>>(other.size()),
                    true);
            for (int i = 0; i < other.size(); i++) {
                getBackingValue().add(other.get(i) == null ? new NullableInstance<>(subType) :
                        (NullableInstance<R, Instance<R>>) other.get(i).toNullable());
            }
            this.subType = subType;
            this.size = other.size();
        }
    
       
        
        
        
        public NullableInstance<R, Instance<R>> getAt(int index) {
            if(index >= getBackingValue().size()) throw new SegmentationFault("Index " + index + " out of bounds of " +
                    "size " + getBackingValue().size());
            return getBackingValue().get(index);
        }
        
        public void setAt(int index, Instance<R> value) {
            if(value instanceof NullableInstance) {
                getBackingValue().set(index, ((NullableInstance<R, Instance<R>>) value));
            } else {
                getBackingValue().get(index).setValue(value);
            }
        }
        
        public PointerInstance<R> asPointer() {
            return new PointerInstance<>(subType,
                    new ArrayList<Instance<R>>(
                            getBackingValue()
                    ), 0);
        }
        
        public int getSize() {
            return size;
        }
        
        public R getSubType() {
            return subType;
        }
        
        public int size() {
            return size;
        }
        
        @Override
        public String toString() {
            if (subType.equals(CXPrimitiveType.CHAR)) {
                StringBuilder output = new StringBuilder("\"");
                
                try {
                    for (int i = 0; i < getSize() && getAt(i).getValue() != null; i++) {
                        output.append(((PrimitiveInstance<Character, ?>) getAt(i).getValue()).backingValue);
                    }
                }catch (IndexOutOfBoundsException e) {
                    throw new SegmentationFault(e);
                }
                
                return output + "\" (true size = " + getSize() + ")";
            }
            return "ArrayInstance{" +
                    "type=" + getType() +
                    ", size=" + size +
                    '}';
        }
        
        @Override
        void copyFrom(Instance<?> other) {
            if (other instanceof ArrayInstance) {
                this.setBackingValue(((ArrayInstance<R, T>) other).getBackingValue());
                this.size = this.getBackingValue().size();
            } else {
                assert other instanceof PrimitiveInstance;
                if (((PrimitiveInstance<?, ?>) other).getBackingValue().toString().equals("0")) {
                    this.setBackingValue(null);
                } else {
                    throw new IllegalStateException();
                }
            }
        }
        
        
        @Override
        Instance<T> copy() {
            ArrayList<NullableInstance<R, Instance<R>>> backingValue = getBackingValue();
            ArrayInstance<R, T> output = new ArrayInstance<>(getType(), getSubType(), backingValue.size());
            for (int i = 0; i < backingValue.size(); i++) {
                NullableInstance<R, Instance<R>> value = backingValue.get(i);
                output.setAt(i, value);
            }
            return (Instance<T>) output.asPointer();
        }
        
        @Override
        Instance<?> castTo(CXType castingTo) throws InvalidPrimitiveException {
            throw new IllegalStateException("Can't type cast " + getType() + " to " + castingTo);
        }
    }
    
    
    
    
    public class PointerInstance <R extends CXType> extends ArrayInstance<R, PointerType> {
        
        private int index = 0;
        
        public PointerInstance(R type, Instance<R> pointer) {
            super(type.toPointer(), type, new ArrayList<>(Collections.singletonList(null)));
            setAt(0, pointer);
        }
        
        public PointerInstance(R type, ArrayList<? extends Instance<R>> backing, int offset) {
            super(type.toPointer(), type, backing);
            index = offset;
        }
        
        
        public PointerInstance<R> getPointerOfOffset(int offset) {
            return new PointerInstance<R>(getSubType(), getBackingValue(), index + offset);
        }
        
        public PointerInstance(PointerType type) {
            super(type, (R) type.getSubType(), new ArrayList<>(Collections.singletonList(null)));
        }
        
        
        public Instance<R> getPointer() {
            if(getAt(index) instanceof NullableInstance) {
                return getAt(index).getValue();
            }
            return getAt(index);
        }
        
        public void setPointer(Instance<R> pointer) {
            setAt(index, pointer);
        }
    
        @Override
        public PointerInstance<R> asPointer() {
            return this;
        }
    
        @Override
        public String toString() {
            if (getSubType() == CXPrimitiveType.CHAR) {
                String full = super.toString();
                String output = full.substring(index + 1);
                return "\"" + output + " (char*)";
            }
            if(getBackingValue().size() == 0) {
                return "nullptr (type = " + getType() + ")";
            }
            return (getBackingValue() == null || getBackingValue().get(0) == null ?
                    "[NULL] " : "") +
                    "PointerInstance{" +
                    "type=" + getType() +
                    '}';
        }
        
        @Override
        Instance<PointerType> copy() {
            return new PointerInstance<>(getSubType(), getBackingValue(), index);
        }
        
        @Override
        Instance<?> castTo(CXType castingTo) throws InvalidPrimitiveException {
            if(castingTo instanceof AbstractCXPrimitiveType && getPointer() == null) {
                return new PrimitiveInstance<>(((AbstractCXPrimitiveType) castingTo), 0,
                        castingTo instanceof UnsignedPrimitive);
            }
            if (!(castingTo instanceof PointerType))
                throw new IllegalStateException("Can't type cast " + getType() + " to " + castingTo);
            
            return new PointerInstance<R>((R) castingTo, getPointer());
        }
    }
    
    public class CompoundInstance <T extends CXCompoundType> extends Instance<T> {
        
        private HashMap<String, Instance<?>> fields;
        
        public CompoundInstance(T type) {
            super(type);
            this.fields = new HashMap<>();
            for (ICXCompoundType.FieldDeclaration field : type.getAllFields()) {
                fields.put(field.getName(), createNewInstance(field.getType()));
            }
        }
        
        public <R extends CXType, I extends Instance<R>> I get(String key) {
            return (I) fields.get(key);
        }
        
        @Override
        void copyFrom(Instance<?> other) {
            assert other instanceof CompoundInstance;
            
            for (String s : fields.keySet()) {
                this.fields.replace(s, ((CompoundInstance<CXCompoundType>) other).fields.get(s));
            }
        }
        
        @Override
        Instance<T> copy() {
            CompoundInstance<T> tCompoundInstance = new CompoundInstance<>(getType());
            for (Map.Entry<String, Instance<?>> stringInstanceEntry : fields.entrySet()) {
                tCompoundInstance.fields.put(stringInstanceEntry.getKey(), stringInstanceEntry.getValue());
            }
            return tCompoundInstance;
        }
        
        @Override
        Instance<?> castTo(CXType castingTo) throws InvalidPrimitiveException {
            throw new IllegalStateException("Can't type cast " + getType() + " to " + castingTo);
        }
    }
    
    public <R, T extends AbstractCXPrimitiveType> PrimitiveInstance<R, T> createNewInstance(T type, R backing) {
        PrimitiveInstance<R, T> instance = (PrimitiveInstance<R, T>) createNewInstance(type);
        instance.setBackingValue(backing);
        return instance;
    }
    
    public <T extends CXType> Instance<?> createNewInstance(T type) {
        if (type instanceof ICXWrapper) return createNewInstance(((ICXWrapper) type).getWrappedType());
        
        if (type instanceof PointerType) {
            
            
            return new PointerInstance<>(((PointerType) type));
            
        } else if (type instanceof ArrayType) {
            // TODO: implement proper array sizing
            return new ArrayInstance<>(((ArrayType) type), ((ArrayType) type).getBaseType(), 15);
        } else if (type instanceof AbstractCXPrimitiveType) {
            boolean unsigned = false;
            AbstractCXPrimitiveType fixed = ((AbstractCXPrimitiveType) type);
            if (fixed instanceof UnsignedPrimitive) {
                unsigned = true;
                fixed = ((UnsignedPrimitive) fixed).getPrimitiveCXType();
            }
            
            try {
                
                if (unsigned) return new PrimitiveInstance<>(fixed, 0, true).castTo(type);
                return new PrimitiveInstance<>(fixed, 0, false).castTo(type);
            } catch (InvalidPrimitiveException e){
                return null;
            }
            
            
        } else if (type instanceof CXCompoundType) {
            return new CompoundInstance<>(((CXCompoundType) type));
        }
        return null;
    }
    
    private TypeEnvironment environment;
    private SymbolTable<CXIdentifier, TypeAugmentedSemanticNode> symbols;
    
    private Stack<HashMap<String, Instance<?>>> autoVariables = new Stack<>();
    private Instance<?> returnValue;
    private Stack<Instance<?>> memStack = new Stack<>();
    private Stack<Integer> previousMemStackSize = new Stack<>();
    private Stack<StackTraceInfo> stackTrace = new Stack<>();
    private HashMap<String, Instance<?>> globalAutoVariables;
    private Stack<PointerInstance<CXClassType>> thisStack = new Stack<>();
    private Stack<Boolean> useThisStack = new Stack<>();
    
    private Token nearestCurrentToken = null;
    
    private class StackTraceInfo {
        private Token function;
        private HashMap<String, Instance<?>> stackVariables;
    
        public StackTraceInfo(Token function) {
            this.function = function;
            stackVariables = new HashMap<>();
        }
    
        public Token getFunction() {
            return function;
        }
    
        public HashMap<String, Instance<?>> getStackVariables() {
            return stackVariables;
        }
    
        @Override
        public String toString() {
            if(function.getFilename() != null) return function.getImage() + "(" + function.getFilename() + ":" + function.getActualLineNumber() + ")";
            return function.getImage();
        }
    }
    
    private void startStackTraceFor(Token name){
        logger.info("Starting stack trace for " + name.getImage());
        stackTrace.push(new StackTraceInfo(name));
    }
    
    private void startStackTraceFor(String modname, Token actual) {
        Token output = new Token(actual.getType(), modname);
        output.setActualLineNumber(actual.getActualLineNumber());
        output.setFilename(actual.getFilename());
        startStackTraceFor(output);
    }
    
    boolean disableLogging = false;
    
    public boolean callMethod(PointerInstance<CXClassType> ptr, String methodName, Instance<?>... params) {
        for (Instance<?> param : params) {
            push(param);
        }
    
        thisStack.push(ptr);
        useThisStack.push(true);
    
        createClosure();
        Stack<CXType> types = new Stack<>();
        Stack<Instance<?>> instances = new Stack<>();
        for (int i = 0; i < params.length; i++) {
            Instance<?> pop = pop();
            types.push(pop.getType());
            instances.push(pop);
        }
        for (Instance<?> instance : instances) {
            memStack.push(instance);
        }
        Token idToken = new Token(TokenType.t_id, methodName);
        CompoundInstance<CXClassType> classTypeInstance = (CompoundInstance<CXClassType>) ptr.getPointer();
        
        TypeAugmentedSemanticNode method = dynamicMethodLookup(ptr.getSubType(), idToken, types);
        if(method == null) {
            throw new Error("Method "+ classTypeInstance.getType() + "::" + idToken.getImage() + types + " not " +
                    "defined");
        }
        startStackTraceFor(classTypeInstance.getType() + "::" + idToken.getImage(), idToken);
        // logCurrentState();
        try {
            if (!invoke(method)) return false;
            endClosure();
        } catch (FunctionReturned functionReturned) {
            endClosure();
            push(returnValue);
            returnValue = null;
        
        }
        // logCurrentState();
        stackTrace.pop();
        thisStack.pop();
        useThisStack.pop();
        return true;
    }
    
    
    public void addAutoVariable(String name, Instance<?> value) {
        autoVariables.peek().put(name, value);
        if(!stackTrace.empty()) {
            stackTrace.peek().getStackVariables().put(name, value);
        }
    }
    
    
    public <T extends CXType> Instance<T> getAutoVariable(String name) {
        return ((Instance<T>) autoVariables.peek().get(name));
    }
    
    public void createClosure() {
        autoVariables.push(new HashMap<>(globalAutoVariables));
        previousMemStackSize.push(memStack.size());
    }
    
    public void startLexicalScope() {
        autoVariables.push(new HashMap<>(autoVariables.peek()));
    }
    
    public void endLexicalScope() {
        autoVariables.pop();
    }
    
    public void endClosure() {
        autoVariables.pop();
        logger.info("Available variables: " + autoVariables.peek().keySet());
        int previousSize = previousMemStackSize.pop();
        while (memStack.size() > previousSize) {
            memStack.pop();
        }
    }
    
    public void push(Instance<?> val) {
        logger.fine("Value was pushed to stack: " + val);
        memStack.push(val);
    }
    
    public Instance<?> popNullablePassesThrough() {
        Instance<?> pop = memStack.pop();
        if(pop instanceof NullableInstance) {
            if(((NullableInstance) pop).getValue() != null) return ((NullableInstance) pop).getValue();
        }
        return pop;
    }
    
    public Instance<?> pop() {
        logger.fine("Value was popped from stack: " + memStack.peek());
        Instance<?> pop = memStack.pop();
        if(pop instanceof NullableInstance) {
            if(((NullableInstance) pop).getValue() != null) return ((NullableInstance) pop).getValue();
        }
        return pop;
    }
    
    /*
    public Instance<?> pop() {
        logger.fine("Value was popped from stack: " + memStack.peek());
        return memStack.pop();
    }
    
     */
    
    public Interpreter(TypeEnvironment environment, SymbolTable<CXIdentifier, TypeAugmentedSemanticNode> symbols) {
        this.environment = environment;
        this.symbols = symbols;
        autoVariables.add(new HashMap<>());
        logger.info("Adding symbols and global variables to symbol table");
        /*List<Map.Entry<SymbolTable<CXIdentifier, TypeAugmentedSemanticNode>.Key, TypeAugmentedSemanticNode>> entries =
                new ArrayList<>(this.symbols.entrySet());
                
         */
        useThisStack.push(false);
        Queue<Map.Entry<SymbolTable<CXIdentifier, TypeAugmentedSemanticNode>.Key, TypeAugmentedSemanticNode>> queue =
                new ArrayDeque<>(this.symbols.entrySet());
        
        while (!queue.isEmpty()) {
            Map.Entry<SymbolTable<CXIdentifier, TypeAugmentedSemanticNode>.Key, TypeAugmentedSemanticNode> symbol = queue.poll();
    
    
            if (symbol.getValue().getASTType() == ASTNodeType.function_definition) {
        
            } else if (symbol.getValue().getASTType() != ASTNodeType.constructor_definition) {
                // is a value;
                if (symbol.getValue().getTreeType() != ASTNodeType.empty) {
                    try {
                        Instance<?> newInstance = createNewInstance(symbol.getKey().getType());
                        logger.info("Generating usable value for " + symbol.getKey());
                        if (!invoke(symbol.getValue())) throw new IllegalStateException();
                        if(memStack.peek() == null) {
                            logger.info("No usable value for " + symbol.getKey() + " created...");
                            logger.info("Will retry later");
                            queue.offer(symbol);
                            continue;
                        }
                        logger.fine("Added " + symbol.getKey().getType() + " " + symbol.getKey().getToken() + " with " +
                                "value " + memStack.peek());
                        addAutoVariable(symbol.getKey().getToken().getImage(), newInstance);
                        newInstance.copyFrom(pop());
                    } catch (FunctionReturned functionReturned) {
                        throw new IllegalStateException();
                    }
                } else {
                    Instance<?> newInstance = createNewInstance(symbol.getKey().getType());
                    logger.fine("Added " + symbol.getKey().getToken() + " with default value " + newInstance);
                    addAutoVariable(symbol.getKey().getToken().getImage(),
                            newInstance);
                }
            }
            
        }
        /*
        for (Map.Entry<SymbolTable<CXIdentifier, TypeAugmentedSemanticNode>.Key, TypeAugmentedSemanticNode> symbol : this.symbols) {
            if (symbol.getValue().getASTType() == ASTNodeType.function_definition) {
                
            } else if (symbol.getValue().getASTType() != ASTNodeType.constructor_definition) {
                // is a value;
                if (symbol.getValue().getTreeType() != ASTNodeType.empty) {
                    try {
                        Instance<?> newInstance = createNewInstance(symbol.getKey().getType());
                        logger.info("Generating usable value for " + symbol.getKey());
                        if (!invoke(symbol.getValue())) throw new IllegalStateException();
                        logger.fine("Added " + symbol.getKey().getType() + " " + symbol.getKey().getToken() + " with " +
                                "value " + memStack.peek());
                        addAutoVariable(symbol.getKey().getToken().getImage(), newInstance);
                        newInstance.copyFrom(pop());
                    } catch (FunctionReturned functionReturned) {
                        throw new IllegalStateException();
                    }
                } else {
                    Instance<?> newInstance = createNewInstance(symbol.getKey().getType());
                    logger.fine("Added " + symbol.getKey().getToken() + " with default value " + newInstance);
                    addAutoVariable(symbol.getKey().getToken().getImage(),
                            newInstance);
                }
            }
        }
        
         */
        globalAutoVariables = autoVariables.peek();
    }
    
    protected Instance<?> getInstance(TypeAugmentedSemanticNode node) {
        switch (node.getASTType()) {
            case id: {
                String name = node.getToken().getImage(); /*
                for (int i = autoVariables.size() - 1; i >= 0; i--) {
                    if (autoVariables.get(i).containsKey(name)) {
                        return autoVariables.get(i).get(name);
                    }
                }
                */
                if(name.equals("this") && useThisStack.peek()) {
                    
                    return thisStack.peek();
                }
                return autoVariables.peek().get(name);
            }
            case literal: {
                AbstractCXPrimitiveType primitiveType = (AbstractCXPrimitiveType) node.getCXType();
                if (primitiveType.isFloatingPoint()) {
                    Double d = Double.parseDouble(node.getToken().getImage());
                    return createNewInstance(primitiveType, d);
                } else if (primitiveType.isChar()) {
                    char c = node.getToken().getImage().charAt(1);
                    return createNewInstance(primitiveType, c);
                } else if (primitiveType.isIntegral()) {
                    long l = Long.parseLong(node.getToken().getImage());
                    return createNewInstance(primitiveType, l);
                }
            }
            case string: {
                String image = node.getToken().getImage();
                return createCharPointerFromString(image.substring(1, image.length() - 1));
            }
        }
        return null;
    }
    
    private class FunctionReturned extends Throwable {
        
    }
    
    private TypeAugmentedSemanticNode getSymbol(String s) {
        return symbols.get(new CXIdentifier(new Token(TokenType.t_id, s), false));
    }
    
    public <T extends CXType> ArrayInstance<T, ArrayType> createArray(T type, int size) {
        return new ArrayInstance<>(new ArrayType(type), type, size);
    }
    
    public ArrayInstance<CXType, ArrayType> createArrayOfType(CXType type, int size) {
        return new ArrayInstance<>(new ArrayType(type), type, size);
    }
    
    
    public ArrayInstance<? extends CXType, ArrayType> createArray(ArrayType type, List<Integer> sizes) {
        if(sizes.size() == 1) return createArray(type.getBaseType(), sizes.get(0));
        int thisSize = sizes.get(0);
        List<Integer> restSizes = sizes.subList(1, sizes.size());
        ArrayList<NullableInstance<ArrayType, ArrayInstance<?, ArrayType>>> subArrays = new ArrayList<>();
        for (int i = 0; i < thisSize; i++) {
            subArrays.set(i, new NullableInstance<>(((ArrayType) type.getBaseType()),
                    createArray((ArrayType) type.getBaseType(),
                    restSizes)));
        }
        ArrayInstance<ArrayType, ArrayType> output = new ArrayInstance<>(type, (ArrayType) type.getBaseType(),
                thisSize);
        for (int i = 0; i < thisSize; i++) {
            output.setAt(i, subArrays.get(i).value);
        }
        return output;
    }
    
    
    
    
    public ArrayInstance<CXPrimitiveType, ArrayType> createCharArrayFromString(String s) {
        ArrayInstance<CXPrimitiveType, ArrayType> output = new ArrayInstance<>(new ArrayType(CXPrimitiveType.CHAR),
                CXPrimitiveType.CHAR,
                s.length() + 1);
        for (int i = 0; i < output.size() - 1; i++) {
            output.setAt(
                    i,
                    createNewInstance(CXPrimitiveType.CHAR, s.charAt(i))
            );
        }
        output.setAt(output.size - 1, null);
        logger.fine("Created Jodin-Style string " + output);
        return output;
    }
    
    public PointerInstance<CXPrimitiveType> createCharPointerFromString(String s) {
        return createCharArrayFromString(s).asPointer();
    }
    
    
    
    public void logCurrentState() {
        if(System.getenv("DEBUG") != null) {
            if (disableLogging) return;
            disableLogging = true;
            var logger = ICompilationSettings.interpreterStateLogger;
            logger.finest("WHILE EXECUTING AT " + nearestCurrentToken.getFilename() + "::" + nearestCurrentToken.getActualLineNumber());
            int indent = 0;
            for (StackTraceInfo stackTraceInfo : new LinkedList<>(stackTrace)) {
                logger.finest("   ".repeat(indent) + "Frame = " + stackTraceInfo.function.getImage());
                if (indent < useThisStack.size() && useThisStack.get(indent)) {
                    String msg = "   ".repeat(indent) + "   + " + String.format("%-15s = %s", "this",
                            thisStack.peek());
                    logger.finest(msg);
                    int eqIndex = msg.indexOf('=');
            
                    int thisStackIndex = 0;
                    for (int i = 0; i < indent; i++) {
                        if (useThisStack.get(i)) {
                            ++thisStackIndex;
                        }
                    }
            
            
                    PointerInstance<CXClassType> thisInstance = thisStack.get(thisStackIndex);
                    CompoundInstance<CXClassType> pointer = (CompoundInstance<CXClassType>) thisInstance.getPointer();
                    for (Map.Entry<String, Instance<?>> stringInstanceEntry : pointer.fields.entrySet()) {
                        logger.finest(" ".repeat(eqIndex) + "   + " + String.format("%-15s = %s", stringInstanceEntry.getKey(),
                                stringInstanceEntry.getValue()));
                    }
                }
                for (Map.Entry<String, Instance<?>> instanceEntry : stackTraceInfo.getStackVariables().entrySet()) {
                    String msg = "   ".repeat(indent) + "   + " + String.format("%-15s = %s", instanceEntry.getKey(),
                            instanceEntry.getValue());
                    logger.finest(msg);
                    int eqIndex = msg.indexOf('=');
            
                    if (instanceEntry.getValue() instanceof PointerInstance && ((PointerInstance<?>) instanceEntry.getValue()).getSubType() instanceof CXClassType) {
                        PointerInstance<CXClassType> value = (PointerInstance<CXClassType>) instanceEntry.getValue();
                        if (value.getPointer() == null) {
                            continue;
                        }
                        try {
                            callMethod(value, "toString");
                        } catch (Throwable e) {
                            continue;
                        }
                        PointerInstance<CXClassType> string = (PointerInstance<CXClassType>) pop();
                        callMethod(string, "getCStr");
                        PointerInstance<CXPrimitiveType> cString = (PointerInstance<CXPrimitiveType>) pop();
                        logger.finest(" ".repeat(eqIndex) + "  toString() = " + cString);
                    } else if (instanceEntry.getValue() instanceof ArrayInstance && !(instanceEntry.getValue() instanceof PointerInstance)) {
                        var backingValue = ((ArrayInstance<?, ?>) instanceEntry.getValue()).getBackingValue();
                        for (int i = 0; i < backingValue.size(); i++) {
                            logger.finest(" ".repeat(eqIndex) + "  [" + i + "]" + " " + backingValue.get(i));
                        }
                    } else if (instanceEntry.getValue() instanceof CompoundInstance) {
                        CompoundInstance<?> value = (CompoundInstance<?>) instanceEntry.getValue();
                        for (Map.Entry<String, Instance<?>> stringInstanceEntry : value.fields.entrySet()) {
                            logger.finest(" ".repeat(eqIndex) + "   + " + String.format("%-15s = %s", stringInstanceEntry.getKey(),
                                    stringInstanceEntry.getValue()));
                        }
                    }
                }
        
                ++indent;
            }
    
            logger.finest("Memory Stack: ");
            for (Instance<?> instance : memStack) {
                logger.finest(" + " + instance);
            }
    
            // logger.finest("Return Value: " + returnValue);
            disableLogging = false;
        }
    }
    
    
    public int run(String[] args) {
        try {
            createClosure();
            
            TypeAugmentedSemanticNode main = getSymbol("start");
            push(createNewInstance(CXPrimitiveType.INTEGER, args.length));
            ArrayInstance<PointerType, ArrayType> argv = createArray(CXPrimitiveType.CHAR.toPointer(), args.length);
            for (int i = 0; i < args.length; i++) {
                argv.setAt(i, createCharPointerFromString(args[i]));
            }
            push(argv);
            startStackTraceFor(new Token(TokenType.t_id, "start"));
            if (!invoke(main)) return -1;
            stackTrace.pop();
            endClosure();
        } catch (FunctionReturned e) {
            return ((PrimitiveInstance<Number, CXPrimitiveType>) returnValue).backingValue.intValue();
        } catch (Throwable e) {
            System.err.println("\nError " + e.toString() + " thrown (in jodin):");
            logCurrentState();
            StackTraceInfo peek = stackTrace.peek();
            Token function = new Token(TokenType.t_id, peek.function.getImage());
            if(nearestCurrentToken != null) {
                function.setFilename(nearestCurrentToken.getFilename());
                function.setActualLineNumber(nearestCurrentToken.getActualLineNumber());
            }
            System.err.println("\tat " + new StackTraceInfo(function));
            while (!stackTrace.empty()) {
                StackTraceInfo s = stackTrace.pop();
                System.err.println("\tat " + s);
            }
            e.printStackTrace();
        }
        return -1;
    }
    
    private Instance<?> opOnObjects(TokenType op, Instance<?> lhs, Instance<?> rhs) {
        switch (op) {
            case t_lte:
                return createNewInstance(CXPrimitiveType.CHAR, memStack.indexOf(lhs) <= memStack.indexOf(rhs) ? 1 : 0);
            case t_gte:
                return createNewInstance(CXPrimitiveType.CHAR, memStack.indexOf(lhs) >= memStack.indexOf(rhs) ? 1 : 0);
            //return lhs >= rhs? 1 : 0;
            case t_eq:
                return createNewInstance(CXPrimitiveType.CHAR, lhs.equals(rhs) ? 1 : 0);
            // return lhs == rhs? 1 : 0;
            case t_neq:
                return createNewInstance(CXPrimitiveType.CHAR, !lhs.equals(rhs) ? 1 : 0);
            case t_lt:
                return createNewInstance(CXPrimitiveType.CHAR, memStack.indexOf(lhs) < memStack.indexOf(rhs) ? 1 : 0);
            
            case t_gt:
                return createNewInstance(CXPrimitiveType.CHAR, memStack.indexOf(lhs) > memStack.indexOf(rhs) ? 1 : 0);
            
            default:
                throw new UnsupportedOperationException();
        }
    }
    
    private Number opOnFloatingPoint(TokenType op, double lhs, double rhs) {
        switch (op) {
            case t_dand:
                return lhs != 0 && rhs != 0 ? 1 : 0;
            case t_dor:
                return lhs != 0 || rhs != 0 ? 1 : 0;
            case t_lte:
                return lhs <= rhs ? 1 : 0;
            case t_gte:
                return lhs >= rhs ? 1 : 0;
            case t_eq:
                return lhs == rhs ? 1 : 0;
            case t_neq:
                return lhs != rhs ? 1 : 0;
            case t_minus:
                return lhs - rhs;
            case t_add:
                return lhs + rhs;
            case t_star:
                return lhs * rhs;
            case t_fwslash:
                return lhs / rhs;
            case t_percent:
                return lhs % rhs;
            case t_lt:
                return lhs < rhs ? 1 : 0;
            case t_gt:
                return lhs > rhs ? 1 : 0;
            default:
                throw new UnsupportedOperationException();
        }
    }
    
    private Number opOnIntegral(TokenType op, long lhs, long rhs) {
        switch (op) {
            case t_dand:
                return lhs != 0 && rhs != 0 ? 1 : 0;
            case t_dor:
                return lhs != 0 || rhs != 0 ? 1 : 0;
            case t_lte:
                return lhs <= rhs ? 1 : 0;
            case t_gte:
                return lhs >= rhs ? 1 : 0;
            case t_eq:
                return lhs == rhs ? 1 : 0;
            case t_neq:
                return lhs != rhs ? 1 : 0;
            case t_minus:
                return lhs - rhs;
            case t_add:
                return lhs + rhs;
            case t_star:
                return lhs * rhs;
            case t_fwslash:
                return lhs / rhs;
            case t_percent:
                return lhs % rhs;
            case t_lt:
                return lhs < rhs ? 1 : 0;
            case t_gt:
                return lhs > rhs ? 1 : 0;
            case t_lshift:
                return lhs << rhs;
            case t_rshift:
                return lhs >> rhs;
            case t_bar:
                return lhs | rhs;
            case t_and:
                return lhs & rhs;
            case t_crt:
                return lhs ^ rhs;
            default:
                throw new UnsupportedOperationException();
        }
    }
    
    private Token closestToken(TypeAugmentedSemanticNode node) {
        Token firstToken = node.findFirstToken();
        if(firstToken == null) {
            return closestToken(node.getParent());
        }
        return firstToken;
    }
    
    
    public Boolean invoke(TypeAugmentedSemanticNode input) throws FunctionReturned {
        logger.info("Executing " + input);
        nearestCurrentToken = closestToken(input);
        switch (input.getASTType()) {
            case operator:
                break;
            case binop: {
                Token op = input.getChild(0).getToken();
                if (!invoke(input.getChild(1))) return false;
                Instance<?> lhsNull = pop();
                if (!invoke(input.getChild(2))) return false;
                Instance<?> rhsNull = pop();
                
                
                
                PrimitiveInstance<?, ?> lhs, rhs;
                if(lhsNull instanceof NullableInstance) {
                    lhs = (PrimitiveInstance<?, ?>) ((NullableInstance) lhsNull).getValue();
                } else if(lhsNull instanceof ArrayInstance) {
                    // in binary operations, treat arrays as pointers
                    lhs = ((ArrayInstance) lhsNull).asPointer();
                } else {
                    lhs = (PrimitiveInstance<?, ?>) lhsNull;
                }
                
                if(rhsNull instanceof NullableInstance) {
                    rhs = (PrimitiveInstance<?, ?>) ((NullableInstance) rhsNull).getValue();
                }else if(rhsNull instanceof ArrayInstance) {
                    // in binary operations, treat arrays as pointers
                    rhs = ((ArrayInstance) rhsNull).asPointer();
                }  else {
                    rhs = (PrimitiveInstance<?, ?>) rhsNull;
                }
                
                
                logger.fine("Performing " + op + " on " + lhs + " and " + rhs);
                
                
                
                Instance<?> createdValue;
                if (lhs instanceof PointerInstance && rhs instanceof PointerInstance) {
                    logger.finer("Comparing two pointers");
                    createdValue = opOnObjects(op.getType(), lhs, rhs);
                } else if (
                        (lhs == null ||
                                lhs.getBackingValue() == null) &&
                                rhs.getType().isIntegral() &&
                                (
                                        (rhs.backingValue instanceof Number && ((Number) rhs.backingValue).longValue() == 0) ||
                                                rhs.backingValue instanceof Character && ((Character) rhs.backingValue).charValue() == 0)
                ) {
                    if(rhs.backingValue instanceof Character) {
                        logger.finer("Comparing a character to a ptr");
                    } else {
                        logger.finer("Comparing an integral to a ptr");
                    }
                    createdValue = new PrimitiveInstance<>(LongPrimitive.create(), opOnIntegral(
                            op.getType(),
                            0L,
                            ((Number) rhs.backingValue).longValue()
                    ), rhs.unsigned);
                    // createdValue = null;
                } else if ((rhs == null ||
                        rhs.getBackingValue() == null) && lhs.getType().isIntegral() && ((Number) lhs.backingValue).longValue() == 0) {
                    logger.finer("Comparing a ptr to an integral");
                    createdValue = new PrimitiveInstance<>(LongPrimitive.create(), opOnIntegral(
                            op.getType(),
                            ((Number) lhs.backingValue).longValue(),
                            0L
                    ),
                            lhs.unsigned);
                } else if (lhs.getType().isFloatingPoint()) {
                    logger.finer("Operation is on floating points");
                    createdValue = new PrimitiveInstance<>(lhs.getType(),
                            opOnFloatingPoint(op.getType(), ((Number) lhs.backingValue).doubleValue(),
                                    ((Number) rhs.backingValue).doubleValue()),
                            lhs.unsigned);
                } else {
                    logger.finer("Operation is on integrals");
                    try {
                        if(lhs.getType() == CXPrimitiveType.CHAR) {
                            PrimitiveInstance<Number, ?> lhsCasted =
                                    (PrimitiveInstance<Number, ?>) lhs.castTo(CXPrimitiveType.INTEGER);
                            PrimitiveInstance<Number, ?> rhsCasted =
                                    (PrimitiveInstance<Number, ?>) rhs.castTo(CXPrimitiveType.INTEGER);
                            
                            var mid = new PrimitiveInstance<>(lhsCasted.getType(), opOnIntegral(op.getType(),
                                    lhsCasted.getBackingValue().longValue(),
                                    rhsCasted.getBackingValue().longValue()),
                                    lhs.unsigned);
                            createdValue = mid.castTo(CXPrimitiveType.CHAR);
                        }else {
                            PrimitiveInstance<Number, ?> casted = (PrimitiveInstance<Number, ?>) rhs.castTo(lhs.getType());
                            
                            createdValue = new PrimitiveInstance<>(lhs.getType(), opOnIntegral(op.getType(),
                                    ((Number) lhs.backingValue).longValue(),
                                    casted.getBackingValue().longValue()),
                                    lhs.unsigned);
                        }
                    } catch (InvalidPrimitiveException e) {
                        return false;
                    }
                }
                push(createdValue);
                
            }
            break;
            case uniop: {
                if (!invoke(input.getChild(1))) return false;
                Instance<?> pop = pop();
                switch (input.getChild(0).getToken().getType()) {
                    case t_inc: {
                        if (pop instanceof PointerInstance) {
                            ((PointerInstance) pop).index += 1;
                        } else if (pop instanceof PrimitiveInstance) {
                            if (((PrimitiveInstance<?, ?>) pop).getBackingValue() instanceof Character) {
                                ((PrimitiveInstance<Character, ?>) pop).setBackingValue((char) (((Character) ((PrimitiveInstance) pop).getBackingValue()).charValue() + 1));
                            } else {
                                ((PrimitiveInstance<Number, ?>) pop).setBackingValue(opOnIntegral(TokenType.t_add,
                                        ((PrimitiveInstance<Number, ?>) pop).backingValue.longValue(), 1));
                            }
                        }
                    }
                    break;
                    case t_dec: {
                        if (pop instanceof PointerInstance) {
                            ((PointerInstance) pop).index -= 1;
                        } else if (pop instanceof PrimitiveInstance) {
                            if (((PrimitiveInstance<?, ?>) pop).getBackingValue() instanceof Character) {
                                ((PrimitiveInstance<Character, ?>) pop).setBackingValue((char) (((Character) ((PrimitiveInstance) pop).getBackingValue()).charValue() - 1));
                            } else {
                                ((PrimitiveInstance<Number, ?>) pop).setBackingValue(opOnIntegral(TokenType.t_minus,
                                        ((PrimitiveInstance<Number, ?>) pop).backingValue.longValue(), 1));
                            }
                        }
                    }
                    case t_add:
                        break;
                    case t_minus:
                        if (pop instanceof PrimitiveInstance) {
                            if (((PrimitiveInstance<?, ?>) pop).getBackingValue() instanceof Character) {
                                ((PrimitiveInstance<Character, ?>) pop).setBackingValue((char) (((Character) ((PrimitiveInstance) pop).getBackingValue()).charValue() * -1));
                            } else {
                                ((PrimitiveInstance<Number, ?>) pop).setBackingValue(opOnIntegral(TokenType.t_star,
                                        ((PrimitiveInstance<Number, ?>) pop).backingValue.longValue(), -1));
                            }
                        }
                        break;
                    case t_not:
                        if (pop instanceof PrimitiveInstance) {
                            if (((PrimitiveInstance<?, ?>) pop).getBackingValue() instanceof Character) {
                                ((PrimitiveInstance<Character, ?>) pop).setBackingValue((char) (~((Character) ((PrimitiveInstance) pop).getBackingValue()).charValue()));
                            } else {
                                // TODO: Implement this
                                
                            }
                        }
                        break;
                    case t_bang:
                        if (pop instanceof PrimitiveInstance) {
                            if (((PrimitiveInstance<?, ?>) pop).getBackingValue() instanceof Character) {
                                ((PrimitiveInstance<Character, ?>) pop).setBackingValue((char) (~((Character) ((PrimitiveInstance) pop).getBackingValue()).charValue()));
                            } else {
                                if (((PrimitiveInstance<?, ?>) pop).getBackingValue() instanceof Double) {
                                
                                } else {
                                    ((PrimitiveInstance<Number, ?>) pop).setBackingValue(opOnIntegral(TokenType.t_eq,
                                            ((PrimitiveInstance<Number, ?>) pop).backingValue.longValue(), 0));
                                }
                            }
                        }
                        break;
                }
                push(pop);
            }
            break;
            case declaration: {
                String id = input.getASTChild(ASTNodeType.id).getToken().getImage();
                CXType cxType = ((TypedAbstractSyntaxNode) input.getASTNode()).getCxType();
                if(cxType instanceof ArrayType && !(cxType instanceof PointerType)) {
                    MultiDimensionalArrayWithSizeTag compilationTag = input.getCompilationTag(MultiDimensionalArrayWithSizeTag.class);
                    List<Integer> sizes = new LinkedList<>();
                    for (TypeAugmentedSemanticNode expression : compilationTag.getExpressions()) {
                        if(!invoke(expression)) return false;
                        PrimitiveInstance<Number, ?> pop = ((PrimitiveInstance<Number, ?>) pop());
                        sizes.add(pop.backingValue.intValue());
                    }
    
                    ArrayInstance<?, ArrayType> array = createArray(((ArrayType) cxType), sizes);
                    logger.info("Created a " + cxType + " array of size " + array.size);
                    addAutoVariable(id, array);
                    logCurrentState();
                } else {
                    addAutoVariable(id, createNewInstance(cxType));
                }
            }
            break;
            case assignment: {
                if (!invoke(input.getChild(0))) return false;
                // LHS should be pushed
                Instance<?> lhs = pop();
                if (!invoke(input.getChild(2))) return false;
                Instance<?> rhs = pop().copy();
                
                Token assignmentToken = input.getASTChild(ASTNodeType.assignment_type).getToken();
                logger.fine("Assigning " + rhs + " to " + lhs + " using " + assignmentToken);
                if (assignmentToken.getType() == TokenType.t_assign) {
                    lhs.copyFrom(rhs);
                } else if (assignmentToken.getType() == TokenType.t_operator_assign) {
                
                } else return false;
    
                logCurrentState();
                
                break;
            }
            case assignment_type:
                break;
            case ternary:
                break;
            case array_reference: {
                if (!invoke(input.getChild(0))) return false;
                ArrayInstance<?, ?> arr = (ArrayInstance<?,?>) pop();
                if (!invoke(input.getChild(1))) return false;
                PrimitiveInstance<Number, ?> index = (PrimitiveInstance<Number, ?>) pop();
                logger.info("Getting at index " + index + " of " + arr);
                push(arr.getAt(index.backingValue.intValue()));
            }
            break;
            case postop: {
                if(!invoke(input.getChild(0))) return false;
                Instance<?> pop = pop();
                Instance<?> output = pop.copy();
                switch (input.getChild(1).getToken().getType()) {
                    case t_inc: {
                        if(pop instanceof PointerInstance) {
                            ((PointerInstance) pop).index += 1;
                        } else if (pop instanceof PrimitiveInstance) {
                            if(((PrimitiveInstance<?, ?>) pop).getBackingValue() instanceof Character) {
                                ((PrimitiveInstance<Character, ?>) pop).setBackingValue((char) (((Character) ((PrimitiveInstance) pop).getBackingValue()).charValue() + 1));
                            } else {
                                ((PrimitiveInstance<Number, ?>) pop).setBackingValue(opOnIntegral(TokenType.t_add,
                                        ((PrimitiveInstance<Number, ?>) pop).backingValue.longValue(), 1));
                            }
                        }
                    }
                    break;
                    case t_dec: {
                        if(pop instanceof PointerInstance) {
                            ((PointerInstance) pop).index -= 1;
                        } else if (pop instanceof PrimitiveInstance) {
                            if(((PrimitiveInstance<?, ?>) pop).getBackingValue() instanceof Character) {
                                ((PrimitiveInstance<Character, ?>) pop).setBackingValue((char) (((Character) ((PrimitiveInstance) pop).getBackingValue()).charValue() - 1));
                            } else {
                                ((PrimitiveInstance<Number, ?>) pop).setBackingValue(opOnIntegral(TokenType.t_minus,
                                        ((PrimitiveInstance<Number, ?>) pop).backingValue.longValue(), 1));
                            }
                        }
                    }
                    break;
                }
                push(output);
            }
            break;
            case literal:
                push(getInstance(input));
                break;
            case id:
                push(getInstance(input));
                break;
            case string:
                push(getInstance(input));
                break;
            case sequence:
                for (TypeAugmentedSemanticNode entry : input.getDirectChildren()) {
                    if (!invoke(entry)) return false;
                }
                break;
            case typename:
                break;
            case parameter_list:
                break;
            case function_call: {
               
                Token token = input.getASTChild(ASTNodeType.id).getToken();
                String funcCall = token.getImage();
                
                if (funcCall.equals("calloc")) {
                    if (!invoke(input.getASTChild(ASTNodeType.sequence))) return false;
                    startStackTraceFor(token);
                    CXType cxType =
                            ((TypedAbstractSyntaxNode) input.getASTChild(ASTNodeType.sequence).getASTChild(ASTNodeType.sizeof).getASTNode()).getCxType();
                    PrimitiveInstance<Number, ?> size = (PrimitiveInstance<Number, ?>) pop();
                    logger.info("Using simulated Calloc to creating an array of " + cxType + "...");
                    push(createArrayOfType(cxType, size.backingValue.intValue()));
                    logger.info("Array of " + cxType + "created with size " + size.backingValue.intValue() + " => " + memStack.peek());
                    logCurrentState();
                    stackTrace.pop();
                    break;
                } else if(funcCall.equals("free")) {
                    if (!invoke(input.getASTChild(ASTNodeType.sequence))) return false;
                    startStackTraceFor(token);
                    PointerInstance<?> pop = (PointerInstance<?>) pop();
                    logger.info("freeing object " + pop);
                    pop.setPointer(null);
                    stackTrace.pop();
                    break;
                } else if(funcCall.equals("printf")) {
                    if (!invoke(input.getASTChild(ASTNodeType.sequence))) return false;
                    startStackTraceFor(token);
                    PointerInstance<CXPrimitiveType> pop = (PointerInstance<CXPrimitiveType>) pop();
                    while (pop.getPointer() != null) {
                        PrimitiveInstance<Character, ?> pointer = (PrimitiveInstance<Character, ?>) pop.getPointer();
                        if(pointer.backingValue == '\\') {
                            pop = pop.getPointerOfOffset(1);
                            char escape = ((PrimitiveInstance<Character, ?>) pop.getPointer()).backingValue;
                            switch (escape) {
                                case 'n': {
                                    System.out.println();
                                    break;
                                }
                                case 't': {
                                    System.out.print('\t');
                                    break;
                                }
                                case 'r': {
                                    System.out.print('\r');
                                    break;
                                }
                                case '\\': {
                                    System.out.print('\\');
                                    break;
                                }
                                case '\'': {
                                    System.out.print('\'');
                                    break;
                                }
                                case '\"': {
                                    System.out.print('\"');
                                    break;
                                }
                                case '?': {
                                    System.out.print('?');
                                    break;
                                }
                            }
                        }else {
                            System.out.print(pointer.backingValue);
                        }
                        pop = pop.getPointerOfOffset(1);
                    }
                    stackTrace.pop();
                    logCurrentState();
                    break;
                }
                
                
                TypeAugmentedSemanticNode function = getSymbol(input.getASTChild(ASTNodeType.id).getToken().getImage());
                
                
                if (function == null) {
                    
                    throw new CompilationError("Symbol " + funcCall + " not " +
                            "defined", input.getASTChild(ASTNodeType.id).getToken());
                } else {
                    if (!invoke(input.getASTChild(ASTNodeType.sequence))) return false;
                    useThisStack.push(false);
                    startStackTraceFor(token);
                    logCurrentState();
                    try {
                        logger.info("Calling function: " + input.getASTChild(ASTNodeType.id).getToken().getImage());
                        if (!invoke(function)) return false;
                    } catch (FunctionReturned functionReturned) {
                        if (returnValue != null) {
                            push(returnValue);
                        }
                        returnValue = null;
                    }
                }
                logCurrentState();
                stackTrace.pop();
                useThisStack.pop();
                
            }
            break;
            case method_call:
                // owner is pushed to stack
            {
                
                if (!invoke(input.getChild(0))) return false;
                CompoundInstance<CXClassType> classTypeInstance = ((CompoundInstance<CXClassType>) pop());
                
                
                Token idToken = input.getASTChild(ASTNodeType.id).getToken();
                int memstackPrevious = memStack.size();
                int parameters = input.getASTChild(ASTNodeType.sequence).getChildren().size();
                if (!invoke(input.getASTChild(ASTNodeType.sequence))) return false;
    
                thisStack.push(classTypeInstance.toPointer());
                useThisStack.push(true);
    
                createClosure();
                Stack<CXType> types = new Stack<>();
                Stack<Instance<?>> instances = new Stack<>();
                for (int i = 0; i < parameters; i++) {
                    Instance<?> pop = pop();
                    types.push(pop.getType());
                    instances.push(pop);
                }
                for (Instance<?> instance : instances) {
                    memStack.push(instance);
                }
                TypeAugmentedSemanticNode method = dynamicMethodLookup(classTypeInstance.getType(), idToken, types);
                if(method == null) {
                    throw new Error("Method "+ classTypeInstance.getType() + "::" + idToken.getImage() + types + " not " +
                            "defined");
                }
                startStackTraceFor(classTypeInstance.getType() + "::" + idToken.getImage(), idToken);
                logCurrentState();
                try {
                    if (!invoke(method)) return false;
                    endClosure();
                } catch (FunctionReturned functionReturned) {
                    endClosure();
                    push(returnValue);
                    returnValue = null;
                   
                }
                logCurrentState();
                stackTrace.pop();
                thisStack.pop();
                useThisStack.pop();
               
            }
            break;
            case field_get:
                if (!invoke(input.getChild(0))) return false;
                // owner on stack
            {
                Instance<?> pop = pop();
                CompoundInstance<?> compoundInstance = (CompoundInstance<?>) pop;
                String field = input.getChild(1).getToken().getImage();
                
                // push field
                push(compoundInstance.get(field));
            }
            break;
            case if_cond: {
                if (!invoke(input.getChild(0))) return false;
                PrimitiveInstance<?, ?> pop = (PrimitiveInstance<Number, ?>) pop();
                boolean cond;
                if (pop.getBackingValue() instanceof Double || pop.getBackingValue() instanceof Float) {
                    cond = ((Number) pop.getBackingValue()).doubleValue() != 0;
                } else if(pop.getBackingValue() instanceof Character) {
                    cond = ((Character) pop.getBackingValue()).charValue() != (char) 0;
                }else {
                    cond = ((Number) pop.getBackingValue()).intValue() != 0;
                }
                if (cond) {
                    logger.fine("Using true branch for if statement");
                    if (!invoke(input.getChild(1))) return false;
                } else if (input.getChild(2).getASTType() != ASTNodeType.empty) {
                    logger.fine("Using else branch for if statement");
                    if (!invoke(input.getChild(2))) return false;
                } else {
                    logger.fine("No branch for if statement");
                }
            }
            break;
            case while_cond:
                while (true) {
                    if (!invoke(input.getChild(0))) return false;
                    PrimitiveInstance<?, ?> pop = (PrimitiveInstance<Number, ?>) pop();
                    boolean cond;
                    if (pop.getBackingValue() instanceof Double || pop.getBackingValue() instanceof Float) {
                        cond = ((Number) pop.getBackingValue()).doubleValue() != 0;
                    } else if (pop.getBackingValue() instanceof Character) {
                        cond = ((Character) pop.getBackingValue()).charValue() != '\0';
                    } else {
                        cond = ((Number) pop.getBackingValue()).intValue() != 0;
                    }
                    if (!cond) break;
                    if (!invoke(input.getChild(1))) return false;
                }
                
                break;
            case do_while_cond:
                while (true) {
                    if (!invoke(input.getChild(0))) return false;
                    if (!invoke(input.getChild(1))) return false;
                    PrimitiveInstance<Number, ?> pop = (PrimitiveInstance<Number, ?>) pop();
                    boolean cond;
                    if (pop.getBackingValue() instanceof Double || pop.getBackingValue() instanceof Float) {
                        cond = pop.getBackingValue().doubleValue() != 0;
                    } else {
                        cond = pop.getBackingValue().intValue() != 0;
                    }
                    if (!cond) break;
                }
                break;
            case for_cond: {
                startLexicalScope();
                if (!invoke(input.getChild(0))) return false;
                while (true) {
                    if (!invoke(input.getChild(1))) return false;
                    PrimitiveInstance<Number, ?> pop = (PrimitiveInstance<Number, ?>) pop();
                    boolean cond;
                    if (pop.getBackingValue() instanceof Double || pop.getBackingValue() instanceof Float) {
                        cond = pop.getBackingValue().doubleValue() != 0;
                    } else {
                        cond = pop.getBackingValue().intValue() != 0;
                    }
                    if (!cond) break;
                    try {
                        if (!invoke(input.getChild(3))) return false;
                        
                    } catch (FunctionReturned e) {
                        endLexicalScope();
                        throw e;
                    }
                    if (!invoke(input.getChild(2))) return false;
                }
                endLexicalScope();
            }
            break;
            case _return:
                if (input.getChildren().size() > 0) {
                    if (!invoke(input.getChild(0))) return false;
                    returnValue = pop();
                    logger.fine("Function to return " + returnValue);
                }
                throw new FunctionReturned();
            case constructor_definition:
            case function_definition:
                createClosure();
                if (input.containsCompilationTag(PriorConstructorTag.class)) {
                    PriorConstructorTag prior = input.getCompilationTag(PriorConstructorTag.class);
                    startStackTraceFor(prior.getPriorConstructor().toString(), input.findFirstToken());
                    if (!invoke(prior.getSequence())) return false;
                    if (!invoke(MethodTASNTracker.getInstance().get(prior.getPriorConstructor()))) return false;
                    //push(classTypeInstance);
                    stackTrace.pop();
                }
                List<TypeAugmentedSemanticNode> parameters = input.getASTChild(ASTNodeType.parameter_list).getChildren();
                for (int i = parameters.size() - 1; i >= 0; i--) {
                    addAutoVariable(
                            parameters.get(i).getASTChild(ASTNodeType.id).getToken().getImage(),
                            pop().copy()
                    );
                }
                
                try {
                    if (!invoke(input.getASTChild(ASTNodeType.compound_statement))) return false;
                }
                catch (FunctionReturned e) {
                    endClosure();
                    throw e;
                }
                endClosure();
                break;
            case basic_compound_type_dec:
                break;
            case specifiers:
                break;
            case specifier:
                break;
            case qualifier:
                break;
            case qualifiers:
                break;
            case qualifiers_and_specifiers:
                break;
            case class_level_decs:
                break;
            case class_type_definition:
                break;
            case class_type_declaration:
                break;
            case class_type_name:
                break;
            case compound_type_reference:
                startLexicalScope();
                for (TypeAugmentedSemanticNode child : input.getChildren()) {
                    if (!invoke(child)) return false;
                }
                endLexicalScope();
                break;
            case typedef:
                break;
            case top_level_decs:
                break;
            case indirection:
                if (!invoke(input.getChild(0))) return false;
                
                Instance<?> og = pop();
                if (og instanceof PrimitiveInstance && !(og instanceof PointerInstance)) {
                    if (((PrimitiveInstance<Number, ?>) og).backingValue.doubleValue() == 0) {
                        throw new Error("Can't dereference a null pointer");
                    }
                } else if (og instanceof PointerInstance) {
                    if (((PointerInstance) og).getBackingValue() == null) {
                        throw new Error("Can't dereference a null pointer");
                    }
                }
                PointerInstance<?> pointerInstance = (PointerInstance<?>) og;
                logger.finer("Getting indirection of " + pointerInstance.getType() + " => " + pointerInstance.getPointer());
                push(pointerInstance.getPointer());
                break;
            case addressof:
                break;
            case cast: {
                if (!invoke(input.getChild(0))) return false;
                Instance<?> castingOn = pop();
                CXType castingTo = input.getCXType();
                try {
                    Instance<?> output = castingOn.castTo(castingTo);
                    push(output);
                } catch (InvalidPrimitiveException e) {
                    return false;
                }
                
            }
            break;
            case empty:
                break;
            case array_type:
                break;
            case pointer_type:
                break;
            case abstract_declarator:
                break;
            case struct:
                break;
            case union:
                break;
            case _class:
                break;
            case basic_compound_type_fields:
                break;
            case basic_compound_type_field:
                break;
            case declarations:
                for (TypeAugmentedSemanticNode child : input.getChildren()) {
                    if (!invoke(child)) return false;
                }
                break;
            case initialized_declaration:
                if (!invoke(input.getChild(0))) return false;
                String id = input.getChild(0).getASTChild(ASTNodeType.id).getToken().getImage();
                if (!invoke(input.getChild(1))) return false;
                Instance<CXType> autoVariable = getAutoVariable(id);
                autoVariable.copyFrom(pop());
                break;
            case compound_statement:
                startLexicalScope();
                for (TypeAugmentedSemanticNode directChild : input.getDirectChildren()) {
                    try {
                        if (!invoke(directChild)) return false;
                    } catch (FunctionReturned e) {
                        endLexicalScope();
                        throw e;
                    }
                }
                endLexicalScope();
                break;
            case sizeof:
                push(createNewInstance(LongPrimitive.create(), ((TypedAbstractSyntaxNode) input.getASTNode()).getCxType().getDataSize(environment)));
                break;
            case constructor_call: {
                
                CXClassType subType = (CXClassType) ((PointerType) input.getCXType()).getSubType();
                PointerInstance<CXClassType> classTypeInstance =
                        (PointerInstance<CXClassType>) createNewInstance(subType).toPointer();
                
               
                int memstackPrevious = memStack.size();
                if (!invoke(input.getASTChild(ASTNodeType.sequence))) return false;
                thisStack.push(classTypeInstance);
                useThisStack.push(true);
                createClosure();
                /*Stack<CXType> types = new Stack<>();
                Stack<Instance<?>> instances = new Stack<>();
                while (memStack.size() > memstackPrevious) {
                    Instance<?> pop = pop();
                    types.push(pop.getType());
                    instances.push(pop);
                }
                for (Instance<?> instance : instances) {
                    memStack.push(instance);
                }
                TypeAugmentedSemanticNode constructor =
                        dynamicConstructorLookup(((CXClassType) ((PointerType) input.getCXType()).getSubType()),
                        types);
                try {
                    if (!invoke(constructor)) return false;
                } catch (FunctionReturned functionReturned) {
                }
               
                 */
                //push(classTypeInstance);
                ConstructorCallTag compilationTag = input.getCompilationTag(ConstructorCallTag.class);
                CXConstructor cxConstructor = compilationTag.getConstructor();
                startStackTraceFor(cxConstructor.getParent().toString() + "::<init>", input.findFirstToken());
                logCurrentState();
                
                
               
                
                logger.info("Calling constructor for " + cxConstructor.getParent());
                TypeAugmentedSemanticNode cons = MethodTASNTracker.getInstance().get(cxConstructor);
                try {
                    if (!invoke(cons)) return false;
                } catch (FunctionReturned e) {
                
                }
    
                stackTrace.pop();
                thisStack.pop();
                useThisStack.pop();
                endClosure();
                push(classTypeInstance);
                logCurrentState();
            }
            break;
            case function_description:
                break;
            case visibility:
                break;
            case class_level_declaration:
                break;
            case _virtual:
                break;
            case _super:
                break;
            case inherit:
                break;
            case namespaced:
                break;
            case implement:
                break;
            case implementing:
                break;
            case using:
                break;
            case alias:
                break;
            case _import:
                break;
            case compilation_tag:
                break;
            case compilation_tag_list:
                break;
            case constructor_description:
                break;
            case typeid:
                break;
            case syntax:
                break;
            case _true:
                push(createNewInstance(CXPrimitiveType.CHAR, 1));
                break;
            case _false:
                push(createNewInstance(CXPrimitiveType.CHAR, 0));
                break;
            case ast:
                break;
            case generic:
                break;
            case trait:
                break;
            case id_list:
                break;
            case parameterized_types:
                break;
            case parameter_type:
                break;
        }
        return true;
    }
    
    public TypeAugmentedSemanticNode dynamicMethodLookup(CXClassType clazz, Token id, List<CXType> inputTypes) {
        ParameterTypeList parameterTypeList = new ParameterTypeList(inputTypes);
        CXMethod method = clazz.getMethod(id, parameterTypeList, null);
        if(method == null) return null;
        return MethodTASNTracker.getInstance().get(method);
    }
    
    public TypeAugmentedSemanticNode dynamicConstructorLookup(CXClassType clazz, List<CXType> inputTypes) {
        ParameterTypeList parameterTypeList = new ParameterTypeList(inputTypes);
        CXMethod method = clazz.getConstructor(parameterTypeList);
        return MethodTASNTracker.getInstance().get(method);
    }
}
