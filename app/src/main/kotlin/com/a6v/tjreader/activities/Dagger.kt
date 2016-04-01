package com.a6v.tjreader.activities

import android.app.Activity
import dagger.Module
import dagger.Provides
import dagger.Subcomponent
import javax.inject.Scope

/**
 * Created by alexey on 20.02.16.
 */

@PerActivity
@Subcomponent(modules = arrayOf(ActivityModule::class))
interface AbstractActivityComponent {
  fun activity(): Activity
}

@Module
class ActivityModule(val activity: Activity) {
  @Provides @PerActivity
  fun activity(): Activity {
    return activity
  }
}

@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class PerActivity