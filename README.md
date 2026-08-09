# Khwab Aura

Android ambient background library for the Khwab ecosystem.

Aura renders a living sky behind the Khwab application — reacting to real time of day, live weather, season, and location. It is a purely visual module with no network access and no Android permissions of its own.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [Public API](#public-api)
- [Integration](#integration)
- [Lifecycle](#lifecycle)
- [Configuration](#configuration)
- [Environment Model](#environment-model)
- [Weather](#weather)
- [Time & Solar Calculations](#time--solar-calculations)
- [Performance](#performance)
- [Testing](#testing)
- [Example Usage](#example-usage)

---

## Overview

Aura is a standalone Android library module (`com.toblad.khwab.aura`) that provides a `@Composable` `AuraScene` widget. The scene renders as a full-screen sky background that changes smoothly based on:

- Current time of day (real device clock)
- Season and hemisphere
- Weather conditions (supplied by the host app)
- GPS-based sunrise/sunset and moon phase (when location is available)

The host app communicates with Aura entirely through the `AuraApi` interface, which is obtained from `AuraModule.get()`.

---

## Features

| Feature | Details |
|---|---|
| Sky gradient | Animated gradient transitions across 13 sky styles (dawn → midnight) |
| Stars | Twinkling 120-star field during night phases |
| Sun | Radial disc with corona, dawn/sunset ray spokes, solar-arc position |
| Moon | Phase-correct rendering (crescent → full) with halo |
| Clouds | Procedural cluster-puffs, wind-speed aware drift |
| Weather effects | Animated rain, snow, fog bands, lightning flash |
| Seasonal particles | Spring petals, autumn leaves, summer fireflies/pollen, winter frost |
| Birds | V-formation flocks, season-aware population |
| Ambient lighting | Computed lighting tint + darkening overlay |
| Lightning | Synchronized flash events via `LightningBus` |
| Solar position | Accurate sunrise/sunset from GPS coordinates using the sunrise equation |
| Moon phase | Real synodic-month phase calculation |
| Lifecycle aware | Animations pause when screen is not visible |

---

## Architecture

### Runtime Flow

```
AuraModule.get()
      │
      ▼
AuraManager (AuraApi)
      │  StateFlow<AuraTheme>
      ▼
AuraEngine
      │
      ├── WeatherEngine (single shared instance)
      │
      ├── WorldSimulationEngine
      │     ├── TimeEngine          (advances simulated time)
      │     ├── WeatherEngine       (same instance as above)
      │     └── LightingEngine      (Time + Weather → LightingState)
      │
      ├── LightingEngine            (computes AuraTheme.lighting)
      ├── TimePhaseEngine           (Time → TimePhase)
      ├── ThemeEngine               (all inputs → AuraTheme)
      ├── SolarCalculator           (lat/lon → sunrise/sunset)
      ├── MoonPhaseCalculator       (date → MoonStyle)
      └── SeasonEngine              (date + lat → Season)
                │
                ▼
           AuraTheme  ──────────────►  StateFlow<AuraTheme>
                │
                ▼
           AuraScene (Compose)
                │
      ┌─────────┼─────────┐
      ▼         ▼         ▼
    SkyLayer  CloudLayer  SunLayer
    StarLayer MoonLayer   BirdLayer
    WeatherLayer SeasonLayer AnimationLayer
                │
                ▼
            LightLayer (renders AuraTheme.lighting)
```

### Design Principle

> **Simulation calculates. State stores. Compose renders.**

- `AuraEngine` (and its sub-engines) compute all environmental state.
- `AuraTheme` carries the complete authoritative state for one render cycle.
- Compose layers read `AuraTheme` and render; they do not independently recalculate environment, time, or lighting.

### Single Sources of Truth

| Concern | Owner |
|---|---|
| Weather state | `WeatherEngine` (single instance shared by `AuraEngine` and `WorldSimulationEngine`) |
| Lighting state | `LightingEngine` inside `AuraEngine.generateTheme()` → stored in `AuraTheme.lighting` |
| Time state | `TimeEngine` inside `WorldSimulationEngine`, or `TimeState.now()` (real device clock) |
| Time phase | `TimePhaseEngine` |
| Season | `SeasonEngine.calculate(latitude, date)` |
| Moon phase | `MoonPhaseCalculator.calculate(date)` |
| Solar times | `SolarCalculator.calculate(lat, lon, date)` |
| Theme | `ThemeEngine.createTheme(...)` |
| Reactive state | `AuraManager._themeFlow: MutableStateFlow<AuraTheme>` |

---

## Public API

The public API surface is `AuraApi`, obtained via `AuraModule.get()`.

```kotlin
interface AuraApi {

    /** Reactive stream of the current theme. Collect in Compose with collectAsStateWithLifecycle(). */
    val themeFlow: StateFlow<AuraTheme>

    fun activate()
    fun deactivate()
    fun toggle()
    fun isActive(): Boolean
    fun getState(): AuraState
    fun getTheme(): AuraTheme          // synchronous snapshot
    fun getConfig(): AuraConfig
    fun updateConfig(config: AuraConfig)
    fun updateWeather(weather: WeatherState)
    fun refresh()
}
```

### Key Types

| Type | Package | Role |
|---|---|---|
| `AuraApi` | `api` | Public contract |
| `AuraModule` | root | Singleton entry point |
| `AuraConfig` | `model` | All configuration |
| `AuraTheme` | `model` | Complete visual state |
| `AuraState` | `model` | Lifecycle state (OFF / ACTIVE / PAUSED / …) |
| `WeatherState` | `model` | Weather enum (CLEAR / RAIN / SNOW / FOG / STORM / CLOUDY) |
| `AuraScene` | `ui` | Root `@Composable` |
| `LightningBus` | `ui` | Shared lightning event stream |

---

## Integration

### 1. Gradle dependency

The Aura module is included as a project dependency:

```kotlin
// khwab/settings.gradle.kts
include(":aura")
project(":aura").projectDir = file("../khwab-aura/aura")
```

```kotlin
// app/build.gradle.kts
implementation(project(":aura"))
```

### 2. Initialisation

```kotlin
// Application class or ViewModel init
val aura: AuraApi = AuraModule.get()
aura.activate()
```

### 3. Displaying the scene

```kotlin
@Composable
fun MyScreen() {
    val aura = remember { AuraModule.get() }
    val theme by aura.themeFlow.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        AuraScene(theme = theme, modifier = Modifier.fillMaxSize())
        // Your UI content on top
    }
}
```

### 4. Supplying location (optional)

```kotlin
// After obtaining GPS coordinates:
aura.updateConfig(
    aura.getConfig().copy(
        latitude  = location.latitude,
        longitude = location.longitude
    )
)
```

This enables solar-accurate sunrise/sunset and hemisphere-aware seasons.

### 5. Supplying weather (optional)

```kotlin
// From your weather API response:
aura.updateWeather(WeatherState.RAIN)
```

### 6. Connecting lightning to ambient sound

```kotlin
// In your AmbientSoundController:
scope.launch {
    LightningBus.flashes.collect {
        playThunderSound()
    }
}
```

---

## Lifecycle

### Activation States

| State | Meaning |
|---|---|
| `OFF` | Aura is disabled, theme has `enabled = false` |
| `ACTIVE` | Aura is running, theme reflects real environment |
| `PAUSED` | Engine is paused (frame updates suspended) |
| `STARTING` / `STOPPING` | Transitional states |
| `ERROR` | Fatal error state |

### Screen visibility

All animation coroutines check `rememberIsResumed()`:

```
Screen visible      → all loops run normally
Screen backgrounded → LaunchedEffect gates return early
Screen resumed      → loops restart automatically
```

This prevents wasted CPU/battery when Aura is not visible.

### LightningBus

`LightningBus.reset()` is called automatically on `deactivate()` to stop the lightning ticker. It restarts the next time a storm theme is active and `WeatherLayer` calls `LightningBus.update(stormActive = true, ...)`.

---

## Configuration

```kotlin
data class AuraConfig(
    val enabled: Boolean = false,          // master on/off
    val autoTime: Boolean = true,          // use real device clock (vs simulated time)
    val autoWeather: Boolean = true,       // use weather supplied via updateWeather()
    val animationsEnabled: Boolean = true, // enable/disable all animation loops
    val ambientSoundEnabled: Boolean = true, // hint to host app
    val refreshIntervalMinutes: Int = 5,   // hint to host app for polling interval
    val latitude: Double? = null,          // GPS latitude for solar calculations
    val longitude: Double? = null,         // GPS longitude for solar calculations
    val stormIntensity: Float = 0.5f       // 0..1 storm severity from weather provider
)
```

### Configuration Behaviour

| Field | Implemented | Effect |
|---|---|---|
| `enabled` | ✅ | Drives `AuraState` and `AuraTheme.enabled` |
| `autoTime` | ✅ | `true` = always use `TimeState.now()` (real clock); `false` = use simulated world time |
| `autoWeather` | ✅ | `false` = ignore `updateWeather()` calls, use CLEAR |
| `animationsEnabled` | ✅ | `false` = all animation coroutine loops are gated off |
| `ambientSoundEnabled` | 📋 | Exposed to host app in `AuraTheme`; sound implementation is host-app responsibility |
| `refreshIntervalMinutes` | 📋 | Hint for host app polling frequency; not enforced inside Aura |
| `latitude` / `longitude` | ✅ | Enables solar-accurate sunrise/sunset and hemisphere-aware season |
| `stormIntensity` | ✅ | Propagated into `ThemeProfile.stormIntensity` → rain density / lightning frequency |

---

## Environment Model

### TimePhase

Aura uses nine time-of-day phases:

```
PRE_DAWN → SUNRISE → MORNING → NOON → AFTERNOON → SUNSET → EVENING → NIGHT → MIDNIGHT
```

When GPS coordinates are available, phase boundaries track the real local sunrise/sunset. Otherwise a fixed approximate schedule is used.

### LightingState

`AuraTheme.lighting` carries the authoritative scene brightness:

```kotlin
data class LightingState(
    val intensity: Float,  // 0.0 (dark) → 1.0 (full sun)
    val ambient: Float     // same value — reserved for future independent use
)
```

The `LightLayer` composable reads this to apply a darkening overlay: night and storms are visibly darker than clear midday.

### LocationState

Location is passed directly as `latitude`/`longitude` in `AuraConfig`. The architecture is ready for a future `LocationProvider` abstraction (GPS, network, cached, manual) — Aura does not care how the coordinates were obtained.

---

## Weather

Weather is supplied by the host app via `updateWeather(WeatherState)`.

### WeatherState

```kotlin
enum class WeatherState { CLEAR, CLOUDY, RAIN, SNOW, FOG, STORM }
```

### Effect on rendering

| WeatherState | Sky | Clouds | Weather particles | Lighting multiplier |
|---|---|---|---|---|
| CLEAR | Time-based | CLEAR / FEW | None | 1.00 |
| CLOUDY | Time-based | SCATTERED | None | 0.80 |
| RAIN | Time-based | OVERCAST | Rain drops | 0.65 |
| SNOW | Time-based | OVERCAST | Snowflakes | 0.90 |
| FOG | Time-based | OVERCAST | Fog bands | 0.60 |
| STORM | STORM sky | STORM | Heavy rain + lightning | 0.45 |

### Ownership

There is **one** `WeatherEngine` instance. It is created by `AuraEngine` and passed into `WorldSimulationEngine`. Both the theme-generation path and the simulation update path always see the same weather value.

---

## Time & Solar Calculations

### SolarCalculator

Pure math — no network, no third-party dependency.

```kotlin
val times = SolarCalculator.calculate(
    latitude  = 51.5,
    longitude = -0.12,
    date      = Calendar.getInstance()
)
// times?.sunriseHour  (e.g. 6.42 = 06:25)
// times?.sunsetHour   (e.g. 21.38 = 21:23)
// returns null during polar day or polar night
```

### MoonPhaseCalculator

Uses the known synodic month (29.53 days) and a reference new moon.

```kotlin
val phase: MoonStyle = MoonPhaseCalculator.calculate()
// HIDDEN | CRESCENT | HALF | GIBBOUS | FULL
```

### SeasonEngine

```kotlin
val season: Season = SeasonEngine.calculate(latitude = 51.5)
// SPRING | SUMMER | AUTUMN | WINTER
// Hemisphere is inferred from latitude sign — negative = southern hemisphere
```

---

## Performance

### Animation

- Every `LaunchedEffect` animation loop is gated on **both** `isResumed` (screen visible) and `theme.animationsEnabled` (config flag).
- Loops stop immediately when the screen is backgrounded and restart cleanly when it returns.
- No duplicate loops — each visual layer has at most one ticker loop.

### Object allocation

- Particle types in `WeatherLayer`, `SeasonLayer` are **mutable classes** updated in-place (not data classes), avoiding per-frame GC pressure.
- `AuraWorld` uses Kotlin `data class copy()` for immutable simulation steps.

### LightningBus

- `Mutex` prevents duplicate ticker job creation even if multiple callers race.
- Job is cancelled on `deactivate()`.

### Coroutine scopes

- `LightningBus` uses a module-level `CoroutineScope(SupervisorJob() + Dispatchers.Default)`.
- Compose animation loops use their own `LaunchedEffect` scopes — no long-lived coroutines escape into the host app.

---

## Testing

All tests are in `aura/src/test/`:

| Test class | Coverage |
|---|---|
| `SolarCalculatorTest` | Sunrise/sunset calculation, polar edge cases, day length ordering |
| `TimePhaseEngineTest` | All 9 phases in fixed mode; sunrise/morning/noon/afternoon/sunset/night in solar mode |
| `SeasonEngineTest` | All 4 seasons, both hemispheres, null latitude fallback |
| `LightingEngineTest` | Intensity range, midday > midnight, night cap, all weather multipliers |
| `WeatherEngineTest` | All WeatherState values, multiple updates, default |
| `ThemeEngineTest` | Night/clear/rain/snow/storm profiles, lighting passthrough, animationsEnabled, isSolarAccurate, season |
| `AuraManagerLifecycleTest` | activate/deactivate/toggle, StateFlow, config, weather, autoWeather=false |
| `MoonPhaseCalculatorTest` | Phase fraction range, known new/full moon dates, consistency |
| `AuraEngineWeatherSharedTest` | Single shared WeatherEngine, autoWeather=false, lighting changes with weather |

Run tests:

```bash
./gradlew :aura:testDebugUnitTest
```

---

## Example Usage

```kotlin
// 1. In your Application or dependency injection setup:
val aura: AuraApi = AuraModule.get()

// 2. Configure with location (after requesting permission):
aura.updateConfig(
    AuraConfig(
        enabled = true,
        autoTime = true,
        autoWeather = true,
        latitude = userLocation.latitude,
        longitude = userLocation.longitude
    )
)
aura.activate()

// 3. Supply weather updates from your weather provider:
aura.updateWeather(WeatherState.RAIN)

// 4. In your Compose screen:
@Composable
fun HomeScreen() {
    val theme by AuraModule.get().themeFlow.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        // Aura background layer
        AuraScene(
            theme = theme,
            modifier = Modifier.fillMaxSize()
        )

        // Your UI content on top
        HomeContent()
    }
}

// 5. Connect lightning to sound (optional):
class AmbientSoundController(private val scope: CoroutineScope) {
    fun start() {
        scope.launch {
            LightningBus.flashes.collect {
                playThunderSound()
            }
        }
    }
}

// 6. Deactivate when no longer needed:
aura.deactivate()
```
