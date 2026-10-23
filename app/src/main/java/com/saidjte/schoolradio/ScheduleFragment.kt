package com.saidjte.schoolradio

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ScheduleFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_schedule, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recycler = view.findViewById<RecyclerView>(R.id.schedule_recycler)
        val empty = view.findViewById<TextView>(R.id.schedule_empty)

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = ScheduleAdapter()

        CatalogRepository.loadAsync(requireContext()) { catalog, _ ->
            if (!isAdded) return@loadAsync
            val adapter = recycler.adapter as ScheduleAdapter
            adapter.submit(catalog.schedule)
            empty.visibility =
                if (catalog.schedule.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}

class ScheduleAdapter : RecyclerView.Adapter<ScheduleAdapter.VH>() {

    private val items = mutableListOf<ScheduleItem>()

    fun submit(list: List<ScheduleItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val day: TextView = view.findViewById(R.id.sch_day)
        val time: TextView = view.findViewById(R.id.sch_time)
        val title: TextView = view.findViewById(R.id.sch_title)
        val presenter: TextView = view.findViewById(R.id.sch_presenter)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val it = items[position]
        holder.day.text = it.day
        holder.time.text = it.time
        holder.title.text = it.title
        holder.presenter.text = it.presenter
    }

    override fun getItemCount() = items.size
}
