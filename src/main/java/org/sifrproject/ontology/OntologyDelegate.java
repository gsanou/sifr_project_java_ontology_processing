package org.sifrproject.ontology;

import com.hp.hpl.jena.rdf.model.Model;

import java.util.Collection;

public interface OntologyDelegate {

    Collection<String> findCUIsForMappings(final String classURI);

    Collection<String> findCUIsInAltLabel(final String classURI);

    Collection<String> findTUIsForMappings(final String classURI);

    void cuisFromModel(final Model model, final String classURI, final Collection<String> cuis);

    void tuisFromModel(final Model model, final String classURI, final Collection<String> tuis);

    void addTUIToModel(final String classURI, final String tui, final Model model);
    void addCUIToModel(final String classURI, final String cui, final Model model);
}
