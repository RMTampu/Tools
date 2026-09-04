package com.toolbox.tools.product;

import org.junit.Test;

import java.util.Collections;
import java.util.EnumSet;

import static org.junit.Assert.assertFalse;

public final class FullProductVerifierNegativeTest {
    @Test
    public void kekuranganWajibHarusMemblokirProduk() {
        FullProductVerifier.Result result =
                new FullProductVerifier.Result(
                        EnumSet.noneOf(ProductCapability.class),
                        Collections.emptyList()
                );
        assertFalse(result.isPass());
    }
}
