package org.sifrproject.cli.api;


import java.io.IOException;

@FunctionalInterface
public interface OntologyProcessor {
    void process() throws IOException;
}
