package org.sifrproject.stats;

@SuppressWarnings("PublicMethodNotExposedInInterface")
public final class OntologyStats {

    private OntologyStats() {
    }


    @SuppressWarnings("StaticVariableOfConcreteClass")
    public static final OntologyStats stats = new OntologyStats();

    private long totalClassCount;
    private long classesWithoutCUI;
    private long classesWithoutTUI;
    private long classesWithCUIInAltLabel;
    private long classesWithCUIInMappings;
    private long classesWithAmbiguousCUI;
    private long classesRemainingWithoutCUI;
    private long classesRemainingWithoutTUI;

    public void incrementClassesRemainingWithoutCUI() {
        classesRemainingWithoutCUI++;
    }

    public void incrementClassesRemainingWithoutTUI() {
        classesRemainingWithoutTUI++;
    }

    public void incrementTotalClassCount() {
        totalClassCount++;
    }

    public void incrementClassesWithoutCUI() {
        classesWithoutCUI++;
    }

    public void incrementClassesWithoutTUI() {
        classesWithoutTUI++;
    }

    public void incrementClassesWithCUIInAltLabel() {
        classesWithCUIInAltLabel++;
    }

    public void incrementClassesWithCUIInMappings() {
        classesWithCUIInMappings++;
    }

    public void incrementClassesWithAmbiguousCUI() {
        classesWithAmbiguousCUI++;
    }


    public long getTotalClassCount() {
        return totalClassCount;
    }

    public long getClassesWithoutCUI() {
        return classesWithoutCUI;
    }

    public long getClassesWithoutTUI() {
        return classesWithoutTUI;
    }

    public long getClassesWithCUIInAltLabel() {
        return classesWithCUIInAltLabel;
    }

    public long getClassesWithCUIInMappings() {
        return classesWithCUIInMappings;
    }

    public long getClassesRemainingWithoutCUI() {
        return classesRemainingWithoutCUI;
    }

    public long getClassesWithAmbiguousCUI() {
        return classesWithAmbiguousCUI;
    }

    public long getClassesRemainingWithoutTUI() {
        return classesRemainingWithoutTUI;
    }
}
