package com.dessmonitor.smartess.data.api

import android.util.Log
import com.dessmonitor.smartess.data.models.DataPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class DessMonitorAPI(
    var username: String = "",
    var password: String = "",
    var companyKey: String = "bnrl_frRFjEz8Mkn"
) {
    private val baseUrl = "https://api.dessmonitor.com/public/"
    var token: String? = null
    var secret: String? = null
    private var tokenExpire: Long? = null

    private val client = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "DessMonitorAPI"
    }

    private fun sha1(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    private fun generateSignature(salt: String, actionString: String): String {
        return if (!token.isNullOrEmpty() && !secret.isNullOrEmpty()) {
            val signatureString = "$salt$secret$token$actionString"
            sha1(signatureString)
        } else {
            val pwdSha1 = sha1(password)
            val signatureString = "$salt$pwdSha1$actionString"
            sha1(signatureString)
        }
    }

    private fun isTokenExpired(): Boolean {
        val expire = tokenExpire ?: return true
        if (token == null) return true
        val currentTime = System.currentTimeMillis() / 1000
        return currentTime >= expire
    }

    private fun buildActionString(action: String, params: Map<String, Any>?): String {
        val sb = StringBuilder("&action=").append(action)
        params?.forEach { (key, value) ->
            sb.append('&').append(key).append('=').append(value)
        }
        return sb.toString()
    }

    private suspend fun makeRequest(action: String, params: Map<String, Any>? = null): JSONObject = withContext(Dispatchers.IO) {
        if (action != "authSource" && isTokenExpired()) {
            Log.d(TAG, "Token expired, re-authenticating...")
            authenticate()
        }

        val salt = System.currentTimeMillis().toString()
        val actionString = buildActionString(action, params)
        val signature = generateSignature(salt, actionString)

        val urlBuilder = StringBuilder(baseUrl)
            .append("?sign=").append(signature)
            .append("&salt=").append(salt)

        if (!token.isNullOrEmpty() && action != "authSource") {
            urlBuilder.append("&token=").append(token)
        }
        urlBuilder.append(actionString)

        val request = Request.Builder()
            .url(urlBuilder.toString())
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    Log.e(TAG, "HTTP error $action: $code")
                    throw IOException("HTTP $code")
                }
                val responseBody = response.body?.string() ?: throw IOException("Empty response body")
                val json = JSONObject(responseBody)
                val err = json.optInt("err", 0)
                if (err != 0) {
                    val desc = json.optString("desc", "API error $err")
                    // Handle session expiry specifically if the API supports it
                    if (err == 10001 || desc.contains("token", ignoreCase = true)) {
                        tokenExpire = 0
                    }
                    Log.e(TAG, "API error $action: $desc")
                    throw IOException(desc)
                }
                json
            }
        } catch (e: Exception) {
            if (e !is IOException) {
                Log.e(TAG, "Unexpected error in $action", e)
                throw IOException(e.message, e)
            }
            throw e
        }
    }

    suspend fun authenticate(): Boolean = withContext(Dispatchers.IO) {
        try {
            token = null
            secret = null
            tokenExpire = null

            val authParams = mapOf(
                "usr" to username,
                "company-key" to companyKey,
                "source" to "1",
                "_app_client_" to "web",
                "_app_id_" to "ha-dessmonitor",
                "_app_version_" to "2.2.0"
            )

            val response = makeRequest("authSource", authParams)
            val data = response.optJSONObject("dat")
            if (data != null) {
                token = data.optString("token")
                secret = data.optString("secret")
                val expire = data.optLong("expire", 0L)
                if (expire > 0) {
                    tokenExpire = (System.currentTimeMillis() / 1000) + expire
                }
                Log.i(TAG, "Authentication successful")
                return@withContext true
            }
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Authentication failed", e)
            throw e
        }
    }

    suspend fun queryPlants(): List<JSONObject> = withContext(Dispatchers.IO) {
        val response = makeRequest("queryPlants", mapOf("pagesize" to 50))
        val dat = response.optJSONObject("dat")
        val plantsArray = dat?.optJSONArray("plant") ?: return@withContext emptyList()
        List(plantsArray.length()) { plantsArray.getJSONObject(it) }
    }

    suspend fun queryCollectorsForProject(pid: Long): List<JSONObject> = withContext(Dispatchers.IO) {
        val response = makeRequest("webQueryCollectorsEs", mapOf("pid" to pid, "page" to 0, "pagesize" to 50))
        val dat = response.optJSONObject("dat")
        val colArray = dat?.optJSONArray("collector") ?: return@withContext emptyList()
        List(colArray.length()) { colArray.getJSONObject(it) }
    }

    suspend fun queryCollectorDevices(pn: String): List<JSONObject> = withContext(Dispatchers.IO) {
        val response = makeRequest("queryCollectorDevices", mapOf("pn" to pn))
        val dat = response.optJSONObject("dat")
        val devArray = dat?.optJSONArray("dev") ?: return@withContext emptyList()
        List(devArray.length()) { devArray.getJSONObject(it) }
    }

    suspend fun queryDeviceLastData(pn: String, devcode: Int, devaddr: Int, sn: String): List<DataPoint> = withContext(Dispatchers.IO) {
        val params = mapOf(
            "pn" to pn,
            "devcode" to devcode,
            "devaddr" to devaddr,
            "sn" to sn,
            "i18n" to "en"
        )
        val response = try { makeRequest("queryDeviceLastData", params) } catch (_: Exception) { return@withContext emptyList() }
        val dat = response.optJSONObject("dat")
        val datArray = dat?.optJSONArray("list") ?: dat?.optJSONArray("data") ?: response.optJSONArray("dat")
        if (datArray == null) return@withContext emptyList()

        val list = ArrayList<DataPoint>(datArray.length())
        for (i in 0 until datArray.length()) {
            val item = datArray.getJSONObject(i)
            val title = listOf("title", "name", "label", "des", "desc").map { item.optString(it) }.firstOrNull { it.isNotEmpty() } ?: ""
            val value = listOf("val", "value", "v").map { item.opt(it) }.firstOrNull { it != null } ?: ""
            val unit = item.optString("unit")
            if (title.isNotEmpty()) {
                list.add(DataPoint(title = title, value = value, unit = if (unit.isEmpty()) null else unit))
            }
        }
        list
    }

    suspend fun queryDeviceParameters(pn: String, devcode: Int, devaddr: Int, sn: String): List<DataPoint> = withContext(Dispatchers.IO) {
        val params = mapOf(
            "pn" to pn,
            "devcode" to devcode,
            "devaddr" to devaddr,
            "sn" to sn,
            "i18n" to "en_US",
            "source" to "1"
        )
        val response = try { makeRequest("queryDeviceParsEs", params) } catch (_: Exception) { return@withContext emptyList() }
        val dat = response.optJSONObject("dat")
        val paramsArray = dat?.optJSONArray("parameter") ?: dat?.optJSONArray("list") ?: dat?.optJSONArray("data") ?: return@withContext emptyList()

        val list = ArrayList<DataPoint>(paramsArray.length())
        for (i in 0 until paramsArray.length()) {
            val item = paramsArray.getJSONObject(i)
            val name = listOf("name", "title", "label").map { item.optString(it) }.firstOrNull { it.isNotEmpty() } ?: ""
            val value = listOf("val", "value", "v").map { item.opt(it) }.firstOrNull { it != null } ?: ""
            val unit = item.optString("unit", "")
            val id = listOf("par", "id", "key").map { item.optString(it) }.firstOrNull { it.isNotEmpty() } ?: ""
            if (name.isNotEmpty()) {
                list.add(DataPoint(title = name, value = value, unit = if (unit.isEmpty()) null else unit, id = id))
            }
        }
        list
    }

    suspend fun webQueryDeviceEs(pid: Long): List<JSONObject> = withContext(Dispatchers.IO) {
        val response = makeRequest("webQueryDeviceEs", mapOf("pid" to pid, "pagesize" to 50))
        val dat = response.optJSONObject("dat")
        val devices = dat?.optJSONArray("device") ?: return@withContext emptyList()
        List(devices.length()) { devices.getJSONObject(it) }
    }

    suspend fun queryDeviceHistoryData(pn: String, devcode: Int, devaddr: Int, sn: String, date: String, parameter: String? = null): JSONObject = withContext(Dispatchers.IO) {
        val params = mutableMapOf<String, Any>(
            "pn" to pn,
            "devcode" to devcode,
            "devaddr" to devaddr,
            "sn" to sn,
            "date" to date,
            "i18n" to "en"
        )
        
        val actions = if (parameter != null) {
            listOf("querySPDeviceKeyParameterOneDay")
        } else {
            listOf(
                "queryDeviceDataOneDay",
                "queryDeviceDataOneDayPaging",
                "queryDeviceDataEs",
                "webQueryHistoryData",
                "querySPDeviceHistoryData"
            )
        }
        
        for (action in actions) {
            try {
                val parameterKeys = if (parameter != null) listOf("parameter", "par", "id") else listOf("")
                
                for (paramKey in parameterKeys) {
                    val currentParams = params.toMutableMap()
                    if (paramKey.isNotEmpty()) {
                        currentParams[paramKey] = parameter!!
                    }
                    
                    if (action == "queryDeviceDataOneDayPaging") {
                        currentParams["page"] = 0
                        currentParams["pagesize"] = 100
                    }

                    if (action.contains("web")) {
                        currentParams["beginDate"] = date
                        currentParams["endDate"] = date
                    }

                    val response = makeRequest(action, currentParams)
                    val dat = response.optJSONObject("dat")
                    
                    val hasItems = (dat?.optJSONArray("detail")?.length() ?: 0) > 0 ||
                                  (dat?.optJSONArray("list")?.length() ?: 0) > 0 ||
                                  (dat?.optJSONArray("row")?.length() ?: 0) > 0 ||
                                  (dat?.optJSONArray("data")?.length() ?: 0) > 0 ||
                                  (response.optJSONArray("dat")?.length() ?: 0) > 0
                    
                    if (hasItems) {
                        return@withContext response
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Action $action failed for $parameter: ${e.message}")
            }
        }
        throw IOException("Failed to fetch history data")
    }

    suspend fun queryDeviceControlFields(pn: String, devcode: Int, devaddr: Int, sn: String): JSONObject = withContext(Dispatchers.IO) {
        val params = mapOf(
            "pn" to pn,
            "devcode" to devcode,
            "devaddr" to devaddr,
            "sn" to sn,
            "i18n" to "en_US",
            "source" to "1"
        )
        makeRequest("queryDeviceCtrlField", params)
    }

    suspend fun queryDeviceCtrlValue(pn: String, devcode: Int, devaddr: Int, sn: String, fieldId: String): JSONObject = withContext(Dispatchers.IO) {
        val params = mapOf(
            "pn" to pn,
            "devcode" to devcode,
            "devaddr" to devaddr,
            "sn" to sn,
            "id" to fieldId,
            "i18n" to "en_US",
            "source" to "1"
        )
        makeRequest("queryDeviceCtrlValue", params)
    }

    suspend fun queryDeviceAlarms(pn: String, devcode: Int, devaddr: Int, sn: String, page: Int = 0, startDate: String? = null, endDate: String? = null): JSONObject = withContext(Dispatchers.IO) {
        val params = mutableMapOf<String, Any>(
            "pn" to pn,
            "devcode" to devcode,
            "devaddr" to devaddr,
            "sn" to sn,
            "page" to page,
            "pagesize" to 50,
            "mode" to "strict",
            "i18n" to "en"
        )
        if (startDate != null) params["sdate"] = startDate
        if (endDate != null) params["edate"] = endDate
        
        makeRequest("queryDeviceWarning", params)
    }

    suspend fun setDeviceControlValue(pn: String, devcode: Int, devaddr: Int, sn: String, fieldId: String, value: String): JSONObject = withContext(Dispatchers.IO) {
        val params = mapOf(
            "pn" to pn,
            "devcode" to devcode,
            "devaddr" to devaddr,
            "sn" to sn,
            "id" to fieldId,
            "val" to value,
            "i18n" to "en_US",
            "source" to "1"
        )
        makeRequest("ctrlDevice", params)
    }
}
