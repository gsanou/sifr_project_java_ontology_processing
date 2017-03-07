package org.sifrproject.ontology;

import com.hp.hpl.jena.ontology.OntClass;

import java.util.Collection;
import java.util.List;


public interface OntologyDelegate {

    List<OntClass> getClasses();

    String getConceptLabel(final String classURI);

    @SuppressWarnings("all")
    void addSkosProperty(final String classURI, final String value, final String propertyName);

    void addStatement(String sourceURI, String propertyURI, String targetURI);
    void addLiteralStatement(final String sourceURI, final String propertyURI, final String literal);

    void writeModel();

    Collection<String> getObjectsThroughRelation(String classURI, String relationURI);
    Collection<String> getObjectsThroughRelation(final Collection<String> classURIs, final String propertyURI);

    String getOntologyName();

}
