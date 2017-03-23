package org.sifrproject.stats;


public final class SkosGeneratorOntologyStats extends OntologyStats{


    public static final String TOTAL_CLASS_COUNT_STATISTIC = "numberOfClasses";


    @SuppressWarnings("HardcodedFileSeparator")
    public SkosGeneratorOntologyStats(final String dctionaryFile) {
        super(dctionaryFile);
        registerStatistic(TOTAL_CLASS_COUNT_STATISTIC,"# Classes");
    }
}
