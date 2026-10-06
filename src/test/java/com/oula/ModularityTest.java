package com.oula;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {
    @Test
    void verifiesModuleBoundaries() {
        ApplicationModules.of(OulaApplication.class).verify();
    }
}
