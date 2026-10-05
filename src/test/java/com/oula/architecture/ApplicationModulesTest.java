package com.oula.architecture;
import com.oula.OulaApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
class ApplicationModulesTest {
  @Test void modulesRespectDeclaredBoundaries() { ApplicationModules.of(OulaApplication.class).verify(); }
}
