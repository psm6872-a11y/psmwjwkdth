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

    /**
     * Google Sign-In Client를 생성합니다.
     * DriveScopes.DRIVE_FILE 스코프를 추가하여 앱이 생성하거나 연 파일에만 접근 가능하도록 제한합니다.
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
     * 최상위 "다날라 견적" 폴더가 존재하는지 검색하고, 없으면 생성하여 폴더 ID를 반환합니다.
     */
    private fun getOrCreateParentFolder(driveService: Drive): String {
        val query = "mimeType = 'application/vnd.google-apps.folder' and name = '$FOLDER_NAME' and trashed = false"
        val resultList = driveService.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()

        val files = resultList.files
        if (!files.isNullOrEmpty()) {
            return files[0].id
        }

        val folderMetadata = DriveFile().apply {
            name = FOLDER_NAME
            mimeType = "application/vnd.google-apps.folder"
        }
        val folder = driveService.files().create(folderMetadata)
            .setFields("id")
            .execute()
        return folder.id
    }

    /**
     * 최상위 폴더 하위에 특정 월별 폴더(예: "2026년 06월")가 존재하는지 검색하고, 없으면 생성하여 반환합니다.
     */
    private fun getOrCreateSubFolder(driveService: Drive, parentId: String, subFolderName: String): String {
        val query = "mimeType = 'application/vnd.google-apps.folder' and name = '$subFolderName' and '$parentId' in parents and trashed = false"
        val resultList = driveService.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()

        val files = resultList.files
        if (!files.isNullOrEmpty()) {
            return files[0].id
        }

        val folderMetadata = DriveFile().apply {
            name = subFolderName
            mimeType = "application/vnd.google-apps.folder"
            parents = listOf(parentId)
        }
        val folder = driveService.files().create(folderMetadata)
            .setFields("id")
            .execute()
        return folder.id
    }

    /**
     * 지정된 이미지 파일(JPG)을 구글 드라이브 폴더에 백그라운드 스레드에서 업로드합니다.
     */
    suspend fun uploadEstimateJpg(context: Context, account: GoogleSignInAccount, file: File, fileName: String, estimateDate: String): String? = withContext(Dispatchers.IO) {
        try {
            val driveService = getDriveService(context, account)
            val parentId = getOrCreateParentFolder(driveService)

            // 날짜 기반 하위 폴더명 도출 (예: "2026-06-13" -> "2026년 06월")
            val subFolderName = try {
                val parts = estimateDate.split("-")
                if (parts.size >= 2) {
                    "${parts[0]}년 ${parts[1]}월"
                } else {
                    "기타 견적"
                }
            } catch (e: Exception) {
                "기타 견적"
            }

            val folderId = getOrCreateSubFolder(driveService, parentId, subFolderName)

            // 파일 메타데이터 세팅
            val fileMetadata = DriveFile().apply {
                name = fileName
                parents = listOf(folderId)
            }

            // 파일 콘텐츠 세팅
            val mediaContent = FileContent("image/jpeg", file)

            // 파일 업로드 실행
            val uploadedFile = driveService.files().create(fileMetadata, mediaContent)
                .setFields("id, webViewLink")
                .execute()

            Log.d(TAG, "File uploaded successfully inside $subFolderName. ID: ${uploadedFile.id}")
            uploadedFile.id
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload file to Google Drive", e)
            null
        }
    }
}
