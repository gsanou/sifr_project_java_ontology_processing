package org.sifrproject.ontology;

import com.hp.hpl.jena.ontology.OntClass;
import com.hp.hpl.jena.ontology.OntModel;

import java.util.Collection;
import java.util.List;


public interface OntologyDelegate {

    List<OntClass> getClasses();

    String getConceptLabel(final String classURI);

    void addSkosProperty(final String classURI, final String value, final String propertyName, final String languageCode);
    void addSkosProperty(final String classURI, final String value, final String propertyName);

    void addStatement(String sourceURI, String propertyURI, String targetURI);
    void addLiteralStatement(final String sourceURI, final String propertyURI, final String literal);
    void addLiteralStatement(final String sourceURI, final String propertyURI, final String literal, final String languageCode);

    void writeModel();

    Collection<String> getObjectsThroughRelation(String classURI, String relationURI);
    Collection<String> getObjectsThroughRelation(final Collection<String> classURIs, final String propertyURI);

    String getOntologyName();

    void appendModel(final OntModel ontModel);

}
