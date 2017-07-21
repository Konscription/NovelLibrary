package com.mgn.bingenovelreader.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.RecyclerView
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.mgn.bingenovelreader.R
import com.mgn.bingenovelreader.adapter.GenericAdapter
import com.mgn.bingenovelreader.database.*
import com.mgn.bingenovelreader.dbHelper
import com.mgn.bingenovelreader.event.EventType
import com.mgn.bingenovelreader.event.NovelEvent
import com.mgn.bingenovelreader.extension.applyFont
import com.mgn.bingenovelreader.extension.setDefaults
import com.mgn.bingenovelreader.extension.startReaderPagerActivity
import com.mgn.bingenovelreader.model.Novel
import com.mgn.bingenovelreader.model.WebPage
import com.mgn.bingenovelreader.network.NovelApi
import com.mgn.bingenovelreader.service.DownloadService
import com.mgn.bingenovelreader.util.Constants
import com.mgn.bingenovelreader.util.Utils
import kotlinx.android.synthetic.main.activity_chapters.*
import kotlinx.android.synthetic.main.content_chapters.*
import kotlinx.android.synthetic.main.listitem_chapter.view.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class ChaptersActivity : AppCompatActivity(), GenericAdapter.Listener<WebPage> {


    lateinit var novel: Novel
    lateinit var adapter: GenericAdapter<WebPage>
    var chapters: ArrayList<WebPage>? = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chapters)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        novel = intent.getSerializableExtra("novel") as Novel
        val dbNovel = dbHelper.getNovel(novel.name!!)
        if (dbNovel != null) novel.copyFrom(dbNovel)

        progressLayout.showLoading()
        setRecyclerView()
        getChapters()

    }

    private fun setRecyclerView() {
        adapter = GenericAdapter(items = ArrayList<WebPage>(), layoutResId = R.layout.listitem_chapter, listener = this)
        recyclerView.setDefaults(adapter)
        recyclerView.addItemDecoration(object : DividerItemDecoration(this, DividerItemDecoration.VERTICAL) {

            override fun getItemOffsets(outRect: Rect?, view: View?, parent: RecyclerView?, state: RecyclerView.State?) {
                val position = parent?.getChildAdapterPosition(view)
                if (position == parent?.adapter?.itemCount?.minus(1)) {
                    outRect?.setEmpty()
                } else {
                    super.getItemOffsets(outRect, view, parent, state)
                }
            }
        })
        swipeRefreshLayout.setOnRefreshListener { getChapters() }
    }

    private fun getChapters() {
        if (!Utils.checkNetwork(this)) {
            chapters = ArrayList(dbHelper.getAllWebPages(novel.id))
            updateData()
            progressLayout.showContent()
            return
        }

        Thread(Runnable {
            chapters = NovelApi().getChapterUrls(novel.url!!)
            if (novel.id != -1L) syncWithChaptersFromDB()
            Handler(Looper.getMainLooper()).post {
                if (chapters == null) chapters = ArrayList()
                updateData()
            }
        }).start()
    }

    private fun syncWithChaptersFromDB() {
        chapters!!.forEach {
            val webPage = dbHelper.getWebPage(novel.id, it.url!!)
            if (webPage != null) it.copyFrom(webPage)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateData() {
        adapter.updateData(chapters!!)
        scrollToBookmark()
        progressLayout.showContent()
        swipeRefreshLayout.isRefreshing = false

        downloadLabel.applyFont(assets).text = getString(R.string.downloaded) + ": "
        updateChapterCount()
        setDownloadButton()
        invalidateOptionsMenu()
    }

    private fun setDownloadButton() {

        chaptersDownloadButton.setOnClickListener {
            progressLayout.showLoading()
            setToDownloadingButton()
            addChaptersToDB()
        }

        setToDownloadButton()
        if (novel.id != -1L) {
            val dq = dbHelper.getDownloadQueue(novel.id)
            val readableWebPages = dbHelper.getAllReadableWebPages(novel.id)

            if (dq != null) {
                if (dq.status == Constants.STATUS_DOWNLOAD) {
                    setToDownloadingButton()
                } else if (dq.status == Constants.STATUS_COMPLETE) {
                    if (readableWebPages.size != chapters!!.size)
                        setToSyncButton()
                    else
                        setToDownloadCompleteButton()
                }
            }
        }
    }

    private fun setToDownloadButton() {
        chaptersDownloadButton.isClickable = true
        chaptersDownloadButton.setBackgroundColor(ContextCompat.getColor(this@ChaptersActivity, R.color.DodgerBlue))
        chaptersDownloadButton.setIconResource(R.drawable.ic_cloud_download_white_vector)
        chaptersDownloadButton.setIconColor(ContextCompat.getColor(this@ChaptersActivity, R.color.white))
        chaptersDownloadButton.setText(getString(R.string.download))
    }


    private fun setToDownloadingButton() {
        chaptersDownloadButton.isClickable = false
        chaptersDownloadButton.setBackgroundColor(ContextCompat.getColor(this@ChaptersActivity, R.color.black_transparent))
        chaptersDownloadButton.setIconResource(R.drawable.ic_trending_down_white_vector)
        chaptersDownloadButton.setIconColor(ContextCompat.getColor(this@ChaptersActivity, R.color.DodgerBlue))
        chaptersDownloadButton.setText(getString(R.string.downloading))
    }

    private fun setToDownloadCompleteButton() {
        chaptersDownloadButton.isClickable = false
        chaptersDownloadButton.setBackgroundColor(ContextCompat.getColor(this@ChaptersActivity, R.color.Green))
        chaptersDownloadButton.setIconColor(ContextCompat.getColor(this@ChaptersActivity, R.color.white))
        chaptersDownloadButton.setIconResource(R.drawable.ic_check_circle_white_vector)
        chaptersDownloadButton.setText(getString(R.string.downlaoded))
    }

    private fun setToSyncButton() {
        chaptersDownloadButton.isClickable = true
        chaptersDownloadButton.setBackgroundColor(ContextCompat.getColor(this@ChaptersActivity, R.color.DodgerBlue))
        chaptersDownloadButton.setIconColor(ContextCompat.getColor(this@ChaptersActivity, R.color.white))
        chaptersDownloadButton.setIconResource(R.drawable.ic_sync_white_vector)
        chaptersDownloadButton.setText(getString(R.string.sync))
    }


    @SuppressLint("SetTextI18n")
    override fun bind(item: WebPage, itemView: View, position: Int) {

//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            itemView.chapterStatusImage.drawable?.mutate()?.setTint(ContextCompat.getColor(this, R.color.White))
//        }

        if (item.filePath != null) {
            itemView.chapterTitleTextView.text = item.title
            itemView.chapterStatusImage.setImageResource(R.drawable.ic_beenhere_white_vector)
        } else {
            itemView.chapterTitleTextView.text = item.chapter
            itemView.chapterStatusImage.setImageResource(R.drawable.ic_chrome_reader_mode_white_vector)
        }

        if (novel.id != -1L) {
            val dq = dbHelper.getDownloadQueue(novel.id)
            if (dq != null && dq.status == Constants.STATUS_DOWNLOAD && item.filePath == null) {
                itemView.chapterStatusImage.setImageResource(R.drawable.ic_queue_white_vector)
            }
        }

        if (item.isRead == 0) // Is not read
            itemView.chapterOverlay.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))
        else
            itemView.chapterOverlay.setBackgroundColor(ContextCompat.getColor(this, R.color.black))

        if (novel.currentWebPageId != -1L && novel.currentWebPageId == item.id) {
            itemView.chapterOverlay.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))
            itemView.chapterStatusImage.setImageResource(R.drawable.ic_bookmark_white_vector)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                itemView.chapterStatusImage.drawable?.mutate()?.setTint(ContextCompat.getColor(this, R.color.Orange))
            }
        }
    }

    override fun onItemClick(item: WebPage) {
        startReaderPagerActivity(novel, item, chapters)
    }

    override fun onResume() {
        super.onResume()
        EventBus.getDefault().register(this)
    }

    override fun onPause() {
        EventBus.getDefault().unregister(this)
        super.onPause()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onWebPageDownload(event: NovelEvent) {
        if (novel.id == event.novelId) {
            if (EventType.UPDATE == event.type) {
                updateChapterCount()
                if (event.webPage != null) {
                    adapter.updateItem(event.webPage!!)
                }

            } else if (event.type == EventType.COMPLETE) {
                adapter.notifyDataSetChanged()
                setToDownloadCompleteButton()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateChapterCount() {
        if (novel.id != -1L) {
            val webPages = dbHelper.getAllReadableWebPages(novel.id)
            val allPages = dbHelper.getAllWebPages(novel.id)
            chaptersDownloadStatus.applyFont(assets).text = "${webPages.size}/${chapters?.size}"
            if (allPages == webPages) setToDownloadCompleteButton()
        } else {
            chaptersDownloadStatus.applyFont(assets).text = "0/${chapters?.size}"
        }
    }

    private fun addChaptersToDB() {
        if (novel.id == -1L) novel.id = dbHelper.insertNovel(novel)
        dbHelper.createDownloadQueue(novel.id)
        dbHelper.updateDownloadQueueStatus(Constants.STATUS_DOWNLOAD, novel.id)
        chapters!!.forEach {
            it.novelId = novel.id
            val dbWebPage = dbHelper.getWebPage(it.novelId, it.url!!)
            if (dbWebPage == null)
                it.id = dbHelper.createWebPage(it)
            else
                it.copyFrom(dbWebPage)
        }
        dbHelper.updateChapterUrlsCached(1L, novel.id)
        addToDownloads()
    }

    private fun addToDownloads() {
        adapter.notifyDataSetChanged()
        startDownloadService(novel.id)
        progressLayout.showContent()
    }

    private fun startDownloadService(novelId: Long) {
        val serviceIntent = Intent(this, DownloadService::class.java)
        serviceIntent.putExtra(Constants.NOVEL_ID, novelId)
        startService(serviceIntent)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_activity_chapters, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.getItem(0)?.isVisible = recyclerView.adapter != null
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item?.itemId == android.R.id.home) finish()
        else if (item?.itemId == R.id.action_sort) {
            val newChapters = ArrayList<WebPage>(adapter.items.asReversed())
            adapter.updateData(newChapters)
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == Constants.READER_ACT_REQ_CODE) {
            if (novel.id != -1L) novel = dbHelper.getNovel(novel.id)!!
            syncWithChaptersFromDB()
            adapter.updateData(chapters!!)
            adapter.notifyDataSetChanged()
            scrollToBookmark()
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun scrollToBookmark() {
        if (novel.currentWebPageId != -1L) {
            val index = adapter.items.indexOfFirst { it.id == novel.currentWebPageId }
            if (index != -1)
                recyclerView.scrollToPosition(index)
        }
    }

}