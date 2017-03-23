package org.sifrproject.ontology.mapping;


import com.hp.hpl.jena.ontology.OntClass;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntModelSpec;
import com.hp.hpl.jena.rdf.model.*;
import org.sifrproject.ontology.prefix.OntologyPrefix;
import org.sifrproject.utils.EmptyResultsCache;
import org.sifrproject.utils.OntologyLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@SuppressWarnings("MethodParameterOfConcreteClass")
public class DefaultOntologyMappingDelegate implements OntologyMappingDelegate {

    private static final Logger logger = LoggerFactory.getLogger(DefaultOntologyMappingDelegate.class);

    private static final String MAPPING_FREETRANSLATION_PROPERTY_URI = OntologyPrefix.getURI("gold:freeTranslation");
    private static final String MAPPING_TRANSLATION_PROPERTY_URI = OntologyPrefix.getURI("gold:translation");
    private static final String MAPPING_EXACTMATCH_PROPERTY_URI = OntologyPrefix.getURI("skos:exactMatch");
    private static final String MAPPING_CLOSEMATCH_PROPERTY_URI = OntologyPrefix.getURI("skos:closeMatch");
    private static final String MAPPING_RELATEDMATCH_PROPERTY_URI = OntologyPrefix.getURI("skos:relatedMatch");
    private static final String MAPPING_BROADMATCH_PROPERTY_URI = OntologyPrefix.getURI("skos:broadMatch");

    private static final String MAPPING_CACHE_PREFIX = "m|";


    private final OntModel mappingModel;
    private final String sourceOntologyName;
    private final String targetOntologyName;
    private final JedisPool jedisPool;

    public DefaultOntologyMappingDelegate(final String sourceOntologyName, final String targetOntologyName, final JedisPool jedisPool) {
        this.sourceOntologyName = sourceOntologyName;
        this.targetOntologyName = targetOntologyName;
        mappingModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM_RDFS_INF);
        this.jedisPool = jedisPool;
    }

    public DefaultOntologyMappingDelegate(final String mappingsFile,final String sourceOntologyName, final String targetOntologyName, final JedisPool jedisPool) {
        this.sourceOntologyName = sourceOntologyName;
        this.targetOntologyName = targetOntologyName;
        mappingModel = OntologyLoader.loadModel(mappingsFile);
        this.jedisPool = jedisPool;

    }

    @Override
    public List<Mapping> getAllMappings() {
        return getMappings(null,null);
    }

    @SuppressWarnings("FeatureEnvy")
    private synchronized List<Mapping> getMappings(final String sourceClass, final String targetClass){
        String key = MAPPING_CACHE_PREFIX;
        OntClass subject = null;
        if (sourceClass != null) {
            subject = mappingModel.createClass(sourceClass);
            key+=sourceClass;
        }

        OntClass object = null;
        if (targetClass != null) {
            object = mappingModel.createClass(targetClass);
            key+="|"+targetClass;
        }
        final List<Mapping> mappings = new ArrayList<>();
        try (Jedis jedis = jedisPool.getResource()) {
            final List<String> cachedMappings = jedis.lrange(key, 0, -1);
            if (cachedMappings.isEmpty() && !EmptyResultsCache.isEmpty(key, jedis)) {

                getMappingsForProperty(mappings,subject,object,MAPPING_EXACTMATCH_PROPERTY_URI);
                getMappingsForProperty(mappings,subject,object,MAPPING_CLOSEMATCH_PROPERTY_URI);
                getMappingsForProperty(mappings,subject,object,MAPPING_RELATEDMATCH_PROPERTY_URI);
                getMappingsForProperty(mappings,subject,object,MAPPING_BROADMATCH_PROPERTY_URI);
                getMappingsForProperty(mappings,subject,object,MAPPING_FREETRANSLATION_PROPERTY_URI);
                getMappingsForProperty(mappings,subject,object,MAPPING_TRANSLATION_PROPERTY_URI);


                if (mappings.isEmpty()) {
                    EmptyResultsCache.markEmpty(key, jedis);
                } else {
                    mappings.forEach(mapping -> cachedMappings.add(mapping.toString()));
                    final String[] strMapping = cachedMappings.toArray(new String[mappings.size()]);
                    jedis.lpush(key, strMapping);
                }
            } else {
                for(final String cachedMapping: cachedMappings){
                    mappings.add(new DefaultMapping(cachedMapping));
                }
            }
        }
        logger.debug("Found {} mappings...", mappings.size());
        return mappings;
    }

    private void getMappingsForProperty(final Collection<Mapping> mappings, final Resource subject, final RDFNode object, final String propertyURI){
        final StmtIterator stmtIteratorRelatedM = mappingModel.listStatements(subject, mappingModel.createOntProperty(propertyURI), object);
        while(stmtIteratorRelatedM.hasNext()){
            mappings.add(mappingFromStatement(stmtIteratorRelatedM.nextStatement()));
        }
    }

    private Mapping mappingFromStatement(final Statement statement){
            return new DefaultMapping(statement.getSubject().getURI(),statement.getObject().toString(),statement.getPredicate().getURI());
    }

    @SuppressWarnings("FeatureEnvy")
    @Override
    public synchronized void putMapping(final Mapping mapping) {
        mappingModel.add(mappingModel.createClass(mapping.getSourceClass()),mappingModel.createProperty(mapping.getProperty()), mappingModel.createClass(mapping.getTargetClass()));
    }

    @Override
    public List<Mapping> sourceMappings(final String classURI) {
        return getMappings(classURI,null);
    }

    @Override
    public List<Mapping> targetMappings(final String classURI) {
        return getMappings(null,classURI);
    }


    @Override
    public void writeMappings() {
        try {
            final String outputFileName = sourceOntologyName + "_" + targetOntologyName + "_mappings.ttl";
            mappingModel.writeAll(new FileOutputStream(outputFileName), OntologyLoader.TURTLE);
        } catch (final FileNotFoundException e) {
            logger.error(e.getLocalizedMessage());
        }
    }
}
