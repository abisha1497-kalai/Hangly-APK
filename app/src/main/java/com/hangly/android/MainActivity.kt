package com.hangly.android

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Settings screen. Everything is built in code to keep the project dependency-free. */
class MainActivity : Activity() {

    private companion object {
        const val REQ_IMAGE = 11
        const val INK = 0xFF1E2430.toInt()
        const val MUTED = 0xFF667085.toInt()
        const val ACCENT = 0xFFB07A0A.toInt()
    }

    private lateinit var prefs: Prefs
    private lateinit var master: Switch
    private lateinit var status: TextView
    private lateinit var permButton: Button
    private val slotViews = ArrayList<LinearLayout>()
    private val slotRefreshers = ArrayList<() -> Unit>()
    private var ignoreSwitch = false
    private var waitingForPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(32))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFFF7F4EE.toInt())
            isFillViewport = true
            addView(root)
        }
        setContentView(scroll)

        root.addView(text("Hangly", 30f, true, INK))
        root.addView(text("A little charm that hangs from the top of your screen and swings when you touch, tilt or shake.", 14f, false, MUTED).apply {
            setPadding(0, dp(4), 0, dp(16))
        })

        // Master switch card
        val card = card(root)
        master = Switch(this).apply {
            text = "Show pendant on screen"
            textSize = 17f
            setTextColor(INK)
            typeface = Typeface.DEFAULT_BOLD
        }
        card.addView(master)
        status = text("", 13f, false, MUTED).apply { setPadding(0, dp(6), 0, 0) }
        card.addView(status)
        permButton = Button(this).apply {
            text = "Allow display over other apps"
            setOnClickListener { openOverlaySettings() }
        }
        card.addView(permButton)

        master.setOnCheckedChangeListener { _, on ->
            if (ignoreSwitch) return@setOnCheckedChangeListener
            if (on) turnOn() else turnOff()
        }

        // Charms: pendant gallery and charm bracelet
        buildCharmCard(root)

        // Look and feel
        val look = card(root)
        look.addView(text("Look & feel", 17f, true, INK))
        slider(look, "Pendant size", " dp", 40, 200, prefs.sizeDp) { prefs.sizeDp = it }
        slider(look, "Chain length", " dp", 80, 420, prefs.chainDp) { prefs.chainDp = it }
        slider(look, "Position (left to right)", " %", 0, 100, prefs.pivot) { prefs.pivot = it }
        slider(look, "Swing strength", " %", 0, 100, prefs.motion) { prefs.motion = it }

        look.addView(text("Chain style", 14f, false, INK).apply { setPadding(0, dp(12), 0, dp(2)) })
        val group = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val names = PendulumView.STYLE_NAMES
        for (i in names.indices) {
            group.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = names[i]
                setTextColor(INK)
                isChecked = prefs.style == i
            })
        }
        group.setOnCheckedChangeListener { g, checkedId ->
            val idx = g.indexOfChild(g.findViewById(checkedId))
            if (idx >= 0) prefs.style = idx
        }
        look.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(group)
        })

        look.addView(Switch(this).apply {
            text = "Move with tilt and shakes"
            setTextColor(INK)
            isChecked = prefs.tilt
            setPadding(0, dp(10), 0, dp(4))
            setOnCheckedChangeListener { _, c -> prefs.tilt = c }
        })

        // Touch
        val touch = card(root)
        touch.addView(text("Touch", 17f, true, INK))
        dropdown(touch, "Tap on a charm", arrayOf("Sparkle and swing", "Next charm", "Nothing"), prefs.tapAction) {
            prefs.tapAction = it
        }
        dropdown(
            touch, "Long-press on a charm",
            arrayOf("Open Hangly settings", "Next charm", "Hide Hangly", "Nothing"), prefs.longAction
        ) { prefs.longAction = it }
        touch.addView(Switch(this).apply {
            text = "Haptic feedback"
            setTextColor(INK)
            isChecked = prefs.haptics
            setPadding(0, dp(12), 0, dp(4))
            setOnCheckedChangeListener { _, c -> prefs.haptics = c }
        })

        // General
        val gen = card(root)
        gen.addView(text("General", 17f, true, INK))
        gen.addView(Switch(this).apply {
            text = "Start when the phone restarts"
            setTextColor(INK)
            isChecked = prefs.bootStart
            setPadding(0, dp(8), 0, dp(4))
            setOnCheckedChangeListener { _, c -> prefs.bootStart = c }
        })
        gen.addView(text("Drag a charm with your finger to swing it, or tap it for sparkles. Use the Stop button in the notification to hide it quickly.", 12f, false, MUTED).apply {
            setPadding(0, dp(8), 0, 0)
        })

    }

    override fun onResume() {
        super.onResume()
        val granted = Settings.canDrawOverlays(this)
        if (waitingForPermission && granted) {
            waitingForPermission = false
            turnOn()
        }
        refreshStatus()
    }

    // ---------- actions ----------

    private fun turnOn() {
        if (!Settings.canDrawOverlays(this)) {
            setSwitch(false)
            Toast.makeText(this, "Allow \"Display over other apps\" for Hangly first", Toast.LENGTH_LONG).show()
            openOverlaySettings()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        prefs.enabled = true
        OverlayService.start(this)
        refreshStatus()
    }

    private fun turnOff() {
        prefs.enabled = false
        OverlayService.stop(this)
        refreshStatus()
    }

    private fun openOverlaySettings() {
        waitingForPermission = true
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun pickImage(slot: Int) {
        val i = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
        @Suppress("DEPRECATION")
        startActivityForResult(Intent.createChooser(i, "Choose pendant image"), REQ_IMAGE + slot)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val slot = requestCode - REQ_IMAGE
        if (slot !in 0 until Prefs.MAX_CHARMS || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val removeBg = prefs.removeBg
        Thread {
            val ok = PendantStore.saveFromUri(this, slot, uri, removeBg)
            runOnUiThread {
                if (ok) {
                    prefs.setPendantId(slot, "custom")
                    prefs.bumpImage()
                    refreshCharms()
                } else {
                    Toast.makeText(this, "Couldn't read that image", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    // ---------- UI helpers ----------

    private fun refreshStatus() {
        val granted = Settings.canDrawOverlays(this)
        permButton.visibility = if (granted) View.GONE else View.VISIBLE
        setSwitch(granted && prefs.enabled)
        status.text = when {
            !granted -> "Hangly needs permission to draw over other apps."
            prefs.enabled -> "Hanging around. Tap the pendant to swing it."
            else -> "Off."
        }
    }

    private fun setSwitch(on: Boolean) {
        ignoreSwitch = true
        master.isChecked = on
        ignoreSwitch = false
    }

    // ---------- charm bracelet ----------

    private fun buildCharmCard(root: LinearLayout) {
        val c = card(root)
        c.addView(text("Charm bracelet", 17f, true, INK))
        c.addView(text("Hang up to three charms side by side. They swing on their own and bump into each other.", 12f, false, MUTED).apply {
            setPadding(0, dp(2), 0, dp(6))
        })

        c.addView(text("Number of charms", 14f, false, INK).apply { setPadding(0, dp(6), 0, dp(2)) })
        val countGroup = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        for (n in 1..Prefs.MAX_CHARMS) {
            countGroup.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = n.toString()
                setTextColor(INK)
                tag = n
                isChecked = prefs.charmCount == n
            })
        }
        countGroup.setOnCheckedChangeListener { g, checkedId ->
            val n = g.findViewById<View>(checkedId)?.tag as? Int
            if (n != null) {
                prefs.charmCount = n
                updateSlotVisibility()
            }
        }
        c.addView(countGroup)

        for (slot in 0 until Prefs.MAX_CHARMS) buildSlot(c, slot)
        updateSlotVisibility()

        c.addView(CheckBox(this).apply {
            text = "Remove a plain background from photos (for JPG or white backgrounds)"
            textSize = 13f
            setTextColor(INK)
            isChecked = prefs.removeBg
            setPadding(0, dp(8), 0, 0)
            setOnCheckedChangeListener { _, checked -> prefs.removeBg = checked }
        })
        c.addView(text("Tip: a PNG with a transparent background looks best. Tap Photo again to pick a different picture.", 12f, false, MUTED))
    }

    private fun buildSlot(parent: LinearLayout, slot: Int) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(text("Charm ${slot + 1}", 14f, true, INK).apply { setPadding(0, dp(14), 0, dp(4)) })
        val preview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        box.addView(preview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(96)))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        box.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        })
        parent.addView(box)
        slotViews.add(box)

        val tiles = ArrayList<Pair<String, ImageView>>()
        for (pid in DefaultPendant.IDS + listOf("custom", "emoji")) {
            val tile = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(6), dp(6), dp(6), dp(6))
                contentDescription = when (pid) {
                    "custom" -> "Your photo"
                    "emoji" -> "Emoji"
                    else -> DefaultPendant.label(pid)
                }
                setOnClickListener { onTile(slot, pid) }
            }
            row.addView(tile, LinearLayout.LayoutParams(dp(60), dp(66)).apply {
                rightMargin = dp(8)
                topMargin = dp(6)
                bottomMargin = dp(2)
            })
            tiles.add(pid to tile)
        }

        val refresh: () -> Unit = {
            val current = PendantStore.idFor(this, slot)
            preview.setImageBitmap(PendantStore.load(this, slot, dp(90)))
            for ((id, tile) in tiles) {
                val selected = if (id == "emoji") current.startsWith("emoji:") else current == id
                tile.setImageBitmap(thumb(slot, id, current))
                tile.background = GradientDrawable().apply {
                    setColor(0xFFFFFFFF.toInt())
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(if (selected) 2 else 1), if (selected) ACCENT else 0xFFE4E0D8.toInt())
                }
            }
        }
        slotRefreshers.add(refresh)
        refresh()
    }

    private fun thumb(slot: Int, id: String, current: String): Bitmap {
        val w = dp(40)
        return when (id) {
            "custom" ->
                if (PendantStore.hasCustom(this, slot)) PendantStore.render(this, slot, "custom", w)
                else DefaultPendant.emoji("📷", w) // camera: "choose a photo"
            "emoji" ->
                DefaultPendant.emoji(
                    if (current.startsWith("emoji:")) current.removePrefix("emoji:") else "😀", w
                )
            else -> DefaultPendant.create(id, w)
        }
    }

    private fun onTile(slot: Int, id: String) {
        when (id) {
            "custom" -> {
                if (PendantStore.hasCustom(this, slot) && PendantStore.idFor(this, slot) != "custom") {
                    prefs.setPendantId(slot, "custom")
                } else {
                    pickImage(slot)
                }
            }
            "emoji" -> askEmoji(slot)
            else -> prefs.setPendantId(slot, id)
        }
        refreshCharms()
    }

    private fun askEmoji(slot: Int) {
        val current = PendantStore.idFor(this, slot)
        val input = EditText(this).apply {
            hint = "Type an emoji"
            textSize = 28f
            isSingleLine = true
            if (current.startsWith("emoji:")) setText(current.removePrefix("emoji:"))
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle("Emoji charm")
            .setView(input)
            .setPositiveButton("Use it") { _, _ ->
                val t = input.text.toString().trim()
                if (t.isNotEmpty()) {
                    prefs.setPendantId(slot, "emoji:" + t.take(16))
                    refreshCharms()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateSlotVisibility() {
        for (i in slotViews.indices) {
            slotViews[i].visibility = if (i < prefs.charmCount) View.VISIBLE else View.GONE
        }
    }

    private fun refreshCharms() {
        for (r in slotRefreshers) r()
    }

    private fun dropdown(
        parent: LinearLayout, title: String, options: Array<String>, selected: Int, onSet: (Int) -> Unit
    ) {
        parent.addView(text(title, 14f, false, INK).apply { setPadding(0, dp(12), 0, 0) })
        val sp = Spinner(this)
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
        sp.setSelection(selected.coerceIn(0, options.size - 1))
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                onSet(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        parent.addView(sp)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun text(s: String, size: Float, bold: Boolean, color: Int) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun card(parent: LinearLayout): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(0xFFFFFFFF.toInt())
                cornerRadius = dp(16).toFloat()
            }
        }
        parent.addView(c, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(14) })
        return c
    }

    private fun slider(
        parent: LinearLayout, title: String, unit: String,
        lo: Int, hi: Int, initial: Int, onSet: (Int) -> Unit
    ) {
        val label = text("$title: $initial$unit", 14f, false, INK).apply { setPadding(0, dp(12), 0, 0) }
        val bar = SeekBar(this)
        bar.max = hi - lo
        bar.progress = initial - lo
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                val v = progress + lo
                label.text = "$title: $v$unit"
                if (fromUser) onSet(v)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        parent.addView(label)
        parent.addView(bar)
    }
}
