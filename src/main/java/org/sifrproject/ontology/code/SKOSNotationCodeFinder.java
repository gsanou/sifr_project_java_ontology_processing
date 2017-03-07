package org.sifrproject.ontology.code;


import org.sifrproject.ontology.cuis.CUIOntologyDelegate;
import org.sifrproject.ontology.prefix.OntologyPrefix;

import java.util.Collection;
import java.util.Iterator;

public class SKOSNotationCodeFinder implements CodeFinder {


    private final CUIOntologyDelegate ontologyDelegate;

    public SKOSNotationCodeFinder(final CUIOntologyDelegate ontologyDelegate) {
        this.ontologyDelegate = ontologyDelegate;
    }

    @Override
    public String getCode(final String classURI) {
        final Collection<String> notations =
                ontologyDelegate.getObjectsThroughRelation
                        (classURI, OntologyPrefix.getURI("skos:notation"));

        String code = null;
        if(!notations.isEmpty()){
            final Iterator<String> iterator = notations.iterator();
            code = iterator.next();
        }
        return code;
    }
}
