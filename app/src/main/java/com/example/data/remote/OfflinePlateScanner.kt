package com.example.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

data class LivePlateCandidate(
    val plateNumber: String,
    val stateOrRegion: String,
    val boundingBox: Rect?,
    val confidence: Float,
    val consensusHits: Int = 1,
    val isLockedAndReady: Boolean = false,
    val vehicleMake: String = "Vehicle",
    val vehicleModel: String = "Automotive",
    val vehicleType: String = "Passenger",
    val vehicleColor: String = "Silver",
    val engineUsed: String = "On-Device Optical ALPR"
)

data class PlateScanResult(
    val plateNumber: String,
    val stateOrRegion: String,
    val vehicleMake: String,
    val vehicleModel: String,
    val vehicleColor: String,
    val vehicleType: String,
    val confidence: Float,
    val notes: String,
    val engineUsed: String = "On-Device Optical ALPR",
    val boundingBox: Rect? = null
)

/** Minimum accepted confidence before a read is trusted enough to surface or log. */
const val MIN_ACCEPTABLE_CONFIDENCE = 0.60f

/** Minimum average positional character agreement across frames before a live consensus is trusted. */
private const val MIN_AGREEMENT_RATIO = 0.65f

fun levenshteinDistance(a: String, b: String): Int {
    val dp = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) dp[i][0] = i
    for (j in 0..b.length) dp[0][j] = j

    for (i in 1..a.length) {
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            dp[i][j] = minOf(
                dp[i - 1][j] + 1,
                dp[i][j - 1] + 1,
                dp[i - 1][j - 1] + cost
            )
        }
    }
    return dp[a.length][b.length]
}

/**
 * Production-grade Temporal Frame Accumulator & Consensus Voting Engine
 */
class TemporalPlateTracker {
    private data class FrameObservation(
        val rawText: String,
        val timestamp: Long,
        val box: Rect?,
        val state: String
    )

    private class TrackedPlateCluster(
        var consensusPlate: String,
        var state: String,
        var lastSeenTimestamp: Long,
        var bestBox: Rect?,
        val observations: MutableList<FrameObservation> = mutableListOf(),
        var isCommittedToDb: Boolean = false
    )

    private val activeClusters = mutableListOf<TrackedPlateCluster>()
    private val committedPlateCooldowns = ConcurrentHashMap<String, Long>()

    private val CLUSTER_EXPIRY_MS = 1400L
    private val COOLDOWN_AFTER_COMMIT_MS = 30_000L
    private val MIN_CONSENSUS_HITS_FOR_COMMIT = 3

    @Synchronized
    fun processFrameDetection(
        rawPlateCandidate: String,
        detectedState: String,
        box: Rect?
    ): LivePlateCandidate? {
        val now = System.currentTimeMillis()

        // Clean expired clusters
        activeClusters.removeAll { now - it.lastSeenTimestamp > CLUSTER_EXPIRY_MS }

        // Clean expired committed cooldowns
        committedPlateCooldowns.entries.removeIf { now - it.value > COOLDOWN_AFTER_COMMIT_MS }

        if (rawPlateCandidate.isBlank()) return null

        // Only sanitize the raw per-frame reading here. Do NOT force-rewrite characters
        // against an assumed plate format yet - a single frame is not enough evidence that
        // a character was misread, and doing so used to "correct" perfectly clean reads
        // (e.g. a real C forced into 6) before the multi-frame vote ever saw the raw data.
        val sanitized = PlateSyntaxEngine.sanitize(rawPlateCandidate)
        if (sanitized.length !in 4..8) return null

        // Match to existing tracked cluster using Levenshtein distance
        var matchedCluster = activeClusters.firstOrNull { cluster ->
            levenshteinDistance(cluster.consensusPlate, sanitized) <= 2
        }

        if (matchedCluster == null) {
            matchedCluster = TrackedPlateCluster(
                consensusPlate = sanitized,
                state = detectedState,
                lastSeenTimestamp = now,
                bestBox = box
            )
            activeClusters.add(matchedCluster)
        }

        matchedCluster.lastSeenTimestamp = now
        if (box != null) {
            matchedCluster.bestBox = box
        }
        if (detectedState != "US") {
            matchedCluster.state = detectedState
        }

        matchedCluster.observations.add(
            FrameObservation(sanitized, now, box, detectedState)
        )

        // Keep last 10 observations in sliding window
        if (matchedCluster.observations.size > 10) {
            matchedCluster.observations.removeAt(0)
        }

        // Run Multi-Frame Character-by-Character Majority Voting. Ambiguous-character
        // resolution (e.g. 6 vs G vs C) only kicks in here, where we actually have
        // multiple independent reads to disagree with each other on.
        val voteResult = PlateSyntaxEngine.computeMajorityVote(matchedCluster.observations.map { it.rawText })
        matchedCluster.consensusPlate = voteResult.plate

        val hits = matchedCluster.observations.size
        val isAlreadyCommitted = committedPlateCooldowns.containsKey(voteResult.plate) || matchedCluster.isCommittedToDb

        val confidence = min(
            0.99f,
            0.55f + (voteResult.agreementRatio * 0.30f) + (min(hits, 6) * 0.02f)
        )

        // Ready for database commit only when there is enough temporal consensus, the
        // characters actually agree across frames, and confidence clears the floor -
        // this is what throws out the low-confidence / flickery reads instead of logging them.
        val isReadyForCommit = hits >= MIN_CONSENSUS_HITS_FOR_COMMIT &&
                voteResult.agreementRatio >= MIN_AGREEMENT_RATIO &&
                confidence >= MIN_ACCEPTABLE_CONFIDENCE &&
                !isAlreadyCommitted

        return LivePlateCandidate(
            plateNumber = voteResult.plate,
            stateOrRegion = matchedCluster.state,
            boundingBox = matchedCluster.bestBox,
            confidence = confidence,
            consensusHits = hits,
            isLockedAndReady = isReadyForCommit
        )
    }

