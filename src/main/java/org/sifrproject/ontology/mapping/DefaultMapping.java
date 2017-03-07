package org.sifrproject.ontology.mapping;


public final class DefaultMapping implements Mapping, Comparable<Mapping>{
    private final String sourceClass;
    private final String targetClass;
    private final String property;

    DefaultMapping(final String sourceClass, final String targetClass, final String property) {
        this.sourceClass = sourceClass;
        this.targetClass = targetClass;
        this.property = property;
    }

    DefaultMapping(final String key) {
        final String[] parts = key.split("_");
        sourceClass = parts[0];
        property = parts[1];
        targetClass = parts[2];


    }

    @Override
    public String getSourceClass() {
        return sourceClass;
    }

    @Override
    public String getTargetClass() {
        return targetClass;
    }

    @Override
    public String getProperty() {
        return property;
    }

    @Override
    public String toString() {
        return sourceClass+"_"+property+"_"+targetClass;
    }

    @SuppressWarnings("all")
    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof DefaultMapping)) return false;

        final DefaultMapping that = (DefaultMapping) o;

        if ((getSourceClass() != null) ? !getSourceClass().equals(that.getSourceClass()) : (that.getSourceClass() != null))
            return false;
        if ((getTargetClass() != null) ? !getTargetClass().equals(that.getTargetClass()) : (that.getTargetClass() != null))
            return false;
        return getProperty() != null ? getProperty().equals(that.getProperty()) : that.getProperty() == null;
    }

    @SuppressWarnings("all")
    @Override
    public int hashCode() {
        int result = getSourceClass() != null ? getSourceClass().hashCode() : 0;
        result = 31 * result + (getTargetClass() != null ? getTargetClass().hashCode() : 0);
        result = 31 * result + (getProperty() != null ? getProperty().hashCode() : 0);
        return result;
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public int compareTo(final Mapping o) {
        final String s = toString();
        return s.compareTo(o.toString());
    }
}
