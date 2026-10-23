package com.saidjte.schoolradio

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton

class AboutFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_about, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val versionText = view.findViewById<TextView>(R.id.about_version)
        versionText.text = getString(R.string.version_line, BuildConfig.VERSION_NAME)

        val checkBtn = view.findViewById<MaterialButton>(R.id.btn_check_update)
        val releasesBtn = view.findViewById<MaterialButton>(R.id.btn_releases)
        val repoBtn = view.findViewById<MaterialButton>(R.id.btn_repo)

        checkBtn.setOnClickListener {
            checkBtn.isEnabled = false
            checkBtn.text = getString(R.string.checking)
            UpdateChecker.checkAsync { info ->
                if (!isAdded) return@checkAsync
                checkBtn.isEnabled = true
                checkBtn.text = getString(R.string.check_update)
                if (info == null) {
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.up_to_date_title)
                        .setMessage(getString(R.string.up_to_date_msg, BuildConfig.VERSION_NAME))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                } else {
                    AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.update_available, info.latestTag))
                        .setMessage(R.string.update_msg)
                        .setPositiveButton(R.string.download_update) { _, _ ->
                            (requireActivity() as MainActivity).openUrl(info.apkUrl)
                        }
                        .setNegativeButton(R.string.view_notes) { _, _ ->
                            (requireActivity() as MainActivity).openUrl(info.notesUrl)
                        }
                        .show()
                }
            }
        }

        releasesBtn.setOnClickListener {
            (requireActivity() as MainActivity).openUrl(UpdateChecker.REPO_URL + "/releases")
        }
        repoBtn.setOnClickListener {
            (requireActivity() as MainActivity).openUrl(UpdateChecker.REPO_URL)
        }
    }
}
