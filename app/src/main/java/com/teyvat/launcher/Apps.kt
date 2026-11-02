package com.teyvat.launcher

import android.app.ActivityOptions
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap

/** Row order is always Recent -> Apps -> System. System apps never move out of the last group. */
enum class AppGroup { Recent, Apps, System }

data class AppEntry(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap,
    val group: AppGroup,
    val lastUsed: Long,
)

/** Apps that should never be listed (factory menu, Netflix's background "observer" helper, ...). */
object Hidden {
    fun matches(pkg: String, label: String): Boolean {
        val p = pkg.lowercase()
        val l = label.lowercase()
        if (Config.HIDDEN_WORDS.any { p.contains(it) || l.contains(it) }) return true
        // Netflix's helper entry - but never the real Netflix app.
        val observer = p.contains("observer") || l.contains("observer")
        return observer && (p.contains("netflix") || l.contains("netflix"))
    }
}

/** When each app was last opened: this launcher's own history, plus Android's usage stats if access was granted. */
object RecentApps {
    fun lastUsed(ctx: Context): Map<String, Long> {
        val out = HashMap<String, Long>(Prefs.launches(ctx))
        runCatching {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, end - 14L * 24 * 3600 * 1000, end)
            stats?.forEach {
                if (it.lastTimeUsed > 0) out[it.packageName] = maxOf(out[it.packageName] ?: 0L, it.lastTimeUsed)
            }
        }
        return out
    }
}

/**
 * Every launchable app, without this launcher and the hidden ones:
 * recent apps first (newest first), then the other apps A-Z, then system apps A-Z - always last.
 */
fun loadApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    fun query(category: String) =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)

    val used = RecentApps.lastUsed(ctx)
    val seen = HashSet<String>()
    val all = (query(Intent.CATEGORY_LEANBACK_LAUNCHER) + query(Intent.CATEGORY_LAUNCHER))
        .filter { it.activityInfo.packageName != ctx.packageName && seen.add(it.activityInfo.packageName) }
        .mapNotNull { ri ->
            val pkg = ri.activityInfo.packageName
            val label = ri.loadLabel(pm).toString()
            if (Hidden.matches(pkg, label)) return@mapNotNull null
            val isSystem = (ri.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            AppEntry(
                label = label,
                packageName = pkg,
                icon = TileArt.render(pm, ri),
                group = if (isSystem) AppGroup.System else AppGroup.Apps,
                lastUsed = used[pkg] ?: 0L,
            )
        }

    fun isStreaming(e: AppEntry) =
        Config.STREAMING_WORDS.any { e.packageName.lowercase().contains(it) || e.label.lowercase().contains(it) }

    val user = all.filter { it.group == AppGroup.Apps }
    // YouTube / Prime / Netflix are system apps: they stay at the end until opened, then they can move to the front.
    val usedStreaming = all.filter { it.group == AppGroup.System && isStreaming(it) && it.lastUsed > 0 }
    val system = all
        .filter { it.group == AppGroup.System && it !in usedStreaming }
        .sortedBy { it.label.lowercase() }
    val recent = (user + usedStreaming)
        .filter { it.lastUsed > 0 }
        .sortedByDescending { it.lastUsed }
        .take(Config.RECENT_COUNT)
        .map { it.copy(group = AppGroup.Recent) }
    val recentPkgs = recent.map { it.packageName }.toSet()
    val rest = user.filter { it.packageName !in recentPkgs }.sortedBy { it.label.lowercase() }
    return recent + rest + system
}

// ---------------- TV inputs (HDMI 1, HDMI 2, AV ...) ----------------

data class InputEntry(val id: String, val label: String)

fun loadInputs(ctx: Context): List<InputEntry> {
    val tv = ctx.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return emptyList()
    return runCatching {
        tv.tvInputList
            .filter { it.isPassthroughInput }
            .map { info ->
                val name = (info.loadCustomLabel(ctx) ?: info.loadLabel(ctx))?.toString()
                InputEntry(info.id, if (name.isNullOrBlank()) info.id else name)
            }
            .sortedBy { it.label.lowercase() }
    }.getOrDefault(emptyList())
}

enum class Act { NETWORK, SETTINGS }

object Actions {
    private fun tryStart(ctx: Context, vararg intents: Intent): Boolean {
        for (i in intents) {
            try {
                ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: Exception) {
            }
        }
        return false
    }

    /**
     * Opens an app the way a normal Android launcher does: in its own task, reset to its first screen
     * (RESET_TASK_IF_NEEDED), so Back inside the app walks out of it and lands on this launcher.
     */
    fun launchApp(ctx: Context, pkg: String) {
        val pm = ctx.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            toast(ctx, "Can't open this app")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        Prefs.recordLaunch(ctx, pkg)
        try {
            val opts = ActivityOptions.makeCustomAnimation(ctx, R.anim.app_open_enter, R.anim.launcher_open_exit)
            ctx.startActivity(intent, opts.toBundle())
        } catch (e: Exception) {
            toast(ctx, "Can't open this app")
        }
    }

    fun openInput(ctx: Context, input: InputEntry) {
        val uri = TvContract.buildChannelUriForPassthroughInput(input.id)
        if (!tryStart(ctx, Intent(Intent.ACTION_VIEW, uri))) toast(ctx, "Can't open ${input.label}")
    }

    fun run(ctx: Context, act: Act) {
        val ok = when (act) {
            Act.NETWORK -> tryStart(ctx, Intent(android.provider.Settings.ACTION_WIFI_SETTINGS), Intent(android.provider.Settings.ACTION_SETTINGS))
            Act.SETTINGS -> tryStart(ctx, Intent(android.provider.Settings.ACTION_SETTINGS))
        }
        if (!ok) toast(ctx, "Not available on this TV")
    }

    private fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
}
