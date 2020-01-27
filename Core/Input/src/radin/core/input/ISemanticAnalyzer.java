package radin.core.input;

import radin.core.chaining.IToolChain;
import radin.core.chaining.IToolChainHead;
import radin.core.errorhandling.ICompilationErrorCollector;
import radin.core.AbstractTree;
import radin.core.semantics.TypeEnvironment;

public interface ISemanticAnalyzer<P extends AbstractTree<? extends P>, S> extends IToolChain<P, S> {
    
    long getRunCount();
    
    void resetRunCount();
    
    S analyze(P tree);
    
    TypeEnvironment getEnvironment();
}
