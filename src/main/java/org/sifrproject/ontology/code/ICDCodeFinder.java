package org.sifrproject.ontology.code;


import org.sifrproject.ontology.cuis.CUIOntologyDelegate;
import org.sifrproject.ontology.prefix.OntologyPrefix;

import java.util.Collection;
import java.util.Iterator;

public class ICDCodeFinder implements CodeFinder {

    private final CUIOntologyDelegate ontologyDelegate;

    ICDCodeFinder(final CUIOntologyDelegate ontologyDelegate) {
        this.ontologyDelegate = ontologyDelegate;
    }

    @Override
    public String getCode(final String classURI) {
        final Collection<String> notations =
                ontologyDelegate.getObjectsThroughRelation
                        (classURI, OntologyPrefix.getURI("icd:cdCode"));

        String code = null;
        if (!notations.isEmpty()) {
            final Iterator<String> iterator = notations.iterator();
            code = iterator.next();
        }
        return code;
    }
}
