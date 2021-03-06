package com.criptext.mail.scenes.composer.data

import android.accounts.NetworkErrorException
import com.criptext.mail.R
import com.criptext.mail.aes.AESUtil
import com.criptext.mail.api.HttpClient
import com.criptext.mail.api.HttpErrorHandlingHelper
import com.criptext.mail.api.ServerErrorException
import com.criptext.mail.bgworker.BackgroundWorker
import com.criptext.mail.bgworker.ProgressReporter
import com.criptext.mail.db.models.ActiveAccount
import com.criptext.mail.utils.ServerErrorCodes
import com.criptext.mail.utils.UIMessage
import com.criptext.mail.utils.file.ChunkFileReader
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.flatMap
import com.github.kittinunf.result.mapError
import org.json.JSONException
import org.json.JSONObject
import java.io.File

class UploadAttachmentWorker(private val filepath: String,
                             httpClient: HttpClient,
                             activeAccount: ActiveAccount,
                             val fileKey: String?,
                             override val publishFn: (ComposerResult.UploadFile) -> Unit)
    : BackgroundWorker<ComposerResult.UploadFile> {
    override val canBeParallelized = false

    private val fileServiceAPIClient = FileServiceAPIClient(httpClient, activeAccount.jwt)

    override fun catchException(ex: Exception): ComposerResult.UploadFile =
            if(ex is ServerErrorException) {
                when {
                    ex.errorCode == ServerErrorCodes.Unauthorized ->
                        ComposerResult.UploadFile.Unauthorized(UIMessage(R.string.device_removed_remotely_exception))
                    ex.errorCode == ServerErrorCodes.Forbidden ->
                        ComposerResult.UploadFile.Forbidden()
                    else -> ComposerResult.UploadFile.Failure(filepath, createErrorMessage(ex))
                }
            }
            else ComposerResult.UploadFile.Failure(filepath, createErrorMessage(ex))



    private fun uploadFile(file: File, reporter: ProgressReporter<ComposerResult.UploadFile>): (String) -> Result<Unit, Exception> = { fileToken ->
        reporter.report(ComposerResult.UploadFile.Register(file.absolutePath, fileToken))
        Result.of {

            val chunks = (file.length() / chunkSize).toInt() + 1
            val onNewChunkRead: (ByteArray, Int) -> Unit = { chunk, index ->
                reporter.report(ComposerResult.UploadFile.Progress(file.absolutePath,
                        index * 100 / chunks))
                fileServiceAPIClient.uploadChunk(chunk = if(fileKey != null)
                                                            AESUtil(fileKey).encrypt(chunk)
                                                        else
                                                            chunk,
                        fileName = file.name,
                        part = index + 1, fileToken = fileToken)
            }
            ChunkFileReader.read(file, chunkSize, onNewChunkRead)
        }
    }

    private val getFileTokenFromJSONResponse: (String) -> Result<String, Exception> = { stringResponse ->
        Result.of {
            val jsonObject = JSONObject(stringResponse)
            jsonObject.getString("filetoken")
        }
    }

    private fun registerFile(file: File): Result<String, Exception> =
        Result.of {
            val fileSize = file.length()
            val totalChunks = (fileSize / chunkSize).toInt() + 1

            fileServiceAPIClient.registerFile(fileName = file.name,
                    chunkSize = chunkSize, fileSize = fileSize.toInt(), totalChunks = totalChunks)
        }

    override fun work(reporter: ProgressReporter<ComposerResult.UploadFile>)
            : ComposerResult.UploadFile? {
        val file = File(filepath)
        val result = registerFile(file)
                .mapError(HttpErrorHandlingHelper.httpExceptionsToNetworkExceptions)
                .flatMap(getFileTokenFromJSONResponse)
                .flatMap(uploadFile(file, reporter))


        return when (result) {
            is Result.Success -> ComposerResult.UploadFile.Success(filepath)
            is Result.Failure -> catchException(result.error)
        }
    }

    private val createErrorMessage: (ex: Exception) -> UIMessage = { ex ->
        ex.printStackTrace()
        when (ex) { // these are not the real errors TODO fix!
            is JSONException -> UIMessage(resId = R.string.json_error_exception)
            is ServerErrorException -> UIMessage(resId = R.string.server_error_exception)
            is NetworkErrorException -> UIMessage(resId = R.string.network_error_exception)
            else -> UIMessage(resId = R.string.fail_register_try_again_error_exception)
        }
    }
    override fun cancel() {
        TODO("not implemented") //To change body of created functions use CRFile | Settings | CRFile Templates.
    }

    companion object {
        private const val chunkSize = 512 * 1024

    }

}