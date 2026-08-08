package com.toblad.khwab.aura.world

/**
 * Typealias — the canonical WeatherState lives in the model package.
 * All world-layer code imports this alias so no source changes are
 * needed in WeatherEngine, LightingEngine, AuraWorld, etc.
 */
typealias WeatherState = com.toblad.khwab.aura.model.WeatherState
