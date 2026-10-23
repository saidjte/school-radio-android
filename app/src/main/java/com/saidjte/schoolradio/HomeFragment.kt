package com.saidjte.schoolradio

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recycler = view.findViewById<RecyclerView>(R.id.segments_recycler)
        val empty = view.findViewById<TextView>(R.id.empty_view)
        val schoolName = view.findViewById<TextView>(R.id.school_name)
        val liveBtn = view.findViewById<View>(R.id.live_button)

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = SegmentAdapter { seg ->
            PlayerManager.play(requireContext(), seg)
            (requireActivity() as MainActivity).showNowPlaying(seg)
        }

        liveBtn.visibility = View.GONE
        liveBtn.setOnClickListener {
            // البث المباشر يُفعَّل من الكتالوج عند توفر رابط المدرسة
        }

        CatalogRepository.loadAsync(requireContext()) { catalog, source ->
            if (!isAdded) return@loadAsync
            schoolName.text = catalog.school
            val adapter = recycler.adapter as SegmentAdapter
            adapter.submit(catalog.segments)
            liveBtn.visibility = if (catalog.live.enabled && catalog.live.url.isNotBlank())
                View.VISIBLE else View.GONE
            empty.visibility = if (catalog.segments.isEmpty()) View.VISIBLE else View.GONE
            // مصدر الكتالوج: remote/cache/bundled — للتوثيق ورسائل التشخيص
            empty.contentDescription = "source=$source"
        }
    }
}
