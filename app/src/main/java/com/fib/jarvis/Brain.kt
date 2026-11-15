package com.fib.jarvis

import android.content.Context

object Brain {
    /** One entry point for typed and spoken input. */
    suspend fun reply(c: Context, text: String): String {
        FibState.remember(c, Msg(true, text))
        val r = Router.handle(c, text) ?: Ai.answer(c, text)
        FibState.remember(c, Msg(false, r))
        return r
    }
}
