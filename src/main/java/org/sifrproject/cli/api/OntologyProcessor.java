package org.sifrproject.cli.api;


import java.io.IOException;

public interface OntologyProcessor {
    void processSourceOntology();
    void processTargetOntology();
    void postProcess();
    void cleanUp() throws IOException;
}
