package com.mgn.bingenovelreader.fragment

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import com.mgn.bingenovelreader.R
import com.mgn.bingenovelreader.adapter.WebPageAdapter
import com.mgn.bingenovelreader.cleaner.HtmlHelper
import com.mgn.bingenovelreader.dataCenter
import com.mgn.bingenovelreader.model.WebPage
import com.mgn.bingenovelreader.network.NovelApi
import com.mgn.bingenovelreader.util.Utils
import kotlinx.android.synthetic.main.activity_reader_pager.*
import kotlinx.android.synthetic.main.fragment_reader.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File


class WebPageFragment : Fragment() {

    var listener: WebPageAdapter.Listener? = null
    var webPage: WebPage? = null
    var doc: Document? = null

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater!!.inflate(R.layout.fragment_reader, container, false)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        webPage = arguments.getSerializable(WEB_PAGE) as WebPage?
        if (webPage == null) activity.finish()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            readerWebView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                run {
                    if (scrollY > oldScrollY && scrollY > 0) {
                        activity.floatingToolbar.hide()
                        activity.fab.hide()
                    }
                    if (scrollY < oldScrollY) {
                        activity.fab.show()
                    }
                }
            }
        }

        if (webPage!!.filePath != null) {
            val internalFilePath = "file://${webPage!!.filePath}"
            if (webPage!!.filePath!!.contains("royalroadl.com")) {
                applyTheme(internalFilePath)
            } else {
                readerWebView.loadUrl(internalFilePath)
            }
        } else
            downloadWebPage()
        setWebView()
    }

    private fun downloadWebPage() {
        progressLayout.showLoading()
        if (!Utils.checkNetwork(activity)) {
            progressLayout.showError(ContextCompat.getDrawable(context, R.drawable.ic_warning_white_vector), "No Active Internet!", "Try Again", {
                downloadWebPage()
            })
            return
        }

        Thread(Runnable {
            try {
                val doc = NovelApi().getDocumentWithUserAgent(webPage!!.url!!)
                val cleaner = HtmlHelper.getInstance(doc.location())
                cleaner.removeJS(doc)
                cleaner.cleanDoc(doc)
                cleaner.toggleTheme(dataCenter.isDarkTheme, doc)
                Handler(Looper.getMainLooper()).post {
                    loadDocument(doc)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }).start()
    }

    private fun loadDocument(doc: Document) {
        if (activity != null && (!isRemoving || !isDetached)) {
            this.doc = doc
            progressLayout.showContent()
            readerWebView.loadDataWithBaseURL(doc.location(), doc.outerHtml(), "text/html", "UTF-8", null)
        }
    }

    fun setWebView() {
        //readerWebView.setOnClickListener { if (!floatingToolbar.isShowing) floatingToolbar.show() else floatingToolbar.hide() }
        readerWebView.webViewClient = object : WebViewClient() {
            @Suppress("OverridingDeprecatedMember")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                listener?.checkUrl(url)
                return true
            }
        }
        changeTextSize(dataCenter.textSize)
    }

    companion object {
        private val WEB_PAGE = "webPage"
        fun newInstance(webPage: WebPage): WebPageFragment {
            val fragment = WebPageFragment()
            val args = Bundle()
            args.putSerializable(WEB_PAGE, webPage)
            fragment.arguments = args
            return fragment
        }
    }

    private fun applyTheme(internalFilePath: String) {
        val input = File(internalFilePath.substring(7))
        val doc = Jsoup.parse(input, "UTF-8", internalFilePath)
        applyTheme(doc, internalFilePath)
    }

    private fun applyTheme(doc: Document, internalFilePath: String) {
        readerWebView.loadDataWithBaseURL(
            internalFilePath,
            HtmlHelper.getInstance(doc.location()).toggleTheme(dataCenter.isDarkTheme, doc).outerHtml(),
            "text/html", "UTF-8", null)
    }

    fun changeTextSize(size: Int) {
        val settings = readerWebView.settings
        settings.textZoom = (size + 50) * 2
    }

    fun reloadPage() {
        if (webPage!!.filePath != null) {
            val internalFilePath = "file://${webPage!!.filePath}"
            applyTheme(internalFilePath)
        } else {
            applyTheme(doc!!, doc!!.location())
        }
    }
}