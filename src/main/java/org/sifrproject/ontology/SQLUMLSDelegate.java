package org.sifrproject.ontology;

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


public class SQLUMLSDelegate implements UMLSDelegate {

    private static final String CONCEPT_NAME_MAP = "concept_name_map";
    private static final String ERROR_MESSAGE_CANNOT_RUN_SQL_QUERY = "Cannot run SQL query: {}";
    private Connection connection;
    private final JedisPool jedisPool;
    private static final Logger logger = LoggerFactory.getLogger(SQLUMLSDelegate.class);
    private static final String CUITUI_PREFIX = "cuitui_";

    private final List<CUITerm> cuiTerms;


    public SQLUMLSDelegate(final String jdbcURI, final String sqlUser, final String sqlPass, final String sqlDB, final JedisPool jedisPool) {
        try {
            //Class.forName("com.mysql.jdbc.Driver");
            connection = DriverManager.getConnection(jdbcURI, sqlUser, sqlPass);
            connection.setCatalog(sqlDB);
        } catch (final SQLException e) {
            logger.error("Cannot connect to database: {}", e.getLocalizedMessage());
        }
        this.jedisPool = jedisPool;
        cuiTerms = new ArrayList<>();
    }

    @SuppressWarnings("OverlyNestedMethod")
    @Override
    public Collection<String> getTUIsForCUIs(final Collection<String> cuis) {
        final Collection<String> tuis = new ArrayList<>();

        try (Jedis jedis = jedisPool.getResource()) {
            for (final String cui : cuis) {
                Collection<String> localTuis = jedis.lrange(CUITUI_PREFIX + cui, 0, -1);
                if (localTuis.isEmpty()) {
                    try (final Statement statement = connection.createStatement()) {
                        final String query = String.format("SELECT TUI FROM MRSTY WHERE CUI='%s'", cui);
                        try (final ResultSet resultSet = statement.executeQuery(query)) {
                            localTuis = new ArrayList<>();
                            while (resultSet.next()) {
                                localTuis.add(resultSet.getString(1));
                            }
                            if(!localTuis.isEmpty()) {
                                jedis.lpush(CUITUI_PREFIX + cui, localTuis.toArray(new String[localTuis.size()]));
                            }
                        }
                    } catch (final SQLException e) {
                        logger.error(ERROR_MESSAGE_CANNOT_RUN_SQL_QUERY, e.getLocalizedMessage());

                    }

                }
                    tuis.addAll(localTuis);
            }
        }
        return tuis;
    }

    @Override
    public List<CUITerm> getCUIConceptNameMap(final UMLSLanguageCode languageCode) {
        final String code = languageCode.getLanguageCode();
        try (Jedis jedis = jedisPool.getResource()) {
            Map<String, String> conceptNameMap = jedis.hgetAll(CONCEPT_NAME_MAP + languageCode.getLanguageCode());
            if (conceptNameMap.isEmpty()) {
                try (final Statement statement = connection.createStatement()) {
                    final String query = "SELECT CUI,STR FROM MRCONSO WHERE LAT='"+code+"';";
                    try (final ResultSet resultSet = statement.executeQuery(query)) {
                        conceptNameMap = new HashMap<>();
                        while (resultSet.next()) {
                            conceptNameMap.put(resultSet.getString(2), resultSet.getString(1));
                        }
                        jedis.hmset(CONCEPT_NAME_MAP + code, conceptNameMap);
                    }
                } catch (final SQLException e) {
                    logger.error(ERROR_MESSAGE_CANNOT_RUN_SQL_QUERY, e.getLocalizedMessage());
                }
                for(final Map.Entry<String,String> entry : conceptNameMap.entrySet()){
                    final SemanticSignature semanticSignature =
                            DefaultSemanticSignatureFactory.DEFAULT.createSemanticSignature(entry.getKey());
                    cuiTerms.add(new CUITermImpl(entry.getValue(),entry.getKey(),languageCode,semanticSignature));
                }
            }
        }
        return Collections.unmodifiableList(cuiTerms);
    }
}
