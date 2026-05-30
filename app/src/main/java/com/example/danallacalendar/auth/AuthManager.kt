package com.example.danallacalendar.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.navercorp.nid.NaverIdLoginSDK
import com.navercorp.nid.oauth.OAuthLoginCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object AuthManager {
    private const val TAG = "AuthManager"

    // Placeholder Developer Credentials - plug real values here!
    private const val GOOGLE_WEB_CLIENT_ID = "1055536440733-vabuh8j0aev10e5o9h0c2aev10e5o9h0.apps.googleusercontent.com"
    private const val NAVER_CLIENT_ID = "O5_bL4j0_NAVER_CLIENT_ID"
    private const val NAVER_CLIENT_SECRET = "NAVER_CLIENT_SECRET"
    private const val NAVER_CLIENT_NAME = "다날라 캘린더"

    fun isNaverConfigured(): Boolean {
        return NAVER_CLIENT_ID != "O5_bL4j0_NAVER_CLIENT_ID" && NAVER_CLIENT_SECRET != "NAVER_CLIENT_SECRET"
    }

    fun initialize(context: Context) {
        try {
            if (!isNaverConfigured()) {
                Log.w(TAG, "Naver Client ID is placeholder, skipping initialization.")
                return
            }
            // Initialize Naver Login SDK
            NaverIdLoginSDK.initialize(
                context.applicationContext,
                NAVER_CLIENT_ID,
                NAVER_CLIENT_SECRET,
                NAVER_CLIENT_NAME
            )
            Log.d(TAG, "Naver SDK Initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Naver SDK", e)
        }
    }

    suspend fun loginWithGoogle(
        context: Context,
        onSuccess: (name: String, email: String) -> Unit,
        onError: (message: String) -> Unit
    ) {
        withContext(Dispatchers.Main) {
            try {
                val credentialManager = CredentialManager.create(context)
                
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(GOOGLE_WEB_CLIENT_ID)
                    .setAutoSelectEnabled(true)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(context, request)
                val credential = result.credential

                if (credential is GoogleIdTokenCredential) {
                    val name = credential.displayName ?: "Google User"
                    val email = credential.id
                    onSuccess(name, email)
                } else {
                    onError("지원하지 않는 인증 타입입니다.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Google Login failed", e)
                onError(e.localizedMessage ?: "Google 로그인 실패")
            }
        }
    }

    fun loginWithNaver(
        context: Context,
        onSuccess: (name: String, email: String) -> Unit,
        onError: (message: String) -> Unit
    ) {
        val callback = object : OAuthLoginCallback {
            override fun onSuccess() {
                val accessToken = NaverIdLoginSDK.getAccessToken()
                if (accessToken != null) {
                    // Fetch user profile on background thread
                    CoroutineScope(Dispatchers.IO).launch {
                        fetchNaverProfile(accessToken, onSuccess, onError)
                    }
                } else {
                    onError("Access Token을 가져올 수 없습니다.")
                }
            }

            override fun onFailure(httpStatus: Int, message: String) {
                val errorCode = NaverIdLoginSDK.getLastErrorCode().code
                val errorDesc = NaverIdLoginSDK.getLastErrorDescription()
                onError("로그인 실패 ($errorCode): $errorDesc")
            }

            override fun onError(errorCode: Int, message: String) {
                onFailure(errorCode, message)
            }
        }
        
        NaverIdLoginSDK.authenticate(context, callback)
    }

    private suspend fun fetchNaverProfile(
        accessToken: String,
        onSuccess: (name: String, email: String) -> Unit,
        onError: (message: String) -> Unit
    ) {
        try {
            val url = URL("https://openapi.naver.com/v1/nid/me")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                val response = reader.readText()
                reader.close()
                
                val json = JSONObject(response)
                val responseObj = json.optJSONObject("response")
                if (responseObj != null) {
                    val name = responseObj.optString("nickname", responseObj.optString("name", "Naver User"))
                    val email = responseObj.optString("email", "")
                    withContext(Dispatchers.Main) {
                        onSuccess(name, email)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onError("프로필 정보를 파싱할 수 없습니다.")
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    onError("프로필 요청 실패: ${conn.responseCode}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Naver Profile Fetch failed", e)
            withContext(Dispatchers.Main) {
                onError("Naver API 호출 실패: ${e.localizedMessage}")
            }
        }
    }
}
