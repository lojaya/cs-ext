package com.lojaya

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network
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
        val doc = app.get(pagedlink, interceptor = CloudflareKiller()).document
        val home = doc.select("body div.class-card-inner-wrapper").mapNotNull { toSearchResponse(it) }
        if (home.isEmpty()) {
            throw ErrorLoadingException("No homepage data found!")
        }

        return newHomePageResponse(MainPageRequest(request.name, request.data, true), home)
    }

    // search result
    override suspend fun search(query: String): List<SearchResponse> {
        var searchUrl = "$mainUrl/en/search?query=$query"
        var doc = app.get(searchUrl, interceptor = CloudflareKiller()).document
        val search = doc.select("body div.class-card-inner-wrapper").mapNotNull { toSearchResponse(it) }
        if (search.isEmpty()) {
            throw ErrorLoadingException("No result found!")
        }
        
        return search
    }

    // entry detail
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, interceptor = CloudflareKiller()).document

        val title = doc.selectFirst("meta[property='og:title']")!!.attr("content")
        val thumb = doc.selectFirst("meta[property='og:image']")!!.attr("content")
        val link = doc.selectFirst("meta[property='og:url']")!!.attr("content")
        val plot = doc.selectFirst("meta[property='og:description']")!!.attr("content")
        val trailer = doc.selectFirst("meta[name='twitter:player:stream']")!!.attr("content")
        val genre = doc.select("a.tag").mapNotNull{ it!!.text().trim() }
        val actors = listOf(ActorData(Actor(doc.selectFirst("a.link-main")!!.text())))
        // val publishedAt = doc.selectFirst("meta[property='og:video:release']")!!.attr("content")
        // val duration = doc.selectFirst("meta[property='og:video:duration']")!!.attr("content").toInt() / 60

        var trailers =  mutableListOf<TrailerData>()
        trailer.let {
            trailers.add(TrailerData(extractorUrl = it, referer = "", raw = true))
        }

        val recommendations = doc.select("div.ss-class").mapNotNull{
            var recTitle = it.select(".ss-card__title a").text()
            var recThumb = it.select("a img").attr("src")
            var recGuid = it.select(".ss-card__title a").attr("href")
            MovieSearchResponse(
                recTitle,
                recGuid,
                this@SkillShareProvider.name,
                globalTvType,
                recThumb
            )
        }

        return MovieLoadResponse(
            name = title, // film title (string)
            url = url, // film url (string)
            apiName = this@SkillShareProvider.name, // apiName (string)
            type = globalTvType, // tvType (tvtype)
            dataUrl = link, // data url (string)

            posterUrl = thumb, // poster (string?)
            // year = extractYear(publishedAt), // year (int?)
            plot = plot, // plot (string?)

            tags = genre, // tags (list<string>?)
            // duration = duration, // duration (int?)
            trailers = trailers,
            actors = actors,
            backgroundPosterUrl = thumb, // poster (string?)
            recommendations = recommendations
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