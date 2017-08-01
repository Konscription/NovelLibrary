package io.github.gmathi.novellibrary.fragment

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.support.v4.view.MotionEventCompat
import android.support.v7.widget.helper.ItemTouchHelper
import android.util.Log
import android.view.*
import com.afollestad.materialdialogs.MaterialDialog
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import io.github.gmathi.novellibrary.R
import io.github.gmathi.novellibrary.activity.NavDrawerActivity
import io.github.gmathi.novellibrary.activity.NovelDetailsActivity
import io.github.gmathi.novellibrary.adapter.GenericAdapter
import io.github.gmathi.novellibrary.database.createDownloadQueue
import io.github.gmathi.novellibrary.database.getAllNovels
import io.github.gmathi.novellibrary.database.updateOrderId
import io.github.gmathi.novellibrary.dbHelper
import io.github.gmathi.novellibrary.event.NovelEvent
import io.github.gmathi.novellibrary.util.getFileName
import io.github.gmathi.novellibrary.util.setDefaults
import io.github.gmathi.novellibrary.model.Novel
import io.github.gmathi.novellibrary.service.DownloadService
import io.github.gmathi.novellibrary.util.Constants
import io.github.gmathi.novellibrary.util.SimpleItemTouchHelperCallback
import io.github.gmathi.novellibrary.util.SimpleItemTouchListener
import kotlinx.android.synthetic.main.activity_library.*
import kotlinx.android.synthetic.main.content_recycler_view.*
import kotlinx.android.synthetic.main.listitem_library.view.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File
import java.io.FileOutputStream


class LibraryFragment : BaseFragment(), GenericAdapter.Listener<Novel>, SimpleItemTouchListener {

    lateinit var adapter: GenericAdapter<Novel>
    lateinit var touchHelper: ItemTouchHelper
    var lastDeletedId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater!!.inflate(R.layout.activity_library, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        toolbar.title = getString(R.string.title_library)
        (activity as NavDrawerActivity).setToolbar(toolbar)
        setRecyclerView()
    }

    private fun setRecyclerView() {
        adapter = GenericAdapter(ArrayList(dbHelper.getAllNovels()), R.layout.listitem_library, this)
        val callback = SimpleItemTouchHelperCallback(this)
        touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(recyclerView)
        recyclerView.setDefaults(adapter)
        swipeRefreshLayout.setOnRefreshListener {
            updateOrderIds()
            adapter.updateData(ArrayList(dbHelper.getAllNovels()))
            swipeRefreshLayout.isRefreshing = false
        }
    }


    //region Adapter Listener Methods - onItemClick(), viewBinder()

    override fun onItemClick(item: Novel) {
        if (lastDeletedId != item.id)
            startNovelDetailsActivity(item)
    }

