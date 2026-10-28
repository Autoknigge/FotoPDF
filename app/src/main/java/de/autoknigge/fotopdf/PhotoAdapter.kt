package de.autoknigge.fotopdf

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

class PhotoAdapter(
    private val items: MutableList<PhotoItem>,
    private val onChanged: () -> Unit
) : RecyclerView.Adapter<PhotoAdapter.VH>() {

    private val loader = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<Long, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val num: TextView = v.findViewById(R.id.tvNum)
        val thumb: ImageView = v.findViewById(R.id.ivThumb)
        val rot: TextView = v.findViewById(R.id.tvRot)
        val up: View = v.findViewById(R.id.btnUp)
        val down: View = v.findViewById(R.id.btnDown)
        val rotate: View = v.findViewById(R.id.btnRotate)
        val delete: View = v.findViewById(R.id.btnDelete)
    }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = items[position].id

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_photo, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        h.num.text = (position + 1).toString()
        h.rot.text = if (item.rotation % 360 == 0) "" else "gedreht ${item.rotation % 360}°"
        h.thumb.rotation = item.rotation.toFloat()
        h.thumb.tag = item.id

        val cached = cache.get(item.id)
        if (cached != null) {
            h.thumb.setImageBitmap(cached)
        } else {
            h.thumb.setImageBitmap(null)
            val ctx = h.itemView.context.applicationContext
            loader.execute {
                val bmp = try {
                    Photos.loadOriented(ctx, item.uri, 200)
                } catch (e: Exception) {
                    null
                } catch (e: OutOfMemoryError) {
                    null
                }
                if (bmp != null) {
                    cache.put(item.id, bmp)
                    main.post {
                        if (h.thumb.tag == item.id) h.thumb.setImageBitmap(bmp)
                    }
                }
            }
        }

        h.up.setOnClickListener {
            val p = h.bindingAdapterPosition
            if (p > 0) swap(p, p - 1)
        }
        h.down.setOnClickListener {
            val p = h.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION && p < items.size - 1) swap(p, p + 1)
        }
        h.rotate.setOnClickListener {
            val p = h.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) {
                items[p].rotation = (items[p].rotation + 90) % 360
                notifyItemChanged(p)
            }
        }
        h.delete.setOnClickListener {
            val p = h.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) {
                items.removeAt(p)
                notifyItemRemoved(p)
                notifyItemRangeChanged(p, items.size - p)
                onChanged()
            }
        }
    }

    private fun swap(a: Int, b: Int) {
        val tmp = items[a]
        items[a] = items[b]
        items[b] = tmp
        notifyItemMoved(a, b)
        notifyItemChanged(a)
        notifyItemChanged(b)
    }

    fun shutdown() {
        loader.shutdownNow()
    }
}
