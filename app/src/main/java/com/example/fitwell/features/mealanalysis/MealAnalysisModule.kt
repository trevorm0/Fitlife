package com.example.fitwell.features.mealanalysis

import com.example.fitwell.data.local.MealEntity
import com.example.fitwell.data.local.MealCategory
import com.example.fitwell.data.local.MealPhotoAnalysisEntity
import com.example.fitwell.data.local.MealPhotoDao
import com.example.fitwell.data.repository.MealRepository
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Meal photo analysis, in two stages so the recognizer can be swapped:
 *
 *  1. A [FoodRecognizer] looks at the photo and returns labels with confidence.
 *     Today: [MlKitFoodRecognizer] (on-device, free, offline, generic labels such as "rice" or "fish").
 *     Later: a cloud recognizer implementing the same interface (it can return dish names like "ugali"
 *     directly, and optionally portion sizes). Keep the API key on YOUR server, never inside the app.
 *  2. [FoodMapper] turns those labels into catalog foods with calories/macros.
 *
 * Honest limits: portion size cannot be measured from a photo (standard servings are assumed, the user adjusts),
 * the on-device model rarely tells ugali/sukuma/githeri apart (the user adds those by hand), and oil/sauces are
 * invisible. Photos are not kept after analysis.
 */
data class RecognizedFoodItem(
    val name: String,
    val portionLabel: String,
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val confidence: Float
)

data class MealAnalysisResult(
    val items: List<RecognizedFoodItem>,
    val totalCalories: Int,
    val totalProteinG: Int,
    val totalCarbsG: Int,
    val totalFatG: Int,
    val overallConfidence: Float,
    val uncertaintyNotes: String
)

/** One thing the recognizer thinks it sees. [label] is free text ("Rice", "ugali"). */
data class FoodGuess(val label: String, val confidence: Float)

/** Swap point: on-device ML Kit today, a cloud vision model later. */
interface FoodRecognizer {
    suspend fun recognize(bitmap: Bitmap, rotationDegrees: Int): List<FoodGuess>
}

class MlKitFoodRecognizer : FoodRecognizer {
    private val labeler by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.4f).build())
    }
    override suspend fun recognize(bitmap: Bitmap, rotationDegrees: Int): List<FoodGuess> =
        labeler.process(InputImage.fromBitmap(bitmap, rotationDegrees)).await()
            .map { FoodGuess(it.text, it.confidence) }
}

/** Pure logic (no Android): labels -> catalog foods. Unit-testable. */
object FoodMapper {
    class Food(val name: String, val cal: Int, val p: Int, val c: Int, val f: Int, val serving: String)

    val catalog = listOf(
        Food("Ugali", 180, 4, 38, 1, "1 serving"),
        Food("Sukuma wiki", 50, 3, 6, 2, "1 side"),
        Food("Nyama choma (beef)", 280, 28, 0, 18, "200 g"),
        Food("Githeri", 220, 10, 35, 4, "1 bowl"),
        Food("Chapati", 120, 3, 20, 4, "1 piece"),
        Food("White rice", 160, 3, 35, 0, "1 cup"),
        Food("Beans stew", 140, 9, 20, 3, "1 bowl"),
        Food("Tilapia (fried)", 240, 22, 5, 14, "1 piece"),
        Food("Mukimo", 200, 6, 36, 4, "1 serving"),
        Food("Mandazi", 150, 3, 22, 6, "1 piece"),
        Food("Kachumbari", 30, 1, 5, 0, "1 side"),
        Food("Avocado", 80, 1, 4, 7, "half"),
        Food("Tea with milk", 60, 2, 8, 2, "1 cup"),
        Food("Samosa", 130, 4, 14, 7, "1 piece"),
        Food("Chicken stew", 210, 20, 6, 12, "1 serving"),
        Food("Cabbage salad", 40, 2, 6, 1, "1 side"),
        Food("Matoke", 170, 2, 40, 0, "1 serving"),
        Food("Omena", 160, 18, 2, 9, "1 small plate"),
        Food("Boiled egg", 78, 6, 1, 5, "1 egg"),
        Food("Bread (slice)", 80, 3, 15, 1, "1 slice"),
        Food("Banana", 105, 1, 27, 0, "1 medium"),
        Food("Fries", 320, 4, 41, 15, "1 serving"),
        Food("Pasta", 200, 7, 40, 1, "1 cup"),
        Food("Pizza (slice)", 285, 12, 36, 10, "1 slice")
    )

