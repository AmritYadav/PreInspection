package com.tataaig.preinspection.main.adapter

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tataaig.preinspection.main.model.Inspection

class InspectionAdapter : RecyclerView.Adapter<InspectionViewHolder>() {

    var inspections: List<Inspection> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InspectionViewHolder {
        return InspectionViewHolder.create(parent)
    }

    override fun getItemCount(): Int = inspections.size

    override fun onBindViewHolder(holder: InspectionViewHolder, position: Int) {
        holder.bind(inspections[position])
    }


}