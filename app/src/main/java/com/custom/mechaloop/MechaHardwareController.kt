package com.custom.mechaloop

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.provider.Settings
import java.io.File

/**
 * Multi-layer hardware bridge for Infinix GT 20 Pro (X6871) Mecha Loop LEDs.
 * Layer 1: Direct sysfs /proc/ LED kernel writes (Root / Magisk / KernelSU)
 * Layer 2: Transsion / XOS Settings.System & Settings.Global overrides (ADB / Shizuku WRITE_SECURE_SETTINGS)
 * Layer 3: Hidden Transsion Mecha Lighting Intent launcher
 */
object MechaHardwareController {

    private val CANDIDATE_LED_DIRS = listOf(
        "/sys/class/leds/aw22xxx_led",
        "/sys/class/leds/aw210xx_led",
        "/sys/class/leds/ktd202x",
        "/sys/class/leds/rgb",
        "/sys/class/leds/red",
        "/sys/class/leds/green",
        "/sys/class/leds/blue",
        "/sys/class/leds/atmosphere_led",
        "/sys/class/leds/mecha_led"
    )

    private val XOS_SETTING_KEYS = listOf(
        "transsion_mecha_led_enable",
        "transsion_atmosphere_light_enable",
        "mecha_loop_color",
        "atmosphere_light_color",
        "gt_mecha_light_mode",
        "led_light_music_mode"
    )

    fun probeHardware(): String {
        val sb = StringBuilder()
        val ledsDir = File("/sys/class/leds")
        if (ledsDir.exists() && ledsDir.isDirectory) {
            val nodes = ledsDir.listFiles()?.map { it.name } ?: emptyList()
            sb.append("LED Nodes: ${if (nodes.isEmpty()) "Restricted (SELinux)" else nodes.joinToString(", ")}\n")
        } else {
            sb.append("LED Nodes: /sys/class/leds hidden\n")
        }
        val hasRoot = checkRootAvailable()
        sb.append("Root Access: ${if (hasRoot) "GRANTED (Full Kernel RGB)" else "NO ROOT (Using XOS Settings/Intent Bridge)"}")
        return sb.toString()
    }

    fun checkRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    fun applyCustomColor(context: Context, r: Int, g: Int, b: Int, brightness: Int = 255): String {
        val hexColor = String.format("#%02X%02X%02X", r, g, b)
        val colorInt = Color.rgb(r, g, b)
        val logs = mutableListOf<String>()

        // 1. Try XOS System & Global Settings keys (works non-root with WRITE_SECURE_SETTINGS)
        try {
            Settings.System.putInt(context.contentResolver, "transsion_mecha_led_enable", 1)
            Settings.System.putInt(context.contentResolver, "transsion_atmosphere_light_enable", 1)
            Settings.System.putInt(context.contentResolver, "mecha_loop_color", colorInt)
            Settings.System.putString(context.contentResolver, "mecha_loop_color_hex", hexColor)
            logs.add("XOS Settings updated ($hexColor)")
        } catch (e: SecurityException) {
            logs.add("XOS Settings locked (run ADB grant)")
        } catch (e: Exception) {
            logs.add("XOS write err: ${e.message}")
        }

        // 2. Broadcast Transsion lighting update intent
        try {
            val intent = Intent("com.transsion.lighting.ACTION_UPDATE_LED").apply {
                putExtra("enable", true)
                putExtra("color", colorInt)
                putExtra("hex", hexColor)
                putExtra("r", r)
                putExtra("g", g)
                putExtra("b", b)
                putExtra("brightness", brightness)
            }
            context.sendBroadcast(intent)
        } catch (_: Exception) {
        }

        // 3. Try Root sysfs kernel node write for instant zero-latency hardware RGB
        if (checkRootAvailable()) {
            val scaledR = (r * brightness) / 255
            val scaledG = (g * brightness) / 255
            val scaledB = (b * brightness) / 255
            val cmds = mutableListOf<String>()

            // Standard Linux multi-color / separate RGB LED sysfs paths
            cmds.add("echo $scaledR > /sys/class/leds/red/brightness 2>/dev/null")
            cmds.add("echo $scaledG > /sys/class/leds/green/brightness 2>/dev/null")
            cmds.add("echo $scaledB > /sys/class/leds/blue/brightness 2>/dev/null")

            // Awinic AW22xxx / AW210xx multi-channel RGB LED drivers (common on Transsion GT series)
            val rgbHex = String.format("%02x%02x%02x", scaledR, scaledG, scaledB)
            cmds.add("echo 1 > /sys/class/leds/aw22xxx_led/hwen 2>/dev/null")
            cmds.add("echo '$rgbHex' > /sys/class/leds/aw22xxx_led/rgb 2>/dev/null")
            cmds.add("echo '$scaledR $scaledG $scaledB' > /sys/class/leds/aw210xx_led/multi_intensity 2>/dev/null")

            try {
                Runtime.getRuntime().exec(arrayOf("su", "-c", cmds.joinToString("; "))).waitFor()
                logs.add("Kernel sysfs RGB written ($scaledR,$scaledG,$scaledB)")
            } catch (e: Exception) {
                logs.add("Kernel write err: ${e.message}")
            }
        }

        return logs.joinToString(" | ")
    }

    fun launchHiddenXosMechaSettings(context: Context): Boolean {
        val candidates = listOf(
            ComponentName("com.transsion.lighting", "com.transsion.lighting.MainActivity"),
            ComponentName("com.transsion.lighting", "com.transsion.lighting.ui.LightingSettingActivity"),
            ComponentName("com.android.settings", "com.transsion.settings.lighting.MechaLightSettingsActivity")
        )
        for (cn in candidates) {
            try {
                val intent = Intent().apply {
                    component = cn
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
            }
        }
        return false
    }
}
