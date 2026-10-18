// OWASP Dependency-Check over every project's dependencies:
//   ./gradlew dependencyCheckAggregate
// It downloads the NVD vulnerability database first; set NVD_API_KEY (free, from
// https://nvd.nist.gov/developers/request-an-api-key) or it crawls for a very long time.
plugins {
    id("org.owasp.dependencycheck") version "13.0.0"
}

dependencyCheck {
    nvd {
        apiKey = System.getenv("NVD_API_KEY")
    }
    failBuildOnCVSS = 7.0f
    formats = listOf("HTML", "SARIF")
}
