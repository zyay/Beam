package com.beammental.app.data

import android.content.Context

/** Simple service locator; initialized once in MainActivity. */
object Locator {
    lateinit var session: Session
        private set
    val api: Api by lazy { Api(session) }

    fun init(context: Context) {
        if (!::session.isInitialized) session = Session(context.applicationContext)
    }
}
