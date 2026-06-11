package com.example.danallacalendar.estimate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danallacalendar.data.EstimatePdf
import com.example.danallacalendar.data.EstimatePdfDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EstimateListViewModel @Inject constructor(
    private val estimatePdfDao: EstimatePdfDao
) : ViewModel() {

    val pdfList: StateFlow<List<EstimatePdf>> = estimatePdfDao.getAllPdfs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deletePdf(pdf: EstimatePdf) {
        viewModelScope.launch {
            // Room DB에서 삭제
            estimatePdfDao.deletePdf(pdf.id)
            
            // 로컬 파일 시스템에서도 삭제 (로컬 절대 경로 형식일 때만 시도)
            try {
                val isLocalFile = pdf.filePath.startsWith("/") || pdf.filePath.contains(":") || pdf.filePath.endsWith(".pdf")
                if (isLocalFile) {
                    val file = java.io.File(pdf.filePath)
                    if (file.exists()) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("EstimateListViewModel", "Failed to delete file: ${pdf.filePath}", e)
            }
        }
    }
}
