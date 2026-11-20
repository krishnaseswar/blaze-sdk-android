package `in`.breeze.blaze.customtab

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

internal object CustomTab {

  const val EXTRA_LAUNCH_URL = "in.breeze.blaze.customtab.LAUNCH_URL"
  const val EXTRA_REQUEST_ID = "in.breeze.blaze.customtab.REQUEST_ID"

  private class Pending(val requestId: String, val onResult: (JSONObject) -> Unit)

  private val pending = AtomicReference<Pending?>(null)

  fun launch(activity: Activity?, url: String, onResult: (JSONObject) -> Unit) {
    if (activity == null) {
      onResult(failed("no_activity"))
      return
    }
    val uri = url.toUri()
    if (!"https".equals(uri.scheme, ignoreCase = true) || uri.host.isNullOrEmpty()) {
      onResult(failed("invalid_url"))
      return
    }
    val requestId = UUID.randomUUID().toString()
    if (!pending.compareAndSet(null, Pending(requestId, onResult))) {
      onResult(failed("tab_already_open"))
      return
    }
    activity.runOnUiThread {
      activity.startActivity(
        Intent(activity, CustomTabActivity::class.java)
          .putExtra(EXTRA_LAUNCH_URL, url)
          .putExtra(EXTRA_REQUEST_ID, requestId)
          .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
      )
      activity.overridePendingTransition(0, 0)
    }
  }

  fun cancel(requestId: String?) {
    reply(requestId, JSONObject().put("status", "cancelled"))
  }

  fun completeFromRedirect(uri: Uri) {
    val current = pending.getAndSet(null) ?: return
    current.onResult(
      JSONObject()
        .put("status", "returned")
        .put("path", uri.path ?: "")
        .put("query", uri.encodedQuery ?: "")
    )
  }

  fun isActiveRequest(requestId: String?): Boolean {
    return requestId != null && pending.get()?.requestId == requestId
  }

  fun fail(requestId: String?, error: String) {
    reply(requestId, failed(error))
  }

  fun reset() {
    pending.set(null)
  }

  private fun reply(requestId: String?, result: JSONObject) {
    val current = pending.get()
    if (current?.requestId == requestId && pending.compareAndSet(current, null)) {
      current?.onResult?.invoke(result)
    }
  }

  private fun failed(error: String): JSONObject {
    return JSONObject().put("status", "failed").put("error", error)
  }
}
