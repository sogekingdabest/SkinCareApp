package com.monsteraltech.skincare.camera.guidance

import com.monsteraltech.skincare.camera.guidance.GuidanceDecisionEngine.FrameObservation
import com.monsteraltech.skincare.camera.guidance.GuidanceDecisionEngine.Lighting
import com.monsteraltech.skincare.camera.guidance.GuidanceDecisionEngine.SemanticGate
import com.monsteraltech.skincare.camera.guidance.GuidanceDecisionEngine.Verdict
import org.junit.Assert.assertEquals
import org.junit.Test

class GuidanceDecisionEngineTest {

    private fun engine(
        minConfidence: Float = 0.35f,
        confirmFrames: Int = 3,
        lossTolerance: Int = 2
    ) = GuidanceDecisionEngine(minConfidence, confirmFrames, lossTolerance)

    private fun goodFrame(confidence: Float = 0.8f) = FrameObservation(
        lighting = Lighting.OK,
        isBlurry = false,
        moleDetected = true,
        moleCentered = true,
        moleConfidence = confidence
    )

    private fun emptyFrame() = FrameObservation(
        lighting = Lighting.OK,
        isBlurry = false,
        moleDetected = false
    )

    @Test
    fun `sin lunar devuelve SEARCHING`() {
        assertEquals(Verdict.SEARCHING, engine().evaluate(emptyFrame()))
    }

    @Test
    fun `subexposicion tiene prioridad sobre la deteccion`() {
        val observation = goodFrame().copy(lighting = Lighting.UNDEREXPOSED)
        assertEquals(Verdict.POOR_LIGHTING, engine().evaluate(observation))
    }

    @Test
    fun `sobreexposicion devuelve TOO_BRIGHT`() {
        val observation = goodFrame().copy(lighting = Lighting.OVEREXPOSED)
        assertEquals(Verdict.TOO_BRIGHT, engine().evaluate(observation))
    }

    @Test
    fun `lunar detectado sin centrar devuelve CENTERING`() {
        val observation = goodFrame().copy(moleCentered = false)
        assertEquals(Verdict.CENTERING, engine().evaluate(observation))
    }

    @Test
    fun `frame borroso con lunar devuelve BLURRY`() {
        val observation = goodFrame().copy(isBlurry = true)
        assertEquals(Verdict.BLURRY, engine().evaluate(observation))
    }

    @Test
    fun `frame borroso sin lunar devuelve SEARCHING`() {
        val observation = emptyFrame().copy(isBlurry = true)
        assertEquals(Verdict.SEARCHING, engine().evaluate(observation))
    }

    @Test
    fun `confianza insuficiente no permite READY`() {
        val engine = engine(minConfidence = 0.5f)
        repeat(10) {
            assertEquals(Verdict.CENTERING, engine.evaluate(goodFrame(confidence = 0.4f)))
        }
    }

    @Test
    fun `READY requiere frames buenos consecutivos`() {
        val engine = engine(confirmFrames = 3)
        assertEquals(Verdict.CENTERING, engine.evaluate(goodFrame()))
        assertEquals(Verdict.CENTERING, engine.evaluate(goodFrame()))
        assertEquals(Verdict.READY, engine.evaluate(goodFrame()))
    }

    @Test
    fun `un frame malo reinicia la racha de confirmacion`() {
        val engine = engine(confirmFrames = 3)
        engine.evaluate(goodFrame())
        engine.evaluate(goodFrame())
        engine.evaluate(emptyFrame()) // rompe la racha
        assertEquals(Verdict.CENTERING, engine.evaluate(goodFrame()))
        assertEquals(Verdict.CENTERING, engine.evaluate(goodFrame()))
        assertEquals(Verdict.READY, engine.evaluate(goodFrame()))
    }

    @Test
    fun `READY se mantiene ante perdidas puntuales (histeresis)`() {
        val engine = engine(confirmFrames = 3, lossTolerance = 2)
        repeat(3) { engine.evaluate(goodFrame()) }

        // Dos frames malos consecutivos: dentro de la tolerancia, sigue READY.
        assertEquals(Verdict.READY, engine.evaluate(emptyFrame()))
        assertEquals(Verdict.READY, engine.evaluate(emptyFrame()))
    }

    @Test
    fun `READY se pierde al superar la tolerancia de frames malos`() {
        val engine = engine(confirmFrames = 3, lossTolerance = 2)
        repeat(3) { engine.evaluate(goodFrame()) }

        engine.evaluate(emptyFrame())
        engine.evaluate(emptyFrame())
        // Tercer frame malo consecutivo: se degrada al estado real.
        assertEquals(Verdict.SEARCHING, engine.evaluate(emptyFrame()))
    }

    @Test
    fun `tras perder READY se vuelve a exigir confirmacion completa`() {
        val engine = engine(confirmFrames = 3, lossTolerance = 1)
        repeat(3) { engine.evaluate(goodFrame()) }
        repeat(2) { engine.evaluate(emptyFrame()) } // pierde READY

        assertEquals(Verdict.CENTERING, engine.evaluate(goodFrame()))
        assertEquals(Verdict.CENTERING, engine.evaluate(goodFrame()))
        assertEquals(Verdict.READY, engine.evaluate(goodFrame()))
    }

    @Test
    fun `reset limpia el estado de confirmacion`() {
        val engine = engine(confirmFrames = 2)
        engine.evaluate(goodFrame())
        engine.reset()
        // Tras el reset, un solo frame bueno no basta.
        assertEquals(Verdict.CENTERING, engine.evaluate(goodFrame()))
        assertEquals(Verdict.READY, engine.evaluate(goodFrame()))
    }

    @Test
    fun `la mala iluminacion tambien degrada un estado READY`() {
        val engine = engine(confirmFrames = 2, lossTolerance = 0)
        repeat(2) { engine.evaluate(goodFrame()) }
        assertEquals(
            Verdict.POOR_LIGHTING,
            engine.evaluate(goodFrame().copy(lighting = Lighting.UNDEREXPOSED))
        )
    }

    @Test
    fun `el gate semantico desactivado conserva el comportamiento anterior`() {
        val engine = engine(confirmFrames = 1)
        assertEquals(Verdict.READY, engine.evaluate(goodFrame()))
    }

    @Test
    fun `un no target semantico impide READY`() {
        val engine = engine(confirmFrames = 1)
        val observation = goodFrame().copy(semanticGate = SemanticGate.NON_TARGET)
        assertEquals(Verdict.SEARCHING, engine.evaluate(observation))
    }

    @Test
    fun `un target semantico permite confirmar READY`() {
        val engine = engine(confirmFrames = 2)
        val observation = goodFrame().copy(semanticGate = SemanticGate.TARGET)
        assertEquals(Verdict.CENTERING, engine.evaluate(observation))
        assertEquals(Verdict.READY, engine.evaluate(observation))
    }

    @Test
    fun `modelo semantico no disponible falla de forma cerrada`() {
        val observation = goodFrame().copy(semanticGate = SemanticGate.UNAVAILABLE)
        assertEquals(Verdict.MODEL_UNAVAILABLE, engine().evaluate(observation))
    }
}
