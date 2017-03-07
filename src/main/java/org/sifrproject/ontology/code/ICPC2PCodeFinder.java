package org.sifrproject.ontology.code;


import org.sifrproject.ontology.cuis.CUIOntologyDelegate;
import org.sifrproject.ontology.prefix.OntologyPrefix;

import java.util.Collection;
import java.util.Iterator;

public class ICPC2PCodeFinder implements CodeFinder {


    private final CUIOntologyDelegate ontologyDelegate;

    ICPC2PCodeFinder(final CUIOntologyDelegate ontologyDelegate) {
        this.ontologyDelegate = ontologyDelegate;
    }

    @Override
    public String getCode(final String classURI) {
        final Collection<String> notations =
                ontologyDelegate.getObjectsThroughRelation
                        (classURI, OntologyPrefix.getURI("icpc2p:ICPCCODE"));

        String code = null;
        if(!notations.isEmpty()){
            final Iterator<String> iterator = notations.iterator();
            code = iterator.next();
        }
        return code;
    }
}
