package com.droiddesk.launcher

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

class FileHit(val path: String, val name: String, val isDir: Boolean)

/**
 * In-memory index of file and folder names on shared storage, used by the search box.
 * Needs "All files access" (Android 11+) or storage permission (older). Built on a background thread.
 */
object FileIndex {
    @Volatile private var folders: List<String> = emptyList()
    @Volatile private var files: List<String> = emptyList()
    @Volatile private var builtAt = 0L
    @Volatile private var building = false

    fun hasAccess(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            ctx.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

    fun isBuilding(): Boolean = building
    fun isReady(): Boolean = builtAt != 0L

    fun invalidate() { builtAt = 0L }

    fun ensureIndex(ctx: Context, onDone: () -> Unit) {
        if (building || !hasAccess(ctx)) return
        if (builtAt != 0L && System.currentTimeMillis() - builtAt < 10 * 60_000L) return
        building = true
        Thread {
            try {
                val root = Environment.getExternalStorageDirectory()
                val d = ArrayList<String>()
                val f = ArrayList<String>()
                val stack = ArrayDeque<File>()
                stack.add(root)
                while (stack.isNotEmpty() && d.size + f.size < 250_000) {
                    val dir = stack.removeLast()
                    val list = dir.listFiles() ?: continue
                    for (x in list) {
                        val n = x.name
                        if (n.startsWith(".")) continue
                        if (x.isDirectory) {
                            if (dir == root && n == "Android") continue
                            d.add(x.path)
                            stack.add(x)
                        } else {
                            f.add(x.path)
                        }
                    }
                }
                folders = d
                files = f
                builtAt = System.currentTimeMillis()
            } catch (e: Exception) {
                // Leave the previous index in place.
            } finally {
                building = false
            }
            onDone()
        }.start()
    }

    fun search(query: String, limit: Int): List<FileHit> {
        val q = query.lowercase(Locale.getDefault())
        val out = ArrayList<FileHit>()
        val half = maxOf(1, limit / 2)
        for (p in folders) {
            val n = p.substring(p.lastIndexOf('/') + 1)
            if (n.lowercase(Locale.getDefault()).contains(q)) {
                out.add(FileHit(p, n, true))
                if (out.size >= half) break
            }
        }
        for (p in files) {
            val n = p.substring(p.lastIndexOf('/') + 1)
            if (n.lowercase(Locale.getDefault()).contains(q)) {
                out.add(FileHit(p, n, false))
                if (out.size >= limit) break
            }
        }
        return out
    }

    /** Opens a folder in the system Files app, or a file in whichever app handles it. */
    fun open(ctx: Context, hit: FileHit): Boolean {
        return try {
            val f = File(hit.path)
            if (hit.isDir) {
                val base = Environment.getExternalStorageDirectory().absolutePath
                val rel = f.absolutePath.removePrefix(base).trimStart('/')
                val uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:$rel")
                val i = Intent(Intent.ACTION_VIEW)
                i.setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
            } else {
                val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
                val ext = MimeTypeMap.getFileExtensionFromUrl(hit.name).lowercase(Locale.getDefault())
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
                val i = Intent(Intent.ACTION_VIEW)
                i.setDataAndType(uri, mime)
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
