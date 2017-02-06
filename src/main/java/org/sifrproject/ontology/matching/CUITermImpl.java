package org.sifrproject.ontology.matching;

import org.getalp.lexsema.similarity.signatures.SemanticSignature;
import org.sifrproject.ontology.UMLSLanguageCode;

public class CUITermImpl implements CUITerm {

    private final String cui;
    private final String term;
    private final UMLSLanguageCode languageCode;

    private final SemanticSignature semanticSignature;

    public CUITermImpl(final String cui, final String term, final UMLSLanguageCode languageCode, final SemanticSignature semanticSignature) {
        this.cui = cui;
        this.term = term;
        this.languageCode = languageCode;
        this.semanticSignature = semanticSignature;
    }

    @Override
    public String getCUI() {
        return cui;
    }

    @Override
    public String getTerm() {
        return term;
    }

    @Override
    public UMLSLanguageCode getLanguageCode() {
        return languageCode;
    }

    @Override
    public SemanticSignature getSemanticSignature() {
        return semanticSignature;
    }
}
