package org.sifrproject.cli.api.generation;


import org.sifrproject.ontology.SKOSOntologyDelegate;

@FunctionalInterface
public interface SKOSOntologyGenerator {
    void generate(final SKOSOntologyDelegate ontologyDelegate);
}
