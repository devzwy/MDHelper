package io.github.devzwy

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONObject
import okhttp3.OkHttpClient
import java.lang.RuntimeException
import java.time.Duration

private var okHttpClient: OkHttpClient? = null

/**
 * 全局公用一个请求对象
 */
fun MDHelper.getRequestClient(timeout: Long) = okHttpClient ?: OkHttpClient.Builder().also {
    Duration.ofSeconds(timeout).apply {
        it.connectTimeout(this)
        it.readTimeout(this)
        it.writeTimeout(this)
    }
}.build().also {
    okHttpClient = it
}

/**
 * 任意对象转json字符串
 */
fun Any.toJson() = JSON.toJSONString(this)

class MDException( msg: String? = null) : RuntimeException(msg)

private data class Image(val DownloadUrl: String? = null)

fun JSONObject.filterToMap(enclosureKey: String? = null, filterKeys: ArrayList<String>, isFilterNullOrEmptyParams: Boolean) = hashMapOf<String, Any?>().also {
    this.forEach { k, v ->
        if (!filterKeys.contains(k)) {
            if (k == "rowid") {
                it.put("id", v)
            } else if (k == "utime") {
                if (canPut(v, isFilterNullOrEmptyParams)){
                    it.put("updateTime", v)
                }
            } else if (k == "ctime") {
                if (canPut(v, isFilterNullOrEmptyParams)){
                    it.put("createTime", v)
                }
            } else if (enclosureKey != null && k == enclosureKey) {
                v?.let { value ->
                    //附件
                    val arr = JSON.parseArray(value.toString())
                    if (!arr.isNullOrEmpty()) {
                        JSON.parseObject(arr[0].toJson(), Image::class.java)?.let { image ->
                            image.DownloadUrl?.let { url ->
                                it.put(enclosureKey, url)
                            }
                        }
                    }
                }
            } else {
                if (canPut(v, isFilterNullOrEmptyParams)){
                    it.put(k, v)
                }
            }
        }
    }
}

//写入实体
data class MDRowBean(val key:String,val value:String)

private fun canPut(value: Any?, isFilterNullOrEmptyParams: Boolean): Boolean {
    return if (isFilterNullOrEmptyParams) value != null && value.toString().isNotEmpty() else true
}