    /** Lower-case label fragment -> catalog food. First matching entry wins, so order matters. */
    private val labelToFood = listOf(
        "fried rice" to "White rice", "rice" to "White rice",
        "flatbread" to "Chapati", "chapati" to "Chapati", "roti" to "Chapati", "naan" to "Chapati",
        "bread" to "Bread (slice)", "toast" to "Bread (slice)",
        "tilapia" to "Tilapia (fried)", "fish" to "Tilapia (fried)", "seafood" to "Tilapia (fried)",
        "beef" to "Nyama choma (beef)", "steak" to "Nyama choma (beef)", "barbecue" to "Nyama choma (beef)",
        "grilled" to "Nyama choma (beef)", "meat" to "Nyama choma (beef)", "pork" to "Nyama choma (beef)",
        "chicken" to "Chicken stew", "poultry" to "Chicken stew",
        "kale" to "Sukuma wiki", "collard" to "Sukuma wiki", "spinach" to "Sukuma wiki", "leaf vegetable" to "Sukuma wiki",
        "cabbage" to "Cabbage salad", "salad" to "Kachumbari", "tomato" to "Kachumbari",
        "plantain" to "Matoke", "cooking banana" to "Matoke", "banana" to "Banana",
        "bean" to "Beans stew", "legume" to "Beans stew", "lentil" to "Beans stew",
        "corn" to "Githeri", "maize" to "Githeri",
        "polenta" to "Ugali", "porridge" to "Ugali", "grits" to "Ugali",
        "mashed potato" to "Mukimo", "potato" to "Mukimo",
        "french fries" to "Fries", "fries" to "Fries",
        "doughnut" to "Mandazi", "donut" to "Mandazi", "fried dough" to "Mandazi",
        "samosa" to "Samosa", "dumpling" to "Samosa", "pastry" to "Samosa",
        "avocado" to "Avocado", "egg" to "Boiled egg",
        "tea" to "Tea with milk", "pasta" to "Pasta", "spaghetti" to "Pasta", "noodle" to "Pasta", "pizza" to "Pizza (slice)"
    )

    /** Labels that say "this is food" without saying which food. */
    private val generic = listOf("food", "dish", "cuisine", "ingredient", "staple", "recipe", "meal", "produce", "baked goods", "fast food", "comfort food")

    fun find(name: String) = catalog.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun item(food: Food, confidence: Float) = RecognizedFoodItem(food.name, food.serving, food.cal, food.p, food.c, food.f, confidence)

    fun result(items: List<RecognizedFoodItem>, notes: String): MealAnalysisResult = MealAnalysisResult(
        items = items, totalCalories = items.sumOf { it.calories }, totalProteinG = items.sumOf { it.proteinG },
        totalCarbsG = items.sumOf { it.carbsG }, totalFatG = items.sumOf { it.fatG },
        overallConfidence = if (items.isEmpty()) 0f else items.map { it.confidence }.average().toFloat(), uncertaintyNotes = notes
    )

    fun map(guesses: List<FoodGuess>): MealAnalysisResult {
        val best = HashMap<String, Float>() // catalog name -> best confidence
        guesses.sortedByDescending { it.confidence }.forEach { g ->
            val l = g.label.lowercase()
            val food = find(g.label)?.name ?: labelToFood.firstOrNull { l.contains(it.first) }?.second
            if (food != null && food !in best) best[food] = g.confidence
        }
        val items = best.entries.sortedByDescending { it.value }.take(5).mapNotNull { (name, conf) ->
            find(name)?.let { item(it, (conf * 0.9f).coerceAtMost(0.9f)) } // never claim more certainty than the model had
        }
        val looksLikeFood = items.isNotEmpty() || guesses.any { g -> generic.any { g.label.lowercase().contains(it) } }
        val note = when {
            items.isNotEmpty() -> "These are guesses from a general on-device model. It often cannot tell local dishes apart (ugali, sukuma wiki, githeri), " +
                "and it cannot see portion size, oil or sauces. Check every item, remove wrong ones, add missing ones and adjust portions."
            looksLikeFood -> "This looks like food, but the model could not tell which. Add the foods below."
            else -> "No food was recognised in this photo. Retake it in good light from above, or add the foods below."
        }
        return result(items, note)
    }
}

