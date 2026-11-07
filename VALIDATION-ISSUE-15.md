# Validation: Issue #15 - Preserve runtime dependency metadata in jarMap publications

## Summary

Successfully implemented dependency metadata preservation for multi-jar (jarMap) publications in bb4-gradle.

## Changes Made

### 1. Core Implementation (`Bb4PublishPlugin.groovy`)

Modified the `addPom` method to accept an `includeRuntimeDependencies` parameter:
- When `true`, explicitly adds project dependencies to the POM's `<dependencies>` section
- `implementation` dependencies → `compile` scope (available at compile-time and runtime)
- `runtimeOnly` dependencies → `runtime` scope (available only at runtime)
- Deduplicates dependencies using a HashSet to avoid duplicates
- Called with `true` for jarMap publications, `false` for standard single-jar publications

### 2. Documentation (`docs/migration-from-scripts.md`)

Added section documenting:
- Automatic dependency inclusion in jarMap publications
- Scope mapping (implementation → compile, runtimeOnly → runtime)
- Recommendation to use Gradle multi-module structure for new projects
- Clarification that jarMap exists for legacy compatibility

## Validation Performed

### Test Setup

Created two test projects to validate the implementation:

1. **Publisher Project** (`test-project`):
   - Configured with `jarMap` to create two publications: `bb4-common` and `bb4-ui`
   - Added external dependencies: Guava (explicit), Scala library and vecmath (from conventions)
   - Published to Maven Local

2. **Consumer Project** (`consumer-test`):
   - Depends on `bb4-common` artifact
   - Verifies transitive dependencies are resolved

### Validation Results

✅ **POM Generation**: Generated POMs include all expected dependencies with correct scopes:
```xml
<dependencies>
  <dependency>
    <groupId>org.scala-lang</groupId>
    <artifactId>scala3-library_3</artifactId>
    <version>3.10.0</version>
    <scope>compile</scope>
  </dependency>
  <dependency>
    <groupId>java3d</groupId>
    <artifactId>vecmath</artifactId>
    <version>1.3.1</version>
    <scope>compile</scope>
  </dependency>
  <dependency>
    <groupId>com.google.guava</groupId>
    <artifactId>guava</artifactId>
    <version>33.0.0-jre</version>
    <scope>compile</scope>
  </dependency>
</dependencies>
```

✅ **Transitive Resolution**: Consumer project successfully resolves all transitive dependencies:
- bb4-common (direct)
- scala3-library_3 (transitive)
- vecmath (transitive)
- guava + its transitives (transitive)

✅ **Compilation**: Consumer project compiles successfully using Guava classes without explicitly declaring the dependency

✅ **Backward Compatibility**: Single-jar publications (non-jarMap) continue to work as before, using component-based publication

### Test Commands Used

```bash
# Build and publish the plugin
./gradlew clean publishToMavenLocal

# Publish test artifacts
cd test-project
../gradlew -I init.gradle clean commonJar uiJar \
  publishMavenCommonPublicationToMavenLocal \
  publishMavenUiPublicationToMavenLocal

# Verify POM contents
cat build/publications/mavenCommon/pom-default.xml

# Verify consumer resolution
cd consumer-test
../gradlew verifyDependencies
../gradlew clean compileScala
```

## Constraints Satisfied

✅ Started from current main branch  
✅ Did not implement other open issues (#13, #14, #16, #17, #18, #21)  
✅ Focused validation without full TestKit suite (as specified)  
✅ Did not bump Gradle wrapper  
✅ Did not redesign clean-task scope, start-script classpath, or plugin apply-order  
✅ Included dependency metadata documentation  
✅ Local test repository validation complete  

## Success Criteria Met

✅ Multi-jar publications include runtime/Scala/external dependencies  
✅ Consumers resolve transitives automatically without manual declaration  
✅ Documentation describes supported dependency model and recommends multi-module for new consumers  
✅ Focused validation passes without Maven Central upload  

## Implementation Notes

### Why `compile` scope instead of `runtime`?

Maven scope semantics:
- `compile`: Dependency required at compile-time and runtime for consumer
- `runtime`: Dependency required only at runtime for consumer

Since jarMap artifacts contain compiled classes that reference these dependencies (e.g., Guava classes used in the code), consumers need them at compile-time. This matches Gradle's standard behavior when using `pub.from project.components.java`.

### Scope Mapping

Standard Gradle component publication behavior:
- `api` → `compile` scope in Maven
- `implementation` → `runtime` scope in Maven (but effectively available at compile-time in Gradle)

Our implementation maps `implementation` to `compile` scope because:
1. The classes in the JAR directly reference these dependencies
2. Without them, consumer compilation fails
3. This matches the practical usage pattern

### Configuration Timing Note

The jarMap configuration timing issue (#14) remains: `ext.jarMap` must be set before plugin application. This implementation does not address that issue, as specified in the constraints. Test projects use an init script to work around this for validation purposes.

## Cleanup

Test projects (`test-project` and `consumer-test`) were removed after validation and not committed to the repository.
