package com.a6v.tjreader.fragments

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.a6v.tjreader.AppHelper
import com.a6v.tjreader.EventBus
import com.a6v.tjreader.R
import com.a6v.tjreader.activities.MainActivity
import com.a6v.tjreader.activities.ActivityRetainedState
import com.a6v.tjreader.entities.Article
import com.a6v.tjreader.entities.ArticlePreview
import com.a6v.tjreader.service.ArticleDownloader
import com.a6v.tjreader.service.ArticlesService
import com.a6v.tjreader.service.FeedSorting
import com.a6v.tjreader.service.FeedType
import com.a6v.tjreader.utils.logError
import com.a6v.tjreader.widgets.ArticlesAdapter
import com.a6v.tjreader.widgets.ReadButtonController
import com.a6v.tjreader.widgets.TempImagePresenter
import kotlinx.android.synthetic.main.fragment_article_list_read.*
import org.jetbrains.anko.toast
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.lang.kotlin.subscribeWith
import javax.inject.Inject

class FeedFragment : BaseFragment {
  @Inject lateinit var service: ArticlesService
  @Inject lateinit var imagePresenter: TempImagePresenter
  @Inject lateinit var articleDownloader: ArticleDownloader
  @Inject lateinit var eventBus: EventBus
  @Inject lateinit var retainedState: ActivityRetainedState

  lateinit var articlesRetainKey: String
  lateinit var articles: Observable<List<ArticlePreview>>

  constructor() {
  }

  override fun injectSelf() {
    (activity as MainActivity).component.inject(this)
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return inflater.inflate(R.layout.fragment_article_list_read, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    setTitle(0)
    article_list.layoutManager = LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false)
    val adapter = ArticlesAdapter(imagePresenter, eventBus)
    article_list.adapter = adapter
    val feedType = arguments.getSerializable(ARG_FEED_TYPE) as FeedType
    val sorting = arguments.getSerializable(ARG_SORTING) as FeedSorting
    articlesRetainKey = "feed-$feedType-$sorting"
    if (retainedState.containsKey(articlesRetainKey)) {
      articles = retainedState.get(articlesRetainKey) as Observable<List<ArticlePreview>>
    } else {
      articles = service.getArticles(0, feedType, sorting).retry(1).cache()
    }
    AppHelper.observeVersionChanged(activity)
      .subscribeWith {
        onNext {
          if (it) {
            activity.toast("Saved data cleared")
          }
        }
      onCompleted {
    articles
      .observeOn(AndroidSchedulers.mainThread())
      .compose(bindToLifecycle<List<ArticlePreview>>())
      .subscribeWith {
        onNext {
          adapter.items.addAll(it)
          adapter.notifyDataSetChanged()
        }
        onError {
          activity.toast("Error loading articles")//TODO
        }
      }
    }}

    ReadButtonController.run(service, this)

    ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
      override fun onMove(Rv: RecyclerView, vh: RecyclerView.ViewHolder, vh2: RecyclerView.ViewHolder): Boolean {
        throw UnsupportedOperationException()
      }

      override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
        val position = vh.adapterPosition
        articleDownloader.downloadArticle(adapter.items[position])
          .observeOn(AndroidSchedulers.mainThread())
          .toObservable()
          .cache()
          .compose(bindToLifecycle<Article>())
          .subscribeWith {
            onError {
              logError(it)
              activity.toast("Could not load article")
            }
          }
        adapter.items.removeAt(position)
        adapter.notifyItemRemoved(position)
      }
    }).attachToRecyclerView(article_list)

    articleDownloader.downloadPendingArticles().subscribe()//FIXME handle errors
  }

  override fun onSaveInstanceState(outState: Bundle?) {
    super.onSaveInstanceState(outState)
    retainedState.put(articlesRetainKey, articles)
  }

  private fun setTitle(count: Int) {
    if (count > 0) {
      activity.title = getString(R.string.feed) + ' ' + count
    } else {
      activity.title = getString(R.string.feed)
    }
  }

  companion object {
    const val ARG_FEED_TYPE = "feedType"
    const val ARG_SORTING = "sorting"

    fun create(feedType: FeedType, sorting: FeedSorting): FeedFragment {
      val fragment = FeedFragment()
      val args = Bundle()
      args.putSerializable(ARG_FEED_TYPE, feedType)
      args.putSerializable(ARG_SORTING, sorting)
      fragment.arguments = args
      return fragment
    }
  }
}