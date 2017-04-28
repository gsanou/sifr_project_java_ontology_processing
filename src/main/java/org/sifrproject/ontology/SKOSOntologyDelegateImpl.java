package org.sifrproject.ontology;


import org.sifrproject.cli.OWLOntologyCleaner;
import org.sifrproject.ontology.prefix.OntologyPrefix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;

import java.util.Collection;

public final class SKOSOntologyDelegateImpl extends BaseOntologyDelegate implements SKOSOntologyDelegate {

    private static final Logger logger = LoggerFactory.getLogger(SKOSOntologyDelegateImpl.class);

    private static final String SKOS_CONCEPT_SCHEME_URI = OntologyPrefix.getURI("skos:ConceptScheme");
    private static final String SKOS_CONCEPT_URI = OntologyPrefix.getURI(SKOS_CONCEPT);
    private static final String SKOS_BROADER_URI = OntologyPrefix.getURI("skos:broader");

    private static final String SKOS_IN_SCHEME = OntologyPrefix.getURI("skos:inScheme");

    private static final String RDF_TYPE_URI = OntologyPrefix.getURI("rdf:type");
    private static final String SKOS_HAS_TOP_CONCEPT_URI = OntologyPrefix.getURI("skos:hasTopConcept");

    private static final String SKOS_PREF_LABEL_URI = OntologyPrefix.getURI(OWLOntologyCleaner.SKOS_PREF_LABEL);

    @SuppressWarnings("HardcodedFileSeparator")
    public SKOSOntologyDelegateImpl(final String outputFileName, final JedisPool jedisPool) {
        super(outputFileName, jedisPool);
    }

    @Override
    public void addConcept(final String URI, final String parentConceptURI, final String prefLabel, final Collection<String> altLabels, final String languageCode) {
        addConcept(URI, null, parentConceptURI, prefLabel, altLabels, languageCode);
    }

    @SuppressWarnings("MethodWithTooManyParameters")
    @Override
    public void addConcept(final String URI, final String schemeURI, final String parentConceptURI, final String prefLabel, final Collection<String> altLabels, final String languageCode) {
        addStatement(URI, RDF_TYPE_URI, SKOS_CONCEPT_URI);
        if (parentConceptURI != null) {
            addStatement(URI, SKOS_BROADER_URI, parentConceptURI);
        }

        if(schemeURI!=null){
            addStatement(URI, SKOS_IN_SCHEME, schemeURI);
        }
        addLiteralStatement(URI, SKOS_PREF_LABEL_URI, prefLabel, languageCode);

        for (final String altLabel : altLabels) {
            addLiteralStatement(URI, SKOS_ALT_LABEL_PROPERTY, altLabel, languageCode);
        }

    }

    @Override
    public void addConceptScheme(final String uri) {
        addStatement(uri, RDF_TYPE_URI, SKOS_CONCEPT_SCHEME_URI);
    }

    @Override
    public void setTopConcept(final String topConceptURI, final String ontologyURI){
        addStatement(ontologyURI,SKOS_HAS_TOP_CONCEPT_URI,topConceptURI);
    }
}
