# Szablony modułów feature

## `feature/{{feature}}/api/build.gradle.kts`

```kotlin
plugins { id("am.feature.api") }

dependencies {
    api(project(":core:domain"))
    testFixturesApi(project(":core:testing"))
}
```

## `feature/{{feature}}/api/src/main/kotlin/dev/agentmanager/{{pkg}}/api/{{Feature}}Api.kt`

```kotlin
package dev.agentmanager.{{pkg}}.api

/**
 * Public contract of the {{feature}} feature.
 * Threading: all members are safe to call from any dispatcher.
 */
interface {{Feature}}Api
```

## `feature/{{feature}}/api/src/testFixtures/kotlin/dev/agentmanager/{{pkg}}/api/{{Feature}}ApiContract.kt`

```kotlin
package dev.agentmanager.{{pkg}}.api

/** Contract every [{{Feature}}Api] implementation (fake and real) must satisfy. */
abstract class {{Feature}}ApiContract {
    protected abstract fun create(): {{Feature}}Api
}
```

## `feature/{{feature}}/api/src/testFixtures/kotlin/dev/agentmanager/{{pkg}}/api/Fake{{Feature}}Api.kt`

```kotlin
package dev.agentmanager.{{pkg}}.api

/** In-memory fake for tests of other modules. Must pass [{{Feature}}ApiContract]. */
class Fake{{Feature}}Api : {{Feature}}Api
```

## `feature/{{feature}}/impl/build.gradle.kts`

```kotlin
plugins { id("am.feature.impl") }

dependencies {
    implementation(project(":feature:{{feature}}:api"))
    implementation(project(":core:domain"))
    testImplementation(testFixtures(project(":feature:{{feature}}:api")))
}
```

## `feature/{{feature}}/impl/src/main/kotlin/dev/agentmanager/{{pkg}}/impl/{{Feature}}Module.kt`

```kotlin
package dev.agentmanager.{{pkg}}.impl

import dev.agentmanager.{{pkg}}.api.{{Feature}}Api

/** The only public entry point of this module; called from the composition root in `app`. */
fun {{feature-camel}}Module(/* deps */): {{Feature}}Api = Default{{Feature}}Api()

internal class Default{{Feature}}Api : {{Feature}}Api
```

## `feature/{{feature}}/ui/build.gradle.kts`

```kotlin
plugins { id("am.feature.ui") }

dependencies {
    implementation(project(":feature:{{feature}}:api"))
    implementation(project(":core:design-system"))
}
```
