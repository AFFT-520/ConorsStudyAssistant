package com.conor.quizzer

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class StudyTopicRecyclerAdapter(
    private val context: Context,
    private var items: MutableList<Map<String, String>> // The list of items to display
) : RecyclerView.Adapter<StudyTopicRecyclerAdapter.MyViewHolder>() {

    // --- ViewHolder: Holds the views for a single item ---
    inner class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Get references to the views defined in study_list_item.xml
        val titleTextView: TextView = itemView.findViewById(R.id.item_title_textview)

        // You can also add click listeners here
        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val clickedItem = items[position]
                    Log.d("AdapterClick", "Item clicked: ${clickedItem["subject"]}")

                    // Launch TopicDetails Activity from here
                    val intent = Intent(context, TopicDetails::class.java).apply {
                        // Pass data from the clickedItem Map to the TopicDetails activity
                        putExtra(TopicDetails.EXTRA_TOPIC, clickedItem["name"])
                        putExtra(TopicDetails.EXTRA_TEXT_MARKDOWN, clickedItem["text"])
                        // Add more putExtra calls if there are other relevant fields
                    }
                    context.startActivity(intent) // <<< Use context to start activity
                }
            }
        }
    }

    // --- Adapter Overridden Methods ---

    // Called when RecyclerView needs a new ViewHolder of the given type to represent an item.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        // Inflate your study_list_item.xml layout
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.study_list_item, parent, false)
        return MyViewHolder(itemView)
    }

    // Called by RecyclerView to display the data at the specified position.
    // This method should update the contents of the ViewHolder's itemView to reflect the item at the given position.
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val currentItem = items[position]
        holder.titleTextView.text = currentItem["name"]
    }

    // Returns the total number of items in the data set held by the adapter.
    override fun getItemCount() = items.size

    // --- Methods to Dynamically Update Data ---

    /**
     * Replaces the entire list of items and notifies the adapter.
     */
    fun submitList(newItems: List<Map<String, String>>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged() // Notifies the adapter that the entire dataset has changed.
        // For more granular updates, see below.
    }

    /**
     * Adds a single item to the list and notifies the adapter.
     */
    fun addItem(newItem: Map<String, String>) {
        items.add(newItem)
        notifyItemInserted(items.size - 1) // Notifies that an item was inserted at the last position
    }

    /**
     * Adds a list of items to the end of the current list.
     */
    fun addItems(newItems: List<Map<String, String>>) {
        val startPosition = items.size
        items.addAll(newItems)
        notifyItemRangeInserted(startPosition, newItems.size)
    }

    /**
     * Removes an item from the list and notifies the adapter.
     */
    fun removeItem(position: Int) {
        if (position >= 0 && position < items.size) {
            items.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(
                position,
                items.size - position
            ) // To update positions of subsequent items
        }
    }

    /**
     * Updates an item at a specific position.
     */
    fun updateItem(position: Int, updatedItem: Map<String, String>) {
        if (position >= 0 && position < items.size) {
            items[position] = updatedItem
            notifyItemChanged(position)
        }
    }

    /**
     * Clears all items from the list.
     */
    fun clearItems() {
        val oldSize = items.size
        items.clear()
        notifyItemRangeRemoved(0, oldSize)
    }


    // --- Optional: Item Click Listener ---
    private var onItemClickListener: ((Map<String, String>) -> Unit)? = null

    fun setOnItemClickListener(listener: (Map<String, String>) -> Unit) {
        onItemClickListener = listener
    }
}