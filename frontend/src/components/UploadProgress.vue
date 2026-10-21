<template>
  <div class="upload-progress-container">
    <!-- 进度列表 -->
    <div v-for="progress in progressList" :key="progress.documentId" class="progress-item">

      <!-- 文件信息 -->
      <div class="file-info">
        <div class="file-name">
          <el-icon><Document /></el-icon>
          {{ progress.fileName }}
        </div>
        <div class="document-id">ID: {{ progress.documentId }}</div>
      </div>

      <!-- 进度条 -->
      <div class="progress-section">
        <div class="progress-header">
          <span class="current-step">{{ progress.currentStep }}</span>
          <span class="progress-percent">{{ progress.progress }}%</span>
        </div>

        <el-progress
          :percentage="progress.progress"
          :status="getProgressStatus(progress.status)"
          :stroke-width="8"
        />

        <!-- 详细信息 -->
        <div class="progress-details">
          <div v-if="progress.totalChunks" class="chunk-info">
            文本块处理: {{ progress.processedChunks }} / {{ progress.totalChunks }}
          </div>

          <div v-if="progress.estimatedSecondsRemaining && progress.status !== 'completed'" class="time-info">
            预计剩余: {{ formatTime(progress.estimatedSecondsRemaining) }}
          </div>

          <div v-if="progress.status === 'failed'" class="error-info">
            <el-alert type="error" :title="progress.errorMessage" :closable="false" />
          </div>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="action-buttons">
        <el-button
          v-if="progress.status === 'failed'"
          type="warning"
          size="small"
          @click="retryDocument(progress.documentId)"
        >
          重试
        </el-button>

        <el-button
          v-if="progress.status === 'completed'"
          type="success"
          size="small"
          @click="searchDocument(progress.documentId)"
        >
          搜索
        </el-button>

        <el-button
          type="danger"
          size="small"
          @click="deleteProgress(progress.documentId)"
        >
          删除
        </el-button>
      </div>
    </div>

    <!-- 空状态 -->
    <el-empty
      v-if="progressList.length === 0"
      description="暂无处理中的文档"
      :image-size="100"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { Document } from '@element-plus/icons-vue'
import axios from 'axios'

const props = defineProps({
  documentIds: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['completed', 'failed'])

const progressList = ref([])
const pollingIntervals = ref(new Map())

// 获取进度状态
const getProgressStatus = (status) => {
  const statusMap = {
    uploading: 'warning',
    parsing: 'warning',
    chunking: 'warning',
    embedding: 'warning',
    storing: 'warning',
    completed: 'success',
    failed: 'exception'
  }
  return statusMap[status] || 'info'
}

// 格式化时间
const formatTime = (seconds) => {
  if (seconds < 60) {
    return `${Math.round(seconds)}秒`
  } else if (seconds < 3600) {
    return `${Math.round(seconds / 60)}分钟`
  } else {
    return `${Math.round(seconds / 3600)}小时`
  }
}

// 轮询单个文档进度
const startPolling = (documentId) => {
  // 立即查询一次
  fetchProgress(documentId)

  // 设置轮询
  const interval = setInterval(() => {
    fetchProgress(documentId)
  }, 2000) // 每2秒查询一次

  pollingIntervals.value.set(documentId, interval)
}

// 获取进度信息
const fetchProgress = async (documentId) => {
  try {
    const response = await axios.get(\`http://localhost:8080/api/kb/document/\${documentId}/progress\`)

    if (response.data.success) {
      const progress = response.data.data

      // 更新或添加进度
      const existingIndex = progressList.value.findIndex(p => p.documentId === documentId)
      if (existingIndex >= 0) {
        progressList.value[existingIndex] = progress
      } else {
        progressList.value.push(progress)
      }

      // 如果完成或失败，停止轮询
      if (progress.status === 'completed' || progress.status === 'failed') {
        stopPolling(documentId)

        // 通知父组件
        if (progress.status === 'completed') {
          emit('completed', progress)
        } else {
          emit('failed', progress)
        }

        // 5秒后自动删除进度记录
        setTimeout(() => {
          removeProgress(documentId)
        }, 5000)
      }
    }
  } catch (error) {
    console.error('获取进度失败:', error)
  }
}

// 停止轮询
const stopPolling = (documentId) => {
  const interval = pollingIntervals.value.get(documentId)
  if (interval) {
    clearInterval(interval)
    pollingIntervals.value.delete(documentId)
  }
}

// 删除进度记录
const removeProgress = (documentId) => {
  stopPolling(documentId)
  const index = progressList.value.findIndex(p => p.documentId === documentId)
  if (index >= 0) {
    progressList.value.splice(index, 1)
  }
}

// 重试文档处理
const retryDocument = async (documentId) => {
  try {
    await axios.post(\`http://localhost:8080/api/kb/document/\${documentId}/retry\`)
    // 重新开始轮询
    startPolling(documentId)
  } catch (error) {
    console.error('重试失败:', error)
  }
}

// 搜索文档
const searchDocument = (documentId) => {
  // 触发搜索功能
  console.log('搜索文档:', documentId)
}

// 监听文档ID变化
watch(() => props.documentIds, (newIds) => {
  // 清理旧的轮询
  pollingIntervals.value.forEach((interval, id) => {
    clearInterval(interval)
  })
  pollingIntervals.value.clear()

  // 开始新的轮询
  newIds.forEach(id => {
    if (id) {
      startPolling(id)
    }
  })
}, { immediate: true })

// 组件卸载时清理
onUnmounted(() => {
  pollingIntervals.value.forEach((interval) => {
    clearInterval(interval)
  })
  pollingIntervals.value.clear()
})
</script>

<style scoped>
.upload-progress-container {
  padding: 20px;
  max-width: 800px;
  margin: 0 auto;
}

.progress-item {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
  background: #fff;
}

.file-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.file-name {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
}

.document-id {
  font-size: 12px;
  color: #909399;
}

.progress-section {
  margin-bottom: 12px;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 13px;
}

.current-step {
  color: #606266;
  font-weight: 500;
}

.progress-percent {
  font-weight: 600;
  color: #409eff;
}

.progress-details {
  margin-top: 12px;
  font-size: 12px;
  color: #606266;
}

.chunk-info,
.time-info {
  margin-bottom: 4px;
}

.error-info {
  margin-top: 8px;
}

.action-buttons {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}
</style>