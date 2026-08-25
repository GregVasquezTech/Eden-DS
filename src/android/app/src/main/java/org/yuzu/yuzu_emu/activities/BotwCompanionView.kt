


package org.yuzu.yuzu_emu.activities

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.LruCache
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject
import org.yuzu.yuzu_emu.BuildConfig
import org.yuzu.yuzu_emu.NativeLibrary
import org.yuzu.yuzu_emu.utils.Log
import kotlin.math.ceil
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin


internal class BotwCompanionView(
    context: Context,
    private val onPlayerAppearanceChanged: (BotwPlayerAppearance, Boolean) -> Unit = { _, _ -> }
) : View(context) {
    private enum class Page(val title: String) { Map("Map"), Inventory("Inventory"), Quests("Quests") }

    private data class Stats(
        val health: Int?, val maxHealth: Int?, val stamina: Float?, val maxStamina: Float?,
        val rupees: Int?, val defense: Int?, val attack: Int?, val bowAttack: Int?,
        val shieldGuard: Int?
    )

    private data class Item(
        val id: Long, val actorName: String, val name: String, val category: String,
        val power: Int?, val defense: Int?,
        val durability: Int?, val count: Int?, val modifierValue: Int?, val modifierFlags: Int?,
        val equippable: Boolean, val equipped: Boolean, val description: String
    )

    private data class Equipment(val slot: String, val item: Item)
    private data class PlayerMap(
        val x: Float, val y: Float, val z: Float, val heading: Float
    )
    private data class MapPoint(
        val category: String, val name: String, val x: Float, val z: Float,
        val minZoom: Int, val maxZoom: Int
    )
    private data class ShrinePoint(
        val index: Int, val id: String, val name: String, val trial: String,
        val x: Float, val y: Float, val z: Float
    )
    private data class Quest(
        val id: Long, val actorName: String, val name: String, val objective: String,
        val step: String, val location: String, val type: String, val complete: Boolean
    )
    private data class Effect(val name: String, val seconds: Int)
    private data class Rune(
        val id: String, val name: String, val type: Int, val available: Boolean,
        val upgraded: Boolean, val selected: Boolean
    )
    private data class ChampionPower(
        val id: String, val name: String, val available: Boolean, val enabled: Boolean,
        val uses: Int, val maxUses: Int, val cooldownSeconds: Int
    )
    private data class SheikahSensor(
        val unlocked: Boolean, val upgraded: Boolean, val enabled: Boolean,
        val searchMode: Int
    )

    private data class Snapshot(
        val status: String, val saveLoaded: Boolean, val capabilities: Int, val stats: Stats?,
        val inventory: List<Item>,
        val equipment: List<Equipment>, val quests: List<Quest>, val effects: List<Effect>,
        val runes: List<Rune>, val championPowers: List<ChampionPower> = emptyList(),
        val map: PlayerMap? = null,
        val enteredShrines: Set<String> = emptySet(),
        val clearedShrines: Set<String> = emptySet(),
        val equipState: Int = 0, val equipAttempts: Int = 0,
        val sensor: SheikahSensor? = null
    )

    private data class LiveSnapshot(
        val status: String, val stats: Stats?, val selectedRune: Int?, val map: PlayerMap?
    )

    private data class PollPayload(
        val generation: Int,
        val full: Boolean,
        val raw: String,
        val fullSnapshot: Snapshot?,
        val liveSnapshot: LiveSnapshot?,
        val nativeNanos: Long,
        val parseNanos: Long,
        val totalNanos: Long
    )

    companion object {
        private const val DESIGN_WIDTH = 1240f
        private const val DESIGN_HEIGHT = 1080f
        private const val CAP_STATS = 1 shl 0
        private const val CAP_INVENTORY = 1 shl 2
        private const val CAP_EQUIPMENT = 1 shl 3
        private const val CAP_QUESTS = 1 shl 4
        private const val CAP_MAP = 1 shl 5
        private const val CAP_ITEM_ACTIONS = 1 shl 6
        private const val CAP_RUNES = 1 shl 8
        private const val CAP_FAST_TRAVEL = 1 shl 9
        private const val CAP_SHEIKAH_SENSOR = 1 shl 10
        private const val ACTION_EQUIP_ITEM = 1
        private const val ACTION_SELECT_RUNE = 2
        private const val ACTION_FAST_TRAVEL = 3


        private val SNAPSHOT_POLL_MODE = BuildConfig.BOTW_SNAPSHOT_POLL_MODE
        private const val LIVE_STATS_POLL_INTERVAL_MS = 1_000L
        private const val FULL_SNAPSHOT_INTERVAL_MS = 5_000L
        private const val ACTION_REFRESH_DELAY_MS = 120L
        private const val EQUIP_REFRESH_RETRY_MS = 200L
        private const val RUNE_REFRESH_TIMEOUT_MS = 1_000L
        private const val EQUIP_REFRESH_TIMEOUT_MS = 2_500L
        private const val DIAGNOSTIC_INTERVAL_MS = 10_000L
        private const val CATEGORY_VISIBLE_COUNT = 4
        private const val CATEGORY_LEFT = 18f
        private const val CATEGORY_TOP = 98f
        private const val CATEGORY_RIGHT = 669f
        private const val CATEGORY_BOTTOM = 174f
        private const val CATEGORY_CELL_WIDTH =
            (CATEGORY_RIGHT - CATEGORY_LEFT) / CATEGORY_VISIBLE_COUNT
        private const val GRID_COLUMNS = 5
        private const val GRID_ROWS_VISIBLE = 4
        private const val GRID_CELL_WIDTH = 122f
        private const val GRID_CELL_HEIGHT = GRID_CELL_WIDTH
        private const val GRID_GAP = 6f
        private const val GRID_START_X = 20f
        private const val GRID_START_Y = 184f
        private const val GRID_BOTTOM =
            GRID_START_Y + GRID_ROWS_VISIBLE * (GRID_CELL_HEIGHT + GRID_GAP) - GRID_GAP
        private const val ITEM_CARD_TOP = 702f
        private const val ITEM_CARD_BOTTOM = 872f
        private const val POWER_ROW_TOP = 884f
        private const val POWER_ROW_BOTTOM = 1048f
        private const val QUEST_LIST_LEFT = 30f
        private const val QUEST_LIST_RIGHT = 520f
        private const val QUEST_LIST_TOP = 214f
        private const val QUEST_LIST_BOTTOM = 1046f
        private const val QUEST_ROW_STRIDE = 104f
        private const val QUEST_ROW_HEIGHT = 96f
        private const val QUEST_VISIBLE_ROWS = 8


        private const val MAP_WORLD_MIN = -6000f
        private const val MAP_WORLD_MAX = 6000f
        private const val MAP_MAX_ZOOM = 12f
        private val MAP_SCALE_DISTANCES = floatArrayOf(2_000f, 1_000f, 500f, 200f, 100f, 50f)
        private val MAP_MARKER_CATEGORIES = setOf(
            "Sheikah Tower", "Shrine", "Tech Lab", "Stable", "Village", "Settlement",
            "Great Fairy", "Goddess Statue", "Memory"
        )
        private val categories = listOf(
            "Weapons", "Bows", "Shields", "Armor", "Materials", "Food", "Key Items"
        )

        private val categoryIconAssets = listOf(
            "botw/ui/categories/Nt_PauseIconL_00_s.png",
            "botw/ui/categories/Nt_PauseIconL_01_s.png",
            "botw/ui/categories/Nt_PauseIconL_02_s.png",
            "botw/ui/categories/Nt_PauseIconL_03_s.png",
            "botw/ui/categories/Nt_PauseIconL_04_s.png",
            "botw/ui/categories/Nt_PauseIconL_05_s.png",
            "botw/ui/categories/Nt_PauseIconL_06_s.png"
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val normalTypeface = Typeface.create("sans-serif", Typeface.NORMAL)
    private val backgroundGradient = LinearGradient(
        0f, 0f, DESIGN_WIDTH, DESIGN_HEIGHT,
        intArrayOf(Color.rgb(2, 15, 18), Color.rgb(3, 24, 27), Color.rgb(1, 10, 13)),
        null, Shader.TileMode.CLAMP
    )
    private var page = Page.Inventory
    private var categoryIndex = 0
    private var categoryScrollX = 0f
    private var selectedItem = 0
    private var rowOffset = 0
    private var selectedQuestId: Long? = null
    private var questFilter = 0
    private var questScroll = 0
    private var questMoved = false
    private var draggingQuests = false
    private var downQuestScroll = 0
    private var downQuestId: Long? = null
    private var mapZoom = 1f
    private var mapCenterX = 0f
    private var mapCenterZ = 0f
    private var mapCentered = false
    private var draggingMap = false
    private var mapMoved = false
    private var downMapCenterX = 0f
    private var downMapCenterZ = 0f
    private var selectedShrine: ShrinePoint? = null
    private val mapScaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                if (page != Page.Map) return false
                val viewScale = min(width / DESIGN_WIDTH, height / DESIGN_HEIGHT)
                if (viewScale <= 0f || !viewScale.isFinite()) return false
                val focusX = (detector.focusX - (width - DESIGN_WIDTH * viewScale) / 2f) / viewScale
                val focusY = (detector.focusY - (height - DESIGN_HEIGHT * viewScale) / 2f) / viewScale
                if (!mapViewportRect().contains(focusX, focusY)) return false
                multiTouchGesture = true
                mapMoved = true
                draggingMap = false
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (page != Page.Map || !detector.scaleFactor.isFinite()) return false
                val viewScale = min(width / DESIGN_WIDTH, height / DESIGN_HEIGHT)
                if (viewScale <= 0f || !viewScale.isFinite()) return false
                val focusX = (detector.focusX - (width - DESIGN_WIDTH * viewScale) / 2f) / viewScale
                val focusY = (detector.focusY - (height - DESIGN_HEIGHT * viewScale) / 2f) / viewScale
                val viewport = mapViewportRect()
                if (!viewport.contains(focusX, focusY)) return false

                val worldRange = MAP_WORLD_MAX - MAP_WORLD_MIN
                val oldVisibleWorld = worldRange / mapZoom
                val focusOffsetX = (focusX - viewport.centerX()) / viewport.width()
                val focusOffsetZ = (focusY - viewport.centerY()) / viewport.height()
                val focusWorldX = mapCenterX + focusOffsetX * oldVisibleWorld
                val focusWorldZ = mapCenterZ + focusOffsetZ * oldVisibleWorld
                val nextZoom = (mapZoom * detector.scaleFactor).coerceIn(1f, MAP_MAX_ZOOM)
                if (nextZoom == mapZoom) return true
                val nextVisibleWorld = worldRange / nextZoom
                mapZoom = nextZoom
                mapCenterX = focusWorldX - focusOffsetX * nextVisibleWorld
                mapCenterZ = focusWorldZ - focusOffsetZ * nextVisibleWorld
                clampMapCenter()
                mapCentered = true
                invalidate()
                return true
            }
        }
    )
    private var snapshot = Snapshot(
        "not_running", false, 0, null, emptyList(), emptyList(), emptyList(), emptyList(),
        emptyList()
    )
    private var downX = 0f
    private var downY = 0f
    private var downRowOffset = 0
    private var downCategoryScrollX = 0f
    private var downInventoryCell: Int? = null
    private var downInventoryItemId: Long? = null
    private var downCategoryIndex: Int? = null
    private var downRuneType: Int? = null
    private var draggingInventory = false
    private var draggingScrollbar = false
    private var draggingCategories = false
    private var inventoryMoved = false
    private var categoryMoved = false
    private var multiTouchGesture = false
    private var lastPlayerAppearance: BotwPlayerAppearance? = null
    private var lastPlayerAppearanceVisible = false
    private var filteredInventorySource: List<Item>? = null
    private var filteredInventoryCategory = -1
    private var filteredInventoryItems: List<Item> = emptyList()
    private var lastLoggedStatus = ""
    private var lastLoggedCapabilities = -1
    private var lastLoggedEquipState = -1
    private var lastLoggedEquipAttempts = -1
    private var lastRawSnapshot = ""
    private var lastRawLiveSnapshot = ""
    private var lastFullSnapshotAt = 0L
    private var pendingRuneType: Int? = null
    private var pendingRuneRefreshDeadline = 0L
    private var pendingEquipItemId: Long? = null
    private var pendingEquipRefreshDeadline = 0L
    private var pollInFlight = false
    private var pollGeneration = 0
    private var pollExecutor: ExecutorService? = null
    private var consecutivePollFailures = 0
    private var pollSamples = 0L
    private var fullPollSamples = 0L
    private var livePollSamples = 0L
    private var changedPollSamples = 0L
    private var nativePollNanos = 0L
    private var parsePollNanos = 0L
    private var totalPollNanos = 0L
    private var maximumNativePollNanos = 0L
    private var maximumTotalPollNanos = 0L
    private var drawSamples = 0L
    private var drawNanos = 0L
    private var maximumDrawNanos = 0L
    private var lastDiagnosticAt = 0L
    private val missingAssets = mutableSetOf<String>()
    private val stockIconAliases: Map<String, String> by lazy {
        try {
            context.assets.open("botw/stock_aliases.json").bufferedReader().use { reader ->
                val root = JSONObject(reader.readText())
                buildMap(root.length()) {
                    root.keys().forEach { actor -> put(actor, root.getString(actor)) }
                }
            }
        } catch (exception: Exception) {
            Log.error("[BOTW Companion] Stock icon aliases failed: ${exception.message}")
            emptyMap()
        }
    }
    private val bitmapCache = object : LruCache<String, Bitmap>(12 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = max(1, value.byteCount / 1024)
    }
    private val mapMarkers: List<MapPoint> by lazy {
        loadMapPoints("botw/ui/map/pins.json", MAP_MARKER_CATEGORIES)
    }
    private val mapLabels: List<MapPoint> by lazy {
        loadMapPoints("botw/ui/map/locations.json", setOf("Region", "Subregion", "Landmark"))
    }
    private val shrines: List<ShrinePoint> by lazy { loadShrines() }




    private val categoryIconTintFilters by lazy {
        arrayOf(categoryMaskTint(muted), categoryMaskTint(cyan))
    }

    private val poll = object : Runnable {
        override fun run() {
            if (SNAPSHOT_POLL_MODE == 0 || !isAttachedToWindow || pollInFlight) return
            val now = SystemClock.uptimeMillis()
            val requestFull = !isSaveLoaded() || lastFullSnapshotAt == 0L ||
                now - lastFullSnapshotAt >= FULL_SNAPSHOT_INTERVAL_MS
            val generation = pollGeneration
            val executor = pollExecutor ?: return
            pollInFlight = true
            try {
                executor.execute {
                    val pollStartedAt = SystemClock.elapsedRealtimeNanos()
                    try {
                        val nativeStartedAt = SystemClock.elapsedRealtimeNanos()
                        val raw = if (requestFull) {
                            NativeLibrary.getBotwCompanionSnapshot()
                        } else {
                            NativeLibrary.getBotwCompanionStatsSnapshot()
                        }
                        val nativeNanos = SystemClock.elapsedRealtimeNanos() - nativeStartedAt
                        val parseStartedAt = SystemClock.elapsedRealtimeNanos()
                        val fullSnapshot = if (requestFull) parseSnapshot(raw) else null
                        val liveSnapshot = if (requestFull) null else parseLiveSnapshot(raw)
                        val parseNanos = SystemClock.elapsedRealtimeNanos() - parseStartedAt
                        val totalNanos = SystemClock.elapsedRealtimeNanos() - pollStartedAt
                        handler.post {
                            applyPollPayload(
                                PollPayload(
                                    generation, requestFull, raw, fullSnapshot, liveSnapshot,
                                    nativeNanos, parseNanos, totalNanos
                                )
                            )
                        }
                    } catch (exception: Exception) {
                        val totalNanos = SystemClock.elapsedRealtimeNanos() - pollStartedAt
                        handler.post { applyPollFailure(generation, exception, totalNanos) }
                    }
                }
            } catch (exception: Exception) {
                pollInFlight = false
                applyPollFailure(generation, exception, 0L)
            }
        }
    }

    private val actionRefresh = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow) return
            if (pollInFlight) {
                handler.postDelayed(this, 40L)
                return
            }



            if (pendingRuneType != null && pendingEquipItemId == null) {
                lastRawLiveSnapshot = ""
            } else {
                lastRawSnapshot = ""
                lastFullSnapshotAt = 0L
            }
            handler.removeCallbacks(poll)
            handler.post(poll)
        }
    }

    private fun createPollExecutor(): ExecutorService =
        Executors.newSingleThreadExecutor { command ->
            Thread(
                {
                    try {
                        android.os.Process.setThreadPriority(
                            android.os.Process.THREAD_PRIORITY_BACKGROUND
                        )
                    } catch (_: SecurityException) {

                    }
                    command.run()
                },
                "BOTW companion snapshots"
            ).apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
            }
        }

    private fun applyPollPayload(payload: PollPayload) {
        if (payload.generation != pollGeneration) return
        pollInFlight = false
        if (!isAttachedToWindow) return
        var changed = false
        if (payload.full) {
            fullPollSamples++
            lastFullSnapshotAt = SystemClock.uptimeMillis()
            val next = payload.fullSnapshot ?: return scheduleNextPoll()




            if (payload.raw != lastRawSnapshot || next != snapshot) {
                val selectedId = filteredItems().getOrNull(selectedItem)?.id
                lastRawSnapshot = payload.raw
                lastRawLiveSnapshot = ""
                snapshot = next
                if (!next.saveLoaded ||
                    selectedShrine?.let { it.id !in next.clearedShrines } == true
                ) {
                    selectedShrine = null
                }
                if (!next.saveLoaded) {
                    pendingRuneType = null
                    pendingRuneRefreshDeadline = 0L
                    pendingEquipItemId = null
                    pendingEquipRefreshDeadline = 0L
                }
                changed = true
                if (lastLoggedStatus != next.status ||
                    lastLoggedCapabilities != next.capabilities ||
                    lastLoggedEquipState != next.equipState ||
                    lastLoggedEquipAttempts != next.equipAttempts
                ) {
                    lastLoggedStatus = next.status
                    lastLoggedCapabilities = next.capabilities
                    lastLoggedEquipState = next.equipState
                    lastLoggedEquipAttempts = next.equipAttempts
                    Log.info(
                        "[BOTW Companion] State=${next.status}, capabilities=${next.capabilities}, " +
                            "items=${next.inventory.size}, equipment=${next.equipment.size}, " +
                            "runes=${next.runes.count { it.available }}, " +
                            "rupees=${next.stats?.rupees ?: "unavailable"}, " +
                            "equipState=${next.equipState}, " +
                            "equipAttempts=${next.equipAttempts}"
                    )
                }
                clampSelection(selectedId)
                clampQuestSelection()
                if (!mapCentered) recenterMap(next.map, redraw = false)
                notifyPlayerAppearance()
                invalidate()
            }
        } else {
            livePollSamples++
            val live = payload.liveSnapshot


            if (live != null) {
                lastRawLiveSnapshot = payload.raw
                if (live.status == "ready" && isSaveLoaded()) {
                    live.stats?.let {
                        val merged = mergeLiveStats(it)
                        if (merged != snapshot.stats) {
                            snapshot = snapshot.copy(stats = merged)
                            changed = true
                        }
                    }
                    if (mergeLiveRuneSelection(live.selectedRune)) {
                        changed = true
                    }
                    live.map?.let { position ->
                        if (position != snapshot.map) {
                            snapshot = snapshot.copy(map = position)
                            if (!mapCentered) recenterMap(position, redraw = false)
                            changed = true
                        }
                    }
                    if (changed) {
                        invalidate()
                    }
                } else if (live.status != "ready") {
                    lastFullSnapshotAt = 0L
                }
            }
        }
        consecutivePollFailures = 0
        recordPollTiming(payload.nativeNanos, payload.parseNanos, payload.totalNanos, changed)
        if (!schedulePendingActionRefresh()) scheduleNextPoll()
    }

    private fun applyPollFailure(generation: Int, exception: Exception, totalNanos: Long) {
        if (generation != pollGeneration) return
        pollInFlight = false
        if (!isAttachedToWindow) return
        consecutivePollFailures++
        if (consecutivePollFailures == 1 || consecutivePollFailures % 10 == 0) {
            Log.error(
                "[BOTW Companion] Snapshot failed ($consecutivePollFailures): " +
                    exception.message
            )
        }
        if (consecutivePollFailures >= 3 && isSaveLoaded()) {
            snapshot = Snapshot(
                "loading", false, 0, null, emptyList(), emptyList(), emptyList(),
                emptyList(), emptyList()
            )
            lastRawSnapshot = ""
            lastRawLiveSnapshot = ""
            lastFullSnapshotAt = 0L
            pendingRuneType = null
            pendingRuneRefreshDeadline = 0L
            pendingEquipItemId = null
            pendingEquipRefreshDeadline = 0L
            clampSelection()
            notifyPlayerAppearance()
            invalidate()
        }
        recordPollTiming(0L, 0L, totalNanos, changed = false)
        if (!schedulePendingActionRefresh()) scheduleNextPoll()
    }

    private fun recordPollTiming(
        nativeNanos: Long, parseNanos: Long, totalNanos: Long, changed: Boolean
    ) {
        pollSamples++
        if (changed) changedPollSamples++
        nativePollNanos += nativeNanos
        parsePollNanos += parseNanos
        totalPollNanos += totalNanos
        maximumNativePollNanos = maxOf(maximumNativePollNanos, nativeNanos)
        maximumTotalPollNanos = maxOf(maximumTotalPollNanos, totalNanos)
        val now = SystemClock.uptimeMillis()
        if (now - lastDiagnosticAt >= DIAGNOSTIC_INTERVAL_MS) {
            lastDiagnosticAt = now
            val sampleDivisor = pollSamples.coerceAtLeast(1).toDouble()
            val drawDivisor = drawSamples.coerceAtLeast(1).toDouble()
            Log.info(
                "[BOTW Companion] Poll diagnostics mode=$SNAPSHOT_POLL_MODE " +
                    "samples=$pollSamples, full=$fullPollSamples, live=$livePollSamples, " +
                    "changed=$changedPollSamples, " +
                    "nativeAvgMs=${"%.3f".format(nativePollNanos / sampleDivisor / 1e6)}, " +
                    "nativeMaxMs=${"%.3f".format(maximumNativePollNanos / 1e6)}, " +
                    "parseAvgMs=${"%.3f".format(parsePollNanos / sampleDivisor / 1e6)}, " +
                    "totalAvgMs=${"%.3f".format(totalPollNanos / sampleDivisor / 1e6)}, " +
                    "totalMaxMs=${"%.3f".format(maximumTotalPollNanos / 1e6)}, " +
                    "draws=$drawSamples, drawAvgMs=${"%.3f".format(drawNanos / drawDivisor / 1e6)}, " +
                    "drawMaxMs=${"%.3f".format(maximumDrawNanos / 1e6)}, " +
                    "jsonChars=${lastRawSnapshot.length}"
            )
        }
    }

    private fun scheduleNextPoll() {
        val freezeReady = SNAPSHOT_POLL_MODE == 1 && isSaveLoaded()
        if (isAttachedToWindow && !freezeReady) {
            handler.postDelayed(poll, LIVE_STATS_POLL_INTERVAL_MS)
        }
    }

    init {
        setBackgroundColor(Color.rgb(2, 13, 16))
        isFocusable = true
        isFocusableInTouchMode = true
        keepScreenOn = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.removeCallbacks(poll)
        handler.removeCallbacks(actionRefresh)
        pollGeneration++
        pollInFlight = false
        pollExecutor?.shutdownNow()
        pollExecutor = createPollExecutor()
        lastRawSnapshot = ""
        lastRawLiveSnapshot = ""
        lastFullSnapshotAt = 0L
        pendingRuneType = null
        pendingRuneRefreshDeadline = 0L
        pendingEquipItemId = null
        pendingEquipRefreshDeadline = 0L
        mapCentered = false
        mapZoom = 1f
        selectedQuestId = null
        questScroll = 0
        consecutivePollFailures = 0
        handler.post(poll)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(poll)
        handler.removeCallbacks(actionRefresh)
        pollGeneration++
        pollInFlight = false
        pollExecutor?.shutdownNow()
        pollExecutor = null
        pendingRuneType = null
        pendingRuneRefreshDeadline = 0L
        pendingEquipItemId = null
        pendingEquipRefreshDeadline = 0L
        lastPlayerAppearance?.let { onPlayerAppearanceChanged(it, false) }
        lastPlayerAppearanceVisible = false
        bitmapCache.evictAll()
        missingAssets.clear()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val drawStartedAt = SystemClock.elapsedRealtimeNanos()
        super.onDraw(canvas)
        val scale = min(width / DESIGN_WIDTH, height / DESIGN_HEIGHT)
        canvas.save()
        canvas.translate((width - DESIGN_WIDTH * scale) / 2f, (height - DESIGN_HEIGHT * scale) / 2f)
        canvas.scale(scale, scale)
        if (!isSaveLoaded()) {
            drawWaitingForGame(canvas)
            canvas.restore()
            recordDrawTime(drawStartedAt)
            return
        }
        drawBackground(canvas)
        drawTopNavigation(canvas)
        when (page) {
            Page.Inventory -> drawInventoryPage(canvas)
            Page.Map -> drawMapPage(canvas)
            Page.Quests -> drawQuestPage(canvas)
        }
        canvas.restore()
        recordDrawTime(drawStartedAt)
    }

    private fun recordDrawTime(startedAt: Long) {
        val elapsed = SystemClock.elapsedRealtimeNanos() - startedAt
        drawSamples++
        drawNanos += elapsed
        maximumDrawNanos = maxOf(maximumDrawNanos, elapsed)
    }

    private fun isSaveLoaded(candidate: Snapshot = snapshot): Boolean =
        candidate.status == "ready" && candidate.saveLoaded

    private fun drawWaitingForGame(canvas: Canvas) {
        paint.shader = backgroundGradient
        canvas.drawRect(0f, 0f, DESIGN_WIDTH, DESIGN_HEIGHT, paint)
        paint.shader = null
        val card = RectF(258f, 314f, 982f, 766f)
        panel(canvas, card)
        drawSheikahMedallion(canvas, card.centerX(), card.top + 128f, 62f)
        val title = when (snapshot.status) {
            "unsupported_build" -> "Unsupported Breath of the Wild build"
            "wrong_title" -> "Waiting for Breath of the Wild"
            "not_running" -> "Waiting for Breath of the Wild"
            else -> "Waiting for the save to load"
        }
        val detail = when (snapshot.status) {
            "unsupported_build" -> "This companion currently supports the validated 1.6.0 build."
            "wrong_title" -> "Open Breath of the Wild to activate its companion interface."
            "not_running" -> "Start the game to activate the lower-screen interface."
            else -> "The interface will appear when Link and the pouch are ready."
        }
        text(
            canvas,
            title,
            card.centerX(),
            card.top + 258f,
            29f,
            warmWhite,
            Paint.Align.CENTER
        )
        text(canvas, detail, card.centerX(), card.top + 306f, 17f, muted, Paint.Align.CENTER)
        stroke.color = bronzeDark
        canvas.drawLine(card.left + 94f, card.top + 340f, card.right - 94f, card.top + 340f, stroke)
        text(
            canvas, "DUAL-SCREEN COMPANION", card.centerX(), card.top + 388f,
            13f, cyan, Paint.Align.CENTER
        )
        cornerMarks(canvas, card, if (snapshot.status == "unsupported_build") bronze else cyan)
    }

    private fun drawBackground(canvas: Canvas) {
        paint.shader = backgroundGradient
        canvas.drawRect(0f, 0f, DESIGN_WIDTH, DESIGN_HEIGHT, paint)
        paint.shader = null
        stroke.color = Color.argb(45, 37, 205, 219)
        stroke.strokeWidth = 1f
        for (x in 28..1220 step 82) canvas.drawLine(x.toFloat(), 90f, x.toFloat() - 150f, 1016f, stroke)
        paint.color = Color.argb(30, 44, 211, 224)
        for (x in 18..1220 step 105) for (y in 102..1000 step 94) canvas.drawCircle(x.toFloat(), y.toFloat(), 1.25f, paint)
    }

    private fun drawTopNavigation(canvas: Canvas) {
        val gap = 8f
        val margin = 10f
        val tabWidth = (DESIGN_WIDTH - margin * 2f - gap * 2f) / 3f
        Page.entries.forEachIndexed { index, value ->
            val rect = RectF(margin + index * (tabWidth + gap), 9f, margin + index * (tabWidth + gap) + tabWidth, 79f)
            panel(canvas, rect, page == value)
            drawPageIcon(canvas, value, rect.left + 55f, rect.centerY())
            text(canvas, value.title, rect.centerX() + 14f, rect.centerY() + 10f, 25f, if (page == value) cyan else warmWhite, Paint.Align.CENTER)
            if (page == value) cornerMarks(canvas, rect, cyan)
        }
    }

    private fun drawInventoryPage(canvas: Canvas) {
        val left = RectF(10f, 90f, 677f, 1070f)
        val rightStats = RectF(687f, 90f, 1230f, 340f)
        val rightPlayer = RectF(687f, 350f, 1230f, 1070f)
        panel(canvas, left)
        panel(canvas, rightStats)
        panel(canvas, rightPlayer)
        drawCategoryBar(canvas)
        if (snapshot.capabilities and CAP_INVENTORY != 0) {
            drawInventoryGrid(canvas)
            drawSelectedItem(canvas)
            drawChampionPowers(canvas)
        } else {
            drawCalibrating(canvas, RectF(19f, 164f, 668f, 998f), "Reading inventory")
        }
        drawStats(canvas, rightStats)
        drawPlayerPanel(canvas, rightPlayer)
    }

    private fun drawCategoryBar(canvas: Canvas) {
        val rect = RectF(CATEGORY_LEFT, CATEGORY_TOP, CATEGORY_RIGHT, CATEGORY_BOTTOM)
        paint.color = Color.argb(115, 1, 17, 20)
        canvas.drawRect(rect, paint)
        canvas.save()
        canvas.clipRect(rect)
        categories.forEachIndexed { index, name ->
            val x = rect.left + index * CATEGORY_CELL_WIDTH - categoryScrollX
            val tabRect = RectF(x + 3f, rect.top + 3f, x + CATEGORY_CELL_WIDTH - 3f, rect.bottom - 3f)
            if (tabRect.right < rect.left || tabRect.left > rect.right) return@forEachIndexed
            if (index == categoryIndex) {
                paint.color = Color.argb(45, 32, 220, 235)
                canvas.drawRect(tabRect, paint)
                paint.color = cyan
                canvas.drawRect(tabRect.left + 5f, rect.bottom - 4f, tabRect.right - 5f, rect.bottom, paint)
            }
            stroke.color = if (index == categoryIndex) Color.argb(115, 33, 219, 234) else bronzeDark
            canvas.drawRect(tabRect, stroke)
            drawCategoryIcon(canvas, index, x + 34f, rect.centerY(), index == categoryIndex)
            text(
                canvas, shortCategory(name), x + 59f, rect.top + 31f, 18f,
                if (index == categoryIndex) warmWhite else muted
            )
            val count = snapshot.inventory.count { it.category.equals(name, true) }
            text(
                canvas, "$count item${if (count == 1) "" else "s"}", x + 59f,
                rect.top + 55f, 12f, if (index == categoryIndex) cyan else bronze
            )
        }
        canvas.restore()
        drawCategoryScrollIndicators(canvas, rect)
    }

    private fun drawCategoryScrollIndicators(canvas: Canvas, rect: RectF) {
        stroke.color = cyan
        stroke.strokeWidth = 2.5f
        if (categoryScrollX > 1f) {
            paint.color = Color.argb(175, 1, 18, 22)
            canvas.drawRect(rect.left, rect.top, rect.left + 20f, rect.bottom, paint)
            canvas.drawLine(rect.left + 13f, rect.centerY() - 8f, rect.left + 7f, rect.centerY(), stroke)
            canvas.drawLine(rect.left + 7f, rect.centerY(), rect.left + 13f, rect.centerY() + 8f, stroke)
        }
        if (categoryScrollX < maximumCategoryScroll() - 1f) {
            paint.color = Color.argb(175, 1, 18, 22)
            canvas.drawRect(rect.right - 20f, rect.top, rect.right, rect.bottom, paint)
            canvas.drawLine(rect.right - 13f, rect.centerY() - 8f, rect.right - 7f, rect.centerY(), stroke)
            canvas.drawLine(rect.right - 7f, rect.centerY(), rect.right - 13f, rect.centerY() + 8f, stroke)
        }
        stroke.strokeWidth = 1f
    }

    private fun maximumCategoryScroll() =
        max(0f, (categories.size - CATEGORY_VISIBLE_COUNT) * CATEGORY_CELL_WIDTH)

    private fun setCategoryScroll(value: Float) {
        categoryScrollX = value.coerceIn(0f, maximumCategoryScroll())
        invalidate()
    }

    private fun snapCategoryScroll() {
        setCategoryScroll(
            (categoryScrollX / CATEGORY_CELL_WIDTH).roundToInt() * CATEGORY_CELL_WIDTH
        )
    }

    private fun ensureCategoryVisible(index: Int) {
        val itemLeft = index * CATEGORY_CELL_WIDTH
        val itemRight = itemLeft + CATEGORY_CELL_WIDTH
        val viewportRight = categoryScrollX + CATEGORY_VISIBLE_COUNT * CATEGORY_CELL_WIDTH
        when {
            itemLeft < categoryScrollX -> setCategoryScroll(itemLeft)
            itemRight > viewportRight ->
                setCategoryScroll(itemRight - CATEGORY_VISIBLE_COUNT * CATEGORY_CELL_WIDTH)
        }
    }

    private fun drawInventoryGrid(canvas: Canvas) {
        val items = filteredItems()
        if (items.isEmpty()) {
            val empty = RectF(GRID_START_X, GRID_START_Y, 651f, GRID_BOTTOM)
            drawSheikahMedallion(canvas, empty.centerX(), empty.centerY() - 36f, 42f)
            text(
                canvas, "No ${categories.getOrElse(categoryIndex) { "items" }.lowercase()} carried",
                empty.centerX(), empty.centerY() + 42f, 18f, muted, Paint.Align.CENTER
            )
            return
        }
        val first = rowOffset * GRID_COLUMNS
        val last = min(items.size, first + GRID_COLUMNS * GRID_ROWS_VISIBLE)
        for (index in first until last) {
            val local = index - first
            val column = local % GRID_COLUMNS
            val row = local / GRID_COLUMNS
            val rect = RectF(
                GRID_START_X + column * (GRID_CELL_WIDTH + GRID_GAP),
                GRID_START_Y + row * (GRID_CELL_HEIGHT + GRID_GAP),
                GRID_START_X + column * (GRID_CELL_WIDTH + GRID_GAP) + GRID_CELL_WIDTH,
                GRID_START_Y + row * (GRID_CELL_HEIGHT + GRID_GAP) + GRID_CELL_HEIGHT
            )
            drawItemCell(canvas, rect, items[index], index == selectedItem)
        }
        val rowCount = ceil(items.size / GRID_COLUMNS.toFloat()).toInt()
        if (rowCount > GRID_ROWS_VISIBLE) {
            val track = RectF(661f, GRID_START_Y, 671f, GRID_BOTTOM)
            paint.color = Color.argb(90, 40, 190, 202)
            canvas.drawRoundRect(track, 5f, 5f, paint)
            val thumbHeight = max(64f, track.height() * GRID_ROWS_VISIBLE / rowCount)
            val thumbY = track.top + (track.height() - thumbHeight) * rowOffset /
                max(1, rowCount - GRID_ROWS_VISIBLE)
            paint.color = cyan
            canvas.drawRoundRect(
                RectF(track.left - 2f, thumbY, track.right + 2f, thumbY + thumbHeight),
                7f, 7f, paint
            )
        }
    }

    private fun drawItemCell(canvas: Canvas, rect: RectF, item: Item, selected: Boolean) {
        paint.color = if (selected) Color.argb(75, 19, 181, 198) else Color.argb(110, 3, 20, 23)
        canvas.drawRect(rect, paint)
        stroke.color = if (selected) cyan else bronzeDark
        stroke.strokeWidth = if (selected) 2.5f else 1f
        canvas.drawRect(rect, stroke)
        if (selected) glowRect(canvas, rect)
        if (pendingEquipItemId == item.id && !item.equipped) {
            stroke.color = bronze
            stroke.strokeWidth = 2f
            canvas.drawRect(
                RectF(rect.left + 4f, rect.top + 4f, rect.right - 4f, rect.bottom - 4f), stroke
            )
            stroke.strokeWidth = 1f
        }
        drawItemGlyph(canvas, item, rect.centerX(), rect.centerY() - 3f, 1.18f)
        (item.count ?: item.power ?: item.defense)?.let {
            text(canvas, if (item.count != null && it > 1) "×$it" else "$it", rect.right - 7f, rect.bottom - 7f, 15f, warmWhite, Paint.Align.RIGHT)
        }
        if (item.equipped) {
            paint.color = cyan
            canvas.drawCircle(rect.right - 13f, rect.top + 13f, 9f, paint)
            stroke.color = Color.rgb(2, 24, 27)
            stroke.strokeWidth = 2f
            canvas.drawLine(rect.right - 17f, rect.top + 13f, rect.right - 14f, rect.top + 16f, stroke)
            canvas.drawLine(rect.right - 14f, rect.top + 16f, rect.right - 9f, rect.top + 10f, stroke)
            stroke.strokeWidth = 1f
        }
    }

    private fun drawSelectedItem(canvas: Canvas) {
        val items = filteredItems()
        if (items.isEmpty()) return
        val item = items[selectedItem.coerceIn(items.indices)]
        val rect = RectF(18f, ITEM_CARD_TOP, 669f, ITEM_CARD_BOTTOM)
        paint.color = Color.argb(180, 2, 17, 20)
        canvas.drawRect(rect, paint)
        stroke.color = bronzeDark
        canvas.drawRect(rect, stroke)
        canvas.drawLine(129f, rect.top + 10f, 129f, rect.bottom - 10f, stroke)
        drawItemGlyph(canvas, item, 80f, 786f, 1.25f)
        val pending = pendingEquipItemId == item.id
        val titleWidth = if (item.equipped || pending) 382f else 500f
        text(canvas, ellipsizeToWidth(item.name, titleWidth, 23f), 145f, 736f, 23f, warmWhite)
        text(
            canvas, "${item.category}  •  ${selectedItem + 1} of ${items.size}",
            145f, 760f, 13f, muted
        )
        if (pending) {
            badge(canvas, RectF(526f, 716f, 651f, 750f), "SWITCHING", bronze)
        } else if (item.equipped) {
            badge(canvas, RectF(542f, 716f, 651f, 750f), "EQUIPPED", cyan)
        }
        var badgeX = 145f
        val addDetailBadge = { label: String, value: Int ->
            drawLabeledBadge(canvas, RectF(badgeX, 771f, badgeX + 105f, 811f), label, "$value")
            badgeX += 112f
        }
        item.power?.let { addDetailBadge("Attack", it) }
        item.defense?.let { addDetailBadge("Defense", it) }
        item.count?.let { addDetailBadge("Count", it) }
        item.durability?.let { addDetailBadge("Durability", it) }
        val modifier = modifierLabel(item)
        if (modifier.isNotEmpty() && badgeX <= 521f) {
            drawLabeledBadge(
                canvas, RectF(badgeX, 771f, min(651f, badgeX + 124f), 811f),
                "Modifier", modifier
            )
        }
        val description = item.description.ifBlank {
            "No localized description is available for this item."
        }
        multilineText(canvas, description, 145f, 835f, 500f, 14.5f, muted, 2)
        cornerMarks(canvas, rect, bronze)
    }

    private fun drawStats(canvas: Canvas, rect: RectF) {
        if (snapshot.capabilities and CAP_STATS == 0) {
            drawCalibrating(canvas, rect, "Reading player status")
            return
        }
        val stats = snapshot.stats ?: return
        if (stats.health != null && stats.maxHealth != null) {
            drawHealth(
                canvas, rect.left + 42f, rect.top + 40f,
                stats.health, stats.maxHealth
            )
        }
        stats.rupees?.let {
            drawRupee(canvas, rect.right - 112f, rect.top + 32f)
            text(canvas, "%,d".format(it), rect.right - 27f, rect.top + 43f, 24f, warmWhite, Paint.Align.RIGHT)
        }
        if (stats.stamina != null && stats.maxStamina != null) {
            drawStamina(canvas, rect.left + 42f, rect.top + 110f, stats.stamina, stats.maxStamina)
        }
        stats.defense?.let {
            statLine(canvas, rect.left + 12f, rect.bottom - 29f, "Defense", it, "botw/ui/defense.png")
        }
        stats.attack?.let {
            statLine(canvas, rect.left + 142f, rect.bottom - 29f, "Attack", it, "botw/ui/attack.png")
        }
        stats.bowAttack?.let {
            statLine(canvas, rect.left + 272f, rect.bottom - 29f, "Bow", it, "botw/ui/attack.png")
        }
        stats.shieldGuard?.let {
            statLine(canvas, rect.left + 402f, rect.bottom - 29f, "Guard", it, "botw/ui/defense.png")
        }
        if (snapshot.effects.isNotEmpty()) {


            val effectsRect = RectF(
                rect.left + 331f, rect.top + 73f, rect.right - 10f, rect.top + 168f
            )
            paint.color = Color.argb(88, 3, 27, 31)
            canvas.drawRect(effectsRect, paint)
            stroke.color = bronzeDark
            canvas.drawRect(effectsRect, stroke)
            snapshot.effects.take(3).forEachIndexed { index, effect ->
                val y = effectsRect.top + 21f + index * 29f
                drawEffectIcon(canvas, effectsRect.left + 22f, y, index)
                text(
                    canvas, ellipsizeToWidth(effect.name, 105f, 13f),
                    effectsRect.left + 43f, y + 5f, 13f, muted
                )
                text(
                    canvas, formatTime(effect.seconds), effectsRect.right - 9f, y + 5f,
                    13f, warmWhite, Paint.Align.RIGHT
                )
            }
        }
        stroke.color = bronzeDark
        canvas.drawLine(rect.left + 10f, rect.bottom - 77f, rect.right - 10f, rect.bottom - 77f, stroke)
    }

    private fun drawPlayerPanel(canvas: Canvas, rect: RectF) {


        drawSheikahMedallion(canvas, rect.centerX(), rect.centerY() + 20f, 122f)
        if (snapshot.capabilities and CAP_EQUIPMENT != 0) drawEquipmentSlots(canvas, rect)
        if (snapshot.capabilities and CAP_RUNES != 0) drawRunes(canvas, rect)
        cornerMarks(canvas, rect, bronze)
    }

    private fun equipmentSlotPositions(panel: RectF) = mapOf(
            "Head" to RectF(panel.left + 18f, panel.top + 58f, panel.left + 112f, panel.top + 153f),
            "Chest" to RectF(panel.left + 18f, panel.top + 218f, panel.left + 112f, panel.top + 313f),
            "Legs" to RectF(panel.left + 18f, panel.top + 378f, panel.left + 112f, panel.top + 473f),
            "Weapon" to RectF(panel.right - 112f, panel.top + 58f, panel.right - 18f, panel.top + 153f),
            "Bow" to RectF(panel.right - 112f, panel.top + 218f, panel.right - 18f, panel.top + 313f),
            "Shield" to RectF(panel.right - 112f, panel.top + 378f, panel.right - 18f, panel.top + 473f)
        )

    private fun drawEquipmentSlots(canvas: Canvas, panel: RectF) {
        val equipped = snapshot.equipment.associateBy { it.slot }
        equipmentSlotPositions(panel).forEach { (slot, rect) ->
            text(canvas, slot, rect.centerX(), rect.top - 9f, 16f, bronze, Paint.Align.CENTER)
            val item = equipped[slot]?.item
            if (item != null) {
                drawItemCell(canvas, rect, item, false)
            } else {
                paint.color = Color.argb(80, 3, 20, 23)
                canvas.drawRect(rect, paint)
                stroke.color = bronzeDark
                canvas.drawRect(rect, stroke)
                text(canvas, "—", rect.centerX(), rect.centerY() + 8f, 25f, muted, Paint.Align.CENTER)
            }
        }
    }

    private fun drawMapPage(canvas: Canvas) {
        val outer = RectF(10f, 90f, 1230f, 1070f)
        panel(canvas, outer)
        if (snapshot.capabilities and CAP_MAP == 0 || snapshot.map == null) {
            drawCalibrating(canvas, outer, "Reading map and player position")
            return
        }
        val viewport = mapViewportRect()
        val position = snapshot.map ?: return
        if (!mapCentered) recenterMap(position, redraw = false)
        val map = loadBitmap("botw/ui/hyrule_map.png")
        if (map != null) {
            val visibleWorld = (MAP_WORLD_MAX - MAP_WORLD_MIN) / mapZoom
            val half = visibleWorld / 2f
            val worldLeft = (mapCenterX - half).coerceIn(
                MAP_WORLD_MIN, MAP_WORLD_MAX - visibleWorld
            )
            val worldTop = (mapCenterZ - half).coerceIn(
                MAP_WORLD_MIN, MAP_WORLD_MAX - visibleWorld
            )
            mapCenterX = worldLeft + half
            mapCenterZ = worldTop + half
            val source = android.graphics.Rect(
                (((worldLeft - MAP_WORLD_MIN) / (MAP_WORLD_MAX - MAP_WORLD_MIN)) * map.width)
                    .roundToInt(),
                (((worldTop - MAP_WORLD_MIN) / (MAP_WORLD_MAX - MAP_WORLD_MIN)) * map.height)
                    .roundToInt(),
                ((((worldLeft + visibleWorld) - MAP_WORLD_MIN) /
                    (MAP_WORLD_MAX - MAP_WORLD_MIN)) * map.width).roundToInt(),
                ((((worldTop + visibleWorld) - MAP_WORLD_MIN) /
                    (MAP_WORLD_MAX - MAP_WORLD_MIN)) * map.height).roundToInt()
            )
            canvas.drawBitmap(map, source, viewport, paint)
            drawMapLabels(canvas, viewport, worldLeft, worldTop, visibleWorld)
            drawMapMarkers(canvas, viewport, worldLeft, worldTop, visibleWorld)
            drawShrineMarkers(canvas, viewport, worldLeft, worldTop, visibleWorld)
            drawPlayerMapMarker(canvas, viewport, position, worldLeft, worldTop, visibleWorld)
            drawMapGuides(canvas, viewport, visibleWorld)
        } else {
            drawSheikahMedallion(canvas, viewport.centerX(), viewport.centerY(), 150f)
        }
        stroke.color = cyan
        stroke.strokeWidth = 1.5f
        canvas.drawRect(viewport, stroke)
        stroke.strokeWidth = 1f
        cornerMarks(canvas, viewport, cyan)

        val info = RectF(946f, 112f, 1214f, 1018f)
        paint.color = Color.argb(178, 2, 17, 20)
        canvas.drawRect(info, paint)
        stroke.color = bronzeDark
        canvas.drawRect(info, stroke)
        text(canvas, "LIVE POSITION", info.centerX(), 154f, 15f, cyan, Paint.Align.CENTER)
        drawSheikahMedallion(canvas, info.centerX(), 244f, 54f)
        text(canvas, "X", 976f, 340f, 13f, bronze)
        text(canvas, "%+.1f".format(position.x), 1190f, 340f, 22f, warmWhite, Paint.Align.RIGHT)
        text(canvas, "Y", 976f, 386f, 13f, bronze)
        text(canvas, "%+.1f".format(position.y), 1190f, 386f, 22f, warmWhite, Paint.Align.RIGHT)
        text(canvas, "Z", 976f, 432f, 13f, bronze)
        text(canvas, "%+.1f".format(position.z), 1190f, 432f, 22f, warmWhite, Paint.Align.RIGHT)
        stroke.color = bronzeDark
        canvas.drawLine(970f, 458f, 1190f, 458f, stroke)
        text(canvas, "Altitude", 976f, 495f, 14f, muted)
        text(canvas, "${position.y.roundToInt()} m", 1190f, 495f, 20f, warmWhite, Paint.Align.RIGHT)
        text(canvas, "Zoom", 976f, 548f, 14f, muted)
        text(canvas, "${(mapZoom * 100).roundToInt()}%", 1190f, 548f, 20f, cyan, Paint.Align.RIGHT)
        drawMapButton(canvas, mapZoomOutRect(), "-")
        drawMapButton(canvas, mapZoomInRect(), "+")
        drawMapButton(canvas, mapCenterRect(), "CENTER ON LINK")
        val clearedShrines = shrines.count { it.id in snapshot.clearedShrines }
        text(
            canvas, "$clearedShrines / ${shrines.size} SHRINES CLEARED",
            info.centerX(), 797f, 13f, cyan, Paint.Align.CENTER
        )
        text(canvas, "Towns, stables, labs & landmarks", info.centerX(), 823f, 12.5f, muted, Paint.Align.CENTER)
        text(canvas, "Drag to pan  |  Pinch to zoom", info.centerX(), 850f, 13f, muted, Paint.Align.CENTER)
        text(canvas, "Position updates live", info.centerX(), 879f, 14f, green, Paint.Align.CENTER)
        drawSheikahSensorStatus(canvas, info)
        cornerMarks(canvas, info, bronze)
        selectedShrine?.let { drawShrinePopup(canvas, it) }
    }

    private fun drawSheikahSensorStatus(canvas: Canvas, info: RectF) {
        val sensor = snapshot.sensor ?: return
        if (snapshot.capabilities and CAP_SHEIKAH_SENSOR == 0) return

        canvas.drawLine(info.left + 24f, 898f, info.right - 24f, 898f, stroke.apply {
            color = bronzeDark
            strokeWidth = 1f
        })
        val iconX = info.left + 45f
        val iconY = 954f
        stroke.color = if (sensor.unlocked && sensor.enabled) cyan else muted
        stroke.strokeWidth = 2f
        canvas.drawCircle(iconX, iconY, 25f, stroke)
        canvas.drawCircle(iconX, iconY, 15f, stroke)
        canvas.drawLine(iconX, iconY, iconX + 18f, iconY - 18f, stroke)
        paint.color = if (sensor.unlocked && sensor.enabled) cyan else muted
        canvas.drawCircle(iconX, iconY, 5f, paint)
        stroke.strokeWidth = 1f

        val title = if (sensor.upgraded) "SHEIKAH SENSOR +" else "SHEIKAH SENSOR"
        text(canvas, title, info.left + 82f, 927f, 14f, cyan)
        val state = when {
            !sensor.unlocked -> "NOT ACQUIRED"
            sensor.enabled -> "ACTIVE"
            else -> "DISABLED"
        }
        text(
            canvas, state, info.left + 82f, 956f, 17f,
            if (sensor.unlocked && sensor.enabled) green else muted
        )
        val target = when {
            !sensor.unlocked -> "Sensor unavailable"
            sensor.searchMode == 0 -> "Shrine detection"
            else -> "Compendium target"
        }
        text(canvas, target, info.left + 82f, 980f, 13f, warmWhite)
    }

    private fun drawQuestPage(canvas: Canvas) {
        val outer = RectF(10f, 90f, 1230f, 1070f)
        panel(canvas, outer)
        if (snapshot.capabilities and CAP_QUESTS == 0) {
            drawCalibrating(canvas, outer, "Reading quest log")
            return
        }
        clampQuestSelection()
        val listPanel = RectF(22f, 108f, 532f, 1052f)
        val detailPanel = RectF(544f, 108f, 1218f, 1052f)
        paint.color = Color.argb(150, 2, 17, 20)
        canvas.drawRect(listPanel, paint)
        canvas.drawRect(detailPanel, paint)
        stroke.color = bronzeDark
        canvas.drawRect(listPanel, stroke)
        canvas.drawRect(detailPanel, stroke)

        val activeCount = snapshot.quests.count { !it.complete }
        val completeCount = snapshot.quests.count { it.complete }
        text(canvas, "QUEST LOG", 42f, 142f, 18f, warmWhite)
        text(
            canvas, "$activeCount active  |  $completeCount complete", 512f, 142f, 13f,
            muted, Paint.Align.RIGHT
        )
        questFilterRects().forEachIndexed { index, rect ->
            val selected = questFilter == index
            paint.color = if (selected) Color.argb(62, 28, 214, 229) else Color.argb(80, 3, 20, 23)
            canvas.drawRect(rect, paint)
            stroke.color = if (selected) cyan else bronzeDark
            canvas.drawRect(rect, stroke)
            val filterLabel = when (index) {
                1 -> "Active  $activeCount"
                2 -> "Complete  $completeCount"
                else -> "All  ${snapshot.quests.size}"
            }
            text(
                canvas, filterLabel, rect.centerX(),
                rect.centerY() + 6f, 13f, if (selected) cyan else muted, Paint.Align.CENTER
            )
        }

        val quests = filteredQuests()
        val visibleRows = QUEST_VISIBLE_ROWS
        questScroll = questScroll.coerceIn(0, max(0, quests.size - visibleRows))
        quests.drop(questScroll).take(visibleRows).forEachIndexed { local, quest ->
            val rect = questRowRect(local)
            val selected = selectedQuestId == quest.id
            paint.color = if (selected) Color.argb(72, 23, 191, 206) else Color.argb(82, 3, 20, 23)
            canvas.drawRect(rect, paint)
            stroke.color = if (selected) cyan else bronzeDark
            canvas.drawRect(rect, stroke)
            if (selected) {
                paint.color = cyan
                canvas.drawRect(rect.left, rect.top, rect.left + 4f, rect.bottom, paint)
            }
            paint.color = if (quest.complete) green else cyan
            canvas.drawCircle(rect.left + 19f, rect.top + 22f, 6f, paint)
            text(
                canvas, ellipsizeToWidth(quest.name, rect.width() - 53f, 24f),
                rect.left + 34f, rect.top + 31f, 24f,
                if (quest.complete) muted else warmWhite
            )
            val summary = questSummary(quest)
            text(
                canvas, ellipsizeToWidth(summary, rect.width() - 28f, 17f),
                rect.left + 14f, rect.top + 62f, 17f, muted
            )
            text(
                canvas, if (quest.complete) "COMPLETE" else "ACTIVE", rect.right - 12f,
                rect.bottom - 8f, 15f, if (quest.complete) green else cyan, Paint.Align.RIGHT
            )
        }
        drawQuestScrollbar(canvas, quests.size, visibleRows)

        val selected = selectedQuest()
        if (selected == null) {
            drawCalibrating(canvas, detailPanel, "No quests in this filter")
        } else {
            drawQuestDetails(canvas, detailPanel, selected)
        }
        cornerMarks(canvas, listPanel, bronze)
        cornerMarks(canvas, detailPanel, bronze)
    }

    private fun mapViewportRect() = RectF(24f, 112f, 930f, 1018f)
    private fun mapZoomOutRect() = RectF(974f, 590f, 1074f, 654f)
    private fun mapZoomInRect() = RectF(1088f, 590f, 1188f, 654f)
    private fun mapCenterRect() = RectF(974f, 680f, 1188f, 744f)

    private fun drawMapButton(canvas: Canvas, rect: RectF, label: String) {
        paint.color = Color.argb(145, 3, 29, 33)
        canvas.drawRect(rect, paint)
        stroke.color = cyan
        canvas.drawRect(rect, stroke)
        text(
            canvas, label, rect.centerX(), rect.centerY() + 7f,
            if (label.length == 1) 28f else 13f, warmWhite, Paint.Align.CENTER
        )
    }

    private fun recenterMap(position: PlayerMap? = snapshot.map, redraw: Boolean = true) {
        position ?: return
        mapCenterX = position.x.coerceIn(MAP_WORLD_MIN, MAP_WORLD_MAX)
        mapCenterZ = position.z.coerceIn(MAP_WORLD_MIN, MAP_WORLD_MAX)
        clampMapCenter()
        mapCentered = true
        if (redraw) invalidate()
    }

    private fun setMapZoom(value: Float) {
        mapZoom = value.coerceIn(1f, MAP_MAX_ZOOM)
        clampMapCenter()
        invalidate()
    }

    private fun clampMapCenter() {
        val halfVisibleWorld = (MAP_WORLD_MAX - MAP_WORLD_MIN) / mapZoom / 2f
        val minimumCenter = MAP_WORLD_MIN + halfVisibleWorld
        val maximumCenter = MAP_WORLD_MAX - halfVisibleWorld
        mapCenterX = mapCenterX.coerceIn(minimumCenter, maximumCenter)
        mapCenterZ = mapCenterZ.coerceIn(minimumCenter, maximumCenter)
    }

    private fun worldToMap(
        viewport: RectF, x: Float, z: Float, worldLeft: Float, worldTop: Float,
        visibleWorld: Float
    ): Pair<Float, Float> =
        viewport.left + (x - worldLeft) / visibleWorld * viewport.width() to
            viewport.top + (z - worldTop) / visibleWorld * viewport.height()

    private fun mapDataZoom(): Int =
        (ceil(log2(mapZoom.toDouble())).toInt() + 1).coerceIn(0, 6)

    private fun drawMapGuides(canvas: Canvas, viewport: RectF, visibleWorld: Float) {
        val compassX = viewport.right - 42f
        val compassY = viewport.top + 43f
        paint.color = Color.argb(165, 1, 15, 19)
        canvas.drawCircle(compassX, compassY, 27f, paint)
        stroke.color = bronze
        stroke.strokeWidth = 1.5f
        canvas.drawCircle(compassX, compassY, 27f, stroke)
        canvas.drawLine(compassX, compassY - 18f, compassX, compassY + 18f, stroke)
        canvas.drawLine(compassX - 5f, compassY + 9f, compassX, compassY + 18f, stroke)
        canvas.drawLine(compassX + 5f, compassY + 9f, compassX, compassY + 18f, stroke)
        text(canvas, "N", compassX, compassY - 34f, 13f, warmWhite, Paint.Align.CENTER)

        val targetDistance = visibleWorld * 0.18f
        val distance = MAP_SCALE_DISTANCES
            .firstOrNull { it <= targetDistance } ?: 50f
        val barWidth = distance / visibleWorld * viewport.width()
        val left = viewport.left + 24f
        val bottom = viewport.bottom - 24f
        paint.color = Color.argb(165, 1, 15, 19)
        canvas.drawRoundRect(
            RectF(left - 12f, bottom - 31f, left + barWidth + 12f, bottom + 13f),
            5f, 5f, paint
        )
        stroke.color = warmWhite
        stroke.strokeWidth = 2f
        canvas.drawLine(left, bottom, left + barWidth, bottom, stroke)
        canvas.drawLine(left, bottom - 6f, left, bottom + 6f, stroke)
        canvas.drawLine(left + barWidth, bottom - 6f, left + barWidth, bottom + 6f, stroke)
        text(
            canvas, "${distance.roundToInt()} m", left + barWidth / 2f, bottom - 10f,
            12f, warmWhite, Paint.Align.CENTER
        )
        stroke.strokeWidth = 1f
    }

    private fun shrineAt(screenX: Float, screenY: Float): ShrinePoint? {
        val viewport = mapViewportRect()
        if (!viewport.contains(screenX, screenY)) return null
        val visibleWorld = (MAP_WORLD_MAX - MAP_WORLD_MIN) / mapZoom
        val half = visibleWorld / 2f
        val worldLeft = (mapCenterX - half).coerceIn(MAP_WORLD_MIN, MAP_WORLD_MAX - visibleWorld)
        val worldTop = (mapCenterZ - half).coerceIn(MAP_WORLD_MIN, MAP_WORLD_MAX - visibleWorld)
        val touchRadius = max(30f, (12f * mapZoom.coerceAtMost(2.25f)).coerceAtMost(29f) + 10f)
        return shrines.asSequence()
            .filter { it.id in snapshot.clearedShrines }
            .map { shrine ->
                val (x, y) = worldToMap(
                    viewport, shrine.x, shrine.z, worldLeft, worldTop, visibleWorld
                )
                shrine to ((x - screenX) * (x - screenX) + (y - screenY) * (y - screenY))
            }
            .filter { (_, distanceSquared) -> distanceSquared <= touchRadius * touchRadius }
            .minByOrNull { (_, distanceSquared) -> distanceSquared }
            ?.first
    }

    private fun drawMapLabels(
        canvas: Canvas, viewport: RectF, worldLeft: Float, worldTop: Float, visibleWorld: Float
    ) {
        val detailZoom = mapDataZoom()
        mapLabels.forEach { point ->
            if (detailZoom !in point.minZoom..point.maxZoom) return@forEach
            val (screenX, screenY) = worldToMap(
                viewport, point.x, point.z, worldLeft, worldTop, visibleWorld
            )
            if (screenX in (viewport.left + 30f)..(viewport.right - 30f) &&
                screenY in (viewport.top + 20f)..(viewport.bottom - 20f)
            ) {
                paint.color = Color.argb(135, 1, 13, 17)
                val labelSize = if (point.category == "Region") 16f else 12f
                val half = paint.apply { textSize = labelSize }.measureText(point.name) / 2f + 8f
                canvas.drawRoundRect(
                    RectF(screenX - half, screenY - labelSize - 5f, screenX + half, screenY + 5f),
                    4f, 4f, paint
                )
                text(canvas, point.name, screenX, screenY, labelSize, muted, Paint.Align.CENTER)
            }
        }
    }

    private fun drawMapMarkers(
        canvas: Canvas, viewport: RectF, worldLeft: Float, worldTop: Float, visibleWorld: Float
    ) {
        val detailZoom = mapDataZoom()
        canvas.save()
        canvas.clipRect(viewport)
        mapMarkers.forEach { marker ->
            if (marker.category == "Shrine") return@forEach
            if (detailZoom !in marker.minZoom..marker.maxZoom) return@forEach
            val (x, y) = worldToMap(
                viewport, marker.x, marker.z, worldLeft, worldTop, visibleWorld
            )
            if (x !in viewport.left..viewport.right || y !in viewport.top..viewport.bottom) {
                return@forEach
            }
            val iconPath = when (marker.category) {
                "Sheikah Tower" -> "tower"
                "Shrine" -> if (marker.name.startsWith("EX ")) "shrine_dlc" else "shrine"
                "Tech Lab" -> "lab"
                "Stable" -> "stable"
                "Village" -> "village"
                "Settlement" -> "settlement"
                "Great Fairy" -> "fountain"
                "Goddess Statue" -> "statue"
                "Memory" -> "memory"
                else -> return@forEach
            }
            val baseSize = when (marker.category) {
                "Sheikah Tower" -> 17f
                "Shrine" -> 11f
                "Tech Lab", "Village", "Stable" -> 14f
                else -> 12f
            }
            val size = (baseSize * mapZoom.coerceAtMost(2.25f)).coerceAtMost(28f)
            loadBitmap("botw/ui/map/$iconPath.png")?.let { icon ->
                paint.alpha = 235
                canvas.drawBitmap(icon, null, RectF(x - size, y - size, x + size, y + size), paint)
                paint.alpha = 255
            }
            val showName = marker.category != "Shrine" && mapZoom >= 2.25f || mapZoom >= 3.35f
            if (showName) {
                text(canvas, ellipsize(marker.name, 28), x, y + size + 15f, 11f, warmWhite, Paint.Align.CENTER)
            }
        }
        paint.alpha = 255
        canvas.restore()
    }

    private fun drawShrineMarkers(
        canvas: Canvas, viewport: RectF, worldLeft: Float, worldTop: Float, visibleWorld: Float
    ) {
        val shrineIcon = loadBitmap("botw/ui/map/shrine.png")
        val dlcIcon = loadBitmap("botw/ui/map/shrine_dlc.png") ?: shrineIcon
        canvas.save()
        canvas.clipRect(viewport)
        shrines.forEach { shrine ->
            val (x, y) = worldToMap(
                viewport, shrine.x, shrine.z, worldLeft, worldTop, visibleWorld
            )
            if (x !in viewport.left..viewport.right || y !in viewport.top..viewport.bottom) {
                return@forEach
            }
            val cleared = shrine.id in snapshot.clearedShrines
            val entered = cleared || shrine.id in snapshot.enteredShrines
            val size = (12f * mapZoom.coerceAtMost(2.25f)).coerceAtMost(29f)
            val isDlc = shrine.id.removePrefix("Dungeon").toIntOrNull()?.let { it >= 120 } == true
            paint.alpha = when {
                cleared -> 255
                entered -> 190
                else -> 105
            }
            (if (isDlc) dlcIcon else shrineIcon)?.let { icon ->
                canvas.drawBitmap(icon, null, RectF(x - size, y - size, x + size, y + size), paint)
            }
            paint.alpha = 255
            if (cleared) {
                stroke.color = cyan
                stroke.strokeWidth = 2f
                canvas.drawCircle(x, y, size + 3f, stroke)
                stroke.strokeWidth = 1f
            }
            if (mapZoom >= 3.35f) {
                text(
                    canvas, ellipsize(shrine.name, 28), x, y + size + 16f, 11.5f,
                    if (cleared) warmWhite else muted, Paint.Align.CENTER
                )
            }
        }
        paint.alpha = 255
        canvas.restore()
    }

    private fun shrinePopupRect() = RectF(245f, 294f, 995f, 792f)
    private fun shrineTravelRect() = RectF(300f, 684f, 590f, 756f)
    private fun shrineCancelRect() = RectF(650f, 684f, 940f, 756f)

    private fun drawShrinePopup(canvas: Canvas, shrine: ShrinePoint) {
        paint.color = Color.argb(170, 0, 7, 9)
        canvas.drawRect(RectF(10f, 90f, 1230f, 1070f), paint)
        val popup = shrinePopupRect()
        paint.color = Color.rgb(2, 22, 26)
        canvas.drawRect(popup, paint)
        stroke.color = cyan
        stroke.strokeWidth = 2f
        canvas.drawRect(popup, stroke)
        stroke.strokeWidth = 1f
        cornerMarks(canvas, popup, cyan)
        drawSheikahMedallion(canvas, popup.centerX(), popup.top + 94f, 48f)
        text(
            canvas, "SHRINE COMPLETE", popup.centerX(), popup.top + 168f,
            16f, green, Paint.Align.CENTER
        )
        text(
            canvas, shrine.name, popup.centerX(), popup.top + 220f,
            32f, warmWhite, Paint.Align.CENTER
        )
        if (shrine.trial.isNotBlank()) {
            text(
                canvas, shrine.trial, popup.centerX(), popup.top + 260f,
                19f, muted, Paint.Align.CENTER
            )
        }
        text(
            canvas, "Travel to this shrine?", popup.centerX(), popup.top + 330f,
            22f, warmWhite, Paint.Align.CENTER
        )
        drawPopupButton(
            canvas, shrineTravelRect(), "TRAVEL",
            snapshot.capabilities and CAP_FAST_TRAVEL != 0
        )
        drawPopupButton(canvas, shrineCancelRect(), "CANCEL", true)
    }

    private fun drawPopupButton(canvas: Canvas, rect: RectF, label: String, enabled: Boolean) {
        paint.color = if (enabled) Color.argb(185, 3, 39, 44) else Color.argb(120, 14, 25, 27)
        canvas.drawRect(rect, paint)
        stroke.color = if (enabled) cyan else muted
        canvas.drawRect(rect, stroke)
        text(
            canvas, label, rect.centerX(), rect.centerY() + 8f, 20f,
            if (enabled) warmWhite else muted, Paint.Align.CENTER
        )
    }

    private fun loadMapPoints(path: String, includedCategories: Set<String>): List<MapPoint> =
        try {
            context.assets.open(path).bufferedReader().use { reader ->
                val root = JSONArray(reader.readText())
                buildList {
                    for (categoryIndex in 0 until root.length()) {
                        val category = root.optJSONObject(categoryIndex) ?: continue
                        val categoryName = category.optString("name")
                        if (categoryName !in includedCategories) continue
                        val layers = category.optJSONArray("layers") ?: continue
                        for (layerIndex in 0 until layers.length()) {
                            val layer = layers.optJSONObject(layerIndex) ?: continue
                            val minimumZoom = layer.optInt("minZoom", 0)
                            val maximumZoom = layer.optInt("maxZoom", Int.MAX_VALUE)
                            val markers = layer.optJSONArray("markers") ?: continue
                            for (markerIndex in 0 until markers.length()) {
                                val marker = markers.optJSONObject(markerIndex) ?: continue
                                val coords = marker.optJSONArray("coords") ?: continue
                                if (coords.length() < 2 || coords.opt(0) !is Number ||
                                    coords.opt(1) !is Number
                                ) continue

                                val x = (coords.optDouble(1) / 2.0).toFloat()
                                val z = (-coords.optDouble(0) / 2.0).toFloat()
                                if (!x.isFinite() || !z.isFinite()) continue
                                add(
                                    MapPoint(
                                        categoryName, marker.optString("name", marker.optString("id")),
                                        x, z, minimumZoom, maximumZoom
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (exception: Exception) {
            Log.error("[BOTW Companion] Map point data failed for $path: ${exception.message}")
            emptyList()
        }

    private fun loadShrines(): List<ShrinePoint> =
        try {
            context.assets.open("botw/ui/map/shrines.json").bufferedReader().use { reader ->
                val root = JSONArray(reader.readText())
                buildList(root.length()) {
                    for (index in 0 until root.length()) {
                        val shrine = root.getJSONObject(index)
                        add(
                            ShrinePoint(
                                shrine.getInt("index"), shrine.getString("id"),
                                shrine.getString("name"), shrine.optString("trial"),
                                shrine.getDouble("x").toFloat(),
                                shrine.getDouble("y").toFloat(),
                                shrine.getDouble("z").toFloat()
                            )
                        )
                    }
                }
            }
        } catch (exception: Exception) {
            Log.error("[BOTW Companion] Shrine data failed: ${exception.message}")
            emptyList()
        }

    private fun drawPlayerMapMarker(
        canvas: Canvas, viewport: RectF, position: PlayerMap, worldLeft: Float,
        worldTop: Float, visibleWorld: Float
    ) {
        val (x, y) = worldToMap(
            viewport, position.x, position.z, worldLeft, worldTop, visibleWorld
        )
        if (x !in viewport.left..viewport.right || y !in viewport.top..viewport.bottom) return
        paint.color = Color.argb(76, 35, 226, 239)
        canvas.drawCircle(x, y, 25f, paint)
        paint.color = cyan
        canvas.drawCircle(x, y, 12f, paint)
        canvas.save()
        canvas.rotate(position.heading, x, y)
        path.reset()
        path.moveTo(x, y - 25f)
        path.lineTo(x - 9f, y - 7f)
        path.lineTo(x + 9f, y - 7f)
        path.close()
        paint.color = warmWhite
        canvas.drawPath(path, paint)
        canvas.restore()
        stroke.color = Color.rgb(1, 30, 34)
        stroke.strokeWidth = 2f
        canvas.drawCircle(x, y, 12f, stroke)
        stroke.strokeWidth = 1f
    }

    private fun questFilterRects() = listOf(
        RectF(30f, 158f, 185f, 202f),
        RectF(192f, 158f, 347f, 202f),
        RectF(354f, 158f, 524f, 202f)
    )

    private fun questRowRect(localIndex: Int): RectF {
        val top = QUEST_LIST_TOP + localIndex * QUEST_ROW_STRIDE
        return RectF(QUEST_LIST_LEFT, top, QUEST_LIST_RIGHT, top + QUEST_ROW_HEIGHT)
    }

    private fun questAt(x: Float, y: Float): Quest? {
        if (x !in QUEST_LIST_LEFT..QUEST_LIST_RIGHT ||
            y !in QUEST_LIST_TOP..QUEST_LIST_BOTTOM
        ) return null
        val local = ((y - QUEST_LIST_TOP) / QUEST_ROW_STRIDE).toInt()
        if (local !in 0 until QUEST_VISIBLE_ROWS ||
            !questRowRect(local).contains(x, y)
        ) return null
        return filteredQuests().getOrNull(questScroll + local)
    }

    private fun filteredQuests(): List<Quest> = when (questFilter) {
        1 -> snapshot.quests.filterNot { it.complete }
        2 -> snapshot.quests.filter { it.complete }
        else -> snapshot.quests
    }

    private fun selectedQuest(): Quest? {
        val quests = filteredQuests()
        return quests.firstOrNull { it.id == selectedQuestId } ?: quests.firstOrNull()
    }

    private fun clampQuestSelection() {
        val quests = filteredQuests()
        if (quests.none { it.id == selectedQuestId }) selectedQuestId = quests.firstOrNull()?.id
        questScroll = questScroll.coerceIn(0, max(0, quests.size - QUEST_VISIBLE_ROWS))
    }

    private fun drawQuestScrollbar(canvas: Canvas, count: Int, visibleRows: Int) {
        if (count <= visibleRows) return
        val track = RectF(522f, QUEST_LIST_TOP, 528f, QUEST_LIST_BOTTOM)
        paint.color = Color.argb(80, 39, 185, 198)
        canvas.drawRoundRect(track, 3f, 3f, paint)
        val thumbHeight = max(52f, track.height() * visibleRows / count)
        val maximum = max(1, count - visibleRows)
        val top = track.top + (track.height() - thumbHeight) * questScroll / maximum
        paint.color = cyan
        canvas.drawRoundRect(RectF(track.left - 1f, top, track.right + 1f, top + thumbHeight), 4f, 4f, paint)
    }

    private fun questKind(quest: Quest): String = when {
        quest.actorName.contains("Mini", ignoreCase = true) -> "Side Quest"
        quest.actorName.contains("Shrine", ignoreCase = true) ||
            quest.actorName.contains("Dungeon", ignoreCase = true) -> "Shrine Quest"
        quest.type.isNotBlank() -> quest.type
        else -> "Main Quest"
    }

    private fun drawQuestDetails(canvas: Canvas, panel: RectF, quest: Quest) {
        val visibleQuests = filteredQuests()
        val questNumber = visibleQuests.indexOfFirst { it.id == quest.id } + 1
        text(canvas, questKind(quest).uppercase(), panel.left + 28f, panel.top + 38f, 13f, bronze)
        text(
            canvas, ellipsizeToWidth(quest.name, panel.width() - 220f, 32f),
            panel.left + 28f, panel.top + 81f, 32f, warmWhite
        )
        badge(
            canvas, RectF(panel.right - 145f, panel.top + 26f, panel.right - 24f, panel.top + 64f),
            if (quest.complete) "COMPLETE" else "ACTIVE",
            if (quest.complete) green else cyan, 17f
        )
        if (questNumber > 0) {
            text(
                canvas, "$questNumber of ${visibleQuests.size}", panel.right - 28f,
                panel.top + 87f, 13f, muted, Paint.Align.RIGHT
            )
        }
        stroke.color = bronzeDark
        canvas.drawLine(panel.left + 28f, panel.top + 105f, panel.right - 28f, panel.top + 105f, stroke)
        text(canvas, "CURRENT STEP", panel.left + 28f, panel.top + 145f, 13f, bronze)
        text(
            canvas, quest.step.ifBlank { if (quest.complete) "Finished" else "In progress" },
            panel.left + 28f, panel.top + 177f, 21f,
            if (quest.complete) green else cyan
        )
        if (quest.location.isNotBlank()) {
            text(canvas, "Region", panel.right - 250f, panel.top + 145f, 13f, bronze)
            text(canvas, quest.location, panel.right - 28f, panel.top + 177f, 17f, muted, Paint.Align.RIGHT)
        }
        text(canvas, "OBJECTIVE", panel.left + 28f, panel.top + 235f, 13f, bronze)
        multilineText(
            canvas,
            quest.objective.ifBlank { "Quest objective is updating from the loaded save." },
            panel.left + 28f, panel.top + 274f, panel.width() - 56f, 20.5f, warmWhite, 21
        )
        val footer = RectF(panel.left + 24f, panel.bottom - 88f, panel.right - 24f, panel.bottom - 24f)
        paint.color = Color.argb(95, 3, 28, 32)
        canvas.drawRect(footer, paint)
        stroke.color = bronzeDark
        canvas.drawRect(footer, stroke)
        text(
            canvas,
            if (quest.complete) "This quest is complete in the current save."
            else "Live quest state - Updates automatically",
            footer.centerX(), footer.centerY() + 6f, 16f,
            if (quest.complete) green else muted, Paint.Align.CENTER
        )
    }

    private fun ellipsize(value: String, maximum: Int): String =
        if (value.length <= maximum) value else value.take(maximum - 3).trimEnd() + "..."

    private fun ellipsizeToWidth(value: String, maximumWidth: Float, textSize: Float): String {
        paint.textSize = textSize
        paint.typeface = normalTypeface
        if (paint.measureText(value) <= maximumWidth) return value
        val suffix = "..."
        var low = 0
        var high = value.length
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (paint.measureText(value.take(middle).trimEnd() + suffix) <= maximumWidth) {
                low = middle
            } else {
                high = middle - 1
            }
        }
        return value.take(low).trimEnd() + suffix
    }

    private fun questSummary(quest: Quest): String =
        quest.objective.lineSequence().firstOrNull { it.isNotBlank() }
            ?: quest.step.takeIf { it.isNotBlank() }
            ?: if (quest.complete) "Quest completed" else "Objective updating"

    private fun drawCalibrating(canvas: Canvas, rect: RectF, label: String) {
        drawSheikahMedallion(canvas, rect.centerX(), rect.centerY() - 20f, 50f)
        text(canvas, label, rect.centerX(), rect.centerY() + 65f, 18f, muted, Paint.Align.CENTER)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isSaveLoaded()) return true
        if (selectedShrine == null) mapScaleDetector.onTouchEvent(event)
        val scale = min(width / DESIGN_WIDTH, height / DESIGN_HEIGHT)
        if (scale <= 0f || !scale.isFinite()) return true
        val x = (event.x - (width - DESIGN_WIDTH * scale) / 2f) / scale
        val y = (event.y - (height - DESIGN_HEIGHT * scale) / 2f) / scale
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = x
                downY = y
                downRowOffset = rowOffset
                downCategoryScrollX = categoryScrollX
                downQuestScroll = questScroll
                downMapCenterX = mapCenterX
                downMapCenterZ = mapCenterZ
                inventoryMoved = false
                categoryMoved = false
                questMoved = false
                mapMoved = false
                multiTouchGesture = false
                if (page == Page.Map && selectedShrine != null) {
                    draggingInventory = false
                    draggingScrollbar = false
                    draggingCategories = false
                    draggingMap = false
                    draggingQuests = false
                    return true
                }
                draggingCategories = page == Page.Inventory &&
                    x in CATEGORY_LEFT..CATEGORY_RIGHT && y in CATEGORY_TOP..CATEGORY_BOTTOM
                draggingInventory = !draggingCategories && page == Page.Inventory &&
                    x in GRID_START_X..671f &&
                    y in GRID_START_Y..GRID_BOTTOM
                draggingScrollbar = draggingInventory && x >= 650f
                draggingMap = page == Page.Map && mapViewportRect().contains(x, y)
                draggingQuests = page == Page.Quests &&
                    x in QUEST_LIST_LEFT..QUEST_LIST_RIGHT &&
                    y in QUEST_LIST_TOP..QUEST_LIST_BOTTOM
                downInventoryCell = inventoryCellAt(x, y)
                downInventoryItemId = downInventoryCell?.let(::inventoryItemAtCell)?.second?.id
                downCategoryIndex = if (draggingCategories) categoryAt(x, y) else null
                downQuestId = if (draggingQuests) questAt(x, y)?.id else null
                downRuneType = if (page == Page.Inventory &&
                    snapshot.capabilities and CAP_RUNES != 0
                ) runeAt(x, y)?.type else null
                if (draggingScrollbar) {
                    updateScrollbarPosition(y)
                    inventoryMoved = true
                }
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                multiTouchGesture = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (page == Page.Map && selectedShrine != null) return true
                if (mapScaleDetector.isInProgress) return true
                if (draggingMap) {
                    if (max(abs(x - downX), abs(y - downY)) > 8f) mapMoved = true
                    if (mapMoved) {
                        val visibleWorld = (MAP_WORLD_MAX - MAP_WORLD_MIN) / mapZoom
                        val viewport = mapViewportRect()
                        mapCenterX = downMapCenterX - (x - downX) / viewport.width() * visibleWorld
                        mapCenterZ = downMapCenterZ - (y - downY) / viewport.height() * visibleWorld
                        mapCentered = true
                        invalidate()
                    }
                    return true
                }
                if (draggingQuests) {
                    if (max(abs(x - downX), abs(y - downY)) > 10f) questMoved = true
                    if (questMoved) {
                        val deltaRows = ((downY - y) / QUEST_ROW_STRIDE).roundToInt()
                        questScroll = (downQuestScroll + deltaRows).coerceIn(
                            0, max(0, filteredQuests().size - QUEST_VISIBLE_ROWS)
                        )
                        invalidate()
                    }
                    return true
                }
                if (draggingCategories) {
                    if (max(abs(x - downX), abs(y - downY)) > 9f) categoryMoved = true
                    if (categoryMoved) {
                        setCategoryScroll(downCategoryScrollX + downX - x)
                    }
                    return true
                }
                if (draggingScrollbar) {
                    updateScrollbarPosition(y)
                    return true
                }
                if (draggingInventory && max(abs(x - downX), abs(y - downY)) > 12f) {
                    inventoryMoved = true
                    val deltaRows = ((downY - y) / (GRID_CELL_HEIGHT + GRID_GAP)).roundToInt()
                    setRowOffset(downRowOffset + deltaRows)
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (page == Page.Map && selectedShrine != null) {
                    val shrine = selectedShrine ?: return true
                    val isTap = max(abs(x - downX), abs(y - downY)) <= 12f
                    if (isTap) performClick()
                    if (isTap && shrineCancelRect().contains(x, y) &&
                        shrineCancelRect().contains(downX, downY)
                    ) {
                        selectedShrine = null
                        invalidate()
                    } else if (isTap && shrineTravelRect().contains(x, y) &&
                        shrineTravelRect().contains(downX, downY) &&
                        snapshot.capabilities and CAP_FAST_TRAVEL != 0
                    ) {
                        val queued = NativeLibrary.performBotwCompanionAction(
                            ACTION_FAST_TRAVEL, shrine.index.toLong()
                        )
                        Log.info(
                            "[BOTW Companion] Travel ${shrine.name}: " +
                                if (queued) "queued" else "rejected"
                        )
                        if (queued) selectedShrine = null
                        invalidate()
                    }
                    return true
                }
                val wasInventoryScroll = inventoryMoved
                val wasInventoryGesture = draggingInventory
                val wasCategoryGesture = draggingCategories
                val wasMapGesture = draggingMap
                val wasQuestGesture = draggingQuests
                val pressedRuneType = downRuneType
                val isTap = max(abs(x - downX), abs(y - downY)) <= 12f
                draggingInventory = false
                draggingScrollbar = false
                draggingCategories = false
                draggingMap = false
                draggingQuests = false
                downRuneType = null
                if (multiTouchGesture) {
                    multiTouchGesture = false
                    return true
                }
                if (isTap) performClick()
                if (wasInventoryScroll) {
                    return true
                }
                if (wasMapGesture) {
                    if (!mapMoved && isTap) {
                        shrineAt(x, y)?.let {
                            selectedShrine = it
                            invalidate()
                        }
                    }
                    mapMoved = false
                    return true
                }
                if (wasQuestGesture) {
                    if (!questMoved && isTap) {
                        val quest = questAt(x, y)
                        if (quest != null && quest.id == downQuestId) {
                            selectedQuestId = quest.id
                            invalidate()
                        }
                    }
                    questMoved = false
                    return true
                }
                if (wasCategoryGesture) {
                    if (categoryMoved) {
                        snapCategoryScroll()
                        categoryMoved = false
                        return true
                    }
                    if (x in CATEGORY_LEFT..CATEGORY_RIGHT && y in CATEGORY_TOP..CATEGORY_BOTTOM) {
                        val index = categoryAt(x, y)
                        if (index != null) {
                            if (index != downCategoryIndex) return true
                            categoryIndex = index
                            selectedItem = 0
                            rowOffset = 0
                            ensureCategoryVisible(index)
                        }
                        invalidate()
                    }
                    return true
                }
                if (isTap && page == Page.Inventory &&
                    snapshot.capabilities and CAP_RUNES != 0
                ) {
                    val rune = runeAt(x, y)
                    if (rune != null && rune.type == pressedRuneType) {
                        val selected = NativeLibrary.performBotwCompanionAction(
                            ACTION_SELECT_RUNE, rune.type.toLong()
                        )
                        Log.info(
                            "[BOTW Companion] Select rune ${rune.name}: " +
                                if (selected) "queued" else "rejected"
                        )
                        if (selected) {


                            requestActionRefresh(rune.type)
                        }
                        invalidate()
                        return true
                    }
                }
                if (isTap && downY in 9f..79f && y in 9f..79f) {
                    val gap = 8f
                    val margin = 10f
                    val tabWidth = (DESIGN_WIDTH - margin * 2f - gap * 2f) / 3f
                    Page.entries.forEachIndexed { index, candidate ->
                        val left = margin + index * (tabWidth + gap)
                        if (downX in left..(left + tabWidth) && x in left..(left + tabWidth)) {
                            page = candidate
                            selectedShrine = null
                            notifyPlayerAppearance()
                            invalidate()
                            return true
                        }
                    }
                }
                if (isTap && page == Page.Map) {
                    when {
                        mapZoomOutRect().contains(x, y) && mapZoomOutRect().contains(downX, downY) ->
                            setMapZoom(mapZoom / 1.5f)
                        mapZoomInRect().contains(x, y) && mapZoomInRect().contains(downX, downY) ->
                            setMapZoom(mapZoom * 1.5f)
                        mapCenterRect().contains(x, y) && mapCenterRect().contains(downX, downY) ->
                            recenterMap()
                        else -> return true
                    }
                    return true
                }
                if (isTap && page == Page.Quests) {
                    val filters = questFilterRects()
                    val index = filters.indexOfFirst { it.contains(x, y) && it.contains(downX, downY) }
                    if (index >= 0) {
                        questFilter = index
                        questScroll = 0
                        clampQuestSelection()
                        invalidate()
                    }
                    return true
                }
                if (wasInventoryGesture && isTap && page == Page.Inventory &&
                    snapshot.capabilities and CAP_INVENTORY != 0
                ) {
                    val cell = inventoryCellAt(x, y)
                    val hit = cell?.let(::inventoryItemAtCell)
                    if (cell != null && cell == downInventoryCell && hit != null &&
                        hit.second.id == downInventoryItemId
                    ) {
                        val (index, item) = hit
                        selectedItem = index
                        if (snapshot.capabilities and CAP_ITEM_ACTIONS != 0 &&
                            item.equippable
                        ) {
                            val equipped = NativeLibrary.performBotwCompanionAction(
                                ACTION_EQUIP_ITEM, item.id
                            )
                            Log.info(
                                "[BOTW Companion] Equip ${item.actorName}: " +
                                    if (equipped) "queued" else "rejected"
                            )
                            if (equipped) {
                                requestActionRefresh(equipItemId = item.id)
                            }
                        }
                        invalidate()
                    }
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                draggingInventory = false
                draggingScrollbar = false
                draggingCategories = false
                draggingMap = false
                draggingQuests = false
                categoryMoved = false
                mapMoved = false
                questMoved = false
                multiTouchGesture = false
                downRuneType = null
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun runeStripRect(panel: RectF = RectF(687f, 350f, 1230f, 1070f)) =
        RectF(panel.left + 14f, panel.bottom - 108f, panel.right - 14f, panel.bottom - 14f)

    private fun runeAt(x: Float, y: Float): Rune? {
        val visible = snapshot.runes.filter { it.available }
        if (visible.isEmpty()) return null
        val strip = runeStripRect()
        if (x !in strip.left..strip.right || y !in strip.top..strip.bottom) return null


        val index = ((x - strip.left) / (strip.width() / visible.size)).toInt()
            .coerceIn(0, visible.lastIndex)
        return visible.getOrNull(index)
    }

    private fun requestActionRefresh(runeType: Int? = null, equipItemId: Long? = null) {
        pendingRuneType = runeType
        pendingRuneRefreshDeadline = if (runeType != null) {
            SystemClock.uptimeMillis() + RUNE_REFRESH_TIMEOUT_MS
        } else {
            0L
        }
        pendingEquipItemId = equipItemId
        pendingEquipRefreshDeadline = if (equipItemId != null) {
            SystemClock.uptimeMillis() + EQUIP_REFRESH_TIMEOUT_MS
        } else {
            0L
        }
        if (runeType != null && equipItemId == null) {
            lastRawLiveSnapshot = ""
        } else {
            lastRawSnapshot = ""
            lastRawLiveSnapshot = ""
            lastFullSnapshotAt = 0L
        }
        handler.removeCallbacks(poll)
        handler.removeCallbacks(actionRefresh)
        handler.postDelayed(actionRefresh, ACTION_REFRESH_DELAY_MS)
    }

    private fun schedulePendingActionRefresh(): Boolean {
        pendingRuneType?.let { target ->
            val confirmed = snapshot.runes.any { rune ->
                rune.selected && (rune.type == target || (rune.id == "bombs" && target == 0))
            }
            if (confirmed || SystemClock.uptimeMillis() >= pendingRuneRefreshDeadline) {
                pendingRuneType = null
                pendingRuneRefreshDeadline = 0L
            } else {
                lastRawLiveSnapshot = ""
                handler.removeCallbacks(actionRefresh)
                handler.postDelayed(actionRefresh, ACTION_REFRESH_DELAY_MS)
                return true
            }
        }

        pendingEquipItemId?.let { targetId ->
            val target = snapshot.inventory.firstOrNull { it.id == targetId }
            val mailboxBusy = snapshot.equipState == 1 || snapshot.equipState == 2
            val confirmed = target?.equipped == true && !mailboxBusy
            val rejected = snapshot.equipState == 4 && !mailboxBusy
            if (confirmed || rejected ||
                SystemClock.uptimeMillis() >= pendingEquipRefreshDeadline
            ) {
                pendingEquipItemId = null
                pendingEquipRefreshDeadline = 0L
            } else {


                lastRawSnapshot = ""
                lastFullSnapshotAt = 0L
                handler.removeCallbacks(actionRefresh)
                handler.postDelayed(actionRefresh, EQUIP_REFRESH_RETRY_MS)
                return true
            }
        }
        return false
    }

    private fun maximumRowOffset(): Int {
        val rows = ceil(filteredItems().size / GRID_COLUMNS.toFloat()).toInt()
        return max(0, rows - GRID_ROWS_VISIBLE)
    }

    private fun inventoryCellAt(x: Float, y: Float): Int? {
        if (x !in GRID_START_X..(GRID_START_X + GRID_COLUMNS * (GRID_CELL_WIDTH + GRID_GAP) -
                GRID_GAP) || y !in GRID_START_Y..GRID_BOTTOM
        ) {
            return null
        }
        val localX = x - GRID_START_X
        val localY = y - GRID_START_Y
        val column = (localX / (GRID_CELL_WIDTH + GRID_GAP)).toInt()
        val row = (localY / (GRID_CELL_HEIGHT + GRID_GAP)).toInt()
        if (column !in 0 until GRID_COLUMNS || row !in 0 until GRID_ROWS_VISIBLE ||
            localX % (GRID_CELL_WIDTH + GRID_GAP) > GRID_CELL_WIDTH ||
            localY % (GRID_CELL_HEIGHT + GRID_GAP) > GRID_CELL_HEIGHT
        ) {
            return null
        }
        return row * GRID_COLUMNS + column
    }

    private fun inventoryItemAtCell(cell: Int): Pair<Int, Item>? {
        val index = rowOffset * GRID_COLUMNS + cell
        return filteredItems().getOrNull(index)?.let { index to it }
    }

    private fun categoryAt(x: Float, y: Float): Int? {
        if (x !in CATEGORY_LEFT..CATEGORY_RIGHT || y !in CATEGORY_TOP..CATEGORY_BOTTOM) return null
        return ((x - CATEGORY_LEFT + categoryScrollX) / CATEGORY_CELL_WIDTH).toInt()
            .takeIf { it in categories.indices }
    }

    private fun setRowOffset(value: Int) {
        rowOffset = value.coerceIn(0, maximumRowOffset())
        selectedItem = selectedItem.coerceIn(0, max(0, filteredItems().lastIndex))
        invalidate()
    }

    private fun updateScrollbarPosition(y: Float) {
        val maximum = maximumRowOffset()
        if (maximum == 0) return
        val fraction = ((y - GRID_START_Y) / (GRID_BOTTOM - GRID_START_Y)).coerceIn(0f, 1f)
        setRowOffset((fraction * maximum).roundToInt())
    }

    private fun notifyPlayerAppearance() {
        val equipment = snapshot.equipment.associateBy { it.slot }
        val next = BotwPlayerAppearance(
            head = equipment["Head"]?.item?.actorName,
            chest = equipment["Chest"]?.item?.actorName,
            legs = equipment["Legs"]?.item?.actorName,
            weapon = null,
            bow = equipment["Bow"]?.item?.actorName,
            shield = equipment["Shield"]?.item?.actorName
        )
        val visible = page == Page.Inventory && isSaveLoaded()
        if (next != lastPlayerAppearance || visible != lastPlayerAppearanceVisible) {
            lastPlayerAppearance = next
            lastPlayerAppearanceVisible = visible
            Log.info(
                "[BOTW Companion] Appearance visible=$visible, " +
                    "head=${next.head}, chest=${next.chest}, legs=${next.legs}, " +
                    "weapon=${next.weapon}, bow=${next.bow}, shield=${next.shield}"
            )
            onPlayerAppearanceChanged(next, visible)
        }
    }

    private fun clampSelection(preferredItemId: Long? = null) {
        val items = filteredItems()
        if (items.isEmpty()) {
            selectedItem = 0
            rowOffset = 0
            return
        }
        val preferredIndex = preferredItemId?.let { id -> items.indexOfFirst { it.id == id } } ?: -1
        selectedItem = if (preferredIndex >= 0) preferredIndex else selectedItem.coerceIn(items.indices)
        rowOffset = rowOffset.coerceIn(0, maximumRowOffset())
    }

    private fun filteredItems(): List<Item> {
        val source = snapshot.inventory
        if (source !== filteredInventorySource || filteredInventoryCategory != categoryIndex) {
            val category = categories.getOrElse(categoryIndex) { categories.first() }
            filteredInventorySource = source
            filteredInventoryCategory = categoryIndex
            filteredInventoryItems = source.filter { it.category.equals(category, true) }
        }
        return filteredInventoryItems
    }

    private fun parseSnapshot(json: String): Snapshot {
        val root = JSONObject(json)
        val stats = parseStats(root)
        val inventory = root.optJSONArray("inventory").mapObjects { itemFromJson(it) }
        val equipment = root.optJSONArray("equipment").mapObjects { Equipment(it.optString("slot"), itemFromJson(it.getJSONObject("item"))) }
        val quests = root.optJSONArray("quests").mapObjects {
            Quest(
                it.optLong("id"), it.optString("actorName"), it.optString("name"),
                it.optString("objective"), it.optString("step"), it.optString("location"),
                it.optString("type"), it.optBoolean("complete")
            )
        }
        val effects = root.optJSONArray("effects").mapObjects { Effect(it.optString("name"), it.optInt("seconds")) }
        val runes = root.optJSONArray("runes").mapObjects {
            Rune(
                it.optString("id"), it.optString("name"), it.optInt("type", -1),
                it.optBoolean("available"), it.optBoolean("upgraded"),
                it.optBoolean("selected")
            )
        }
        val championPowers = root.optJSONArray("championPowers").mapObjects {
            ChampionPower(
                it.optString("id"), it.optString("name"), it.optBoolean("available"),
                it.optBoolean("enabled"), it.optInt("uses"), it.optInt("maxUses"),
                it.optInt("cooldownSeconds")
            )
        }
        val enteredShrines = root.optJSONArray("enteredShrines").toStringSet()
        val clearedShrines = root.optJSONArray("clearedShrines").toStringSet()
        val sensor = root.optJSONObject("sensor")?.let {
            SheikahSensor(
                it.optBoolean("unlocked"), it.optBoolean("upgraded"),
                it.optBoolean("enabled"), it.optInt("searchMode")
            )
        }
        val debug = root.optJSONObject("debug")
        return Snapshot(
            root.optString("status", "loading"), root.optBoolean("saveLoaded", false),
            root.optInt("capabilities"), stats,
            inventory, equipment, quests, effects, runes, championPowers,
            parseMap(root.optJSONObject("map")),
            enteredShrines, clearedShrines,
            debug?.optInt("equipState") ?: 0,
            debug?.optInt("equipAttempts") ?: 0,
            sensor
        )
    }

    private fun parseLiveSnapshot(json: String): LiveSnapshot {
        val root = JSONObject(json)
        return LiveSnapshot(
            root.optString("status", "loading"), parseStats(root),
            root.optIntOrNull("selectedRune"), parseMap(root.optJSONObject("map"))
        )
    }

    private fun parseMap(value: JSONObject?): PlayerMap? = value?.let {
        val x = it.optFloatOrNull("x")
        val y = it.optFloatOrNull("y")
        val z = it.optFloatOrNull("z")
        val heading = it.optFloatOrNull("heading") ?: 0f
        if (x != null && y != null && z != null) PlayerMap(x, y, z, heading) else null
    }

    private fun parseStats(root: JSONObject): Stats? {
        val statsObject = root.optJSONObject("stats")
        return statsObject?.let {
            Stats(
                it.optIntOrNull("health"), it.optIntOrNull("maxHealth"), it.optFloatOrNull("stamina"),
                it.optFloatOrNull("maxStamina"), it.optIntOrNull("rupees"),
                it.optIntOrNull("defense"), it.optIntOrNull("attack"),
                it.optIntOrNull("bowAttack"), it.optIntOrNull("shieldGuard")
            )
        }
    }

    private fun mergeLiveStats(live: Stats): Stats {
        val previous = snapshot.stats
        return Stats(
            live.health ?: previous?.health,
            live.maxHealth ?: previous?.maxHealth,
            live.stamina ?: previous?.stamina,
            live.maxStamina ?: previous?.maxStamina,
            live.rupees ?: previous?.rupees,
            previous?.defense,
            previous?.attack,
            previous?.bowAttack,
            previous?.shieldGuard
        )
    }

    private fun mergeLiveRuneSelection(selectedType: Int?): Boolean {
        if (selectedType == null || snapshot.runes.isEmpty()) return false
        val nextRunes = snapshot.runes.map { rune ->
            val selected = rune.type == selectedType ||
                (rune.id == "bombs" && selectedType == 1)
            if (rune.selected == selected) rune else rune.copy(selected = selected)
        }
        if (nextRunes == snapshot.runes) return false
        snapshot = snapshot.copy(runes = nextRunes)
        return true
    }

    private fun itemFromJson(item: JSONObject): Item {
        val actorName = item.optString("actorName")
        val displayName = when (actorName) {
            "Armor_Default_Head" -> "No Headgear"
            "Armor_Default_Upper" -> "No Tunic"
            "Armor_Default_Lower" -> "Default Trousers"
            else -> item.optString("name")
        }
        return Item(
            item.optLong("id"), actorName, displayName, item.optString("category"),
            item.optIntOrNull("power"), item.optIntOrNull("defense"),
            item.optIntOrNull("durability"), item.optIntOrNull("count"),
            item.optIntOrNull("modifierValue"), item.optIntOrNull("modifierFlags"),
            item.optBoolean("equippable"), item.optBoolean("equipped"),
            item.optString("description")
        )
    }

    private inline fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return buildList(length()) { for (index in 0 until length()) add(transform(getJSONObject(index))) }
    }

    private fun JSONArray?.toStringSet(): Set<String> {
        if (this == null) return emptySet()
        return buildSet(length()) {
            for (index in 0 until length()) {
                optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun JSONObject.optIntOrNull(name: String) = if (has(name) && !isNull(name)) getInt(name) else null
    private fun JSONObject.optFloatOrNull(name: String) = if (has(name) && !isNull(name)) getDouble(name).toFloat() else null

    private fun panel(canvas: Canvas, rect: RectF, active: Boolean = false) {
        paint.color = if (active) Color.argb(180, 3, 31, 35) else Color.argb(188, 2, 18, 21)
        canvas.drawRect(rect, paint)
        stroke.color = if (active) cyan else bronzeDark
        stroke.strokeWidth = if (active) 2f else 1f
        canvas.drawRect(rect, stroke)
        if (active) glowRect(canvas, rect)
    }

    private fun glowRect(canvas: Canvas, rect: RectF) {
        stroke.color = Color.argb(80, 36, 230, 242)
        stroke.strokeWidth = 6f
        canvas.drawRect(rect, stroke)
        stroke.strokeWidth = 1f
    }

    private fun cornerMarks(canvas: Canvas, rect: RectF, color: Int) {
        stroke.color = color
        stroke.strokeWidth = 1.5f
        val d = 10f
        val inset = 7f
        listOf(
            floatArrayOf(rect.left + inset, rect.top + inset, 1f, 1f),
            floatArrayOf(rect.right - inset, rect.top + inset, -1f, 1f),
            floatArrayOf(rect.left + inset, rect.bottom - inset, 1f, -1f),
            floatArrayOf(rect.right - inset, rect.bottom - inset, -1f, -1f)
        ).forEach {
            canvas.drawLine(it[0], it[1], it[0] + d * it[2], it[1], stroke)
            canvas.drawLine(it[0], it[1], it[0], it[1] + d * it[3], stroke)
        }
    }

    private fun text(canvas: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align = Paint.Align.LEFT) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.textSize = size
        paint.textAlign = align
        paint.typeface = normalTypeface
        canvas.drawText(value, x, y, paint)
    }

    private fun multilineText(canvas: Canvas, value: String, x: Float, y: Float, maxWidth: Float, size: Float, color: Int, lines: Int) {
        val paragraphs = value.replace("\r", "").split('\n')
        var lineIndex = 0
        paragraphs.forEach { paragraph ->
            if (lineIndex >= lines) return
            if (paragraph.isBlank()) {
                lineIndex++
                return@forEach
            }
            var line = ""
            paragraph.split(Regex("\\s+")).forEach { word ->
                val candidate = if (line.isEmpty()) word else "$line $word"
                paint.textSize = size
                if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                    text(canvas, line, x, y + lineIndex * (size + 5f), size, color)
                    line = word
                    if (++lineIndex >= lines) return
                } else {
                    line = candidate
                }
            }
            if (lineIndex < lines && line.isNotEmpty()) {
                text(canvas, line, x, y + lineIndex * (size + 5f), size, color)
                lineIndex++
            }
        }
    }

    private fun drawHeart(
        canvas: Canvas, x: Float, y: Float, filled: Boolean, halfSize: Float = 16f
    ) {
        val icon = loadBitmap("botw/ui/heart.png") ?: return
        paint.alpha = if (filled) 255 else 48
        canvas.drawBitmap(
            icon, null,
            RectF(x - halfSize, y - halfSize, x + halfSize, y + halfSize), paint
        )
        paint.alpha = 255
    }

    private fun drawHealth(canvas: Canvas, x: Float, y: Float, value: Int, maximum: Int) {
        val ratio = if (maximum > 0) (value.toFloat() / maximum).coerceIn(0f, 1f) else 0f
        val percent = (ratio * 100f).roundToInt()
        drawHeart(canvas, x, y, filled = true, halfSize = 37f)
        text(canvas, "Health", x + 50f, y - 9f, 18f, muted)
        text(canvas, "$percent%", x + 278f, y - 9f, 15f, warmWhite, Paint.Align.RIGHT)
        val bar = RectF(x + 50f, y + 3f, x + 278f, y + 19f)
        paint.color = Color.rgb(60, 20, 23)
        canvas.drawRoundRect(bar, 6f, 6f, paint)
        if (ratio > 0f) {
            paint.color = Color.rgb(239, 61, 68)
            canvas.drawRoundRect(
                RectF(bar.left, bar.top, bar.left + bar.width() * ratio, bar.bottom),
                6f, 6f, paint
            )
        }
        stroke.color = bronzeDark
        stroke.strokeWidth = 1f
        canvas.drawRoundRect(bar, 6f, 6f, stroke)
    }

    private fun drawStamina(canvas: Canvas, x: Float, y: Float, value: Float, maximum: Float) {


        stroke.strokeWidth = 5f
        stroke.color = green
        canvas.drawCircle(x, y, 23f, stroke)
        stroke.strokeWidth = 1f
        val ratio = if (maximum > 0f) (value / maximum).coerceIn(0f, 1f) else 0f
        text(canvas, "Stamina", x + 50f, y - 7f, 18f, muted)
        val percent = (ratio * 100f).roundToInt()
        text(canvas, "$percent%", x + 278f, y - 7f, 15f, warmWhite, Paint.Align.RIGHT)
        val bar = RectF(x + 50f, y + 3f, x + 278f, y + 19f)
        paint.color = Color.rgb(23, 54, 30)
        canvas.drawRoundRect(bar, 6f, 6f, paint)
        paint.color = green
        if (ratio > 0f) {
            canvas.drawRoundRect(
                RectF(bar.left, bar.top, bar.left + bar.width() * ratio, bar.bottom),
                6f, 6f, paint
            )
        }
        stroke.color = bronzeDark
        stroke.strokeWidth = 1f
        canvas.drawRoundRect(bar, 6f, 6f, stroke)
    }

    private fun statLine(canvas: Canvas, x: Float, y: Float, label: String, value: Int, iconPath: String) {
        loadBitmap(iconPath)?.let {
            canvas.drawBitmap(it, null, RectF(x, y - 39f, x + 42f, y + 3f), paint)
        }
        text(canvas, label, x + 49f, y - 15f, 16f, muted)
        text(canvas, "$value", x + 49f, y + 13f, 25f, warmWhite)
    }

    private fun badge(
        canvas: Canvas, rect: RectF, value: String, color: Int = warmWhite, textSize: Float = 15f
    ) {
        paint.color = Color.argb(120, 3, 25, 28)
        canvas.drawRect(rect, paint)
        stroke.color = if (color == warmWhite) bronzeDark else color
        canvas.drawRect(rect, stroke)
        text(canvas, value, rect.centerX(), rect.centerY() + 7f, textSize, color, Paint.Align.CENTER)
    }

    private fun drawLabeledBadge(
        canvas: Canvas, rect: RectF, label: String, value: String
    ) {
        paint.color = Color.argb(120, 3, 25, 28)
        canvas.drawRect(rect, paint)
        stroke.color = bronzeDark
        canvas.drawRect(rect, stroke)
        text(canvas, label, rect.left + 8f, rect.top + 16f, 11f, muted)
        text(canvas, value, rect.left + 8f, rect.bottom - 7f, 17f, warmWhite)
    }

    private fun modifierLabel(item: Item): String {
        val flags = item.modifierFlags ?: return ""
        val value = item.modifierValue ?: return ""
        return when {
            flags and 0x1 != 0 -> "Attack +$value"
            flags and 0x100 != 0 -> "Guard +$value"
            value > 0 -> "+$value"
            else -> ""
        }
    }

    private fun drawCategoryIcon(canvas: Canvas, index: Int, x: Float, y: Float, active: Boolean) {
        val icon = categoryIconAssets.getOrNull(index)?.let(::loadBitmap) ?: return
        val halfSize = 24f
        paint.alpha = 255
        paint.colorFilter = categoryIconTintFilters[if (active) 1 else 0]
        canvas.drawBitmap(
            icon, null, RectF(x - halfSize, y - halfSize, x + halfSize, y + halfSize), paint
        )
        paint.colorFilter = null
    }

    private fun categoryMaskTint(color: Int) = ColorMatrixColorFilter(
        floatArrayOf(
            0f, 0f, 0f, 0f, Color.red(color).toFloat(),
            0f, 0f, 0f, 0f, Color.green(color).toFloat(),
            0f, 0f, 0f, 0f, Color.blue(color).toFloat(),
            1f, 0f, 0f, 0f, 0f
        )
    )

    private fun drawPageIcon(canvas: Canvas, value: Page, x: Float, y: Float) {
        stroke.color = if (page == value) cyan else muted
        stroke.strokeWidth = 2f
        when (value) {
            Page.Map -> { path.reset(); path.moveTo(x - 16f, y - 15f); path.lineTo(x - 5f, y - 11f); path.lineTo(x + 6f, y - 15f); path.lineTo(x + 16f, y - 11f); path.lineTo(x + 16f, y + 15f); path.lineTo(x + 5f, y + 11f); path.lineTo(x - 6f, y + 15f); path.lineTo(x - 16f, y + 11f); path.close(); canvas.drawPath(path, stroke) }
            Page.Inventory -> { canvas.drawRoundRect(RectF(x - 16f, y - 11f, x + 16f, y + 14f), 3f, 3f, stroke); canvas.drawLine(x - 8f, y - 14f, x + 8f, y - 14f, stroke) }
            Page.Quests -> { canvas.drawRect(RectF(x - 13f, y - 16f, x + 13f, y + 15f), stroke); canvas.drawLine(x - 7f, y - 7f, x + 7f, y - 7f, stroke); canvas.drawLine(x - 7f, y, x + 5f, y, stroke) }
        }
        stroke.strokeWidth = 1f
    }

    private fun drawItemGlyph(canvas: Canvas, item: Item, x: Float, y: Float, scale: Float = 1f) {
        if (item.actorName.contains('/') || item.actorName.contains("..")) {
            drawItemCategoryFallback(canvas, item, x, y, scale)
            return
        }
        if (item.actorName.startsWith("Armor_Default_")) {
            drawDefaultEquipmentGlyph(canvas, item.actorName, x, y, scale)
            return
        }
        val icon = loadBitmap("botw/stock/${item.actorName}.png")
            ?: stockIconAliases[item.actorName]?.let { alias ->
                loadBitmap("botw/stock/$alias.png")
            }
        if (icon == null) {



            drawItemCategoryFallback(canvas, item, x, y, scale)
            return
        }
        val half = 46f * scale
        paint.alpha = 255
        canvas.drawBitmap(icon, null, RectF(x - half, y - half, x + half, y + half), paint)
    }

    private fun drawItemCategoryFallback(
        canvas: Canvas, item: Item, x: Float, y: Float, scale: Float
    ) {
        val index = categories.indexOfFirst { it.equals(item.category, ignoreCase = true) }
        val icon = categoryIconAssets.getOrNull(index)?.let(::loadBitmap)
        val radius = 34f * scale
        paint.color = Color.argb(80, 3, 37, 42)
        canvas.drawCircle(x, y, radius + 7f * scale, paint)
        stroke.color = Color.argb(150, 33, 219, 234)
        stroke.strokeWidth = 1.5f * scale
        canvas.drawCircle(x, y, radius + 7f * scale, stroke)
        if (icon != null) {
            paint.alpha = 215
            paint.colorFilter = categoryIconTintFilters[1]
            canvas.drawBitmap(
                icon, null, RectF(x - radius, y - radius, x + radius, y + radius), paint
            )
            paint.colorFilter = null
            paint.alpha = 255
        }
        stroke.strokeWidth = 1f
    }

    private fun drawDefaultEquipmentGlyph(
        canvas: Canvas, actorName: String, x: Float, y: Float, scale: Float
    ) {
        stroke.color = muted
        stroke.strokeWidth = 2.5f * scale
        when {
            actorName.endsWith("_Head") -> {
                canvas.drawCircle(x, y - 7f * scale, 17f * scale, stroke)
                canvas.drawArc(
                    RectF(x - 24f * scale, y + 9f * scale, x + 24f * scale, y + 39f * scale),
                    200f, 140f, false, stroke
                )
            }
            actorName.endsWith("_Upper") -> {
                path.reset()
                path.moveTo(x - 21f * scale, y - 25f * scale)
                path.lineTo(x - 34f * scale, y - 8f * scale)
                path.lineTo(x - 22f * scale, y + 31f * scale)
                path.lineTo(x + 22f * scale, y + 31f * scale)
                path.lineTo(x + 34f * scale, y - 8f * scale)
                path.lineTo(x + 21f * scale, y - 25f * scale)
                path.close()
                canvas.drawPath(path, stroke)
            }
            else -> {
                canvas.drawLine(x - 19f * scale, y - 29f * scale, x - 11f * scale, y + 30f * scale, stroke)
                canvas.drawLine(x + 19f * scale, y - 29f * scale, x + 11f * scale, y + 30f * scale, stroke)
                canvas.drawLine(x - 20f * scale, y - 29f * scale, x + 20f * scale, y - 29f * scale, stroke)
                canvas.drawLine(x - 22f * scale, y + 31f * scale, x - 8f * scale, y + 31f * scale, stroke)
                canvas.drawLine(x + 8f * scale, y + 31f * scale, x + 22f * scale, y + 31f * scale, stroke)
            }
        }
        stroke.strokeWidth = 1f
    }

    private fun drawRupee(canvas: Canvas, x: Float, y: Float) {
        val icon = loadBitmap("botw/ui/rupee.png") ?: return
        canvas.drawBitmap(icon, null, RectF(x - 21f, y - 21f, x + 21f, y + 21f), paint)
    }

    private fun drawChampionPowers(canvas: Canvas) {
        if (snapshot.championPowers.isEmpty()) return
        val powers = snapshot.championPowers.take(4)
        val left = 18f
        val right = 669f
        val gap = 6f
        val width = (right - left - gap * 3f) / 4f
        powers.forEachIndexed { index, power ->
            val cellLeft = left + index * (width + gap)
            val cell = RectF(cellLeft, POWER_ROW_TOP, cellLeft + width, POWER_ROW_BOTTOM)
            val active = power.available && power.enabled
            val normalizedId = power.id.lowercase()
            val accent = when {
                "gale" in normalizedId -> Color.rgb(76, 221, 229)
                "fury" in normalizedId -> Color.rgb(242, 184, 56)
                "protection" in normalizedId -> Color.rgb(84, 153, 239)
                "grace" in normalizedId -> Color.rgb(240, 104, 151)
                else -> cyan
            }
            val icon = loadBitmap("botw/ui/champion/$normalizedId.png")
            paint.alpha = if (power.available) 255 else 72
            if (icon != null) {
                canvas.drawBitmap(
                    icon, null,
                    RectF(
                        cell.centerX() - 32f, cell.top + 55f,
                        cell.centerX() + 32f, cell.top + 119f
                    ),
                    paint
                )
            } else {
                drawChampionPowerGlyph(
                    canvas, normalizedId, cell.centerX(), cell.top + 87f, accent,
                    power.available
                )
            }
            paint.alpha = 255
            val state = when {
                !power.available -> "Locked"
                !power.enabled -> "Disabled"
                power.cooldownSeconds > 0 -> formatTime(power.cooldownSeconds)
                power.maxUses > 0 -> "${power.uses}/${power.maxUses} ready"
                else -> "Ready"
            }
            text(
                canvas, state, cell.centerX(), cell.bottom - 8f, 14f,
                if (active) accent else muted, Paint.Align.CENTER
            )
        }
    }

    private fun drawChampionPowerGlyph(
        canvas: Canvas, id: String, x: Float, y: Float, accent: Int, available: Boolean
    ) {
        val color = if (available) accent else muted
        stroke.color = color
        stroke.strokeWidth = 2.2f
        when {
            "gale" in id -> {
                canvas.drawArc(RectF(x - 11f, y - 8f, x + 11f, y + 13f), 205f, 255f, false, stroke)
                canvas.drawLine(x, y + 8f, x, y - 12f, stroke)
                canvas.drawLine(x, y - 12f, x - 5f, y - 6f, stroke)
                canvas.drawLine(x, y - 12f, x + 5f, y - 6f, stroke)
            }
            "fury" in id -> {
                val bolt = Path().apply {
                    moveTo(x + 2f, y - 14f)
                    lineTo(x - 9f, y + 1f)
                    lineTo(x - 1f, y + 1f)
                    lineTo(x - 4f, y + 14f)
                    lineTo(x + 10f, y - 4f)
                    lineTo(x + 2f, y - 4f)
                    close()
                }
                paint.color = color
                canvas.drawPath(bolt, paint)
            }
            "protection" in id -> {
                val shield = Path().apply {
                    moveTo(x, y - 14f)
                    lineTo(x + 11f, y - 9f)
                    lineTo(x + 9f, y + 5f)
                    quadTo(x + 6f, y + 11f, x, y + 15f)
                    quadTo(x - 6f, y + 11f, x - 9f, y + 5f)
                    lineTo(x - 11f, y - 9f)
                    close()
                }
                canvas.drawPath(shield, stroke)
            }
            else -> {
                val heart = Path().apply {
                    moveTo(x, y + 13f)
                    cubicTo(x - 4f, y + 8f, x - 13f, y + 1f, x - 13f, y - 6f)
                    cubicTo(x - 13f, y - 14f, x - 3f, y - 16f, x, y - 9f)
                    cubicTo(x + 3f, y - 16f, x + 13f, y - 14f, x + 13f, y - 6f)
                    cubicTo(x + 13f, y + 1f, x + 4f, y + 8f, x, y + 13f)
                    close()
                }
                paint.color = color
                canvas.drawPath(heart, paint)
            }
        }
        stroke.strokeWidth = 1f
    }

    private fun drawRunes(canvas: Canvas, panel: RectF) {
        val visible = snapshot.runes.filter { it.available }
        if (visible.isEmpty()) return
        val strip = runeStripRect(panel)
        stroke.color = bronzeDark
        canvas.drawRect(strip, stroke)
        val width = strip.width() / visible.size
        visible.forEachIndexed { index, rune ->
            val centerX = strip.left + width * (index + .5f)
            val cell = RectF(
                strip.left + width * index, strip.top,
                strip.left + width * (index + 1), strip.bottom
            )
            if (rune.selected) {
                paint.color = Color.argb(58, 30, 221, 235)
                canvas.drawRect(cell, paint)
                stroke.color = cyan
                stroke.strokeWidth = 2.5f
                canvas.drawRect(RectF(cell.left + 2f, cell.top + 2f, cell.right - 2f, cell.bottom - 2f), stroke)
                stroke.strokeWidth = 1f
            } else if (index > 0) {
                stroke.color = bronzeDark
                canvas.drawLine(cell.left, cell.top + 7f, cell.left, cell.bottom - 7f, stroke)
            }
            val icon = loadBitmap("botw/ui/rune_${rune.id}.png")
            paint.alpha = 255
            icon?.let {
                canvas.drawBitmap(it, null, RectF(centerX - 31f, strip.top + 5f, centerX + 31f, strip.top + 67f), paint)
            }
            text(
                canvas, if (rune.upgraded) "${rune.name}+" else rune.name,
                centerX, strip.bottom - 9f, 12f,
                if (rune.selected || rune.upgraded) cyan else warmWhite,
                Paint.Align.CENTER
            )
        }
        paint.alpha = 255
    }

    private fun loadBitmap(path: String): Bitmap? {
        bitmapCache.get(path)?.let { return it }
        if (path in missingAssets) return null
        return try {
            context.assets.open(path).use { stream ->
                val options = BitmapFactory.Options().apply {
                    if (path == "botw/ui/hyrule_map.png") {
                        inSampleSize = 2
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                }
                BitmapFactory.decodeStream(stream, null, options)?.also { bitmapCache.put(path, it) }
            } ?: run {
                missingAssets += path
                null
            }
        } catch (_: Exception) {
            missingAssets += path
            null
        }
    }

    private fun drawEffectIcon(canvas: Canvas, x: Float, y: Float, index: Int) {
        paint.color = when (index) { 0 -> Color.rgb(239, 157, 26); 1 -> Color.rgb(100, 193, 225); else -> Color.rgb(238, 212, 51) }
        canvas.drawCircle(x, y, 7f, paint)
        stroke.color = paint.color
        for (i in 0 until 8) {
            val angle = Math.toRadians((i * 45).toDouble())
            canvas.drawLine(x + kotlin.math.cos(angle).toFloat() * 11f, y + kotlin.math.sin(angle).toFloat() * 11f, x + kotlin.math.cos(angle).toFloat() * 15f, y + kotlin.math.sin(angle).toFloat() * 15f, stroke)
        }
    }

    private fun drawSheikahMedallion(canvas: Canvas, x: Float, y: Float, radius: Float) {
        stroke.color = Color.argb(75, 31, 207, 220)
        stroke.strokeWidth = 2f
        canvas.drawCircle(x, y, radius, stroke)
        canvas.drawCircle(x, y, radius * .72f, stroke)
        canvas.drawCircle(x, y, radius * .2f, stroke)
        for (index in 0 until 12) {
            val angle = Math.toRadians((index * 30).toDouble())
            canvas.drawLine(x + kotlin.math.cos(angle).toFloat() * radius * .72f, y + kotlin.math.sin(angle).toFloat() * radius * .72f, x + kotlin.math.cos(angle).toFloat() * radius, y + kotlin.math.sin(angle).toFloat() * radius, stroke)
        }
        stroke.strokeWidth = 1f
    }

    private fun shortCategory(value: String) = if (value == "Key Items") "Key" else value
    private fun formatTime(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)
    private val cyan = Color.rgb(33, 219, 234)
    private val warmWhite = Color.rgb(225, 211, 187)
    private val muted = Color.rgb(177, 164, 142)
    private val bronze = Color.rgb(190, 145, 81)
    private val bronzeDark = Color.rgb(80, 75, 60)
    private val green = Color.rgb(104, 224, 62)
}
