package org.sifrproject.ontology.matching;

import org.getalp.lexsema.similarity.measures.SimilarityMeasure;
import org.getalp.lexsema.similarity.measures.tverski.TverskiIndexSimilarityMeasureBuilder;
import org.getalp.lexsema.similarity.signatures.DefaultSemanticSignatureFactory;
import org.getalp.lexsema.similarity.signatures.SemanticSignature;
import redis.clients.jedis.JedisPool;

import java.util.List;

public class TverskiTermSimilarityRanker implements TermSimilarityRanker {

    private JedisPool jedisPool;

    public TverskiTermSimilarityRanker(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    private static final double RATIO_PROPORTION = 0.5d;

    @SuppressWarnings("FeatureEnvy")
    @Override
    public void rankBySimilarity(final List<CUITerm> cuiTermList, final String conceptDescription) {
        final SimilarityMeasure similarityMeasure = new TverskiIndexSimilarityMeasureBuilder()
                .alpha(1d).beta(RATIO_PROPORTION).gamma(RATIO_PROPORTION)
                .computeRatio(true).fuzzyMatching(true).regularizeOverlapInput(true).normalize(true).build();

        final SemanticSignature conceptSemanticSignature =
                DefaultSemanticSignatureFactory.DEFAULT.createSemanticSignature(conceptDescription);

        cuiTermList.parallelStream().forEach(cuiTerm -> cuiTerm.setScore(similarityMeasure.compute(cuiTerm.getSemanticSignature(), conceptSemanticSignature)));
        cuiTermList.sort((o1, o2) -> Double.compare(o2.getScore(),o1.getScore()));
    }

}
