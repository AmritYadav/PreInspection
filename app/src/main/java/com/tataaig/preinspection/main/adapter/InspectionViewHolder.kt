package com.tataaig.preinspection.main.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tataaig.preinspection.R
import com.tataaig.preinspection.utils.RotateTransformation
import com.tataaig.preinspection.databinding.ItemInspectionBinding
import com.tataaig.preinspection.main.model.Inspection

class InspectionViewHolder(private val binding: ItemInspectionBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun bind(inspection: Inspection) {
        binding.title.text = inspection.title

        val shotsLabel = binding.root.context.getString(R.string.label_shots, inspection.shots)
        val formatShots = HtmlCompat.fromHtml(shotsLabel, HtmlCompat.FROM_HTML_MODE_LEGACY)
        binding.shots.text = formatShots

        inspection.thumbnail?.let { thumb ->
            Glide.with(binding.root)
                .load(thumb)
                .transform(
                    RotateTransformation(
                        270f
                    )
                )
                .into(binding.thumbnail)
        }
    }

    companion object {
        fun create(parent: ViewGroup): InspectionViewHolder = InspectionViewHolder(
            ItemInspectionBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }
}