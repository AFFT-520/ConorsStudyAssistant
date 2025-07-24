package com.conor.quizzer

import android.os.Bundle
import android.webkit.WebView
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.conor.quizzer.databinding.ActivityTopicDetailsBinding
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.node.Node

object MarkdownUtils { // Or make it a top-level function if you prefer

    // Configure extensions if you're using them
    // private val extensions = listOf(TablesExtension.create()) // Example

    private val parser: Parser = Parser.builder()
        // .extensions(extensions) // Uncomment if using extensions
        .build()

    private val renderer: HtmlRenderer = HtmlRenderer.builder()
        // .extensions(extensions) // Uncomment if using extensions
        .build()

    fun parseToHtml(markdownText: String?): String {
        if (markdownText.isNullOrEmpty()) {
            return "" // Or return null, or handle as an error, depending on your needs
        }
        val document: Node = parser.parse(markdownText)
        return renderer.render(document)
    }
}

class TopicDetails : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityTopicDetailsBinding

    companion object {
        const val EXTRA_TOPIC = "com.conor.quizzer.EXTRA_TOPIC"
        const val EXTRA_TEXT_MARKDOWN = "com.conor.quizzer.EXTRA_TEXT_MARKDOWN"
        // Add more consts if you pass more data from the Map
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val markdown = intent.getStringExtra(TopicDetails.EXTRA_TEXT_MARKDOWN)
        val topic_name = intent.getStringExtra(TopicDetails.EXTRA_TOPIC)

        binding = ActivityTopicDetailsBinding.inflate(layoutInflater)
        val markdownWebView: WebView = binding.markdownWebView
        val htmlContent = MarkdownUtils.parseToHtml(markdown)
        markdownWebView.loadData(htmlContent, "text/html", "UTF-8")
        binding.subtopicToolbar.title = topic_name

        setContentView(binding.root)

    }
}