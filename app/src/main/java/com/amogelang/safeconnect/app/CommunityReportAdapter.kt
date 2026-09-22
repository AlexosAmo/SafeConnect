package com.amogelang.safeconnect.app

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.amogelang.safeconnect.app.firebase.CommunityReport

class CommunityReportAdapter(private val reports: List<CommunityReport>) :
    RecyclerView.Adapter<CommunityReportAdapter.ReportViewHolder>() {

    class ReportViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
        val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        val tvMeta: TextView = itemView.findViewById(R.id.tvMeta)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_community_report, parent, false)
        return ReportViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
        val report = reports[position]
        holder.tvCategory.text = report.category
        holder.tvDescription.text = report.description

        val relativeTime = report.createdAt?.let {
            DateUtils.getRelativeTimeSpanString(it.time)
        } ?: "Just now"
        holder.tvMeta.text = relativeTime
    }

    override fun getItemCount(): Int = reports.size
}
