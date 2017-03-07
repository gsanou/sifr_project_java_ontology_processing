package org.sifrproject.ontology.mapping;

import org.sifrproject.ontology.mapping.Mapping;

import java.util.List;

public interface OntologyMappingDelegate {

    List<Mapping> getAllMappings();
    void putMapping(Mapping mapping);

    List<Mapping> sourceMappings(String classURI);
    List<Mapping> targetMappings(String classURI);

    void writeMappings();
}
