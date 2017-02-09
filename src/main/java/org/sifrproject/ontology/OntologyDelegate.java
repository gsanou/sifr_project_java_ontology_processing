package org.sifrproject.ontology;

import com.hp.hpl.jena.ontology.OntClass;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.util.iterator.ExtendedIterator;

import java.util.Collection;

public interface OntologyDelegate {

    ExtendedIterator<OntClass> getSourceClasses();

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

    void writeEnrichedModel();

    String getOntologyName();
}
