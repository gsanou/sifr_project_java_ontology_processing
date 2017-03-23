package org.sifrproject.ontology;


import java.util.Collection;

public interface SKOSOntologyDelegate extends OntologyDelegate {
    void addConcept(final String URI, final String parentConceptURI, final String prefLabel, final Collection<String> altLabels, final String languageCode);
    void addConcept(final String URI, final String schemeURI, final String parentConceptURI, final String prefLabel, final Collection<String> altLabels, final String languageCode);
    void addConceptScheme(final String uri);

    void setTopConcept(String topConceptURI, String ontologyURI);
}
