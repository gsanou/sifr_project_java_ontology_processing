package org.sifrproject.stats;


public final class ReconcilerOntologyStats extends OntologyStats{

    //Statistics constants
    private static final String MAPPED_CLASSES = "mapped";
    private static final String UNMAPPED_CLASSES = "unmapped";

    private static final String TOTAL_SOURCE_CLASS_COUNT_STATISTIC = "totalSourceClassCount";
    private static final String TOTAL_TARGET_CLASS_COUNT_STATISTIC = "totalTargetClassCount";

    @SuppressWarnings("HardcodedFileSeparator")
    public ReconcilerOntologyStats(final String sourceOntology, final String targetOntology) {
        super(sourceOntology);
        registerStatistic(TOTAL_SOURCE_CLASS_COUNT_STATISTIC,"# Src. Classes");
        registerStatistic(TOTAL_TARGET_CLASS_COUNT_STATISTIC,"# Trg. Classes");
        registerStatistic(MAPPED_CLASSES,"Mapped");
        registerStatistic(UNMAPPED_CLASSES, "Unmapped");
        registerStatistic(UMLS_CODES_FOUND, "#UMLS Codes");
    }
}