interface MealPhotoAnalyzer {
    /** [imageUri] is a content:// or file:// URI; null starts an empty (manual) entry. */
    suspend fun analyzePhoto(imageUri: String?): MealAnalysisResult
    fun observeHistory(): Flow<List<MealPhotoAnalysisEntity>>
    suspend fun saveAnalysis(result: MealAnalysisResult, localImageRef: String?, userCorrected: Boolean): Long
    suspend fun confirmAsMeal(
        analysisId: Long,
        result: MealAnalysisResult,
        mealName: String,
        category: MealCategory,
        mealRepo: MealRepository
    ): Long
}

class MealPhotoRepository(
    private val dao: MealPhotoDao,
    private val analyzer: MealPhotoAnalyzer
) {
    fun observeHistory() = analyzer.observeHistory()
    suspend fun analyze(localImageRef: String?) = analyzer.analyzePhoto(localImageRef)
    suspend fun save(result: MealAnalysisResult, ref: String?, corrected: Boolean) =
        analyzer.saveAnalysis(result, ref, corrected)
    suspend fun confirm(
        analysisId: Long,
        result: MealAnalysisResult,
        name: String,
        category: MealCategory,
        mealRepo: MealRepository
    ) = analyzer.confirmAsMeal(analysisId, result, name, category, mealRepo)
}


class OnDeviceMealAnalyzer(
    private val context: Context,
    private val dao: MealPhotoDao,
    private val recognizer: FoodRecognizer = MlKitFoodRecognizer()
) : MealPhotoAnalyzer {

    override suspend fun analyzePhoto(imageUri: String?): MealAnalysisResult {
        if (imageUri == null) return FoodMapper.result(emptyList(), "Add the foods you ate below. Portions are standard servings; adjust them.")
        return withContext(Dispatchers.Default) {
            try {
                val (bitmap, rotation) = decode(Uri.parse(imageUri)) ?: return@withContext FoodMapper.result(emptyList(), "That photo could not be opened. Try another, or add foods below.")
                FoodMapper.map(recognizer.recognize(bitmap, rotation))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                FoodMapper.result(emptyList(), "Photo analysis failed on this phone (${e.localizedMessage ?: "unknown error"}). Add the foods below instead.")
            }
        }
    }

    /** Decodes at most ~1024 px on the long side (keeps memory low) and reads the EXIF rotation. */
    private fun decode(uri: Uri): Pair<Bitmap, Int>? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
        val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val rotation = runCatching {
            resolver.openInputStream(uri)?.use {
                when (android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            }
        }.getOrNull() ?: 0
        return bmp to rotation
    }

    override fun observeHistory(): Flow<List<MealPhotoAnalysisEntity>> = dao.observeAll()

    override suspend fun saveAnalysis(
        result: MealAnalysisResult,
        localImageRef: String?,
        userCorrected: Boolean
    ): Long {
        val json = JSONArray().apply {
            result.items.forEach { item ->
                put(JSONObject().apply {
                    put("name", item.name)
                    put("portion", item.portionLabel)
                    put("cal", item.calories)
                    put("p", item.proteinG)
                    put("c", item.carbsG)
                    put("f", item.fatG)
                    put("conf", item.confidence.toDouble())
                })
            }
        }.toString()
        return dao.insert(
            MealPhotoAnalysisEntity(
                localImageRef = localImageRef,
                recognizedItemsJson = json,
                estimatedCalories = result.totalCalories,
                estimatedProteinG = result.totalProteinG,
                estimatedCarbsG = result.totalCarbsG,
                estimatedFatG = result.totalFatG,
                overallConfidence = result.overallConfidence,
                userCorrected = userCorrected,
                notes = result.uncertaintyNotes
            )
        )
    }

    override suspend fun confirmAsMeal(
        analysisId: Long,
        result: MealAnalysisResult,
        mealName: String,
        category: MealCategory,
        mealRepo: MealRepository
    ): Long {
        val meal = MealEntity(
            name = mealName.ifBlank { result.items.joinToString(" + ") { it.name } },
            mealCategory = category,
            calories = result.totalCalories,
            proteinGrams = result.totalProteinG,
            carbohydrateGrams = result.totalCarbsG,
            fatGrams = result.totalFatG,
            timestamp = System.currentTimeMillis(),
            description = "From photo analysis (estimate)" + if (result.overallConfidence < 0.7f) " — low confidence" else ""
        )
        mealRepo.save(meal)
        // Link analysis (best-effort; meal id not returned by upsert in current repo — store note)
        return analysisId
    }
}
