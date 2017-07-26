package io.github.gmathi.novellibrary.activity

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.text.Html
import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.Theme
import com.bumptech.glide.Glide
import io.github.gmathi.novellibrary.R
import io.github.gmathi.novellibrary.database.getNovel
import io.github.gmathi.novellibrary.database.updateNovel
import io.github.gmathi.novellibrary.dbHelper
import io.github.gmathi.novellibrary.extension.*
import io.github.gmathi.novellibrary.model.Novel
import io.github.gmathi.novellibrary.network.NovelApi
import io.github.gmathi.novellibrary.util.Constants
import io.github.gmathi.novellibrary.util.TextViewLinkHandler
import io.github.gmathi.novellibrary.util.Utils
import kotlinx.android.synthetic.main.activity_novel_details.*
import kotlinx.android.synthetic.main.content_novel_details.*
import java.io.File


class NovelDetailsActivity : AppCompatActivity(), TextViewLinkHandler.OnClickListener {

    lateinit var novel: Novel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_novel_details)

        novel = intent.getSerializableExtra("novel") as Novel
        getNovelInfoDB()

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = novel.name

        if (novel.id != -1L)
            setupViews()
        else {
            progressLayout.showLoading()
        }
        getNovelInfo()
        swipeRefreshLayout.setOnRefreshListener { getNovelInfoDB(); getNovelInfo() }
    }

    fun getNovelInfoDB() {
        val dbNovel = dbHelper.getNovel(novel.name!!)
        if (dbNovel != null) novel.copyFrom(dbNovel)
    }

    fun getNovelInfo() {
        if (!Utils.checkNetwork(this)) {
            if (novel.id == -1L) {
                setupViews()
                swipeRefreshLayout.isRefreshing = false
            } else {
                progressLayout.showError(ContextCompat.getDrawable(this, R.drawable.ic_warning_white_vector), getString(R.string.no_internet), getString(R.string.try_again), {
                    progressLayout.showLoading()
                    getNovelInfo()
                })
            }
            return
        }

        Thread(Runnable {
            val downloadedNovel = NovelApi().getNovelDetails(novel.url!!)
            novel.copyFrom(downloadedNovel)
            if (novel.id != -1L) dbHelper.updateNovel(novel)
            Handler(Looper.getMainLooper()).post {
                try {
                    setupViews()
                    progressLayout.showContent()
                    swipeRefreshLayout.isRefreshing = false
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }).start()
    }

    private fun setupViews() {
        setNovelImage()
        novelDetailsName.applyFont(assets).text = novel.name
        setNovelAuthor()

        novelDetailsStatus.applyFont(assets).text = "N/A"
        if (novel.metaData["Year"] != null)
            novelDetailsStatus.applyFont(assets).text = novel.metaData["Year"]

        setNovelRating()
        setNovelAddToLibraryButton()
        setNovelGenre()
        setNovelDescription()
        novelDetailsChaptersLayout.setOnClickListener { startChaptersActivity(novel) }
        novelDetailsMetadataLayout.setOnClickListener { startMetadataActivity(novel) }
        openInBrowserButton.setOnClickListener { openInBrowser(novel.url!!) }
    }

    private fun setNovelImage() {
        if (novel.imageFilePath != null)
            Glide.with(this).load(File(novel.imageFilePath)).into(novelDetailsImage) else
            Glide.with(this).load(novel.imageUrl).into(novelDetailsImage)
        novelDetailsImage.setOnClickListener { startImagePreviewActivity(novel.imageUrl, novel.imageFilePath, novelDetailsImage) }
    }

    private fun setNovelAuthor() {
        val author = novel.metaData["Author(s)"]
        if (author != null) {
//            author = "by " + author
//            val ss1 = SpannableString("by " + author)
//            ss1.setSpan(RelativeSizeSpan(1.2f), 3, author.length, 0)
//            novelDetailsAuthor.applyFont(assets).text = ss1
            novelDetailsAuthor.movementMethod = TextViewLinkHandler(this)
            novelDetailsAuthor.applyFont(assets).text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                Html.fromHtml(author, Html.FROM_HTML_MODE_LEGACY) else Html.fromHtml(author)
        }
    }

    private fun setNovelRating() {
        if (novel.rating != null) {
            var ratingText = "(N/A)"
            try {
                val rating = novel.rating!!.toFloat()
                novelDetailsRatingBar.rating = rating
                ratingText = "(" + String.format("%.1f", rating) + ")"
            } catch (e: Exception) {
                Log.w("Library Activity", "Rating: " + novel.rating, e)
            }
            novelDetailsRatingText.text = ratingText
        }
    }

    private fun setNovelAddToLibraryButton() {
        if (novel.id == -1L) {
            resetAddToLibraryButton()
            novelDetailsDownloadButton.setOnClickListener {
                disableAddToLibraryButton()
                addNovelToDB()
            }
        } else disableAddToLibraryButton()
    }

    private fun addNovelToDB() {
        if (novel.id == -1L) {
            novel.id = dbHelper.insertNovel(novel)
        }
    }

    fun resetAddToLibraryButton() {
        novelDetailsDownloadButton.setText(getString(R.string.add_to_library))
        novelDetailsDownloadButton.setIconResource(R.drawable.ic_library_add_white_vector)
        novelDetailsDownloadButton.setBackgroundColor(ContextCompat.getColor(this@NovelDetailsActivity, android.R.color.transparent))
        novelDetailsDownloadButton.isClickable = true
    }

    fun disableAddToLibraryButton() {
        invalidateOptionsMenu()
        novelDetailsDownloadButton.setText(getString(R.string.in_library))
        novelDetailsDownloadButton.setIconResource(R.drawable.ic_local_library_white_vector)
        novelDetailsDownloadButton.setBackgroundColor(ContextCompat.getColor(this@NovelDetailsActivity, R.color.Green))
        novelDetailsDownloadButton.isClickable = false
    }


    private fun setNovelGenre() {
        if (novel.genres != null) {
            novel.genres!!.forEach {
                novelDetailsGenresLayout.addView(getGenreTextView(it))
            }
        }
    }

    fun getGenreTextView(genre: String): TextView {
        val textView = TextView(this)
        val layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        layoutParams.setMargins(4, 8, 20, 4)
        textView.layoutParams = layoutParams
        textView.setPadding(8, 8, 8, 8)
        textView.setBackgroundColor(ContextCompat.getColor(this, R.color.LightGoldenrodYellow))
        textView.applyFont(assets).text = genre
        textView.setTextColor(ContextCompat.getColor(this, R.color.black))
        return textView
    }

    private fun setNovelDescription() {
        if (novel.longDescription != null) {
            val expandClickable = object : ClickableSpan() {
                override fun onClick(textView: View) {
                    novelDetailsDescription.applyFont(assets).text = novel.longDescription
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.isUnderlineText = false
                }
            }

            val novelDescription = "${novel.longDescription?.subSequence(0, Math.min(300, novel.longDescription!!.length))}… Expand"
            val ss2 = SpannableString(novelDescription)
            ss2.setSpan(expandClickable, Math.min(300, novel.longDescription!!.length) + 2, novelDescription.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            novelDetailsDescription.applyFont(assets).text = ss2
            novelDetailsDescription.movementMethod = LinkMovementMethod.getInstance()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_novel_details, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.getItem(0)?.isVisible = novel.id != -1L
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item?.itemId == android.R.id.home) finish()
        else if (item?.itemId == R.id.action_delete_novel) confirmNovelDelete()
        return super.onOptionsItemSelected(item)
    }

    private fun confirmNovelDelete() {
        MaterialDialog.Builder(this)
            .title(getString(R.string.confirm_remove))
            .content(getString(R.string.confirm_remove_description))
            .positiveText(getString(R.string.remove))
            .negativeText(getString(R.string.cancel))
            .icon(ContextCompat.getDrawable(this, R.drawable.ic_delete_white_vector))
            .typeface("source_sans_pro_regular.ttf", "source_sans_pro_regular.ttf")
            .theme(Theme.DARK)
            .onPositive { _, _ -> deleteNovel() }
            .show()
    }

    private fun deleteNovel() {
        dbHelper.cleanupNovelData(novel.id)
        novel.id = -1L
        setNovelAddToLibraryButton()
        invalidateOptionsMenu()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == Constants.CHAPTER_ACT_REQ_CODE) {
            getNovelInfoDB()
            setNovelAddToLibraryButton()
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onLinkClicked(title: String, url: String) {
        startSearchResultsActivity(title, url)
    }

}
