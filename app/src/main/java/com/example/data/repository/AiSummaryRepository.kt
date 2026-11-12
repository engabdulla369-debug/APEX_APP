package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.model.AiSummaryState
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.InputStreamReader

class AiSummaryRepository {

    private val model by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    fun summarizeContentStream(
        sourceTitle: String,
        contentText: String,
        courseTitle: String = "",
        extraInstructions: String = ""
    ): Flow<AiSummaryState> = flow {
        emit(AiSummaryState.Loading)
        val cleanContent = contentText.trim().take(12_000)
        if (cleanContent.isBlank()) {
            emit(AiSummaryState.Error("النص المراد تلخيصه فارغ، أضف محتوى المحاضرة أو الملاحظات أولاً.", sourceTitle))
            return@flow
        }

        val prompt = buildString {
            appendLine("أنت مساعد أكاديمي متخصص في الهندسة والعلوم الجامعية لطلاب منصة الدفعة.")
            appendLine("لخّص المحتوى التالي باللغة العربية الواضحة والمنظمة بصيغة سهلة للمراجعة والامتحان:")
            appendLine("1) الفكرة الرئيسية للمحاضرة")
            appendLine("2) أهم النقاط والمفاهيم الأساسية (في نقاط مرقمة)")
            appendLine("3) القوانين أو المعادلات الهامة (إن وجدت)")
            appendLine("4) سؤالان سريعا الفهم للمراجعة الذاتية")
            if (courseTitle.isNotBlank()) appendLine("المادة الدراسية: $courseTitle")
            if (sourceTitle.isNotBlank()) appendLine("عنوان الملف/المحتوى: $sourceTitle")
            if (extraInstructions.isNotBlank()) appendLine("ملاحظة إضافية: $extraInstructions")
            appendLine()
            appendLine("المحتوى:")
            appendLine(cleanContent)
        }

        val buffer = StringBuilder()
        try {
            withTimeout(60_000L) {
                model.generateContentStream(prompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        buffer.append(piece)
                        emit(AiSummaryState.Streaming(buffer.toString(), sourceTitle))
                    }
                }
            }
            val finalText = buffer.toString().trim()
            if (finalText.isEmpty()) {
                emit(AiSummaryState.Error("لم يتم استلام ملخص من الخدمة الذكية، حاول مرة أخرى.", sourceTitle))
            } else {
                emit(AiSummaryState.Success(finalText, sourceTitle))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini summary timed out", e)
            emit(AiSummaryState.Error("استغرق التلخيص وقتاً أطول من المتوقع، تأكد من اتصال الإنترنت وحاول مرة أخرى.", sourceTitle))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini summary failed", e)
            val detail = e.localizedMessage?.take(140) ?: "تعذر الاتصال بخدمة التلخيص الذكي"
            emit(AiSummaryState.Error("تعذر إتمام التلخيص: $detail", sourceTitle))
        }
    }.flowOn(Dispatchers.IO)

    fun readReadableTextFromUri(context: Context, uriString: String): String {
        return try {
            val uri = Uri.parse(uriString)
            if (uri.scheme != "content") return ""
            val mime = context.contentResolver.getType(uri) ?: ""
            if (!mime.startsWith("text/") && !mime.contains("json") && !mime.contains("xml")) {
                return ""
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    reader.readText().take(12_000)
                }
            }.orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read local text uri", e)
            ""
        }
    }

    private companion object {
        const val TAG = "AiSummaryRepository"
    }
}
