package com.prof18.feedflow.shared.presentation

import co.touchlab.kermit.Logger
import com.dropbox.core.DbxAppInfo
import com.dropbox.core.DbxPKCEWebAuth
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.DbxWebAuth
import com.dropbox.core.TokenAccessType
import com.dropbox.core.oauth.DbxCredential
import com.prof18.feedflow.core.model.DropboxConnectionUiState
import com.prof18.feedflow.core.model.DropboxSynMessages
import com.prof18.feedflow.core.model.DropboxSyncUIState
import com.prof18.feedflow.feedsync.dropbox.DropboxDataSource
import com.prof18.feedflow.feedsync.dropbox.DropboxException
import com.prof18.feedflow.feedsync.dropbox.DropboxSettings
import com.prof18.feedflow.feedsync.dropbox.DropboxStringCredentials
import com.prof18.feedflow.shared.domain.DateFormatter
import com.prof18.feedflow.shared.domain.feed.retriever.FeedRetrieverRepository
import com.prof18.feedflow.shared.domain.feedsync.FeedSyncRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import java.util.Properties

class DropboxSyncViewModel internal constructor(
    private val logger: Logger,
    private val dropboxSettings: DropboxSettings,
    private val dropboxDataSource: DropboxDataSource,
    private val feedSyncRepository: FeedSyncRepository,
    private val dateFormatter: DateFormatter,
    private val feedRetrieverRepository: FeedRetrieverRepository,
) : BaseViewModel() {

    private var pkceWebAuth: DbxPKCEWebAuth? = null
    private var appInfo: DbxAppInfo? = null

    private val dropboxSyncUiMutableState =
        MutableStateFlow<DropboxConnectionUiState>(DropboxConnectionUiState.Unlinked)
    val dropboxConnectionUiState: StateFlow<DropboxConnectionUiState> = dropboxSyncUiMutableState.asStateFlow()

    private val dropboxSyncMessageMutableState = MutableSharedFlow<DropboxSynMessages>()
    val dropboxSyncMessageState: SharedFlow<DropboxSynMessages> = dropboxSyncMessageMutableState.asSharedFlow()

    init {
        restoreDropboxAuth()
    }

    fun startDropboxAuthFlow() {
        scope.launch {
            val properties = Properties()
            val propsFile = DropboxSyncViewModel::class.java.classLoader?.getResourceAsStream("props.properties")
                ?: InputStream.nullInputStream()
            properties.load(propsFile)

            val key = properties["dropbox_key"]
                ?.toString()

            val appInfoJson = """
                  {
                    "key": "$key"
                  }
            """.trimIndent()

            try {
                val appInfo = DbxAppInfo.Reader.readFully(appInfoJson)
                this@DropboxSyncViewModel.appInfo = appInfo

                val requestConfig = DbxRequestConfig("feedflowapp")
                val appInfoWithoutSecret = DbxAppInfo(appInfo.key)
                val pkceWebAuth = DbxPKCEWebAuth(requestConfig, appInfoWithoutSecret)
                this@DropboxSyncViewModel.pkceWebAuth = pkceWebAuth

                val webAuthRequest = DbxWebAuth.newRequestBuilder()
                    .withNoRedirect()
                    .withTokenAccessType(TokenAccessType.OFFLINE)
                    .build()

                val authorizeUrl = pkceWebAuth.authorize(webAuthRequest)
                dropboxSyncMessageMutableState.emit(DropboxSynMessages.ProceedToAuth(authorizeUrl))
            } catch (e: Throwable) {
                logger.e(e) { "Error while trying to auth with Dropbox before getting the code" }
                dropboxSyncMessageMutableState.emit(DropboxSynMessages.Error)
                dropboxSyncUiMutableState.update { DropboxConnectionUiState.Unlinked }
                return@launch
            }
        }
    }

    fun handleDropboxAuthResponse(authorizationCode: String) {
        scope.launch {
            try {
                dropboxSyncUiMutableState.update { DropboxConnectionUiState.Loading }
                val authResult = pkceWebAuth?.finishFromCode(authorizationCode)
                val appInfo = this@DropboxSyncViewModel.appInfo
                if (authResult != null && appInfo != null) {
                    val credential = DbxCredential(
                        authResult.accessToken,
                        authResult.expiresAt,
                        authResult.refreshToken,
                        appInfo.key,
                        appInfo.secret,
                    )
                    val stringCredentials = DbxCredential.Writer.writeToString(credential)
                    dropboxDataSource.saveAuth(DropboxStringCredentials(stringCredentials))
                    dropboxSettings.setDropboxData(stringCredentials)
                    dropboxSyncUiMutableState.update {
                        DropboxConnectionUiState.Linked(
                            syncState = getSyncState(),
                        )
                    }
                    emitSyncLoading()
                    feedRetrieverRepository.fetchFeeds()
                    emitLastSyncUpdate()
                } else {
                    dropboxSyncMessageMutableState.emit(DropboxSynMessages.Error)
                    dropboxSyncUiMutableState.update { DropboxConnectionUiState.Unlinked }
                }
            } catch (e: Throwable) {
                logger.e(e) { "Error while trying to auth with Dropbox after getting the code" }
                dropboxSyncMessageMutableState.emit(DropboxSynMessages.Error)
                dropboxSyncUiMutableState.update { DropboxConnectionUiState.Unlinked }
            }
        }
    }

    fun triggerBackup() {
        scope.launch {
            emitSyncLoading()
            feedSyncRepository.performBackup(forceBackup = true)
            emitLastSyncUpdate()
        }
    }

    private fun restoreDropboxAuth() {
        dropboxSyncUiMutableState.update { DropboxConnectionUiState.Loading }
        val stringCredentials = dropboxSettings.getDropboxData()
        if (stringCredentials != null) {
            dropboxDataSource.restoreAuth(DropboxStringCredentials(stringCredentials))
            dropboxSyncUiMutableState.update {
                DropboxConnectionUiState.Linked(
                    syncState = getSyncState(),
                )
            }
        } else {
            dropboxSyncUiMutableState.update { DropboxConnectionUiState.Unlinked }
        }
    }

    fun disconnect() {
        scope.launch {
            try {
                dropboxSyncUiMutableState.update { DropboxConnectionUiState.Loading }
                dropboxDataSource.revokeAccess()
                dropboxSettings.clearDropboxData()
                dropboxSyncUiMutableState.update { DropboxConnectionUiState.Unlinked }
            } catch (_: DropboxException) {
                dropboxSyncMessageMutableState.emit(DropboxSynMessages.Error)
            }
        }
    }

    private fun getLastUploadDate(): String? =
        dropboxSettings.getLastUploadTimestamp()?.let { timestamp ->
            dateFormatter.formatDateForLastRefresh(timestamp)
        }

    private fun getLastDownloadDate(): String? =
        dropboxSettings.getLastDownloadTimestamp()?.let { timestamp ->
            dateFormatter.formatDateForLastRefresh(timestamp)
        }

    private fun emitSyncLoading() {
        dropboxSyncUiMutableState.update { oldState ->
            if (oldState is DropboxConnectionUiState.Linked) {
                DropboxConnectionUiState.Linked(
                    syncState = DropboxSyncUIState.Loading,
                )
            } else {
                oldState
            }
        }
    }

    private fun getSyncState(): DropboxSyncUIState {
        return when {
            dropboxSettings.getLastDownloadTimestamp() != null || dropboxSettings.getLastUploadTimestamp() != null -> {
                DropboxSyncUIState.Synced(
                    lastDownloadDate = getLastDownloadDate(),
                    lastUploadDate = getLastUploadDate(),
                )
            }

            else -> DropboxSyncUIState.None
        }
    }

    private fun emitLastSyncUpdate() {
        dropboxSyncUiMutableState.update { oldState ->
            if (oldState is DropboxConnectionUiState.Linked) {
                DropboxConnectionUiState.Linked(
                    syncState = getSyncState(),
                )
            } else {
                oldState
            }
        }
    }
}