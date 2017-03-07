package org.sifrproject.ontology.cuis;

import com.hp.hpl.jena.rdf.model.*;
import org.sifrproject.ontology.BaseOntologyDelegate;
import org.sifrproject.ontology.prefix.OntologyPrefix;
import org.sifrproject.utils.CacheKeyPrefixes;
import org.sifrproject.utils.EmptyResultsCache;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CUIOntologyDelegateImpl extends BaseOntologyDelegate implements CUIOntologyDelegate {

    //private static final Logger logger = LoggerFactory.getLogger(CUIOntologyDelegateImpl.class);

    private static final String CUI_PROPERTY_URI = OntologyPrefix.getURI("umls:cui");
    private static final String TUI_PROPERTY_URI = OntologyPrefix.getURI("umls:tui");
    private static final String HAS_STY_PROPERTY_URI = OntologyPrefix.getURI("umls:hasSty");
    private static final String STY_URL_BASE = "http://purl.lirmm.fr/ontology/STY/";
    private static final Pattern LANG_LITERAL_PATTERN= Pattern.compile("([^\"]*)@([a-z][a-z])");




    public CUIOntologyDelegateImpl(final String modelURI, final String outputFileSuffix, final JedisPool jedisPool) {
        super(modelURI, outputFileSuffix, jedisPool);
    }

    @Override
    public void getCUIs(final String classURI, final Collection<String> cuis) {
        cuis.addAll(getObjectsThroughRelation(classURI,CUI_PROPERTY_URI));
    }

    @Override
    public void getTUIs(final String classURI, final Collection<String> tuis) {
        getObjectsThroughRelation(classURI, TUI_PROPERTY_URI);
    }

    @Override
    public void getCUIs(final Collection<String> classURIs, final Collection<String> cuis) {
        cuis.addAll(getObjectsThroughRelation(classURIs,CUI_PROPERTY_URI));
    }

    @Override
    public void getTUIs(final Collection<String> classURIs, final Collection<String> tuis) {
        tuis.addAll(getObjectsThroughRelation(classURIs,TUI_PROPERTY_URI));
    }



    @Override
    public void addTUIToModel(final String classURI, final String tui) {
        synchronized (model) {
            addLiteralStatement(classURI,TUI_PROPERTY_URI,tui);
            addStatement(classURI,HAS_STY_PROPERTY_URI,STY_URL_BASE + tui);
        }
    }

    @Override
    public void addCUIToModel(final String classURI, final String cui) {
        synchronized (model) {
            model.add(getOrCreateResource(classURI), getOrCreateProperty(CUI_PROPERTY_URI), cui);
        }
    }
    @Override
    public void purgeCUIsFromAltLabel(final String classURI, final Iterable<String> cuis, final String lang) {
        //cuisFromAltLabel(model, classURI, toRemove);
        for (final String value : cuis) {
            model.remove(getOrCreateResource(classURI),
                    getOrCreateProperty(SKOS_ALT_LABEL_PROPERTY),
                    ResourceFactory.createLangLiteral(value,lang));
        }
    }

    @Override
    public void purgeCodeFromAltLabel(final String classURI, final String code, final String lang) {
        //cuisFromAltLabel(model, classURI, toRemove);
            model.remove(getOrCreateResource(classURI),
                    getOrCreateProperty(SKOS_ALT_LABEL_PROPERTY),
                    ResourceFactory.createLangLiteral(code,lang));
    }

    @Override
    public Collection<String> findCUIsInAltLabel(final String classURI) {
        Collection<String> cuis;

        try (Jedis jedis = jedisPool.getResource()) {
            final String key = CacheKeyPrefixes.ALTCUI + classURI;
            cuis = jedis.lrange(key, 0, -1);

            if (cuis.isEmpty() && !EmptyResultsCache.isEmpty(key, jedis)) {
                cuis = new ArrayList<>();
                synchronized (model) {
                    cuisFromAltLabel(model, classURI, cuis);
                }
                if (cuis.isEmpty()) {
                    EmptyResultsCache.markEmpty(key, jedis);
                } else {
                    jedis.lpush(key, cuis.toArray(new String[cuis.size()]));
                }
            }
        }
        return cuis;
    }

    private void cuisFromAltLabel(final Model model, final String classURI, final Collection<String> cuis) {
        final StmtIterator stmtIterator = model.listStatements(
                getOrCreateResource(classURI),
                getOrCreateProperty(SKOS_ALT_LABEL_PROPERTY),
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
    public void cleanSkosAltLabel(final String classURI){
        final String prefLabel = getConceptLabel(classURI);
        final Collection<String> altLabels = getObjectsThroughRelation(classURI, SKOS_ALT_LABEL_PROPERTY);
        for(final String altLabel: altLabels) {
            final Matcher matcher = LANG_LITERAL_PATTERN.matcher(altLabel);
            String literal = altLabel;
            String languageCode = "en";
            if(matcher.matches()) {
                literal = matcher.group(1);
                languageCode = matcher.group(2);
            }

            final String lowerCaseAl = literal.toLowerCase();
            final String lowerCasePl = prefLabel.toLowerCase();
            final String trimmedPl = lowerCasePl.trim();
            if(trimmedPl.equals(lowerCaseAl.trim())) {
                model.remove(getOrCreateResource(classURI),
                        getOrCreateProperty(SKOS_ALT_LABEL_PROPERTY),
                        ResourceFactory.createLangLiteral(literal,languageCode));
            }
        }
    }
}
