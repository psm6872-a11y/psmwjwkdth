package com.example.danallacalendar.estimate

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.client.http.FileContent
import com.google.api.services.drive.model.File as DriveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections

object GoogleDriveHelper {
    private const val TAG = "GoogleDriveHelper"
    private const val FOLDER_NAME = "다날라 견적"
    private const val PREFS_NAME = "drive_folder_prefs"
    private const val KEY_PARENT_FOLDER_ID = "parent_folder_id"
    private const val KEY_PARENT_FOLDER_EMAIL = "parent_folder_email"

    /**
     * Google Sign-In Client를 생성합니다.
     */
    fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    /**
     * 현재 로그인된 구글 계정을 가져옵니다.
     */
    fun getSignedInAccount(context: Context): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    /**
     * 현재 계정이 Drive 스코프 권한을 실제로 가지고 있는지 확인합니다.
     */
    fun hasDrivePermission(context: Context): Boolean {
        val account = getSignedInAccount(context) ?: return false
        return GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))
    }

    /**
     * GoogleSignInAccount를 기반으로 Drive 서비스 인스턴스를 빌드합니다.
     */
    private fun getDriveService(context: Context, account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            Collections.singleton(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = account.account
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("DanallaCalendar")
            .build()
    }

    /**
     * SharedPreferences에 캐시된 폴더 ID를 가져옵니다.
     * 계정 이메일이 다르면 무효화합니다.
     */
    private fun getCachedParentFolderId(context: Context, accountEmail: String?): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedEmail = prefs.getString(KEY_PARENT_FOLDER_EMAIL, null)
        if (cachedEmail != accountEmail) {
            // 계정이 바뀌었으면 캐시 무효화
            prefs.edit().remove(KEY_PARENT_FOLDER_ID).remove(KEY_PARENT_FOLDER_EMAIL).apply()
            return null
        }
        return prefs.getString(KEY_PARENT_FOLDER_ID, null)
    }

    private fun cacheParentFolderId(context: Context, folderId: String, accountEmail: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_PARENT_FOLDER_ID, folderId)
            .putString(KEY_PARENT_FOLDER_EMAIL, accountEmail ?: "")
            .apply()
    }

    private fun clearCachedParentFolderId(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.remove(KEY_PARENT_FOLDER_ID)
        editor.remove(KEY_PARENT_FOLDER_EMAIL)
        
        val allKeys = prefs.all
        for (key in allKeys.keys) {
            if (key.startsWith("sub_")) {
                editor.remove(key)
            }
        }
        editor.apply()
    }

    /**
     * 최상위 "다날라 견적" 폴더를 가져오거나 생성합니다.
     * 캐시된 ID가 있으면 바로 사용하고, 없으면 생성 후 캐시합니다.
     */
    private fun getOrCreateParentFolder(context: Context, driveService: Drive, accountEmail: String?): String {
        // 1. 캐시된 폴더 ID가 있으면 바로 반환 (API 호출 없음)
        val cachedId = getCachedParentFolderId(context, accountEmail)
        if (!cachedId.isNullOrBlank()) {
            Log.d(TAG, "Using cached parent folder ID: $cachedId")
            return cachedId
        }

        // 2. 캐시 없음 → 폴더 검색 (DRIVE_FILE scope는 앱이 만든 파일만 검색됨)
        try {
            val query = "mimeType = 'application/vnd.google-apps.folder' and name = '$FOLDER_NAME' and trashed = false"
            val resultList = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            val files = resultList.files
            if (!files.isNullOrEmpty()) {
                val folderId = files[0].id
                cacheParentFolderId(context, folderId, accountEmail)
                Log.d(TAG, "Found existing parent folder: $folderId")
                return folderId
            }
        } catch (e: Exception) {
            Log.w(TAG, "Folder search failed (may be scope limitation), will create new: ${e.message}")
        }

        // 3. 폴더가 없으면 새로 생성
        val folderMetadata = DriveFile().apply {
            name = FOLDER_NAME
            mimeType = "application/vnd.google-apps.folder"
        }
        val folder = driveService.files().create(folderMetadata)
            .setFields("id")
            .execute()
        val newId = folder.id
        cacheParentFolderId(context, newId, accountEmail)
        Log.d(TAG, "Created new parent folder: $newId")
        return newId
    }

    /**
     * 하위 월별 폴더(예: "2026년 06월")를 가져오거나 생성합니다.
     */
    private fun getOrCreateSubFolder(context: Context, driveService: Drive, parentId: String, subFolderName: String, accountEmail: String?): String {
        val subKey = "sub_${subFolderName.replace(" ", "_")}_${accountEmail}"
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        if (!accountEmail.isNullOrBlank()) {
            val cachedSubId = prefs.getString(subKey, null)
            if (!cachedSubId.isNullOrBlank()) {
                Log.d(TAG, "Using cached sub folder ID for $subFolderName: $cachedSubId")
                return cachedSubId
            }
        }

        try {
            val query = "mimeType = 'application/vnd.google-apps.folder' and name = '$subFolderName' and '$parentId' in parents and trashed = false"
            val resultList = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            val files = resultList.files
            if (!files.isNullOrEmpty()) {
                val subId = files[0].id
                if (!accountEmail.isNullOrBlank()) {
                    prefs.edit().putString(subKey, subId).apply()
                }
                return subId
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sub-folder search failed, will create new: ${e.message}")
        }

        val folderMetadata = DriveFile().apply {
            name = subFolderName
            mimeType = "application/vnd.google-apps.folder"
            parents = listOf(parentId)
        }
        val folder = driveService.files().create(folderMetadata)
            .setFields("id")
            .execute()
        val newSubId = folder.id
        if (!accountEmail.isNullOrBlank()) {
            prefs.edit().putString(subKey, newSubId).apply()
        }
        Log.d(TAG, "Created new sub folder $subFolderName: $newSubId")
        return newSubId
    }

    sealed class UploadResult {
        data class Success(val fileId: String) : UploadResult()
        object NoPermission : UploadResult()
        object UserRecoverable : UploadResult()
        data class Failure(val error: String) : UploadResult()
    }

    /**
     * JPG 파일을 구글 드라이브에 업로드합니다.
     */
    suspend fun uploadEstimateJpgWithResult(
        context: Context,
        account: GoogleSignInAccount,
        file: File,
        fileName: String,
        estimateDate: String
    ): UploadResult = withContext(Dispatchers.IO) {
        // Drive scope 권한 확인
        if (!GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))) {
            Log.w(TAG, "Drive permission not granted for account: ${account.email}")
            return@withContext UploadResult.NoPermission
        }

        try {
            val driveService = getDriveService(context, account)
            val parentId = getOrCreateParentFolder(context, driveService, account.email)

            val subFolderName = try {
                val parts = estimateDate.split("-")
                if (parts.size >= 2) "${parts[0]}년 ${parts[1]}월" else "기타 견적"
            } catch (e: Exception) { "기타 견적" }

            val folderId = getOrCreateSubFolder(context, driveService, parentId, subFolderName, account.email)

            val fileMetadata = DriveFile().apply {
                name = fileName
                parents = listOf(folderId)
            }

            val mediaContent = FileContent("image/jpeg", file)
            val uploadedFile = driveService.files().create(fileMetadata, mediaContent)
                .setFields("id, webViewLink")
                .execute()

            Log.d(TAG, "Uploaded to $subFolderName. ID: ${uploadedFile.id}")
            UploadResult.Success(uploadedFile.id)

        } catch (e: UserRecoverableAuthIOException) {
            Log.w(TAG, "Drive upload requires user action: ${e.message}")
            // 폴더 ID 캐시 초기화 (재로그인 시 새로 받아야 함)
            clearCachedParentFolderId(context)
            UploadResult.UserRecoverable
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload to Google Drive: ${e.message}", e)
            // 폴더 ID 캐시 초기화 (다음 시도 시 다시 생성)
            clearCachedParentFolderId(context)
            UploadResult.Failure(e.message ?: "Unknown error")
        }
    }

    // 하위 호환성 유지용 래퍼
    suspend fun uploadEstimateJpg(
        context: Context,
        account: GoogleSignInAccount,
        file: File,
        fileName: String,
        estimateDate: String
    ): String? = withContext(Dispatchers.IO) {
        when (val result = uploadEstimateJpgWithResult(context, account, file, fileName, estimateDate)) {
            is UploadResult.Success -> result.fileId
            else -> null
        }
    }
}
