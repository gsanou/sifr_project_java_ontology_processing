package org.sifrproject.ontology;


import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.rdf.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OntologyDelegateImpl implements OntologyDelegate {
    private static final Logger logger = LoggerFactory.getLogger(OntologyDelegateImpl.class);

    private static final String ALT_LABEL_PROPERTY = "http://www.w3.org/2004/02/skos/core#altLabel";
    private static final String CUI_PROPERTY_URI = "http://bioportal.bioontology.org/ontologies/umls/cui";
    private static final String TUI_PROPERTY_URI = "http://bioportal.bioontology.org/ontologies/umls/tui";
    private static final String MAPPINGS_PROPERTY_URI = "http://linguistics-ontology.org/gold/freeTranslation";


    private final JedisPool jedisPool;

    private static final String MAPPING_PREFIX = "m_";
    private static final String CUI_PREFIX = "c_";
    private static final String TUI_PREFIX = "t_";
    private static final String ALTCUI_PREFIX = "ac_";

    private final OntModel mappingsModel;
    private final OntModel targetModel;
    private final OntModel sourceModel;

    public OntologyDelegateImpl(final OntModel mappingsModel, final OntModel targetModel, final OntModel sourceModel, final JedisPool jedisPool) {
        this.jedisPool = jedisPool;
        this.targetModel = targetModel;
        this.mappingsModel = mappingsModel;
        this.sourceModel = sourceModel;
    }

    @Override
    public Collection<String> findCUIsInAltLabel(final String classURI) {
        Collection<String> cuis;
        try (Jedis jedis = jedisPool.getResource()) {

            cuis = jedis.lrange(ALTCUI_PREFIX + classURI, 0, -1);

            if (cuis.isEmpty()) {
                cuis = new ArrayList<>();
                cuisFromAltLabel(sourceModel,classURI, cuis);
                if (!cuis.isEmpty()) {
                    jedis.lpush(ALTCUI_PREFIX + classURI, cuis.toArray(new String[cuis.size()]));
                }
            }
        }
        return cuis;
    }


    @Override
    public Collection<String> findTUIsForMappings(final String classURI) {
        Collection<String> tuis = new ArrayList<>();
        final Collection<String> mappings = findMappings(classURI);
        if (!mappings.isEmpty()) {
            try (Jedis jedis = jedisPool.getResource()) {
                for (final String mappingURI : mappings) {
                    tuis = jedis.lrange(TUI_PREFIX + mappingURI, 0, -1);
                    if (tuis.isEmpty()) {
                        tuis = new ArrayList<>();
                        tuisFromModel(targetModel,mappingURI,tuis);
                    }
                }
            }
        }
        return tuis;
    }

    private void cuisFromAltLabel(final Model model, final String classURI, final Collection<String> cuis) {
        final StmtIterator stmtIterator = model.listStatements(
                ResourceFactory.createResource(classURI),
                model.createProperty(ALT_LABEL_PROPERTY),
                (RDFNode) null);

        while (stmtIterator.hasNext()) {
            final Statement statement = stmtIterator.nextStatement();
            final String altLabel = statement.getString();
            final Pattern pattern = Pattern.compile("(C[0-9][0-9][0-9][0-9][0-9][0-9][0-9])");
            final Matcher matcher = pattern.matcher(altLabel);
            if (matcher.matches()) {
                cuis.add(altLabel);
            }
        }
    }

    @Override
    public void cuisFromModel(final Model model, final String classURI, final Collection<String> cuis){
        final StmtIterator stmtIterator = model.listStatements(
                ResourceFactory.createResource(classURI),
                model.createProperty(CUI_PROPERTY_URI),
                (RDFNode) null);

        while (stmtIterator.hasNext()) {
            final Statement statement = stmtIterator.nextStatement();
            cuis.add(statement.getString());
        }
    }

    @Override
    public void tuisFromModel(final Model model, final String classURI, final Collection<String> tuis){
        final StmtIterator stmtIterator = model.listStatements(
                ResourceFactory.createResource(classURI),
                model.createProperty(TUI_PROPERTY_URI),
                (RDFNode) null);

        while (stmtIterator.hasNext()) {
            final Statement statement = stmtIterator.nextStatement();
            tuis.add(statement.getString());
        }
    }

    @Override
    public void addTUIToModel(final String classURI, final String tui, final Model model) {
        model.add(ResourceFactory.createResource(classURI), model.createProperty(TUI_PROPERTY_URI), tui);
    }

    @Override
    public void addCUIToModel(final String classURI, final String cui, final Model model) {
        model.add(ResourceFactory.createResource(classURI), model.createProperty(CUI_PROPERTY_URI), cui);
    }

    @Override
    @SuppressWarnings("FeatureEnvy")
    public Collection<String> findCUIsForMappings(final String classURI) {
        Collection<String> cuis = new ArrayList<>();
        final Collection<String> mappings = findMappings(classURI);
        if (!mappings.isEmpty()) {
            try (Jedis jedis = jedisPool.getResource()) {
                for (final String mappingURI : mappings) {
                    cuis = jedis.lrange(CUI_PREFIX + mappingURI, 0, -1);
                    if (cuis.isEmpty()) {

                        cuisFromModel(targetModel,mappingURI,cuis);

                        if (cuis.isEmpty()) {
                            cuisFromAltLabel(targetModel, mappingURI, cuis);
                        }
                        if(!cuis.isEmpty()) {
                            jedis.lpush(CUI_PREFIX + mappingURI, cuis.toArray(new String[cuis.size()]));
                        }
                    }
                }
                logger.info("\t\t\t\t Found {} CUIs in mappings.", cuis.size());
            }
        }
        return cuis;
    }


    private Collection<String> findMappings(final String classURI) {
        Collection<String> mappings;

        try (Jedis jedis = jedisPool.getResource()) {
            mappings = jedis.lrange(MAPPING_PREFIX + classURI, 0, -1);
            if (mappings.isEmpty()) {
                mappings = new ArrayList<>();

                final StmtIterator stmtIterator = mappingsModel.listStatements(
                        ResourceFactory.createResource(classURI),
                        mappingsModel.createProperty(MAPPINGS_PROPERTY_URI),
                        (RDFNode) null);

                while(stmtIterator.hasNext()){
                    final Statement statement = stmtIterator.nextStatement();
                    final RDFNode object = statement.getObject();
                    mappings.add(object.toString());
                }

                if (!mappings.isEmpty()) {
                    final String[] strMapping = mappings.toArray(new String[mappings.size()]);
                    jedis.lpush(MAPPING_PREFIX + classURI, strMapping);
                }
            }
        }
        logger.info("Found {} mappings...",mappings.size());
        return mappings;
    }
}
