package me.ahoo.cosid.segment;


import static me.ahoo.cosid.segment.IdSegmentDistributor.DEFAULT_OFFSET;
import static me.ahoo.cosid.segment.IdSegmentDistributor.DEFAULT_STEP;

import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * @author : Rocher Kong
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IdSegmentDistributorDefinitionTest {
    private static final String NAMESPACE = "IdSegmentDistributorDefinitionTest";
    private static final String NAME = "segment";
    IdSegmentDistributorDefinition idSegmentDistributorDefinition;

    @BeforeAll
    void setUp() {
        idSegmentDistributorDefinition = new IdSegmentDistributorDefinition(NAMESPACE, NAME, DEFAULT_OFFSET, DEFAULT_STEP);
    }

    @Test
    void getNamespace() {
        Assertions.assertEquals(NAMESPACE, idSegmentDistributorDefinition.getNamespace());
    }

    @Test
    void getName() {
        Assertions.assertEquals(NAME, idSegmentDistributorDefinition.getName());
    }

    @Test
    void getNamespacedName() {
        Assertions.assertEquals(NAMESPACE + "." + NAME, idSegmentDistributorDefinition.getNamespacedName());
    }

    @Test
    void getOffset() {
        Assertions.assertEquals(DEFAULT_OFFSET, idSegmentDistributorDefinition.getOffset());
    }

    @Test
    void getStep() {
        Assertions.assertEquals(DEFAULT_STEP, idSegmentDistributorDefinition.getStep());
    }
}