    @Synchronized
    fun markCommitted(plateNumber: String) {
        val now = System.currentTimeMillis()
        committedPlateCooldowns[plateNumber] = now
        activeClusters.find { it.consensusPlate == plateNumber }?.isCommittedToDb = true
    }
}

/**
 * Character-confusion aware helpers.
 *
 * Historically this engine force-rewrote every character against an assumed positional
 * plate format (e.g. "digit, letter, letter, letter, digit, digit, digit"). That blindly
 * turned correctly-read characters into the wrong one whenever a plate didn't match the
 * assumed format - which is most of them, since US plate formats vary a lot by state and
 * plate class. Now the confusion map is only used to break a genuine tie when multiple
 * independent OCR reads of the same plate actually disagree at a given character position.
 */
object PlateSyntaxEngine {
    data class VoteResult(val plate: String, val agreementRatio: Float)

    // Groups of characters that are commonly confused by OCR because they look alike.
    private val confusionClusters: List<Set<Char>> = listOf(
        setOf('0', 'O', 'Q', 'D'),
        setOf('1', 'I', 'L', '|'),
        setOf('2', 'Z'),
        setOf('3', 'E'),
        setOf('4', 'A'),
        setOf('5', 'S'),
        setOf('6', 'G', 'C'),
        setOf('8', 'B'),
        setOf('9', 'P')
    )

    private fun clusterOf(c: Char): Set<Char> = confusionClusters.firstOrNull { it.contains(c) } ?: setOf(c)

    fun sanitize(input: String): String {
        return input.uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]"), "")
    }

    /**
     * Positional character-by-character voting across a temporal window of OCR reads.
     * A character is only overridden when the raw votes at that position are genuinely
     * split (no outright majority) - in which case we merge votes within each optical
     * confusion cluster and pick the best-supported character from the winning cluster.
     * A character every frame agrees on is always kept exactly as read.
     */
    fun computeMajorityVote(samples: List<String>): VoteResult {
        if (samples.isEmpty()) return VoteResult("", 0f)
        if (samples.size == 1) return VoteResult(samples[0], 1f)

        val mostCommonLength = samples.groupBy { it.length }
            .maxByOrNull { it.value.size }?.key ?: samples.first().length

        val validSamples = samples.filter { it.length == mostCommonLength }
        if (validSamples.isEmpty()) return VoteResult(samples.last(), 0f)

        val resultBuilder = StringBuilder()
        var agreementSum = 0f

        for (i in 0 until mostCommonLength) {
            val charFrequency = mutableMapOf<Char, Int>()
            for (sample in validSamples) {
                val c = sample[i]
                charFrequency[c] = (charFrequency[c] ?: 0) + 1
            }

            val sortedVotes = charFrequency.entries.sortedByDescending { it.value }
            val topEntry = sortedVotes.first()
            val outrightMajority = sortedVotes.size == 1 || topEntry.value * 2 > validSamples.size

            val winningChar: Char
            val winningCount: Int
            if (outrightMajority) {
                winningChar = topEntry.key
                winningCount = topEntry.value
            } else {
                // Genuine ambiguity: merge votes within each optical-confusion cluster.
                val clusterVotes = mutableMapOf<Set<Char>, Int>()
                for ((c, count) in charFrequency) {
                    val cluster = clusterOf(c)
                    clusterVotes[cluster] = (clusterVotes[cluster] ?: 0) + count
                }
                val bestCluster = clusterVotes.maxByOrNull { it.value }?.key
                val resolved = bestCluster?.let { cluster ->
                    charFrequency.filterKeys { it in cluster }.maxByOrNull { it.value }
                } ?: topEntry
                winningChar = resolved.key
                winningCount = charFrequency.entries
                    .filter { clusterOf(it.key) == clusterOf(winningChar) }
                    .sumOf { it.value }
            }

            agreementSum += winningCount.toFloat() / validSamples.size
            resultBuilder.append(winningChar)
        }

        return VoteResult(resultBuilder.toString(), agreementSum / mostCommonLength)
    }
}

