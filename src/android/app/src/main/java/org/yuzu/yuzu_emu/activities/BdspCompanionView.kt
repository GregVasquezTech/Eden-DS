package org.yuzu.yuzu_emu.activities

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pokémon Brilliant Diamond / Shining Pearl
 *
 * Second-screen companion UI.
 *
 * Current implementation:
 * - Uses mock BDSP data.
 * - Supports overworld Map / Party swipe navigation.
 * - Supports Battle -> Fight / Bag / Pokémon / Run.
 * - Supports Fight -> move selection.
 * - Supports Bag -> item selection.
 * - Supports Pokémon -> party selection.
 *
 * The mock state will later be replaced with the native BDSP memory bridge.
 */
internal class BdspCompanionView(context: Context) : View(context) {

    // -------------------------------------------------------------------------
    // DESIGN
    // -------------------------------------------------------------------------

    private companion object {
        private const val DESIGN_WIDTH = 1240f
        private const val DESIGN_HEIGHT = 1080f

        private const val SWIPE_THRESHOLD = 120f

        private const val MODE_OVERWORLD = 0
        private const val MODE_BATTLE = 1

        private const val SCREEN_MAP = 0
        private const val SCREEN_PARTY = 1

        private const val BATTLE_MENU = 0
        private const val BATTLE_MOVES = 1
        private const val BATTLE_BAG = 2
        private const val BATTLE_POKEMON = 3

        private const val ACTION_SELECT_MOVE = 1
        private const val ACTION_USE_ITEM = 2
        private const val ACTION_SWITCH_POKEMON = 3
        private const val ACTION_RUN = 4
    }

    // -------------------------------------------------------------------------
    // STATE
    // -------------------------------------------------------------------------

    private var gameMode = MODE_OVERWORLD

    private var overworldScreen = SCREEN_MAP

    private var battleScreen = BATTLE_MENU

    private var touchDownX = 0f
    private var touchDownY = 0f

    private var selectedMove = -1
    private var selectedItem = -1
    private var selectedPokemon = -1

    private var statusMessage = ""

    // -------------------------------------------------------------------------
    // PAINT
    // -------------------------------------------------------------------------

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    // -------------------------------------------------------------------------
    // MOCK DATA
    // -------------------------------------------------------------------------

    private data class Pokemon(
        val name: String,
        val level: Int,
        val currentHp: Int,
        val maxHp: Int,
        val fainted: Boolean = false
    )

    private data class Move(
        val name: String,
        val type: String,
        val category: String,
        val power: Int,
        val accuracy: Int,
        val pp: Int,
        val maxPp: Int
    )

    private data class Item(
        val name: String,
        val description: String,
        val quantity: Int
    )

    private val party = listOf(
        Pokemon(
            name = "Torterra",
            level = 42,
            currentHp = 132,
            maxHp = 132
        ),
        Pokemon(
            name = "Staraptor",
            level = 40,
            currentHp = 108,
            maxHp = 108
        ),
        Pokemon(
            name = "Luxray",
            level = 39,
            currentHp = 91,
            maxHp = 101
        ),
        Pokemon(
            name = "Bibarel",
            level = 15,
            currentHp = 45,
            maxHp = 45
        ),
        Pokemon(
            name = "Garchomp",
            level = 44,
            currentHp = 0,
            maxHp = 137,
            fainted = true
        ),
        Pokemon(
            name = "Lucario",
            level = 41,
            currentHp = 110,
            maxHp = 110
        )
    )

    private val moves = listOf(
        Move(
            name = "Earthquake",
            type = "Ground",
            category = "Physical",
            power = 100,
            accuracy = 100,
            pp = 10,
            maxPp = 10
        ),
        Move(
            name = "Leaf Storm",
            type = "Grass",
            category = "Special",
            power = 130,
            accuracy = 90,
            pp = 5,
            maxPp = 5
        ),
        Move(
            name = "Crunch",
            type = "Dark",
            category = "Physical",
            power = 80,
            accuracy = 100,
            pp = 12,
            maxPp = 15
        ),
        Move(
            name = "Synthesis",
            type = "Grass",
            category = "Status",
            power = 0,
            accuracy = 100,
            pp = 5,
            maxPp = 5
        )
    )

