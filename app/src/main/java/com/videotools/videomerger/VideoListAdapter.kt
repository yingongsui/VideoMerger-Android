package com.videotools.videomerger

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.videotools.videomerger.databinding.ItemVideoBinding

class VideoListAdapter(
    private val onMoveUp: (Int) -> Unit,
    private val onMoveDown: (Int) -> Unit,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<VideoListAdapter.ViewHolder>() {

    private val items = mutableListOf<VideoItem>()
    var controlsEnabled: Boolean = true
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    fun submitList(newItems: List<VideoItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val isFirst = position == 0
        val isLast = position == items.lastIndex

        with(holder.binding) {
            tvIndex.text = (position + 1).toString()
            tvFileName.text = item.name
            tvMetadata.text = item.formattedMetadata()

            btnMoveUp.isEnabled = controlsEnabled && !isFirst
            btnMoveUp.alpha = if (btnMoveUp.isEnabled) 1f else 0.3f

            btnMoveDown.isEnabled = controlsEnabled && !isLast
            btnMoveDown.alpha = if (btnMoveDown.isEnabled) 1f else 0.3f

            btnRemove.isEnabled = controlsEnabled
            btnRemove.alpha = if (controlsEnabled) 1f else 0.3f

            btnMoveUp.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onMoveUp(pos)
            }
            btnMoveDown.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onMoveDown(pos)
            }
            btnRemove.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onRemove(pos)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(val binding: ItemVideoBinding) : RecyclerView.ViewHolder(binding.root)
}
