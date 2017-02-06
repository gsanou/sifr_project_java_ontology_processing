package org.sifrproject.ontology.matching;

import org.getalp.lexsema.similarity.measures.tverski.TverskiIndexSimilarityMeasureBuilder;
import org.getalp.lexsema.similarity.signatures.DefaultSemanticSignatureFactory;
import org.getalp.lexsema.similarity.signatures.SemanticSignature;

import java.util.List;

public class TverskiTermSimilarityRanker implements TermSimilarityRanker {

    private static final double RATIO_PROPORTION = 0.5d;

    @Override
    public void rankBySimilarity(final List<CUITerm> cuiTermList, final String conceptDescription) {
        new TverskiIndexSimilarityMeasureBuilder()
                .alpha(1d).beta(RATIO_PROPORTION).gamma(RATIO_PROPORTION)
                .computeRatio(true).fuzzyMatching(true).regularizeOverlapInput(true).normalize(true).build();

        final SemanticSignature conceptSemanticSignature =
                DefaultSemanticSignatureFactory.DEFAULT.createSemanticSignature(conceptDescription);

        //cuiTermList.sort();
    }
}
