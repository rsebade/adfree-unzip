// Cloud sessions only (installed into ~/.gradle/init.d by scripts/setup-android-sdk.sh).
// Maven Central (repo.maven.apache.org) answers bursts of parallel requests from shared
// cloud egress with HTTP 429. Google's Maven Central mirror doesn't, so try it first;
// anything missing there falls through to the repositories declared in settings.gradle.kts.
val mirror = "https://maven-central.storage-download.googleapis.com/maven2/"

gradle.beforeSettings {
    pluginManagement.repositories.maven(mirror) { name = "MavenCentralGoogleMirror" }
    dependencyResolutionManagement.repositories.maven(mirror) { name = "MavenCentralGoogleMirror" }
}
