package org.sifrproject.ontology.matching;


import org.getalp.lexsema.similarity.signatures.SemanticSignature;
import org.sifrproject.ontology.umls.UMLSLanguageCode;

public interface CUITerm {
    String getCUI();
    String getTerm();
    UMLSLanguageCode getLanguageCode();
    SemanticSignature getSemanticSignature();
    void appendToSignature(String text);
    double getScore();

    void setScore(double score);
}
