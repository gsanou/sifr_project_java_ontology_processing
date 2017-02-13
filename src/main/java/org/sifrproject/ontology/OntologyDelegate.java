package org.sifrproject.ontology;

import com.hp.hpl.jena.ontology.OntClass;
import com.hp.hpl.jena.rdf.model.Resource;

import java.util.Collection;
import java.util.List;

public interface OntologyDelegate {

    List<OntClass> getSourceClasses();

    Collection<String> findCUIsFromMappings(final String classURI);

    Collection<String> findCUIsInAltLabel(final String classURI);

    Collection<String> findTUIsForMappings(final String classURI);

    void cuisFromSourceModel(final String classURI, final Collection<String> cuis);
    void tuisFromSourceModel(final String classURI, final Collection<String> tuis);

    String conceptLabelFromSourceModel(final Resource thisClass);

    @SuppressWarnings("all")
    void addTUIToSourceModel(final String classURI, final String tui);
    @SuppressWarnings("all")
    void addCUIToSourceModel(final String classURI, final String cui);

    @SuppressWarnings("all")
    void addSkosNote(final String classURI, final String note);

    void purgeCUIsFromAltLabelAndSynonyms(final String classURI, String lang);

    void writeEnrichedModel();

    String getOntologyName();
}
