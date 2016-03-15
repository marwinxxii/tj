package com.github.marwinxxii.tjournal.widgets

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.github.marwinxxii.tjournal.CompositeDiskStorage
import com.github.marwinxxii.tjournal.extensions.generator
import com.github.marwinxxii.tjournal.fragments.BaseFragment
import com.github.marwinxxii.tjournal.service.ArticlesService
import java.io.FileInputStream
import java.io.InputStream
import javax.inject.Inject

class ArticleWebViewController {
  val view: WebView
  val fragment: BaseFragment
  val service: ArticlesService

  @Inject
  constructor(view: WebView, fragment: BaseFragment,
    imageCache: CompositeDiskStorage, service: ArticlesService) {
    this.view = view
    this.view.setWebViewClient(ImageInterceptor(imageCache, service))
    this.fragment = fragment
    this.service = service
  }

  fun loadArticle(id: Int) {
    view.loadUrl("tjournal:$id")
  }
}

class ImageInterceptor(val imageCache: CompositeDiskStorage, val service: ArticlesService) : WebViewClient() {
  override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
    val url = request.url
    if (request.method == "GET") {
      val scheme = url.scheme
      when (scheme) {
        "http", "https" -> return tryLoadImage(url.toString())
        "tjournal" -> return loadArticle(url.toString())
      }
    }
    return null
  }

  override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? {
    if (url.startsWith("tjournal:")) {
      return loadArticle(url)
    }
    if (url.startsWith("http://") || url.startsWith("https://")) {
      return tryLoadImage(url)
    }
    return null
  }

  private fun loadArticle(url: String): WebResourceResponse {
    return WebResourceResponse("text/html", "utf-8", ArticleInputStream(
      generator {
        yieldReturn(articleHead)
        yieldReturn {
          service.getArticle(url.removePrefix("tjournal:").toInt())?.text ?: ""
        }
        yieldReturn("</body></html>")
      }
    ))
  }

  private fun tryLoadImage(url: String): WebResourceResponse? {
    val file = imageCache.getPermanent(url)
    if (file != null && file.exists() && !file.isDirectory) {
      return WebResourceResponse("image/jpeg", null, FileInputStream(file))
    }
    return null
  }
}

const val articleHead = "<!doctype html>" +
  "<html>" +
  "<head>" +
  "<meta charset=\"utf-8\">" +
  "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
  "<style>article img { max-width: 100%;}</style>" +
  "</head>" +
  "<body>"

class ArticleInputStream(stringsProvider: Iterable<String>) : InputStream() {
  private val strings = stringsProvider.iterator()
  private var position = 0
  private var currentByteRange = 0
  private var currentByteArray = ByteArray(0)

  override fun read(): Int {
    if (position >= currentByteRange) {
      if (!strings.hasNext()) {
        return -1
      }
      val newArray = strings.next().toByteArray(charset("utf-8"))
      currentByteRange = newArray.size
      currentByteArray = newArray
      position = 0
    }
    return currentByteArray[position++].toInt()
  }
}