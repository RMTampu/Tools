package com.toolbox.tools.build;

import org.junit.Test;

import static org.junit.Assert.*;

public final class CandidateIdentityTest {
    private static final String PARENT =
            "8f6f504c8f289926ad88550ab2686b801efc3ac12536c9e57f807b208461a116";
    private static final String IR =
            "1111111111111111111111111111111111111111111111111111111111111111";
    private static final String APK =
            "2222222222222222222222222222222222222222222222222222222222222222";

    @Test
    public void identityIsDeterministic() {
        CandidateIdentityFactory factory =
                new CandidateIdentityFactory();

        CandidateIdentity first = factory.create(
                "com.toolbox.tools",
                10,
                "10.0-tahap10-dev",
                PARENT,
                IR,
                APK
        );
        CandidateIdentity second = factory.create(
                "com.toolbox.tools",
                10,
                "10.0-tahap10-dev",
                PARENT,
                IR,
                APK
        );

        assertEquals(first.sha256(), second.sha256());
        assertEquals(
                first.candidateId(),
                second.candidateId()
        );
        assertTrue(first.sha256().matches("[0-9a-f]{64}"));
        assertTrue(first.candidateId().startsWith(
                "candidate."
        ));
    }

    @Test
    public void everyIdentityInputChangesCandidate() {
        CandidateIdentityFactory factory =
                new CandidateIdentityFactory();
        CandidateIdentity base = factory.create(
                "com.toolbox.tools",
                10,
                "10.0-tahap10-dev",
                PARENT,
                IR,
                APK
        );

        assertNotEquals(
                base.sha256(),
                factory.create(
                        "com.toolbox.tools",
                        11,
                        "10.0-tahap10-dev",
                        PARENT,
                        IR,
                        APK
                ).sha256()
        );
        assertNotEquals(
                base.sha256(),
                factory.create(
                        "com.toolbox.tools",
                        10,
                        "10.0-tahap10-dev",
                        PARENT,
                        "3333333333333333333333333333333333333333333333333333333333333333",
                        APK
                ).sha256()
        );
        assertNotEquals(
                base.sha256(),
                factory.create(
                        "com.toolbox.tools",
                        10,
                        "10.0-tahap10-dev",
                        PARENT,
                        IR,
                        "4444444444444444444444444444444444444444444444444444444444444444"
                ).sha256()
        );
    }
}