class OfflinePlateScanner(private val context: Context) {

    // Ultra-low latency On-Device ML Kit Text Recognizer (Latin script)
    val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    val temporalTracker = TemporalPlateTracker()

    // Filter out dealer frame phrases, slogans, and junk text
    private val nonPlateWords = setOf(
        "CALIFORNIA", "TEXAS", "FLORIDA", "WASHINGTON", "NEWYORK", "NEVADA", "ARIZONA",
        "OREGON", "COLORADO", "ILLINOIS", "OHIO", "MICHIGAN", "VIRGINIA", "GEORGIA",
        "POLICE", "SHERIFF", "HIGHWAY", "PATROL", "STATE", "GOVERNMENT", "OFFICIAL", "DMV",
        "COMMERCIAL", "DEALER", "EXEMPT", "SECURITY", "TRANSPORT", "TRANSIT", "DEPARTMENT",
        "TOYOTA", "HONDA", "FORD", "CHEVROLET", "CHEVY", "NISSAN", "HYUNDAI", "TESLA",
        "MERCEDES", "BENZ", "AUDI", "LEXUS", "BMW", "VOLKSWAGEN", "SUBARU", "MAZDA",
        "JEEP", "DODGE", "RAM", "GMC", "VOLVO", "PORSCHE", "KIA", "ACURA", "INFINITI",
        "CAMRY", "COROLLA", "CIVIC", "ACCORD", "SILVERADO", "EXPLORER", "MUSTANG", "PRIUS",
        "HYBRID", "TURBO", "LIMITED", "EDITION", "SPORT", "MOTOR", "MOTORS", "AUTO",
        "CARMAX", "CARVANA", "ENTERPRISE", "HERTZ", "AVIS", "BUDGET", "ALAMO", "NATIONAL",
        "AUTONATION", "DOWNTOWN", "EXPRESSWAY", "NORTH", "SOUTH", "EAST", "WEST",
        "STOP", "SLOW", "SPEED", "LIMIT", "PARKING", "ONLY", "ENTER", "EXIT", "CAUTION",
        "WARNING", "STREET", "AVENUE", "BOULEVARD", "COM", "WWW", "HTTP", "HTTPS",
        "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC",
        "2023", "2024", "2025", "2026", "2027"
    )

    private val statesMap = mapOf(
        "CALIFORNIA" to "CA", "TEXAS" to "TX", "NEW YORK" to "NY", "FLORIDA" to "FL",
        "WASHINGTON" to "WA", "ILLINOIS" to "IL", "ARIZONA" to "AZ", "NEVADA" to "NV",
        "OREGON" to "OR", "COLORADO" to "CO", "OHIO" to "OH", "MICHIGAN" to "MI",
        "VIRGINIA" to "VA", "GEORGIA" to "GA", "NORTH CAROLINA" to "NC", "NEW JERSEY" to "NJ",
        "PENNSYLVANIA" to "PA", "MASSACHUSETTS" to "MA", "UTAH" to "UT", "HAWAII" to "HI"
    )

