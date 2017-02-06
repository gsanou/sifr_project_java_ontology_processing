package org.sifrproject.ontology;


import org.sifrproject.ontology.matching.CUITerm;

import java.util.Collection;
import java.util.List;

public interface UMLSDelegate {
    Collection<String> getTUIsForCUIs(Collection<String> cuis);
    List<CUITerm> getCUIConceptNameMap(final UMLSLanguageCode languageCode);
}