    override fun bind(item: Novel, itemView: View, position: Int) {
        itemView.novelImageView.setImageResource(android.R.color.transparent)
        if (item.imageFilePath != null) {
            itemView.novelImageView.setImageDrawable(Drawable.createFromPath(item.imageFilePath))
        }

        if (item.imageUrl != null) {
            val file = File(activity.filesDir, Constants.IMAGES_DIR_NAME + "/" + Uri.parse(item.imageUrl).getFileName())
            if (file.exists())
                item.imageFilePath = file.path

            if (item.imageFilePath == null) {
                Glide.with(this).asBitmap().load(item.imageUrl).into(object : SimpleTarget<Bitmap>() {
                    override fun onResourceReady(bitmap: Bitmap?, transition: Transition<in Bitmap>?) {
                        itemView.novelImageView.setImageBitmap(bitmap)
                        Thread(Runnable {
                            try {
                                val os = FileOutputStream(file)
                                bitmap?.compress(Bitmap.CompressFormat.JPEG, 100, os)
                                item.imageFilePath = file.path
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }).start()
                    }
                })
            } else {
                itemView.novelImageView.setImageDrawable(Drawable.createFromPath(item.imageFilePath))
            }
        }

        itemView.novelTitleTextView.text = item.name
        if (item.rating != null) {
            var ratingText = "(N/A)"
            try {
                val rating = item.rating!!.toFloat()
                itemView.novelRatingBar.rating = rating
                ratingText = "(" + String.format("%.1f", rating) + ")"
            } catch (e: Exception) {
                Log.w("Library Activity", "Rating: " + item.rating, e)
            }
            itemView.novelRatingText.text = ratingText
        }

        itemView.reorderButton.setOnTouchListener { _, event ->
            @Suppress("DEPRECATION")
            if (MotionEventCompat.getActionMasked(event) ==
                MotionEvent.ACTION_DOWN) {
                touchHelper.startDrag(recyclerView.getChildViewHolder(itemView))
            }
            false
        }

    }

    //endregion

    //region Sync Code
    override fun onCreateOptionsMenu(menu: Menu, menuInflater: MenuInflater) {
//        menuInflater.inflate(R.menu.menu_library, menu)
//        val drawable = DrawableCompat.wrap(menu.findItem(R.id.action_sync).icon)
//        DrawableCompat.setTint(drawable, ContextCompat.getColor(context, R.color.white))
//        menu.findItem(R.id.action_sync).icon = drawable
        super.onCreateOptionsMenu(menu, menuInflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        //menu.findItem(R.id.action_newItem).isVisible = true
        super.onPrepareOptionsMenu(menu)

    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
//        when (item?.itemId) {
//            R.id.action_sync -> {
//                syncNovels()
//                return true
//            }
//        }
        return super.onOptionsItemSelected(item)
    }

    private fun syncNovels() {
        dbHelper.getAllNovels().forEach { dbHelper.createDownloadQueue(it.id) }
        startDownloadService(1L)
    }

    private fun startDownloadService(novelId: Long) {
        val serviceIntent = Intent(activity, DownloadService::class.java)
        serviceIntent.putExtra(Constants.NOVEL_ID, novelId)
        activity.startService(serviceIntent)
    }
    //endregion

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onResume() {
        super.onResume()
        adapter.updateData(ArrayList(dbHelper.getAllNovels()))
    }

    override fun onPause() {
        super.onPause()
        updateOrderIds()
    }

    override fun onStop() {
        EventBus.getDefault().unregister(this)
        super.onStop()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onNovelEvent(event: NovelEvent) {
        print(event.novelId)
    }


    fun startNovelDetailsActivity(novel: Novel) {
        val intent = Intent(activity, NovelDetailsActivity::class.java)
        val bundle = Bundle()
        bundle.putSerializable("novel", novel)
        intent.putExtras(bundle)
        activity.startActivityForResult(intent, Constants.NOVEL_DETAILS_REQ_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Constants.NOVEL_DETAILS_RES_CODE) {
            val novelId = data?.extras?.getLong(Constants.NOVEL_ID)
            if (novelId != null) {
                lastDeletedId = novelId
                adapter.removeItemAt(adapter.items.indexOfFirst { it.id == novelId })
                Handler().postDelayed({ lastDeletedId = -1 }, 1200)
            }
            return
        }
    }

    override fun onItemDismiss(viewHolderPosition: Int) {
        MaterialDialog.Builder(activity)
            .title(getString(R.string.confirm_remove))
            .content(getString(R.string.confirm_remove_description))
            .positiveText(R.string.remove)
            .negativeText(R.string.cancel)
            .onPositive { dialog, _ ->
                run {
                    adapter.onItemDismiss(viewHolderPosition)
                    dialog.dismiss()
                }
            }
            .onNegative { dialog, _ ->
                run {
                    adapter.notifyDataSetChanged()
                    dialog.dismiss()
                }
            }
            .show()
    }

    override fun onItemMove(source: Int, target: Int) {
        adapter.onItemMove(source, target)
    }

    private fun updateOrderIds() {
        if (adapter.items.isNotEmpty())
            for (i in 0..adapter.items.size - 1) {
                dbHelper.updateOrderId(adapter.items[i].id, i.toLong())
            }
    }

}
