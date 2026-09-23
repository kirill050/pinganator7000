package dev.pinganator.widget

import android.content.Intent
import android.widget.RemoteViewsService

class PingListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        PingListFactory(applicationContext)
}
