package org.sifrproject.ontology.code;


@FunctionalInterface
public interface CodeFinder {
    String getCode(String classURI);
}
