package com.ulpro.animalrecognizer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class AvistamientosFragment : Fragment() {

    private lateinit var rvFeed: RecyclerView
    private lateinit var feedAdapter: FeedAdapter

    private var page = 1
    private var isLoading = false
    private var hasMore = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_avistamientos, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ServerConfig.initialize(requireContext())

        rvFeed = view.findViewById(R.id.rvFeed)
        rvFeed.layoutManager = LinearLayoutManager(requireContext())
        feedAdapter = FeedAdapter(mutableListOf())
        rvFeed.adapter = feedAdapter

        loadFeed()

        rvFeed.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (!rv.canScrollVertically(1) && !isLoading && hasMore) {
                    loadFeed()
                }
            }
        })
    }

    private fun loadFeed() {
        if (isLoading) return
        isLoading = true

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val token = TokenStore.getToken(requireContext()) ?: return@launch

                val body = FormBody.Builder()
                    .add("token", token)
                    .add("page", page.toString())
                    .add("limit", "10")
                    .build()

                val request = Request.Builder()
                    .url("${ServerConfig.BASE_URL}api/feed/avistamientos_feed.php")
                    .post(body)
                    .build()

                val response = OkHttpClient().newCall(request).execute()
                val json = JSONObject(response.body!!.string())

                val posts = FeedParser.parsePosts(json.getJSONArray("posts"))
                hasMore = json.getBoolean("has_more_nuevos")
                page++

                withContext(Dispatchers.Main) {
                    feedAdapter.addPosts(posts)
                    isLoading = false
                }

            } catch (_: Exception) {
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }
}
