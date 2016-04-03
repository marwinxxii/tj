package com.a6v.tjreader.service

import android.net.Uri
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Tag

class ArticleHtmlParser(document: Document) {
  private val article = getArticle(document)

  fun findImageUrls(): List<String> {
    return article.getElementsByTag("img")
      .map { it.attr("src") }
      .filter { it != null && !it.isEmpty() }
  }

  fun replaceVideosWithThumbnails(): ArticleHtmlParser {
    for (iframe in article.getElementsByTag("iframe")) {
      val src: String? = iframe.attr("src")
      if (src != null) {
        iframe.replaceWith(createIframeReplacement(src))
      }
    }
    return this
  }

  fun createIframeReplacement(src: String): Element {
    val videoId = Uri.parse(src).encodedPath.replaceFirst("/embed/", "")
    val videoThumbnail = createArticleImage(
      "https://www.youtube.com/watch?v=$videoId",
      "https://img.youtube.com/vi/$videoId/0.jpg", //TODO choose image size
      article.baseUri()
    )
    videoThumbnail.attr("data-tjr-thumbnail", true)//in case of future need to find these thumbnails
    videoThumbnail.appendElement("span").text("(кликните, чтобы открыть YouTube видео)")
    return videoThumbnail
  }

  fun getHtml(): String {
    return article.outerHtml()
  }

  companion object {
    fun getArticle(document: Document): Element {
      return document.getElementsByTag("article").first()
    }

    fun getIntroText(html: String): String {
      return Jsoup.parse(html).text()
    }

    fun createArticleImage(href: String, src: String, baseUri: String): Element {
      val div = Element(Tag.valueOf("div"), baseUri)
      div.attr("data-tjr-image", true)//in case of future need to find these images

      val link = Element(Tag.valueOf("a"), baseUri)
      link.attr("href", href)//TODO styles

      val img = Element(Tag.valueOf("img"), baseUri)
      img.addClass("full-width")
      img.attr("src", src)//TODO choose image size

      link.appendChild(img)
      div.appendChild(link)
      return div
    }
  }
}