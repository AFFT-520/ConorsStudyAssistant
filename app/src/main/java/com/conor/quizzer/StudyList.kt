package com.conor.quizzer

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.conor.quizzer.databinding.ActivityStudyListBinding
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.lang.reflect.Type
import kotlin.io.path.exists

class StudyList : AppCompatActivity() {

    private lateinit var binding: ActivityStudyListBinding
    private lateinit var topicRecycler: RecyclerView
    private lateinit var topicRecyclerAdapter: StudyTopicRecyclerAdapter
    private val initialItems = mutableListOf<Map<String, String>>() // Start with an empty or initial list



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("StudyList", "onCreate called. Intent: $intent")
        if (intent.extras != null) {
            for (key in intent.extras!!.keySet()) {
                Log.d("StudyList", "Extra: Key: $key, Value: ${intent.extras!!.get(key)}")
            }
        } else {
            Log.w("StudyList", "Intent has no extras!")
        }
        val cachedFilename = intent.getStringExtra(CacheConstants.EXTRA_CACHE_FILENAME)
        val loadedResults = mutableListOf<String>()
        if (cachedFilename != null) {
            val file = File(cacheDir, cachedFilename)
            if (file.exists()) {
                try {
                    FileInputStream(file).use { fis ->
                        InputStreamReader(fis).use { isr ->
                            BufferedReader(isr).use { reader ->
                                var line: String?
                                while (reader.readLine().also { line = it } != null) {
                                    line?.let { loadedResults.add(it) }
                                }
                            }
                        }
                    }
                    if (file.delete()) {
                        Log.d("CacheCleanup", "Cache file deleted successfully after reading: ${file.name}")
                    } else {
                        Log.w("CacheCleanup", "Failed to delete cache file after reading: ${file.name}")
                    }
                    // Option 2: If saved as JSON
                    // val jsonString = file.readText()
                    // val jsonArray = JSONArray(jsonString)
                    // for (i in 0 until jsonArray.length()) {
                    //    loadedResults.add(jsonArray.getString(i))
                    // }

                    Log.d("CacheRead", "Successfully loaded ${loadedResults.size} results from cache.")
                    // Now use loadedResults to populate your UI
                    // e.g., resultsTextView.text = loadedResults.joinToString("\n\n")

                    // Optionally, delete the cache file if it's a one-time use
                    // if (file.delete()) {
                    //     Log.d("CacheRead", "Cache file deleted successfully: ${file.name}")
                    // } else {
                    //     Log.w("CacheRead", "Failed to delete cache file: ${file.name}")
                    // }

                } catch (e: IOException) {
                    Log.e("CacheRead", "Error reading results from cache file: ${file.absolutePath}", e)
                    // Display error message
                } catch (e: org.json.JSONException) { // If parsing JSON
                    Log.e("CacheRead", "Error parsing JSON from cache file: ${file.absolutePath}", e)
                }
            } else {
                Log.w("CacheRead", "Cache file not found: ${file.absolutePath}")
                // Handle case where cache file doesn't exist (maybe it was cleared by system)
            }
        } else {
            Log.w("CacheRead", "No cache filename passed in intent.")
            // Handle case where no filename was provided
        }

        val jsonContent = loadedResults.joinToString("\n")


        val listType: Type = object : TypeToken<StudyItem>() {}.type
        val parsedStudyMaterials: StudyItem = Gson().fromJson(jsonContent, listType)
        val layoutManager = LinearLayoutManager(this)

        binding = ActivityStudyListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        topicRecycler = findViewById(R.id.topicRecycler)
        binding.studyListToolbar.title = parsedStudyMaterials.subject
        try {
            topicRecycler.layoutManager = layoutManager
            Log.d("StudyList_RV", "LayoutManager SET successfully.")
        } catch (e: Exception) {
            Log.e("StudyList_RV_ERROR", "Error setting LayoutManager: ${e.message}", e)
            Toast.makeText(this, "Error initializing list layout.", Toast.LENGTH_SHORT).show()
            finish()
            Log.d("StudyList_Lifecycle", "onCreate - FINISHING due to LayoutManager error")
            return
        }

        try {
            val listType: Type = object : TypeToken<StudyItem>() {}.type // Assuming StudyItem holds a list of topics
            val parsedStudyMaterials: StudyItem = Gson().fromJson(jsonContent, listType)

            // Clear previous items if any (important if onCreate can be called multiple times)
            initialItems.clear()
            for (item in parsedStudyMaterials.topics) { // Assuming 'topics' is List<Map<String, String>>
                initialItems.add(item)
            }
            Log.d("StudyList", "Parsed ${initialItems.size} items for RecyclerView.")
        } catch (e: Exception) {
            Log.e("StudyList", "Error parsing JSON for RecyclerView: ${e.message}", e)
            Toast.makeText(this, "Error parsing study data.", Toast.LENGTH_LONG).show()
            // Potentially finish() or show an empty state in the RecyclerView
            initialItems.clear() // Ensure it's empty on error
        }
        topicRecyclerAdapter = StudyTopicRecyclerAdapter(this, initialItems)
        topicRecycler.adapter = topicRecyclerAdapter


        topicRecyclerAdapter.setOnItemClickListener { clickedItem ->
            Toast.makeText(this, "Clicked: ${clickedItem.toString()}", Toast.LENGTH_SHORT).show()
            // Handle the click, e.g., open a detail screen
        }

        binding.studyListToolbar.title = parsedStudyMaterials.subject
        binding.topicRecycler
    }
}