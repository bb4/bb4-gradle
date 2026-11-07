# Configuration Cache Validation Consumer

This test consumer validates that the bb4-gradle plugins work correctly with Gradle's configuration cache.

## What It Tests

This project exercises the three specific fixes for issue #21:

1. **Bb4ScalaLibraryPlugin**: The `clean` task now uses declarative inputs instead of capturing `project` at execution time
2. **Bb4PublishPlugin**: The `javadoc` task's `doLast` action uses injected `FileSystemOperations` instead of capturing `project` and `SourceSet`
3. **Bb4ApplicationPlugin**: The `deployHtml` task models `appMap` as an explicit input, ensuring changes invalidate the cache

## Running the Validation

### Initial Build with Configuration Cache
```bash
./gradlew clean deployHtml javadoc --configuration-cache
```

This should:
- Calculate the task graph (first run)
- Execute the tasks
- Store the configuration cache entry
- Generate HTML with table rows from `appMap`

### Verify Cache Reuse
```bash
./gradlew clean deployHtml javadoc --configuration-cache
```

This should:
- Reuse the configuration cache
- Execute only tasks that need to run (clean always runs, others may be UP-TO-DATE)
- Show "Configuration cache entry reused"

### Verify appMap Changes Invalidate Cache

Modify the `appMap` in `build.gradle.kts` (change titles, add/remove entries), then run:
```bash
./gradlew deployHtml --configuration-cache
```

This should:
- Recalculate the configuration cache (because appMap input changed)
- Execute deployHtml
- Generate updated HTML reflecting the new appMap values

## Expected Outcomes

✅ No configuration cache problems reported  
✅ Cache is stored on first run  
✅ Cache is reused on subsequent runs when inputs haven't changed  
✅ Cache is invalidated when `appMap` changes  
✅ Generated HTML correctly includes table rows from `appMap`

## What Was Not Changed

Per issue #21, this validation focuses only on the three specific execution-time Project access patterns. It does NOT:
- Redesign what `clean` deletes (issue #20)
- Wire deploy/dist paths to archive providers (issue #22)
- Create a general TestKit framework
- Remove all uses of `afterEvaluate` or reflection
