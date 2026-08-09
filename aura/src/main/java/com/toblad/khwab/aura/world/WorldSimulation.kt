package com.toblad.khwab.aura.world

/**
 * @deprecated This class is a placeholder that does nothing meaningful.
 * The authoritative simulation coordinator is [com.toblad.khwab.aura.engine.WorldSimulationEngine],
 * which is owned and driven by [com.toblad.khwab.aura.engine.AuraEngine].
 * This class is retained for API-surface compatibility until a clean removal cycle.
 * Do not add logic to this class — all simulation changes should go to WorldSimulationEngine.
 */
@Deprecated(
    message = "Use WorldSimulationEngine (via AuraEngine) instead. " +
              "This class is a no-op placeholder that will be removed in a future version.",
    replaceWith = ReplaceWith(
        expression = "WorldSimulationEngine",
        imports = ["com.toblad.khwab.aura.engine.WorldSimulationEngine"]
    ),
    level = DeprecationLevel.WARNING
)
class WorldSimulation(

    private var world: AuraWorld

) {

    /** Returns the current immutable world. */
    fun world(): AuraWorld = world

    /**
     * No-op. Does not advance any simulation.
     * Use [com.toblad.khwab.aura.engine.AuraEngine] / [com.toblad.khwab.aura.engine.WorldSimulationEngine].
     */
    fun update(deltaSeconds: Float) {
        // Intentional no-op — see class deprecation notice.
    }
}
