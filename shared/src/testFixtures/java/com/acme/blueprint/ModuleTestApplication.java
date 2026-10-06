package com.acme.blueprint;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * Bootstrap for a single module's tests. An -impl project's test classpath
 * holds exactly one module (plus the -api projects it uses and :shared), so
 * {@code @ApplicationModuleTest} and {@code @WebMvcTest} find this class by
 * walking up from the test's package and boot that module in isolation.
 *
 * <p>:app does not see this fixture — it has its own
 * {@code BlueprintApplication}, and two {@code @SpringBootConfiguration}
 * classes in one package would be ambiguous.
 */
@SpringBootApplication
@Modulithic(sharedModules = "shared")
public class ModuleTestApplication {
}
