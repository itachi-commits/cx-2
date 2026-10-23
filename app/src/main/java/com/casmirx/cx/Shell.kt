package com.casmirx.cx

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object Shell {
    const val KEEP = "/data/local/tmp/cx_keep"

    fun ready(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) { false }

    fun run(cmd: String): String {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val out = p.inputStream.bufferedReader().readText() + p.errorStream.bufferedReader().readText()
        p.waitFor()
        return out.trim()
    }

    /** Runs in the shell process, so it still fires if CX is closed or crashes. */
    fun armWatchdog(resetCmd: String) {
        run("rm -f $KEEP")
        run("(sleep 20; [ -f $KEEP ] || ($resetCmd)) >/dev/null 2>&1 &")
    }

    fun keep() { run("touch $KEEP") }

    fun nativeSize(): Pair<Int, Int>? {
        val r = Regex("Physical size: (\\d+)x(\\d+)").find(run("wm size")) ?: return null
        val a = r.groupValues[1].toInt(); val b = r.groupValues[2].toInt()
        return minOf(a, b) to maxOf(a, b)   // (short, long)
    }

    fun nativeDensity(): Int? =
        Regex("Physical density: (\\d+)").find(run("wm density"))?.groupValues?.get(1)?.toInt()

    fun isStretched(): Boolean = run("wm size").contains("Override size")

    fun applyRatio(ratio: Double) {
        val n = nativeSize() ?: return
        run("wm size ${n.first}x${(n.first * ratio).toInt()}")
    }

    fun reset() { run("wm size reset") }
}
