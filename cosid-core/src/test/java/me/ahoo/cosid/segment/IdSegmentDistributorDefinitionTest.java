package me.ahoo.cosid.segment;


import static me.ahoo.cosid.segment.IdSegmentDistributor.DEFAULT_OFFSET;
import static me.ahoo.cosid.segment.IdSegmentDistributor.DEFAULT_STEP;
import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author : Rocher Kong
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IdSegmentDistributorDefinitionTest {
    IdSegmentDistributorDefinition idSegmentDistributorDefinition;

    @BeforeAll
    void setUp(){
        idSegmentDistributorDefinition=   new IdSegmentDistributorDefinition("IdSegmentDistributorDefinitionTest", MockIdGenerator.INSTANCE.generateAsString(), DEFAULT_OFFSET, DEFAULT_STEP);
    }

    @Test
    void getNamespace() {
        Assertions.assertEquals(idSegmentDistributorDefinition.getNamespace(),"IdSegmentDistributorDefinitionTest");
    }

    @Test
    void getName() {
        Assertions.assertNotNull(idSegmentDistributorDefinition.getName());
    }

    @Test
    void getNamespacedName() {
        Assertions.assertNotNull(idSegmentDistributorDefinition.getNamespacedName());
    }

    @Test
    void getOffset() {
        Assertions.assertEquals(idSegmentDistributorDefinition.getOffset(),DEFAULT_OFFSET);
    }

    @Test
    void getStep() {
        Assertions.assertEquals(idSegmentDistributorDefinition.getStep(),DEFAULT_STEP);
    }
}
