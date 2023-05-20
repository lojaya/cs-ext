package com.lojaya

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class SkillShareProvider : MainAPI() {
    companion object {
        fun extractYear(t: String): Int? {
            return t.trim().take(4).toIntOrNull()
        }
    }

    private val globalTvType = TvType.Others

    override var lang = "en"
    override var name = "SkillShare"
    override val hasMainPage = true
    override val hasQuickSearch = false
    override val hasDownloadSupport = true
    override var mainUrl = "https://skillshare.com"
    override val supportedTypes = setOf(globalTvType)

    // main pages
    override val mainPage = mainPageOf(
        "$mainUrl/en/browse?sort=popular&page=[pageNum]" to "All Popular Clases",
        "$mainUrl/en/browse?sort=trending&page=[pageNum]" to "All Trending Clases",
        "$mainUrl/en/browse/freelance-and-entrepreneurship?sort=popular&page=[pageNum]" to "Popular Freelance & Entrepreneurship Clases",
        "$mainUrl/en/browse/photography?sort=popular&page=[pageNum]" to "Popular Photography Clases",
        "$mainUrl/en/browse/graphic-design?sort=popular&page=[pageNum]" to "Popular Graphic Design Clases",
        "$mainUrl/en/browse/productivity?sort=popular&page=[pageNum]" to "Popular Productivity Clases",
    )

    private fun toSearchResponse(data: Element): SearchResponse {
        val title = data.selectFirst("a.class-link")!!.text()
        val guid = data.selectFirst("a.class-link")!!.attr("href")
        val thumb = data.selectFirst(".thumbnail img")!!.attr("src")
        return MovieSearchResponse(title, guid, this@SkillShareProvider.name, globalTvType, thumb)
    }

    // homepage
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pagedlink = request.data.replace("[pageNum]", page.toString())
        val doc = app.get(pagedlink).document
        val home = doc.select("body div.class-card-inner-wrapper").mapNotNull { toSearchResponse(it) }
        if (home.isEmpty()) {
            throw ErrorLoadingException("No homepage data found!")
        }

        return newHomePageResponse(MainPageRequest(request.name, request.data, true), home)
    }

    // search result
    override suspend fun search(query: String): List<SearchResponse> {
        var searchUrl = "$mainUrl/en/search?query=$query"
        var doc = app.get(searchUrl).document
        val search = doc.select("body div.class-card-inner-wrapper").mapNotNull { toSearchResponse(it) }
        if (search.isEmpty()) {
            throw ErrorLoadingException("No result found!")
        }
        
        return search
    }

    // entry detail
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val thumb = doc.selectFirst("meta[name='og:image']")!!.attr("content")
        val title = doc.selectFirst("meta[name='og:title']")!!.attr("content")
        val description = doc.selectFirst("meta[name='og:description']")!!.attr("content")

        val genre = doc.select("a.tag").mapNotNull{ it!!.text().trim() }
        val stars = doc.select("a.link-main").text()
        val actors = listOf(ActorData(Actor(stars)))
        // val publishedAt = doc.selectFirst("meta[property='og:video:release']")!!.attr("content")
        // val duration = doc.selectFirst("meta[property='og:video:duration']")!!.attr("content").toInt() / 60

        val trailer = doc.selectFirst("meta[name='twitter:player:stream']")!!.attr("content")
        val myTrailer = mutableListOf<TrailerData>()
        trailer.let {
            myTrailer.add(TrailerData(extractorUrl = trailer, referer = "", raw = true))
        }

        val link = ""

        return MovieLoadResponse(
            name = title, // film title (string)
            url = url, // film url (string)
            apiName = this@SkillShareProvider.name, // apiName (string)
            type = globalTvType, // tvType (tvtype)
            dataUrl = link, // data url (string)

            posterUrl = thumb, // poster (string?)
            // year = extractYear(publishedAt), // year (int?)
            plot = description, // plot (string?)

            tags = genre, // tags (list<string>?)
            // duration = duration, // duration (int?)
            trailers = myTrailer,
            actors = actors,
            backgroundPosterUrl = thumb, // poster (string?)
            // recommendations = recommendations!!.mapNotNull {
            //     MovieSearchResponse(it.title, it.guid, this@SkillShareProvider.name, globalTvType, thumb)
            // }
        )
    }

    // entry links
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var count = 0
        data.split("|").distinct().apmap {
            count++
            loadExtractor(
                url = "",
                referer = mainUrl,
                subtitleCallback = subtitleCallback,
                callback = callback
            )
        }
        return count > 0
    }
}