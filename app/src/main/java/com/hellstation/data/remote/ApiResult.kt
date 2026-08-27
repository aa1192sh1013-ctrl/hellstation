package com.hellstation.data.remote

import com.hellstation.domain.model.UnavailableReason

/**
 * 통신 결과.
 *
 * **이 계층 밖으로 예외가 나가지 않습니다.** 지하철 안에서 신호가 끊기는 것은
 * 예외 상황이 아니라 정상 상황이라, 예외로 다루면 화면이 계속 멈춥니다.
 */
sealed interface ApiResult<out T> {

    data class Success<T>(val value: T) : ApiResult<T>

    /**
     * @param reason  화면 문구를 고르는 데 쓰는 분류
     * @param message 개발자용 설명. 사용자에게 그대로 보여주지 마세요
     */
    data class Failure(
        val reason: UnavailableReason,
        val message: String,
        val cause: Throwable? = null,
    ) : ApiResult<Nothing>

    fun valueOrNull(): T? = (this as? Success)?.value

    fun <R> map(transform: (T) -> R): ApiResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }
}

/**
 * 서울 열린데이터광장 공통 응답 코드.
 *
 * 두 API 모두 HTTP 200을 주면서 본문에 에러 코드를 담아 보내는 경우가 있어서,
 * HTTP 상태만 봐서는 성공 여부를 알 수 없습니다.
 */
object SeoulApiCode {
    const val OK = "INFO-000"

    /** 조회 결과가 0건 — 실패가 아닙니다 */
    const val NO_DATA = "INFO-200"

    /** 인증키가 유효하지 않음 */
    const val INVALID_KEY = "ERROR-301"

    /** 인증키 미등록 */
    const val UNREGISTERED_KEY = "ERROR-300"

    /**
     * 코드를 보고 어떤 실패인지 분류합니다.
     * @return 성공이면 null
     */
    fun toReason(code: String?): UnavailableReason? = when {
        code == null -> UnavailableReason.NO_DATA
        code == OK -> null
        code == NO_DATA -> UnavailableReason.NO_DATA
        code.startsWith("ERROR-3") -> UnavailableReason.NO_KEY
        else -> UnavailableReason.NO_DATA
    }
}
