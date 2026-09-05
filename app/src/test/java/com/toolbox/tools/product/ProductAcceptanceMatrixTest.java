package com.toolbox.tools.product;

import com.toolbox.tools.core.AppKernel;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ProductAcceptanceMatrixTest {
    @Test
    public void all135DesignSectionsRequireBehaviorAndPass() {
        AppKernel kernel = AppKernel.createDefault();
        ProductAcceptanceMatrix.Result result =
                new ProductAcceptanceMatrix().evaluate(kernel);

        assertEquals(135, result.requiredCount());
        assertEquals(result.failures().toString(), 135, result.passedCount());
        assertTrue(result.failures().toString(), result.isPass());
    }

    @Test
    public void completionServicesCoverPreviouslyMissingDomains() {
        ProductCompletionServices services = new ProductCompletionServices();
        Set<String> pass = services.selfTest();

        assertEquals(
                ProductCompletionServices.REQUIRED_BEHAVIORS.size(),
                pass.size()
        );
        assertTrue(pass.containsAll(
                ProductCompletionServices.REQUIRED_BEHAVIORS
        ));
        assertTrue(services.isReady());
    }

    @Test
    public void deepContractsCloseFormerMetadataOnlyGaps() {
        ProductDeepContracts deep = new ProductDeepContracts();
        Set<String> pass = deep.selfTest();

        assertEquals(
                ProductDeepContracts.REQUIRED_BEHAVIORS.size(),
                pass.size()
        );
        assertTrue(pass.containsAll(
                ProductDeepContracts.REQUIRED_BEHAVIORS
        ));
        assertTrue(deep.isReady());
    }

    @Test
    public void behaviorGateIsRepeatable() {
        ProductCompletionServices services = new ProductCompletionServices();
        assertTrue(services.isReady());
        assertTrue(services.isReady());

        AppKernel kernel = AppKernel.createDefault();
        ProductAcceptanceMatrix.Result first =
                new ProductAcceptanceMatrix().evaluate(kernel);
        ProductAcceptanceMatrix.Result second =
                new ProductAcceptanceMatrix().evaluate(kernel);
        assertTrue(first.failures().toString(), first.isPass());
        assertTrue(second.failures().toString(), second.isPass());
    }
}
