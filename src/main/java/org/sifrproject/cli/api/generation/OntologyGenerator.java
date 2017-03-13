package org.sifrproject.cli.api.generation;


import java.io.InputStream;

public interface OntologyGenerator {
    void loadSource(InputStream inputStream);
    void generateOntology();
    void writeModel();
}
