package radin.typeanalysis.analyzers;

import radin.interphase.semantics.ASTNodeType;
import radin.interphase.semantics.types.CXType;
import radin.interphase.semantics.types.CXCompoundTypeNameIndirection;
import radin.interphase.semantics.types.TypeAbstractSyntaxNode;
import radin.interphase.semantics.types.compound.CXCompoundType;
import radin.typeanalysis.TypeAnalyzer;
import radin.typeanalysis.TypeAugmentedSemanticNode;
import radin.typeanalysis.errors.IncorrectTypeError;
import radin.typeanalysis.errors.TypeNotDefinedError;

public class StatementDeclarationTypeAnalyzer extends TypeAnalyzer {
    
    public StatementDeclarationTypeAnalyzer(TypeAugmentedSemanticNode tree) {
        super(tree);
    }
    
    @Override
    public boolean determineTypes(TypeAugmentedSemanticNode node) {
        assert node.getASTNode().getType() == ASTNodeType.declarations;
        
        for (TypeAugmentedSemanticNode declaration : node.getChildren()) {
            CXType declarationType;
            String name;
            
            if(declaration.getASTType() == ASTNodeType.declaration) {
                assert declaration.getASTNode() instanceof TypeAbstractSyntaxNode;
                
                declarationType =
                        ((TypeAbstractSyntaxNode) declaration.getASTNode()).getCxType().getTypeRedirection(getEnvironment());
                
                
                if(declarationType instanceof CXCompoundTypeNameIndirection) {
                    declarationType =
                            getEnvironment().getNamedCompoundType(((CXCompoundTypeNameIndirection) declarationType).getTypename());
                } else if(declarationType == null) {
                    throw new TypeNotDefinedError();
                }
                
                
                
                name = declaration.getASTChild(ASTNodeType.id).getToken().getImage();
                
            } else if(declaration.getASTType() == ASTNodeType.initialized_declaration) {
                
                // same process as prior but also checks to see if can place expression into type
                TypeAugmentedSemanticNode subDeclaration = declaration.getASTChild(ASTNodeType.declaration);
                declarationType =
                        ((TypeAbstractSyntaxNode) subDeclaration.getASTNode()).getCxType().getTypeRedirection(getEnvironment());
                if(declarationType instanceof CXCompoundTypeNameIndirection) {
                    declarationType =
                            getEnvironment().getNamedCompoundType(((CXCompoundTypeNameIndirection) declarationType).getTypename());
                } else if(declarationType == null) {
                    throw new TypeNotDefinedError();
                }
    
                name = subDeclaration.getASTChild(ASTNodeType.id).getToken().getImage();
                
                TypeAugmentedSemanticNode expression = declaration.getChild(1);
                ExpressionTypeAnalyzer analyzer = new ExpressionTypeAnalyzer(expression);
                if(!determineTypes(analyzer)) return false;
                
                if(!is(expression.getCXType(), declarationType)) throw new IncorrectTypeError(declarationType, expression.getCXType());
                //if(!expression.getCXType().is(declarationType, getEnvironment())) throw new IncorrectTypeError
                // (declarationType, expression.getCXType());
                
                
                
            } else {
                return false;
            }
            
            if(declarationType instanceof CXCompoundType) {
                CXCompoundType cxCompoundType = ((CXCompoundType) declarationType);
                if(!getCurrentTracker().isTracking(cxCompoundType)) {
                    getCurrentTracker().addBasicCompoundType(cxCompoundType);
                    getCurrentTracker().addIsTracking(cxCompoundType);
                }
            }
            
            
            
            
            getCurrentTracker().addVariable(name, declarationType);
        }
        
        
        return true;
    }
}