package dev.chayeon

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.chayeon.core.engine.DemoCast
import dev.chayeon.core.model.*
import dev.chayeon.core.signature.DefaultSignatureEngine
import dev.chayeon.core.ui.*
import java.io.File
import kotlinx.serialization.json.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.hypot
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w2560dp-h600dp-land-mdpi")
class KnotIdentityTest {
    @get:Rule val compose = createComposeRule()
    private val engine = DefaultSignatureEngine()
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "fixtures/drive").isDirectory }

    private fun evidence(traits: List<PublicTrait>) = traits.map {
        TraitEvidence(it.trait.name, 1, it.trait, TraitAvailability.AVAILABLE, it.level,
            DataSource.SYNTHETIC, EvidenceWindow(0, 1), 3, 100, emptySet(), emptyList())
    }

    private fun examples(): List<Pair<String, List<TraitEvidence>>> {
        // DemoSignatureTest verifies these exact categorical results by replaying all three JSON drives.
        val results = Json.parseToJsonElement(File(root, "core/signature/src/test/resources/demo-results.json").readText()).jsonArray
        val demo = results.map { row ->
            val obj = row.jsonObject
            obj.getValue("title").jsonPrimitive.content to evidence(Json.decodeFromJsonElement<List<PublicTrait>>(obj.getValue("traits")))
        }
        return demo + DemoCast.peers.map { peer ->
            val evidence = evidence(peer.traits)
            assertEquals(engine.knot(evidence), peer.knotSeed)
            peer.nickname to evidence
        }
    }

    private fun silhouetteDistance(a: List<KnotPoint>, b: List<KnotPoint>): Double {
        fun nearest(first: List<KnotPoint>, second: List<KnotPoint>) = first.map { point ->
            second.minOf { hypot((point.x - it.x).toDouble(), (point.y - it.y).toDouble()) }
        }.average()
        return (nearest(a, b) + nearest(b, a)) / 2
    }

    @Test fun allEightSilhouettesDifferAndNearbyEvidenceRemainsRecognizable() {
        val examples = examples()
        val paths = examples.map { knotGeometry(engine.knot(it.second)) }
        var minimum = Double.MAX_VALUE
        for (a in paths.indices) for (b in a + 1 until paths.size) {
            val difference = silhouetteDistance(paths[a], paths[b])
            minimum = minOf(minimum, difference)
            assertTrue(difference >= .055, "${examples[a].first} / ${examples[b].first}: $difference")
        }
        examples.forEachIndexed { index, (_, evidence) ->
            assertEquals(paths[index], knotGeometry(engine.knot(evidence.reversed())))
            evidence.forEach { trait ->
                val next = if (trait.level == TraitLevel.SIGNATURE) TraitLevel.STEADY else TraitLevel.entries[trait.level.ordinal + 1]
                val changed = evidence.map { if (it.trait == trait.trait) it.copy(level = next) else it }
                val difference = silhouetteDistance(paths[index], knotGeometry(engine.knot(changed)))
                assertTrue(difference < .16, "One categorical step changed the silhouette by $difference")
            }
        }
        println("KNOT minimum pairwise silhouette distance=$minimum; required >=0.055, one-level change <0.16")
    }

    @Test fun eightSignatureContactSheet() {
        val examples = examples()
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
                ChayeonBackground(Modifier.requiredSize(2560.dp, 520.dp).testTag("eight-knots")) {
                    Row(Modifier.fillMaxSize().padding(ChayeonSpacing.Medium)) {
                        examples.forEachIndexed { index, (label, evidence) ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                                ChayeonText(if (index < 3) "합성 주행" else "시연용 가상 상대", style = ChayeonType.Minimum, color = ChayeonColors.Pink)
                                SignatureKnot(engine.knot(evidence), Modifier.size(ChayeonDimensions.HeroKnot), reveal = false)
                                ChayeonText(label, style = ChayeonType.Secondary)
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("eight-knots").captureRoboImage("src/test/screenshots/signature-eight-contact-sheet.png")
    }
}
