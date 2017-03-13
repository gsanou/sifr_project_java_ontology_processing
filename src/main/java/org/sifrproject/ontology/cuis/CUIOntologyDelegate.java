package org.sifrproject.ontology.cuis;

import org.sifrproject.ontology.OntologyDelegate;

import java.util.Collection;

public interface CUIOntologyDelegate extends OntologyDelegate {

    Collection<String> findCUIsInAltLabel(final String classURI);

    void getTUIs(final Collection<String> classURIs, final Collection<String> tuis);
    void getCUIs(final Collection<String> classURIs, final Collection<String> cuis);

    void getCUIs(final String classURI, final Collection<String> cuis);
    void getTUIs(final String classURI, final Collection<String> tuis);


    @SuppressWarnings("all")
    void addTUIToModel(final String classURI, final String tui);
    @SuppressWarnings("all")
    void addCUIToModel(final String classURI, final String cui);


    void purgeCUIsFromAltLabel(final String classURI, final Iterable<String> cuis, final String lang);
    void purgeCodeFromAltLabel(final String classURI, final String code, final String lang);

    void cleanSkosAltLabel(final String classURI);

    void addCodeToPrefLabel(final String classURI, final String code);

}
