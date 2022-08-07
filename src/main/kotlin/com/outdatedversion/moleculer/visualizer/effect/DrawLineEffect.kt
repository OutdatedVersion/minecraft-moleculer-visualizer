package com.outdatedversion.moleculer.visualizer.effect

import de.slikey.effectlib.Effect
import de.slikey.effectlib.EffectManager
import de.slikey.effectlib.EffectType
import org.bukkit.Particle
import org.bukkit.util.Vector
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class DrawLineEffect(effectManager: EffectManager): Effect(effectManager) {
    private var step = 0.0
    private var lastPoint: Vector? = null

    init {
        this.type = EffectType.REPEATING
        this.period = 1
        // `iterations` is effectively the timeout
        this.iterations = 100
    }

    private fun getPointOnLine(distance: Double, line: Pair<Vector, Vector>): Vector {
        val (one, two) = line
        // https://math.stackexchange.com/a/83405
        val d = sqrt((two.x - one.x).pow(2) + (two.y - one.y).pow(2) + (two.z + one.z).pow(2))
        val u = abs(distance / d)
        return Vector(
            (1 - u) * one.x + u * two.x,
            (1 - u) * one.y + u * two.y,
            (1 - u) * one.z + u * two.z,
        )
    }

    override fun onRun() {
        val loc = if (lastPoint != null) lastPoint!! else location.toVector()
        val target = getTarget().toVector()

        val distance = loc.distance(target)
        if (distance <= 0.8) {
            return this.cancel()
        }

        val nextPoint = getPointOnLine(distance = this.step, line = Pair(loc, target))
        val nextPointLoc = location.clone().set(nextPoint.x, nextPoint.y, nextPoint.z)

        display(Particle.REDSTONE, nextPointLoc, 1f, 5)

        lastPoint = nextPoint
        step += 0.4
    }
}