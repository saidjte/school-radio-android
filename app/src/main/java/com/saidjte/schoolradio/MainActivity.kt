package com.saidjte.schoolradio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var playerBar: View
    private lateinit var playerTitle: TextView
    private lateinit var playerCategory: TextView
    private lateinit var playerPos: TextView
    private lateinit var playerDur: TextView
    private lateinit var playerSeek: SeekBar
    private lateinit var playerToggle: ImageButton
    private lateinit var playerClose: ImageButton

    private val tickRunnable = object : Runnable {
        override fun run() {
            refreshPlayerBar()
            playerSeek.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerBar = findViewById(R.id.player_bar)
        playerTitle = findViewById(R.id.player_title)
        playerCategory = findViewById(R.id.player_category)
        playerPos = findViewById(R.id.player_pos)
        playerDur = findViewById(R.id.player_dur)
        playerSeek = findViewById(R.id.player_seek)
        playerToggle = findViewById(R.id.player_toggle)
        playerClose = findViewById(R.id.player_close)

        playerToggle.setOnClickListener { PlayerManager.togglePlayPause() }
        playerClose.setOnClickListener {
            PlayerManager.stop()
            playerBar.visibility = View.GONE
        }
        playerSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) PlayerManager.seekTo(progress.toLong())
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        val nav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> show(HomeFragment())
                R.id.nav_schedule -> show(ScheduleFragment())
                R.id.nav_about -> show(AboutFragment())
            }
            true
        }
        if (savedInstanceState == null) show(HomeFragment())

        playerSeek.post(tickRunnable)
    }

    private fun show(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    /** إظهار الفقرة الحالية في شريط المشغل السفلي */
    fun showNowPlaying(segment: Segment) {
        playerBar.visibility = View.VISIBLE
        playerTitle.text = segment.title
        playerCategory.text = segment.category
        refreshPlayerBar()
    }

    fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        return String.format("%d:%02d", s / 60, s % 60)
    }

    private fun refreshPlayerBar() {
        val seg = PlayerManager.nowPlaying ?: return
        if (playerBar.visibility != View.VISIBLE) return
        val dur = PlayerManager.duration
        val pos = PlayerManager.currentPosition
        if (dur > 0) {
            playerSeek.max = dur.toInt()
            playerSeek.progress = pos.toInt()
            playerDur.text = fmt(dur)
        } else {
            playerSeek.max = 100
            playerSeek.progress = 0
            playerDur.text = "مباشر"
        }
        playerPos.text = fmt(pos)
        playerToggle.setImageResource(
            if (PlayerManager.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }
}
