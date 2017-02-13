package org.sifrproject.ontology.matching;

import org.sifrproject.ontology.matching.TermSimilarityRanker;


public interface PooledTermSimilarityRanker extends TermSimilarityRanker {
    void release();
}
