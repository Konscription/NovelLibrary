package io.github.gmathi.novellibrary.model.database

import java.io.Serializable

data class WebPage(var url: String, var chapter: String,
                   var novelId: Long = -1L, var orderId: Long = -1L,
                   var translatorSourceId: Long = -1L) : Serializable {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val webPage = other as WebPage?
        return if (webPage != null) url == webPage.url else false
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + chapter.hashCode()
        result = 31 * result + novelId.hashCode()
        result = 31 * result + translatorSourceId.hashCode()
        result = 31 * result + orderId.hashCode()
        return result
    }

    override fun toString(): String {
        return "WebPage(url='$url', chapter='$chapter', novelId=$novelId, sourceId=$translatorSourceId, orderId=$orderId)"
    }

}
