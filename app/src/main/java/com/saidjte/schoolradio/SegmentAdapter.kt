package com.saidjte.schoolradio

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SegmentAdapter(
    private val onPlay: (Segment) -> Unit
) : RecyclerView.Adapter<SegmentAdapter.VH>() {

    private val items = mutableListOf<Segment>()

    fun submit(list: List<Segment>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card: View = view
        val title: TextView = view.findViewById(R.id.seg_title)
        val desc: TextView = view.findViewById(R.id.seg_desc)
        val category: TextView = view.findViewById(R.id.seg_category)
        val duration: TextView = view.findViewById(R.id.seg_duration)
        val playIcon: ImageView = view.findViewById(R.id.seg_play)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_segment, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val seg = items[position]
        holder.title.text = seg.title
        holder.desc.text = seg.description
        holder.category.text = seg.category
        holder.duration.text = if (seg.durationSec > 0) "${seg.durationSec / 60}:${"%02d".format(seg.durationSec % 60)}" else "—"
        holder.card.setOnClickListener {
            holder.playIcon.setImageResource(R.drawable.ic_pause)
            onPlay(seg)
        }
    }

    override fun getItemCount() = items.size
}
