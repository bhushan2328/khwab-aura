package com.toblad.khwab.aura.world

/**
 * @deprecated This [WorldUpdater] implementation duplicates logic already present in
 * [com.toblad.khwab.aura.engine.TimeEngine], which is used by [com.toblad.khwab.aura.engine.WorldSimulationEngine].
 * No code in the current codebase calls this class. It is retained to avoid an API-surface removal
 * before a planned cleanup cycle. Do not add logic here.
 */
@Deprecated(
    message = "Use TimeEngine (via WorldSimulationEngine / AuraEngine) instead. " +
              "This duplicate will be removed in a future version.",
    replaceWith = ReplaceWith(
        expression = "TimeEngine",
        imports = ["com.toblad.khwab.aura.engine.TimeEngine"]
    ),
    level = DeprecationLevel.WARNING
)
class TimeSimulation : WorldUpdater {

    override fun update(world: AuraWorld, deltaSeconds: Float): AuraWorld {
        val current = world.time
        var hour   = current.hour
        var minute = current.minute
        var second = current.second + deltaSeconds.toInt()
        while (second >= 60) { second -= 60; minute++ }
        while (minute >= 60) { minute -= 60; hour++ }
        while (hour >= 24)   { hour -= 24 }
        return world.copy(time = current.copy(hour = hour, minute = minute, second = second))
    }
}
