package org.sifrproject.cli;


import java.io.IOException;

public interface OntologyProcessor {
    void processOntology();
    void cleanUp() throws IOException;
    void updateModel();
}
