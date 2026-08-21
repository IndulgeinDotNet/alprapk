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
import java.util.regex.Pattern
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

        // Apply Positional Syntax Disambiguation
        val correctedPlate = PlateSyntaxEngine.disambiguateCharacters(rawPlateCandidate)
        if (correctedPlate.length !in 4..8) return null

        // Match to existing tracked cluster using Levenshtein distance
        var matchedCluster = activeClusters.firstOrNull { cluster ->
            levenshteinDistance(cluster.consensusPlate, correctedPlate) <= 2
        }

        if (matchedCluster == null) {
            matchedCluster = TrackedPlateCluster(
                consensusPlate = correctedPlate,
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
            FrameObservation(correctedPlate, now, box, detectedState)
        )

        // Keep last 10 observations in sliding window
        if (matchedCluster.observations.size > 10) {
            matchedCluster.observations.removeAt(0)
        }

        // Run Multi-Frame Character-by-Character Majority Voting
        val votedPlate = computeMajorityVotePlate(matchedCluster.observations.map { it.rawText })
        matchedCluster.consensusPlate = votedPlate

        val hits = matchedCluster.observations.size
        val isAlreadyCommitted = committedPlateCooldowns.containsKey(votedPlate) || matchedCluster.isCommittedToDb

        // Ready for database commit when consensus threshold is met and not in cooldown
        val isReadyForCommit = hits >= MIN_CONSENSUS_HITS_FOR_COMMIT && !isAlreadyCommitted

        return LivePlateCandidate(
            plateNumber = votedPlate,
            stateOrRegion = matchedCluster.state,
            boundingBox = matchedCluster.bestBox,
            confidence = min(0.99f, 0.85f + (hits * 0.03f)),
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

    /**
     * Positional character-by-character frequency voting across temporal window
     */
    private fun computeMajorityVotePlate(samples: List<String>): String {
        if (samples.isEmpty()) return ""
        if (samples.size == 1) return samples[0]

        // Group by most common sample length
        val mostCommonLength = samples.groupBy { it.length }
            .maxByOrNull { it.value.size }?.key ?: samples.first().length

        val validSamples = samples.filter { it.length == mostCommonLength }
        if (validSamples.isEmpty()) return samples.last()

        val resultBuilder = StringBuilder()
        for (i in 0 until mostCommonLength) {
            val charFrequency = mutableMapOf<Char, Int>()
            for (sample in validSamples) {
                val c = sample[i]
                charFrequency[c] = (charFrequency[c] ?: 0) + 1
            }
            val winningChar = charFrequency.maxByOrNull { it.value }?.key ?: validSamples.last()[i]
            resultBuilder.append(winningChar)
        }

        // Re-pass through positional syntax engine
        return PlateSyntaxEngine.disambiguateCharacters(resultBuilder.toString())
    }

    private fun levenshteinDistance(a: String, b: String): Int {
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
}

/**
 * Positional Syntax Disambiguation Engine:
 * Fixes optical character confusion based on positional license plate syntax
 */
object PlateSyntaxEngine {
    // Optical confusion maps
    private val letterToDigit = mapOf(
        'O' to '0', 'Q' to '0', 'D' to '0',
        'I' to '1', 'L' to '1', 'T' to '1', '|' to '1',
        'Z' to '2',
        'E' to '3',
        'A' to '4',
        'S' to '5',
        'G' to '6', 'C' to '6',
        'B' to '8',
        'P' to '9'
    )

    private val digitToLetter = mapOf(
        '0' to 'O',
        '1' to 'I',
        '2' to 'Z',
        '3' to 'E',
        '4' to 'A',
        '5' to 'S',
        '6' to 'G',
        '8' to 'B'
    )

    private fun forceDigit(c: Char): Char = letterToDigit[c] ?: c
    private fun forceLetter(c: Char): Char = digitToLetter[c] ?: c

    fun disambiguateCharacters(input: String): String {
        val clean = input.uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]"), "")
        if (clean.length < 4 || clean.length > 8) return clean

        val chars = clean.toCharArray()
        val len = chars.size

        // Case 1: Standard 7-Character Format (e.g., California/US: 1 Digit + 3 Letters + 3 Digits -> 7ABC123)
        // Position 0 = Digit, Positions 1..3 = Letters, Positions 4..6 = Digits
        if (len == 7) {
            val is7CharStdProb = (chars[0].isDigit() || letterToDigit.containsKey(chars[0])) &&
                    (chars[4].isDigit() || chars[5].isDigit() || chars[6].isDigit())

            if (is7CharStdProb) {
                chars[0] = forceDigit(chars[0])
                chars[1] = forceLetter(chars[1])
                chars[2] = forceLetter(chars[2])
                chars[3] = forceLetter(chars[3])
                chars[4] = forceDigit(chars[4])
                chars[5] = forceDigit(chars[5])
                chars[6] = forceDigit(chars[6])
                return String(chars)
            }

            // Case 2: 7-Char Alt (3 Letters + 4 Digits -> ABC1234)
            val is3L4DProb = (chars[0].isLetter() || chars[1].isLetter()) &&
                    (chars[5].isDigit() || chars[6].isDigit())
            if (is3L4DProb) {
                chars[0] = forceLetter(chars[0])
                chars[1] = forceLetter(chars[1])
                chars[2] = forceLetter(chars[2])
                chars[3] = forceDigit(chars[3])
                chars[4] = forceDigit(chars[4])
                chars[5] = forceDigit(chars[5])
                chars[6] = forceDigit(chars[6])
                return String(chars)
            }
        }

        // Case 3: Standard 6-Character Format (3 Letters + 3 Digits -> ABC123 or 1 Digit + 2 Letters + 3 Digits)
        if (len == 6) {
            if (chars[0].isDigit()) {
                chars[0] = forceDigit(chars[0])
                chars[1] = forceLetter(chars[1])
                chars[2] = forceLetter(chars[2])
                chars[3] = forceDigit(chars[3])
                chars[4] = forceDigit(chars[4])
                chars[5] = forceDigit(chars[5])
                return String(chars)
            } else {
                chars[0] = forceLetter(chars[0])
                chars[1] = forceLetter(chars[1])
                chars[2] = forceLetter(chars[2])
                chars[3] = forceDigit(chars[3])
                chars[4] = forceDigit(chars[4])
                chars[5] = forceDigit(chars[5])
                return String(chars)
            }
        }

        return clean
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
                        val corrected = PlateSyntaxEngine.disambiguateCharacters(cleaned)
                        val detectedColor = detectDominantColor(bitmap)
                        return@withContext PlateScanResult(
                            plateNumber = corrected,
                            stateOrRegion = detectedState ?: "US",
                            vehicleMake = "Vehicle",
                            vehicleModel = "Automotive",
                            vehicleColor = detectedColor,
                            vehicleType = "Passenger Vehicle",
                            confidence = 0.98f,
                            notes = "Optical ALPR Text Recognition",
                            boundingBox = line.boundingBox ?: block.boundingBox
                        )
                    }
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        return@withContext null
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
