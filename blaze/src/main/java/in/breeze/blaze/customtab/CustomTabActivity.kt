package `in`.breeze.blaze.customtab

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri

internal class CustomTabActivity : Activity() {

  private var tabLaunched = false
  private val requestId: String?
    get() = intent.getStringExtra(CustomTab.EXTRA_REQUEST_ID)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    tabLaunched = savedInstanceState?.getBoolean(KEY_TAB_LAUNCHED) ?: false
    if (savedInstanceState == null) {
      handleRedirect(intent, newInstance = true)
    }
  }

  override fun onResume() {
    super.onResume()
    if (isFinishing) {
      return
    }
    if (tabLaunched) {
      finishWith { CustomTab.cancel(requestId) }
      return
    }
    val url = intent.getStringExtra(CustomTab.EXTRA_LAUNCH_URL) ?: return finishWith {}
    if (!CustomTab.isActiveRequest(requestId)) {
      return finishWith {}
    }
    tabLaunched = true
    if (!launchChromeTab(url)) {
      finishWith { CustomTab.fail(requestId, "chrome_unavailable") }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    if (intent.hasExtra(CustomTab.EXTRA_LAUNCH_URL)) {
      setIntent(intent)
      tabLaunched = false
      return
    }
    handleRedirect(intent, newInstance = false)
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putBoolean(KEY_TAB_LAUNCHED, tabLaunched)
  }

  private fun handleRedirect(intent: Intent, newInstance: Boolean) {
    val link = intent.data ?: return
    val closeTabOnly = intent.getBooleanExtra(EXTRA_CLOSE_TAB, false)
    finishWith {
      if (!closeTabOnly) {
        CustomTab.completeFromRedirect(link)
      }
    }
    if (newInstance && !closeTabOnly) {
      startActivity(
        Intent(this, CustomTabActivity::class.java)
          .setData(link)
          .putExtra(EXTRA_CLOSE_TAB, true)
          .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
      )
    }
  }

  private fun launchChromeTab(url: String): Boolean {
    val extras = Bundle()
    extras.putBinder(EXTRA_CUSTOM_TABS_SESSION, null)
    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
      .putExtras(extras)
      .setPackage(CHROME_PACKAGE)
    return try {
      startActivity(intent)
      true
    } catch (e: ActivityNotFoundException) {
      false
    }
  }

  private fun finishWith(block: () -> Unit) {
    block()
    finish()
    overridePendingTransition(0, 0)
  }

  private companion object {
    const val KEY_TAB_LAUNCHED = "tabLaunched"
    const val EXTRA_CLOSE_TAB = "in.breeze.blaze.customtab.CLOSE_TAB"
    const val EXTRA_CUSTOM_TABS_SESSION = "android.support.customtabs.extra.SESSION"
    const val CHROME_PACKAGE = "com.android.chrome"
  }
}
