package com.moneyflow.util

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Turns an exception into a sentence a family member can act on (never raw English system text). */
object UserErrors {
  const val NO_INTERNET = "인터넷에 연결되어 있지 않아요. 와이파이나 모바일 데이터를 켠 뒤 다시 눌러 주세요."
  const val SLOW = "인터넷이 느려서 끝내지 못했어요. 잠시 후 다시 눌러 주세요."

  fun message(e: Throwable, fallback: String = "처리하지 못했어요."): String = when {
    e is UnknownHostException || e is java.net.ConnectException -> NO_INTERNET
    e is SocketTimeoutException -> SLOW
    e is IOException && e.message?.any { it in '가'..'힣' } != true -> NO_INTERNET
    // The app's own messages are Korean; anything else is system text the user can't use.
    e.message?.any { it in '가'..'힣' } == true -> e.message!!
    else -> "$fallback 계속되면 설정 → 문제 보고로 알려 주세요."
  }
}