    private val items = listOf(
        Item(
            name = "Potion",
            description = "Restores 20 HP.",
            quantity = 12
        ),
        Item(
            name = "Super Potion",
            description = "Restores 60 HP.",
            quantity = 5
        ),
        Item(
            name = "Hyper Potion",
            description = "Restores 120 HP.",
            quantity = 3
        ),
        Item(
            name = "Poké Ball",
            description = "A device used to catch wild Pokémon.",
            quantity = 17
        ),
        Item(
            name = "Revive",
            description = "Revives a fainted Pokémon.",
            quantity = 2
        )
    )

    // -------------------------------------------------------------------------
    // INITIALIZATION
    // -------------------------------------------------------------------------

    init {
        isFocusable = true
        isClickable = true

        backgroundPaint.color = Color.rgb(12, 15, 22)
        panelPaint.color = Color.rgb(20, 24, 34)
        cardPaint.color = Color.rgb(30, 36, 50)

        textPaint.color = Color.WHITE
        titlePaint.color = Color.WHITE
        smallPaint.color = Color.LTGRAY
        borderPaint.color = Color.rgb(75, 85, 105)
    }

    // -------------------------------------------------------------------------
    // DRAW
    // -------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width <= 0 || height <= 0) {
            return
        }

        val scale = min(
            width / DESIGN_WIDTH,
            height / DESIGN_HEIGHT
        )

        val offsetX = (width - DESIGN_WIDTH * scale) / 2f
        val offsetY = (height - DESIGN_HEIGHT * scale) / 2f

        canvas.save()

        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)

        canvas.drawColor(Color.rgb(12, 15, 22))

        if (gameMode == MODE_OVERWORLD) {
            drawOverworld(canvas)
        } else {
            drawBattle(canvas)
        }

        canvas.restore()
    }

    // -------------------------------------------------------------------------
    // OVERWORLD
    // -------------------------------------------------------------------------

    private fun drawOverworld(canvas: Canvas) {

        drawHeader(
            canvas,
            if (overworldScreen == SCREEN_MAP) {
                "POKÉMON BDSP"
            } else {
                "PARTY"
            },
            if (overworldScreen == SCREEN_MAP) {
                "OVERWORLD"
            } else {
                "YOUR POKÉMON"
            }
        )

        if (overworldScreen == SCREEN_MAP) {
            drawMap(canvas)
        } else {
            drawParty(canvas, allowSwitch = false)
        }

        drawSwipeHint(canvas)
    }

    // -------------------------------------------------------------------------
    // MAP
    // -------------------------------------------------------------------------

    private fun drawMap(canvas: Canvas) {

        val mapRect = RectF(
            70f,
            180f,
            DESIGN_WIDTH - 70f,
            900f
        )

        panelPaint.color = Color.rgb(38, 52, 42)

        canvas.drawRoundRect(
            mapRect,
            30f,
            30f,
            panelPaint
        )

        borderPaint.color = Color.rgb(90, 120, 95)

        canvas.drawRoundRect(
            mapRect,
            30f,
            30f,
            borderPaint
        )

        // Simple placeholder map.
        // This will eventually be replaced with actual BDSP map data/image.

        val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(145, 132, 105)
            strokeWidth = 90f
            style = Paint.Style.STROKE
        }

        canvas.drawLine(
            160f,
            720f,
            1080f,
            720f,
            roadPaint
        )

        canvas.drawLine(
            620f,
            250f,
            620f,
            720f,
            roadPaint
        )

        val grassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(55, 105, 63)
            style = Paint.Style.FILL
        }

        canvas.drawRoundRect(
            RectF(130f, 250f, 450f, 570f),
            25f,
            25f,
            grassPaint
        )

        canvas.drawRoundRect(
            RectF(790f, 300f, 1100f, 580f),
            25f,
            25f,
            grassPaint
        )

        // Player marker.

        val playerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(70, 150, 255)
            style = Paint.Style.FILL
        }

        canvas.drawCircle(
            620f,
            720f,
            25f,
            playerPaint
        )

        val playerLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
        }

        canvas.drawText(
            "PLAYER",
            620f,
            680f,
            playerLabelPaint
        )

        drawText(
            canvas,
            "MAP",
            620f,
            225f,
            36f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )
    }

    // -------------------------------------------------------------------------
    // PARTY
    // -------------------------------------------------------------------------

    private fun drawParty(
        canvas: Canvas,
        allowSwitch: Boolean
    ) {

        val startY = 175f

        party.forEachIndexed { index, pokemon ->

            val top = startY + index * 135f

            if (top > DESIGN_HEIGHT - 110f) {
                return@forEachIndexed
            }

            drawPokemonCard(
                canvas = canvas,
                pokemon = pokemon,
                index = index,
                top = top,
                allowSwitch = allowSwitch
            )
        }
    }

    private fun drawPokemonCard(
        canvas: Canvas,
        pokemon: Pokemon,
        index: Int,
        top: Float,
        allowSwitch: Boolean
    ) {

        val left = 70f
        val right = DESIGN_WIDTH - 70f
        val bottom = top + 112f

        cardPaint.color = if (pokemon.fainted) {
            Color.rgb(45, 42, 45)
        } else {
            Color.rgb(30, 36, 50)
        }

        canvas.drawRoundRect(
            RectF(left, top, right, bottom),
            18f,
            18f,
            cardPaint
        )

        borderPaint.color = if (pokemon.fainted) {
            Color.rgb(100, 80, 85)
        } else {
            Color.rgb(75, 85, 105)
        }

        canvas.drawRoundRect(
            RectF(left, top, right, bottom),
            18f,
            18f,
            borderPaint
        )

        val nameColor =
            if (pokemon.fainted) Color.GRAY else Color.WHITE

        drawText(
            canvas,
            "${index + 1}. ${pokemon.name}",
            100f,
            top + 38f,
            30f,
            nameColor,
            true
        )

        drawText(
            canvas,
            "Lv.${pokemon.level}",
            390f,
            top + 38f,
            26f,
            Color.LTGRAY,
            false
        )

        val hpPercent =
            if (pokemon.maxHp > 0) {
                pokemon.currentHp.toFloat() / pokemon.maxHp
            } else {
                0f
            }

        val hpBar = RectF(
            500f,
            top + 22f,
            950f,
            top + 48f
        )

        val hpBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(65, 65, 75)
        }

        canvas.drawRoundRect(
            hpBar,
            12f,
            12f,
            hpBackgroundPaint
        )

        val hpColor = when {
            pokemon.fainted -> Color.GRAY
            hpPercent > .5f -> Color.rgb(70, 190, 100)
            hpPercent > .2f -> Color.rgb(230, 185, 65)
            else -> Color.rgb(220, 70, 70)
        }

        val hpForegroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = hpColor
        }

        canvas.drawRoundRect(
            RectF(
                hpBar.left,
                hpBar.top,
                hpBar.left + hpBar.width() * hpPercent.coerceIn(0f, 1f),
                hpBar.bottom
            ),
            12f,
            12f,
            hpForegroundPaint
        )

        drawText(
            canvas,
            "${pokemon.currentHp}/${pokemon.maxHp}",
            985f,
            top + 42f,
            24f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        if (pokemon.fainted) {
            drawText(
                canvas,
                "FAINTED",
                1010f,
                top + 85f,
                22f,
                Color.rgb(220, 100, 100),
                true,
                Paint.Align.CENTER
            )
        } else if (allowSwitch) {
            drawText(
                canvas,
                "TAP TO SWITCH",
                1010f,
                top + 85f,
                18f,
                Color.LTGRAY,
                false,
                Paint.Align.CENTER
            )
        }
    }

    // -------------------------------------------------------------------------
    // BATTLE
    // -------------------------------------------------------------------------

    private fun drawBattle(canvas: Canvas) {

        val subtitle = when (battleScreen) {
            BATTLE_MENU -> "BATTLE"
            BATTLE_MOVES -> "CHOOSE A MOVE"
            BATTLE_BAG -> "BAG"
            BATTLE_POKEMON -> "SWITCH POKÉMON"
            else -> "BATTLE"
        }

        drawHeader(
            canvas,
            "POKÉMON BDSP",
            subtitle
        )

        when (battleScreen) {

            BATTLE_MENU -> {
                drawBattleMenu(canvas)
            }

            BATTLE_MOVES -> {
                drawMoves(canvas)
            }

            BATTLE_BAG -> {
                drawBag(canvas)
            }

            BATTLE_POKEMON -> {
                drawParty(
                    canvas = canvas,
                    allowSwitch = true
                )
            }
        }

        if (statusMessage.isNotEmpty()) {
            drawStatusMessage(canvas)
        }
    }

    // -------------------------------------------------------------------------
    // BATTLE MENU
    // -------------------------------------------------------------------------

    private fun drawBattleMenu(canvas: Canvas) {

        val buttonWidth = 500f
        val buttonHeight = 230f

        val left = 90f
        val right = 650f

        drawButton(
            canvas,
            RectF(left, 220f, left + buttonWidth, 220f + buttonHeight),
            "FIGHT",
            "Choose a move",
            Color.rgb(150, 70, 70)
        )

        drawButton(
            canvas,
            RectF(right, 220f, right + buttonWidth, 220f + buttonHeight),
            "BAG",
            "Use an item",
            Color.rgb(70, 100, 150)
        )

        drawButton(
            canvas,
            RectF(left, 500f, left + buttonWidth, 500f + buttonHeight),
            "POKÉMON",
            "Switch Pokémon",
            Color.rgb(80, 125, 95)
        )

        drawButton(
            canvas,
            RectF(right, 500f, right + buttonWidth, 500f + buttonHeight),
            "RUN",
            "Try to escape",
            Color.rgb(115, 95, 135)
        )
    }

    // -------------------------------------------------------------------------
    // MOVES
    // -------------------------------------------------------------------------

    private fun drawMoves(canvas: Canvas) {

        moves.forEachIndexed { index, move ->

            val top = 175f + index * 205f

            drawMoveCard(
                canvas,
                move,
                index,
                top
            )
        }

        drawBackButton(canvas)
    }

    private fun drawMoveCard(
        canvas: Canvas,
        move: Move,
        index: Int,
        top: Float
    ) {

        val rect = RectF(
            70f,
            top,
            DESIGN_WIDTH - 70f,
            top + 175f
        )

        cardPaint.color = Color.rgb(30, 36, 50)

        canvas.drawRoundRect(
            rect,
            20f,
            20f,
            cardPaint
        )

        borderPaint.color =
            if (selectedMove == index) {
                Color.rgb(100, 170, 255)
            } else {
                Color.rgb(75, 85, 105)
            }

        canvas.drawRoundRect(
            rect,
            20f,
            20f,
            borderPaint
        )

        drawText(
            canvas,
            move.name,
            105f,
            top + 43f,
            31f,
            Color.WHITE,
            true
        )

        drawText(
            canvas,
            "${move.type} • ${move.category}",
            105f,
            top + 80f,
            23f,
            Color.LTGRAY,
            false
        )

        drawText(
            canvas,
            "Power",
            520f,
            top + 42f,
            20f,
            Color.GRAY,
            false
        )

        drawText(
            canvas,
            if (move.power > 0) move.power.toString() else "—",
            520f,
            top + 78f,
            27f,
            Color.WHITE,
            true
        )

        drawText(
            canvas,
            "Accuracy",
            680f,
            top + 42f,
            20f,
            Color.GRAY,
            false
        )

        drawText(
            canvas,
            "${move.accuracy}%",
            680f,
            top + 78f,
            27f,
            Color.WHITE,
            true
        )

        drawText(
            canvas,
            "PP",
            870f,
            top + 42f,
            20f,
            Color.GRAY,
            false
        )

        drawText(
            canvas,
            "${move.pp}/${move.maxPp}",
            870f,
            top + 78f,
            27f,
            Color.WHITE,
            true
        )

        val ppPercent =
            if (move.maxPp > 0) {
                move.pp.toFloat() / move.maxPp
            } else {
                0f
            }

        val ppBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(65, 65, 75)
        }

        canvas.drawRoundRect(
            RectF(
                870f,
                top + 105f,
                1110f,
                top + 127f
            ),
            10f,
            10f,
            ppBackground
        )

        val ppForeground = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(75, 165, 105)
        }

        canvas.drawRoundRect(
            RectF(
                870f,
                top + 105f,
                870f + 240f * ppPercent,
                top + 127f
            ),
            10f,
            10f,
            ppForeground
        )
    }

    // -------------------------------------------------------------------------
    // BAG
    // -------------------------------------------------------------------------

    private fun drawBag(canvas: Canvas) {

        items.forEachIndexed { index, item ->

            val top = 175f + index * 155f

            drawItemCard(
                canvas,
                item,
                index,
                top
            )
        }

        drawBackButton(canvas)
    }

    private fun drawItemCard(
        canvas: Canvas,
        item: Item,
        index: Int,
        top: Float
    ) {

        val rect = RectF(
            70f,
            top,
            DESIGN_WIDTH - 70f,
            top + 125f
        )

        cardPaint.color = Color.rgb(30, 36, 50)

        canvas.drawRoundRect(
            rect,
            18f,
            18f,
            cardPaint
        )

        borderPaint.color =
            if (selectedItem == index) {
                Color.rgb(100, 170, 255)
            } else {
                Color.rgb(75, 85, 105)
            }

        canvas.drawRoundRect(
            rect,
            18f,
            18f,
            borderPaint
        )

        drawText(
            canvas,
            item.name,
            105f,
            top + 42f,
            29f,
            Color.WHITE,
            true
        )

        drawText(
            canvas,
            item.description,
            105f,
            top + 78f,
            21f,
            Color.LTGRAY,
            false
        )

        drawText(
            canvas,
            "×${item.quantity}",
            1080f,
            top + 58f,
            28f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )
    }

    // -------------------------------------------------------------------------
    // BUTTONS
    // -------------------------------------------------------------------------

    private fun drawButton(
        canvas: Canvas,
        rect: RectF,
        title: String,
        subtitle: String,
        color: Int
    ) {

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
        }

        canvas.drawRoundRect(
            rect,
            30f,
            30f,
            paint
        )

        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            this.alpha = 50
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        canvas.drawRoundRect(
            rect,
            30f,
            30f,
            border
        )

        drawText(
            canvas,
            title,
            rect.centerX(),
            rect.centerY() - 10f,
            42f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        drawText(
            canvas,
            subtitle,
            rect.centerX(),
            rect.centerY() + 35f,
            20f,
            Color.LTGRAY,
            false,
            Paint.Align.CENTER
        )
    }

    private fun drawBackButton(canvas: Canvas) {

        val rect = RectF(
            70f,
            DESIGN_HEIGHT - 85f,
            270f,
            DESIGN_HEIGHT - 25f
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(50, 55, 68)
        }

        canvas.drawRoundRect(
            rect,
            18f,
            18f,
            paint
        )

        drawText(
            canvas,
            "← BACK",
            rect.centerX(),
            rect.centerY() + 9f,
            22f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )
    }

    private fun drawSwipeHint(canvas: Canvas) {

        drawText(
            canvas,
            if (overworldScreen == SCREEN_MAP) {
                "Swipe left → Party"
            } else {
                "Swipe right → Map"
            },
            DESIGN_WIDTH / 2f,
            DESIGN_HEIGHT - 45f,
            22f,
            Color.LTGRAY,
            false,
            Paint.Align.CENTER
        )
    }

    private fun drawStatusMessage(canvas: Canvas) {

        val rect = RectF(
            250f,
            DESIGN_HEIGHT - 150f,
            DESIGN_WIDTH - 250f,
            DESIGN_HEIGHT - 90f
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(45, 50, 65)
        }

        canvas.drawRoundRect(
            rect,
            15f,
            15f,
            paint
        )

        drawText(
            canvas,
            statusMessage,
            rect.centerX(),
            rect.centerY() + 7f,
            20f,
            Color.WHITE,
            false,
            Paint.Align.CENTER
        )
    }

    // -------------------------------------------------------------------------
    // HEADER
    // -------------------------------------------------------------------------

    private fun drawHeader(
        canvas: Canvas,
        title: String,
        subtitle: String
    ) {

        drawText(
            canvas,
            title,
            70f,
            65f,
            38f,
            Color.WHITE,
            true
        )

        drawText(
            canvas,
            subtitle,
            70f,
            105f,
            22f,
            Color.LTGRAY,
            false
        )

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(65, 72, 88)
            strokeWidth = 2f
        }

        canvas.drawLine(
            70f,
            135f,
            DESIGN_WIDTH - 70f,
            135f,
            linePaint
        )
    }

    // -------------------------------------------------------------------------
    // TEXT HELPER
    // -------------------------------------------------------------------------

    private fun drawText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        bold: Boolean,
        align: Paint.Align = Paint.Align.LEFT
    ) {

        textPaint.textSize = size
        textPaint.color = color
        textPaint.textAlign = align
        textPaint.typeface =
            if (bold) {
                Typeface.create("sans-serif", Typeface.BOLD)
            } else {
                Typeface.create("sans-serif", Typeface.NORMAL)
            }

        canvas.drawText(
            text,
            x,
            y,
            textPaint
        )
    }

    // -------------------------------------------------------------------------
    // TOUCH
    // -------------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {

        val scale = min(
            width / DESIGN_WIDTH,
            height / DESIGN_HEIGHT
        )

        val offsetX = (width - DESIGN_WIDTH * scale) / 2f
        val offsetY = (height - DESIGN_HEIGHT * scale) / 2f

        val x = (event.x - offsetX) / scale
        val y = (event.y - offsetY) / scale

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {

                touchDownX = x
                touchDownY = y

                return true
            }

            MotionEvent.ACTION_UP -> {

                val deltaX = x - touchDownX
                val deltaY = y - touchDownY

                // -------------------------------------------------------------
                // SWIPE
                // -------------------------------------------------------------

                if (
                    abs(deltaX) > SWIPE_THRESHOLD &&
                    abs(deltaX) > abs(deltaY)
                ) {

                    if (gameMode == MODE_OVERWORLD) {

                        if (deltaX < 0) {
                            overworldScreen = SCREEN_PARTY
                        } else {
                            overworldScreen = SCREEN_MAP
                        }

                        invalidate()
                    }

                    return true
                }

                // -------------------------------------------------------------
                // TAP
                // -------------------------------------------------------------

                handleTap(x, y)

                return true
            }
        }

        return true
    }

    // -------------------------------------------------------------------------
    // TAP HANDLING
    // -------------------------------------------------------------------------

    private fun handleTap(
        x: Float,
        y: Float
    ) {

        if (gameMode == MODE_OVERWORLD) {
            return
        }

        when (battleScreen) {

            BATTLE_MENU -> {

                // FIGHT
                if (x in 90f..590f && y in 220f..450f) {
                    battleScreen = BATTLE_MOVES
                    statusMessage = ""
                    invalidate()
                    return
                }

                // BAG
                if (x in 650f..1150f && y in 220f..450f) {
                    battleScreen = BATTLE_BAG
                    statusMessage = ""
                    invalidate()
                    return
                }

                // POKÉMON
                if (x in 90f..590f && y in 500f..730f) {
                    battleScreen = BATTLE_POKEMON
                    statusMessage = ""
                    invalidate()
                    return
                }

                // RUN
                if (x in 650f..1150f && y in 500f..730f) {
                    performRun()
                    return
                }
            }

            BATTLE_MOVES -> {

                moves.forEachIndexed { index, _ ->

                    val top = 175f + index * 205f

                    if (
                        x in 70f..1170f &&
                        y in top..(top + 175f)
                    ) {

                        selectedMove = index

                        performSelectMove(index)

                        return
                    }
                }

                if (isBackButtonPressed(x, y)) {
                    battleScreen = BATTLE_MENU
                    invalidate()
                }
            }

            BATTLE_BAG -> {

                items.forEachIndexed { index, _ ->

                    val top = 175f + index * 155f

                    if (
                        x in 70f..1170f &&
                        y in top..(top + 125f)
                    ) {

                        selectedItem = index

                        performUseItem(index)

                        return
                    }
                }

                if (isBackButtonPressed(x, y)) {
                    battleScreen = BATTLE_MENU
                    invalidate()
                }
            }

            BATTLE_POKEMON -> {

                party.forEachIndexed { index, pokemon ->

                    val top = 175f + index * 135f

                    if (
                        x in 70f..1170f &&
                        y in top..(top + 112f)
                    ) {

                        if (!pokemon.fainted) {

                            selectedPokemon = index

                            performSwitchPokemon(index)
                        }

                        return
                    }
                }

                if (isBackButtonPressed(x, y)) {
                    battleScreen = BATTLE_MENU
                    invalidate()
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // ACTIONS
    // -------------------------------------------------------------------------

    private fun performSelectMove(index: Int) {

        val move = moves.getOrNull(index) ?: return

        statusMessage =
            "Selected ${move.name}"

        /*
         * TODO:
         *
         * Replace this with:
         *
         * NativeLibrary.performBdspCompanionAction(
         *     ACTION_SELECT_MOVE,
         *     index.toLong()
         * )
         *
         * once the native BDSP bridge is connected.
         */

        invalidate()
    }

    private fun performUseItem(index: Int) {

        val item = items.getOrNull(index) ?: return

        statusMessage =
            "Selected ${item.name}"

        /*
         * TODO:
         *
         * Replace this with:
         *
         * NativeLibrary.performBdspCompanionAction(
         *     ACTION_USE_ITEM,
         *     index.toLong()
         * )
         */

        invalidate()
    }

    private fun performSwitchPokemon(index: Int) {

        val pokemon = party.getOrNull(index) ?: return

        if (pokemon.fainted) {
            statusMessage = "${pokemon.name} has fainted."
            invalidate()
            return
        }

        statusMessage =
            "Switching to ${pokemon.name}"

        /*
         * TODO:
         *
         * Replace this with:
         *
         * NativeLibrary.performBdspCompanionAction(
         *     ACTION_SWITCH_POKEMON,
         *     index.toLong()
         * )
         */

        invalidate()
    }

    private fun performRun() {

        statusMessage =
            "Trying to run..."

        /*
         * TODO:
         *
         * Replace this with:
         *
         * NativeLibrary.performBdspCompanionAction(
         *     ACTION_RUN,
         *     0L
         * )
         */

        invalidate()
    }

    // -------------------------------------------------------------------------
    // BACK BUTTON
    // -------------------------------------------------------------------------

    private fun isBackButtonPressed(
        x: Float,
        y: Float
    ): Boolean {

        return x in 70f..270f &&
                y in DESIGN_HEIGHT - 85f..DESIGN_HEIGHT - 25f
    }

    // -------------------------------------------------------------------------
    // DEVELOPMENT / TESTING
    // -------------------------------------------------------------------------

    /**
     * Allows the existing project to switch between the mock overworld
     * and mock battle UI while we don't yet have the BDSP memory bridge.
     *
     * Call this from the presentation/router if desired:
     *
     *     bdspView.setBattleMode(true)
     */
    fun setBattleMode(inBattle: Boolean) {

        gameMode =
            if (inBattle) {
                MODE_BATTLE
            } else {
                MODE_OVERWORLD
            }

        if (!inBattle) {
            overworldScreen = SCREEN_MAP
        }

        if (inBattle) {
            battleScreen = BATTLE_MENU
        }

        statusMessage = ""

        invalidate()
    }

    /**
     * Returns to the mock overworld.
     */
    fun setOverworldMode() {

        setBattleMode(false)
    }
}