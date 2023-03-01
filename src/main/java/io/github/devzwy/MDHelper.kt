package io.github.devzwy

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 非单例设计，适配多应用，一个应用可以构造一个实体进行操作
 */
class MDHelper(
    /**
     * 全局请求的url 例如:https://xxx.ccc.cn
     */
    private val baseUrl: String,
    /**
     * 应用密钥
     */
    private val appKey: String,
    /**
     * 应用签名
     */
    private val sign: String,

    /**
     * 超时时间，秒
     */
    private val timeout: Long = 5,

    /**
     * 是否在查询结果中过滤掉为Null或为""的字段
     */
    private val isFilterNullOrEmptyParams: Boolean = true,

    /**
     * 不想看到的返回字段
     */
    private var filterKeys: ArrayList<String> = arrayListOf("caid", "ownerid", "autoid", "allowdelete", "controlpermissions")
) {


    /**
     * 在原有过滤字段中新增过滤字段
     */
    fun addFilterKey(key: String) {
        if (!filterKeys.contains(key)) {
            filterKeys.add(key)
        }
    }

    /**
     * 根据id查询一个实体，对应明道云api:getRowByIdPost
     * [tableName] 表名
     * [id] 行记录id
     * [enclosureKey] 附件字段别名，指定别名后会从附件中取出一张图片放在指定的别名中返回,否则将返回全部附件内容
     */
    fun getDataById(
        tableName: String, id: String, enclosureKey: String? = null
    ): String {

        val response = getRequestClient(timeout).newCall(
            Request.Builder().url("${baseUrl}/api/v2/open/worksheet/getRowByIdPost").post(
                hashMapOf(
                    "appKey" to appKey, "sign" to sign, "worksheetId" to tableName, "rowId" to id
                ).toJson().toRequestBody("application/json".toMediaTypeOrNull())
            ).build()
        ).execute()

        if (response.isSuccessful) {
            val jsonObject = JSON.parseObject(response.body?.string())
            if (jsonObject.getBoolean("success")) {
                //取出列表
                return jsonObject.getJSONObject("data").filterToMap(enclosureKey, filterKeys, isFilterNullOrEmptyParams).toJson()
            } else {
                throw MDException("操作失败,${jsonObject?.getString("error_msg")}(${jsonObject.getIntValue("error_code", -1)})")
            }
        } else {
            throw MDException(response.message)
        }
    }

    /**
     * 获取表记录数据,对应明道云api:getFilterRows
     * [tableName] 对应明道api文档中的worksheetId字段
     * [pageIndex] 请求的页数
     * [pageSize] 请求的条数 小于1000
     * [filters] 过滤参数，将防止在请求参数的filters字段下一并请求
     * [enclosureKey] 附件字段别名，指定别名后会从附件中取出一张图片放在指定的别名中返回,否则将返回全部附件内容
     * @return 解析后的json字符串
     */
    fun getListData(
        tableName: String, pageIndex: Int = 1, pageSize: Int = 1000, filters: List<Any>? = null, enclosureKey: String? = null
    ): String {

        if (pageSize > 1000) {
            throw MDException("不支持单次查询大于1000条记录")
        }

        val response = getRequestClient(timeout).newCall(
            Request.Builder().url("${baseUrl}/api/v2/open/worksheet/getFilterRows").post(
                hashMapOf(
                    "appKey" to appKey, "sign" to sign, "worksheetId" to tableName, "pageSize" to pageSize, "pageIndex" to pageIndex, "filters" to filters
                ).toJson().toRequestBody("application/json".toMediaTypeOrNull())
            ).build()
        ).execute()

        if (response.isSuccessful) {
            val jsonObject = JSON.parseObject(response.body?.string())
            if (jsonObject.getBoolean("success")) {
                //取出列表
                val data = jsonObject.getJSONObject("data")
                if (data != null) {
                    val total = data.getIntValue("total", 0)
                    val dataArray = data.getJSONArray("rows")
                    return mapOf<String, Any>("total" to total, "data" to arrayListOf<HashMap<String, Any?>>().apply {
                        dataArray?.forEach {
                            (it as? JSONObject)?.filterToMap(enclosureKey, filterKeys, isFilterNullOrEmptyParams)?.let {
                                add(it)
                            }
                        }
                    }).toJson()
                } else {
                    throw MDException("列表异常")
                }
            } else {
                throw MDException("操作失败,${jsonObject?.getString("error_msg")}(${jsonObject.getIntValue("error_code", -1)})")
            }
        } else {
            throw MDException(response.message)
        }

    }

    /**
     * 插入记录 未抛出异常就是写入成功了
     * [tableName] 对应明道api文档中的worksheetId字段
     * [data] 字段部分 为空报错
     */
    fun addRow(tableName: String, data: List<MDRowBean>) {

        if (data.isEmpty()) {
            throw NullPointerException("字段不能为空")
        }

        //组装请求参数：
        val reqDataList = arrayListOf<HashMap<String, Any>>()
        data.forEach {
            if (it.value!=null && it.value.isNotEmpty()) {
                reqDataList.add(hashMapOf("controlId" to it.key, "value" to it.value))
            }
        }

        val response = getRequestClient(timeout).newCall(
            Request.Builder().url("${baseUrl}/api/v2/open/worksheet/addRow").post(
                hashMapOf(
                    "appKey" to appKey, "sign" to sign, "worksheetId" to tableName, "controls" to reqDataList
                ).toJson().also {
                    println("最终请求数据:${it}")
                }.toRequestBody("application/json".toMediaTypeOrNull())
            ).build()
        ).execute()

        if (response.isSuccessful) {
            val jsonObject = JSON.parseObject(response.body?.string())
            if (!jsonObject.getBoolean("success")) {
//                if (retryTimes>0 && retrys<retryTimes){
//                    retrys++
//                    addRow(tableName,data,retryTimes)
//                    println("操作失败:${jsonObject?.getString("error_msg")},正在重试...")
//                }else{
                    throw MDException("操作失败,${jsonObject?.getString("error_msg")}(${jsonObject.getIntValue("error_code", -1)})")
//                }
            }
        } else {
//            if (retryTimes>0 && retrys<retryTimes){
//                retrys++
//                println("请求失败:${response.message},正在重试...")
//                addRow(tableName,data,retryTimes)
//            }else{
                throw MDException(response.message)
//            }
        }
    }


}