    /**
     * Optical ALPR Frame Analyzer with Temporal Consensus Tracking
     */
    fun analyzeLiveText(visionText: Text): LivePlateCandidate? {
        val detectedBlocks = visionText.textBlocks
        if (detectedBlocks.isEmpty()) return null

        var detectedState: String? = null

        // 1. Detect state header if present
        for (block in detectedBlocks) {
            for (line in block.lines) {
                val upper = line.text.uppercase(Locale.ROOT).trim()
                for ((name, code) in statesMap) {
                    if (upper.contains(name) || upper.split(" ").contains(code)) {
                        detectedState = code
                        break
                    }
                }
                if (detectedState != null) break
            }
            if (detectedState != null) break
        }

        // Find primary candidate line
        var bestRawCandidate: String? = null
        var bestBox: Rect? = null
        var maxCharHeight = 0

        for (block in detectedBlocks) {
            for (line in block.lines) {
                val box = line.boundingBox ?: block.boundingBox
                val rawText = line.text.uppercase(Locale.ROOT).trim()
                val cleaned = rawText.replace(Regex("[^A-Z0-9]"), "")

                if (cleaned.length in 4..8 && !nonPlateWords.contains(cleaned)) {
                    if (box != null) {
                        val w = box.width().toFloat()
                        val h = box.height().toFloat()
                        if (h > 0) {
                            val aspectRatio = w / h
                            if (aspectRatio < 1.2f || aspectRatio > 6.8f) {
                                continue
                            }
                        }

                        // License plate characters have the largest font in the vehicle region
                        if (box.height() > maxCharHeight) {
                            maxCharHeight = box.height()
                            bestRawCandidate = cleaned
                            bestBox = box
                        }
                    } else if (bestRawCandidate == null) {
                        bestRawCandidate = cleaned
                    }
                }
            }
        }

        if (bestRawCandidate != null) {
            return temporalTracker.processFrameDetection(
                rawPlateCandidate = bestRawCandidate,
                detectedState = detectedState ?: "US",
                box = bestBox
            )
        }

        return null
    }

