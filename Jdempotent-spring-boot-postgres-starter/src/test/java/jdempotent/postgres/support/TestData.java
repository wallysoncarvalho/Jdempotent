package jdempotent.postgres.support;

import java.io.Serializable;

/**
 * Test data class that implements Serializable for byte array serialization
 */
public class TestData implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String value;

    public TestData(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        TestData testData = (TestData) obj;
        return value != null ? value.equals(testData.value) : testData.value == null;
    }

    @Override
    public int hashCode() {
        return value != null ? value.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "TestData{value='" + value + "'}";
    }
}
