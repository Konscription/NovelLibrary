package io.github.gmathi.novellibrary.activity

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.analytics.FirebaseAnalytics
import com.zhkrb.cloudflare_scrape_webview.CfCallback
import com.zhkrb.cloudflare_scrape_webview.Cloudflare
import io.github.gmathi.novellibrary.database.DBHelper
import io.github.gmathi.novellibrary.model.source.SourceManager
import io.github.gmathi.novellibrary.model.source.online.HttpSource
import io.github.gmathi.novellibrary.network.NetworkHelper
import io.github.gmathi.novellibrary.model.preference.DataCenter
import io.github.gmathi.novellibrary.util.Logs
import io.github.gmathi.novellibrary.util.lang.LocaleManager
import io.github.gmathi.novellibrary.util.system.DataAccessor
import okhttp3.HttpUrl.Companion.toHttpUrl
import uy.kohesive.injekt.injectLazy
import java.net.HttpCookie


abstract class BaseActivity : AppCompatActivity(), DataAccessor {

    override val firebaseAnalytics: FirebaseAnalytics by injectLazy()
    override val dataCenter: DataCenter by injectLazy()
    override val dbHelper: DBHelper by injectLazy()
    override val sourceManager: SourceManager by injectLazy()
    override val networkHelper: NetworkHelper by injectLazy()
    override fun getContext(): Context? = this

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.updateContextLocale(newBase))
    }

    fun resolveCloudflare(url: String, completionBlock: (success: Boolean, url: String, errorMessage: String?) -> Unit) {
        val cf = Cloudflare(this, url);
        cf.user_agent = HttpSource.DEFAULT_USER_AGENT
        cf.setCfCallback(object : CfCallback {
            override fun onSuccess(cookieList: MutableList<HttpCookie>?, hasNewUrl: Boolean, newUrl: String?) {
                networkHelper.cookieManager.remove(url.toHttpUrl())
                networkHelper.cookieManager.saveFromResponse(newUrl ?: url, cookieList?.map { it.toString() } ?: emptyList())
                completionBlock(true, newUrl ?: url, null)
            }

            override fun onFail(code: Int, msg: String?) {
                completionBlock(false, url, msg)
            }
        })
        try {
            cf.getCookies()
        } catch (e: Exception) {
            Logs.error("BaseActivity", "CloudFlare GetCookies", e)
        }
    }

}