    /**
     * Optical ALPR Plate & Vehicle Snipping:
     * Accurately crops the vehicle bumper and plate region from the full camera frame
     */
    fun snipVehicleFromBitmap(bitmap: Bitmap, boundingBox: Rect?): Bitmap {
        if (boundingBox == null) return bitmap
        try {
            val width = bitmap.width
            val height = bitmap.height
            if (width <= 0 || height <= 0) return bitmap

            // Generate contextual bounding around license plate
            val padX = (boundingBox.width() * 1.6f).toInt().coerceAtLeast(80)
            val padYTop = (boundingBox.height() * 2.5f).toInt().coerceAtLeast(100)
            val padYBottom = (boundingBox.height() * 1.8f).toInt().coerceAtLeast(70)

            val left = (boundingBox.left - padX).coerceIn(0, width - 1)
            val top = (boundingBox.top - padYTop).coerceIn(0, height - 1)
            val right = (boundingBox.right + padX).coerceIn(left + 20, width)
            val bottom = (boundingBox.bottom + padYBottom).coerceIn(top + 20, height)

            val cropWidth = right - left
            val cropHeight = bottom - top

            if (cropWidth >= 40 && cropHeight >= 40) {
                return Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return bitmap
    }

    /**
     * Zoom & Re-Read pass: crops tightly around a detected text region and upsamples it
     * before running OCR again on just that enlarged patch. This is what lets the user
     * photograph the whole vehicle from a normal distance instead of having to fill the
     * frame with the plate - the plate region gets digitally "zoomed into" for the actual
     * character read, independent of how much of the original frame it occupied.
     *
     * Returns the re-read text and its bounding box translated back into the coordinate
     * space of [bitmap], or null if the zoomed patch didn't yield a usable read.
     */
    private fun rescanZoomedRegion(bitmap: Bitmap, boundingBox: Rect): Pair<String, Rect>? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null

        val padX = (boundingBox.width() * 0.35f).toInt().coerceAtLeast(6)
        val padY = (boundingBox.height() * 0.6f).toInt().coerceAtLeast(6)

        val left = (boundingBox.left - padX).coerceIn(0, width - 1)
        val top = (boundingBox.top - padY).coerceIn(0, height - 1)
        val right = (boundingBox.right + padX).coerceIn(left + 10, width)
        val bottom = (boundingBox.bottom + padY).coerceIn(top + 10, height)

        val cropWidth = right - left
        val cropHeight = bottom - top
        if (cropWidth < 10 || cropHeight < 10) return null

        val crop = Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight)

        // Upscale so plate characters are tall enough for the recognizer to resolve
        // reliably, regardless of how small they were in the original frame.
        val targetHeight = 220
        val scale = if (crop.height in 1 until targetHeight) {
            targetHeight.toFloat() / crop.height
        } else 1f

        val zoomed = if (scale > 1f) {
            Bitmap.createScaledBitmap(
                crop,
                max(1, (crop.width * scale).toInt()),
                targetHeight,
                true
            )
        } else crop

        var result: Pair<String, Rect>? = null
        try {
            val inputImage = InputImage.fromBitmap(zoomed, 0)
            val visionText = Tasks.await(textRecognizer.process(inputImage), 2, TimeUnit.SECONDS)

            var best: String? = null
            var bestBox: Rect? = null
            var bestLen = 0
            for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    val cleaned = line.text.uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]"), "")
                    if (cleaned.length in 4..8 && cleaned.length >= bestLen) {
                        best = cleaned
                        bestLen = cleaned.length
                        bestBox = line.boundingBox
                    }
                }
            }

            if (best != null) {
                val translatedBox = bestBox?.let {
                    Rect(
                        left + (it.left / scale).toInt(),
                        top + (it.top / scale).toInt(),
                        left + (it.right / scale).toInt(),
                        top + (it.bottom / scale).toInt()
                    )
                } ?: boundingBox
                result = best to translatedBox
            }
        } catch (e: Exception) {
            result = null
        } finally {
            if (zoomed !== crop) zoomed.recycle()
            crop.recycle()
        }
        return result
    }

    /**
     * Runs the zoom & re-read pass on a live-captured frame to sharpen up the final
     * committed plate text. Only accepted when it closely agrees with the multi-frame
     * temporal consensus (same length, at most one character different) - so a single
     * noisy zoomed read can refine a close call but can never override a well-established
     * consensus with something wildly different.
     */
    suspend fun refinePlateFromFrame(frameBitmap: Bitmap, consensusPlate: String, boundingBox: Rect?): String? {
        if (boundingBox == null) return null
        return withContext(Dispatchers.Default) {
            val refined = rescanZoomedRegion(frameBitmap, boundingBox)?.first ?: return@withContext null
            if (refined.length == consensusPlate.length && levenshteinDistance(refined, consensusPlate) <= 1) {
                refined
            } else {
                null
            }
        }
    }

    /**
     * Scans a single still image for license plates using on-device optical recognition.
     */
    suspend fun scanVehicleImage(bitmap: Bitmap): PlateScanResult? = withContext(Dispatchers.Default) {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val task = textRecognizer.process(inputImage)
            val visionText = Tasks.await(task, 3, TimeUnit.SECONDS)

            var detectedState: String? = null
            for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    val upper = line.text.uppercase(Locale.ROOT).trim()
                    for ((name, code) in statesMap) {
                        if (upper.contains(name) || upper.split(" ").contains(code)) {
                            detectedState = code
                            break
                        }
                    }
                }
            }

            for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    val cleaned = line.text.uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]"), "")
                    if (cleaned.length in 4..8 && !nonPlateWords.contains(cleaned)) {
                        val box = line.boundingBox ?: block.boundingBox

                        // Zoom into the detected region and re-read it at higher effective
                        // resolution. This is what allows the photo to be taken from a
                        // normal "whole vehicle" distance instead of a close-up of the plate.
                        val refined = box?.let { rescanZoomedRegion(bitmap, it) }
                        val finalText = refined?.first ?: cleaned
                        val finalBox = refined?.second ?: box

                        // Trust a clean OCR read as-is - no blind positional character
                        // rewriting here, just strip anything that isn't alphanumeric.
                        val plateText = PlateSyntaxEngine.sanitize(finalText)
                        if (plateText.length !in 4..8) continue

                        val confidence = estimateRegionQuality(bitmap, finalBox)
                        if (confidence < MIN_ACCEPTABLE_CONFIDENCE) {
                            // Low-quality read (too blurry / too small / too far away) -
                            // throw it out instead of logging an unreliable guess.
                            continue
                        }

                        val detectedColor = detectDominantColor(bitmap)
                        return@withContext PlateScanResult(
                            plateNumber = plateText,
                            stateOrRegion = detectedState ?: "US",
                            vehicleMake = "Vehicle",
                            vehicleModel = "Automotive",
                            vehicleColor = detectedColor,
                            vehicleType = "Passenger Vehicle",
                            confidence = confidence,
                            notes = "Optical ALPR Text Recognition",
                            boundingBox = finalBox
                        )
                    }
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        return@withContext null
    }

    /**
     * Estimates how trustworthy a detected plate region is, combining a sharpness signal
     * (blurry/noisy text has low local contrast between neighboring pixels) with how large
     * the plate region is relative to the full frame (a tiny box means the plate was too far
     * away to resolve reliably). Replaces the previous hardcoded confidence value so low
     * quality reads can actually be filtered out instead of always reporting near-100%.
     */
    private fun estimateRegionQuality(bitmap: Bitmap, box: Rect?): Float {
        try {
            val width = bitmap.width
            val height = bitmap.height
            if (width <= 0 || height <= 0) return 0.5f

            val region = box?.let {
                Rect(
                    it.left.coerceIn(0, width - 1),
                    it.top.coerceIn(0, height - 1),
                    it.right.coerceIn(it.left + 1, width),
                    it.bottom.coerceIn(it.top + 1, height)
                )
            }

            val sample: Bitmap? = if (region != null && region.width() > 4 && region.height() > 4) {
                Bitmap.createBitmap(bitmap, region.left, region.top, region.width(), region.height())
            } else null
            val target: Bitmap = sample ?: bitmap

            val sw = target.width
            val sh = target.height
            val stepX = max(1, sw / 60)
            val stepY = max(1, sh / 30)

            var sum = 0.0
            var sumSq = 0.0
            var n = 0

            for (y in 0 until sh step stepY) {
                var prevLum = -1.0
                for (x in 0 until sw step stepX) {
                    val pixel = target.getPixel(x, y)
                    val lum = Color.red(pixel) * 0.299 + Color.green(pixel) * 0.587 + Color.blue(pixel) * 0.114
                    if (prevLum >= 0) {
                        val diff = lum - prevLum
                        sum += diff
                        sumSq += diff * diff
                        n++
                    }
                    prevLum = lum
                }
            }

            sample?.recycle()

            val variance = if (n > 0) (sumSq / n) - (sum / n) * (sum / n) else 0.0
            // Typical sharp text edges land in the low hundreds to low thousands; blurry
            // or washed-out regions land much lower.
            val sharpnessScore = (variance / 900.0).coerceIn(0.0, 1.0).toFloat()

            val sizeScore = if (region != null) {
                val boxHeightRatio = region.height().toFloat() / height.toFloat()
                (boxHeightRatio / 0.10f).coerceIn(0f, 1f)
            } else 0.6f

            return (0.45f + sharpnessScore * 0.36f + sizeScore * 0.18f).coerceIn(0f, 0.99f)
        } catch (e: Exception) {
            return 0.5f
        }
    }

    fun detectDominantColor(bitmap: Bitmap): String {
        try {
            val width = bitmap.width
            val height = bitmap.height
            if (width <= 0 || height <= 0) return "Silver"

            var totalR = 0L
            var totalG = 0L
            var totalB = 0L
            var count = 0

            val startX = (width * 0.2f).toInt()
            val endX = (width * 0.8f).toInt()
            val startY = (height * 0.1f).toInt()
            val endY = (height * 0.7f).toInt()
            val step = 10

            for (x in startX until endX step step) {
                for (y in startY until endY step step) {
                    val pixel = bitmap.getPixel(x, y)
                    totalR += Color.red(pixel)
                    totalG += Color.green(pixel)
                    totalB += Color.blue(pixel)
                    count++
                }
            }

            if (count == 0) return "Silver"
            val avgR = (totalR / count).toInt()
            val avgG = (totalG / count).toInt()
            val avgB = (totalB / count).toInt()

            val brightness = (avgR + avgG + avgB) / 3
            if (brightness < 45) return "Black"
            if (brightness > 215) return "White"

            if (avgR > avgG + 35 && avgR > avgB + 35) return "Red"
            if (avgB > avgR + 30 && avgB > avgG + 20) return "Blue"
            if (avgG > avgR + 25 && avgG > avgB + 25) return "Green"
            if (avgR > 180 && avgG > 180 && avgB < 120) return "Yellow"

            return if (brightness < 125) "Gray" else "Silver"
        } catch (e: Exception) {
            return "Silver"
        }
    }
}
