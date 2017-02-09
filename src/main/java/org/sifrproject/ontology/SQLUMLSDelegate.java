package org.sifrproject.ontology;

import org.apache.commons.dbcp2.BasicDataSource;
import org.getalp.lexsema.similarity.signatures.DefaultSemanticSignatureFactory;
import org.getalp.lexsema.similarity.signatures.SemanticSignature;
import org.sifrproject.ontology.matching.CUITerm;
import org.sifrproject.ontology.matching.CUITermImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.sql.*;
import java.util.*;
import java.util.stream.Stream;


public class SQLUMLSDelegate implements UMLSDelegate {

    private static final String CONCEPT_NAME_MAP = "concept_name_map";
    private static final String ERROR_MESSAGE_CANNOT_RUN_SQL_QUERY = "Cannot run SQL query: {}";
    @SuppressWarnings("resource")
    private final BasicDataSource dataSource = new BasicDataSource();
    private final JedisPool jedisPool;
    private static final Logger logger = LoggerFactory.getLogger(SQLUMLSDelegate.class);
    private static final String CUITUI_PREFIX = "cuitui_";


    public SQLUMLSDelegate(final String jdbcURI, final String sqlUser, final String sqlPass, final String sqlDB, final JedisPool jedisPool) {

        dataSource.setDriverClassName("com.mysql.jdbc.Driver");
        dataSource.setUrl(jdbcURI);
        dataSource.setUsername(sqlUser);
        dataSource.setPassword(sqlPass);
        dataSource.setDefaultCatalog(sqlDB);
        this.jedisPool = jedisPool;
    }

    @SuppressWarnings({"OverlyNestedMethod", "SynchronizeOnNonFinalField"})
    @Override
    public Collection<String> getTUIsForCUIs(final Collection<String> cuis) {
        final Collection<String> tuis = new ArrayList<>();

        try (Jedis jedis = jedisPool.getResource()) {
            for (final String cui : cuis) {
                Collection<String> localTuis = jedis.lrange(CUITUI_PREFIX + cui, 0, -1);
                if (localTuis.isEmpty()) {
                    synchronized (dataSource) {
                        try (final Connection connection = dataSource.getConnection()) {
                            try (final Statement statement = connection.createStatement()) {
                                final String query = String.format("SELECT TUI FROM MRSTY WHERE CUI='%s'", cui);
                                try (final ResultSet resultSet = statement.executeQuery(query)) {
                                    localTuis = new ArrayList<>();
                                    while (resultSet.next()) {
                                        localTuis.add(resultSet.getString(1));
                                    }
                                    if (!localTuis.isEmpty()) {
                                        jedis.lpush(CUITUI_PREFIX + cui, localTuis.toArray(new String[localTuis.size()]));
                                    }
                                }
                            } catch (final SQLException e) {
                                logger.error(ERROR_MESSAGE_CANNOT_RUN_SQL_QUERY, e.getLocalizedMessage());
                            }
                        } catch (final SQLException e) {
                            logger.error(e.getLocalizedMessage());
                        }
                    }

                }
                tuis.addAll(localTuis);
            }
        }
        return tuis;
    }

    @Override
    public List<CUITerm> getCUIConceptNameMap(final UMLSLanguageCode languageCode) {
        return getCUIConceptNameMap(languageCode, null);
    }

    private String generateCUIString(final Collection<String> cuis) {
        final Stream<String> stream = cuis.stream();
        final Optional<String> reduce = stream.reduce(String::concat);
        return reduce.orElse("");
    }

    @SuppressWarnings({"OverlyNestedMethod", "OverlyLongMethod"})
    @Override
    public List<CUITerm> getCUIConceptNameMap(final UMLSLanguageCode languageCode, final Collection<String> cuis) {
        final String code = languageCode.getLanguageCode();
        final List<CUITerm> cuiTerms = new ArrayList<>();
        try (Jedis jedis = jedisPool.getResource()) {
            String key = CONCEPT_NAME_MAP + languageCode.getLanguageCode();
            if (cuis != null) {
                key += generateCUIString(cuis);
            }
            Map<String, String> conceptNameMap = jedis.hgetAll(key);
            if (conceptNameMap.isEmpty()) {
                //noinspection SynchronizeOnNonFinalField
                synchronized (dataSource) {
                    try (final Connection connection = dataSource.getConnection()) {
                        try (final Statement statement = connection.createStatement()) {
                            String query = "SELECT CUI,STR FROM MRCONSO WHERE LAT='" + code + "'";
                            if (cuis != null) {
                                query += " AND (";
                                boolean first = true;
                                for (final String cui : cuis) {
                                    if (first) {
                                        first = false;
                                    } else {
                                        query += "OR ";
                                    }
                                    query += "CUI='" + cui + "' ";
                                }
                                query += ");";
                            }
                            logger.debug(query);
                            try (final ResultSet resultSet = statement.executeQuery(query)) {
                                conceptNameMap = new HashMap<>();
                                while (resultSet.next()) {
                                    final String value = resultSet.getString(2);
                                    conceptNameMap.put(value, resultSet.getString(1));
                                }
                                if (!conceptNameMap.isEmpty()) {
                                    jedis.hmset(key, conceptNameMap);
                                }
                            }
                        } catch (final SQLException e) {
                            logger.error(ERROR_MESSAGE_CANNOT_RUN_SQL_QUERY, e.getLocalizedMessage());
                        }
                    } catch (final SQLException e) {
                        logger.error(e.getLocalizedMessage());
                    }
                }
            }
            populateTermList(cuiTerms, conceptNameMap, languageCode);
        }
        return cuiTerms;
    }


    private void populateTermList(final List<CUITerm> cuiTerms, final Map<String, String> conceptNameMap, final UMLSLanguageCode languageCode) {
        for (final Map.Entry<String, String> entry : conceptNameMap.entrySet()) {
            final SemanticSignature semanticSignature =
                    DefaultSemanticSignatureFactory.DEFAULT.createSemanticSignature(entry.getKey());
            final CUITerm cuiTerm = new CUITermImpl(entry.getValue(), entry.getKey(), languageCode, semanticSignature);
            if (cuiTerms.contains(cuiTerm)) {
                final CUITerm other = cuiTerms.get(cuiTerms.indexOf(cuiTerm));
                other.appendToSignature(cuiTerm.getTerm());
            } else {
                cuiTerms.add(cuiTerm);
            }
        }
    }
}
