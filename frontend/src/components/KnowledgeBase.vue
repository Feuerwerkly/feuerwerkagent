<template>
  <div class="knowledge-base">
    <el-container>
      <el-header>
        <h1>知识库管理系统</h1>
      </el-header>

      <el-main>
        <!-- 文件上传区域 -->
        <el-card class="upload-card">
          <template #header>
            <span>文档上传</span>
          </template>
          <el-upload
              class="upload-demo"
              drag
              action="http://localhost:8080/api/kb/document/upload"
              :on-success="handleUploadSuccess"
              :on-error="handleUploadError"
              :before-upload="beforeUpload"
              :file-list="fileList"
              :limit="10"
              accept=".pdf,.docx,.txt,.md"
          >
            <el-icon class="el-icon--upload"><upload-filled /></el-icon>
            <div class="el-upload__text">
              拖拽文件到此处或 <em>点击上传</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">
                支持 PDF、DOCX、TXT、MD 格式，单个文件不超过 50MB
              </div>
            </template>
          </el-upload>
        </el-card>

        <!-- 文档列表区域 -->
        <el-card class="document-list-card">
          <template #header>
            <div class="card-header">
              <span>文档列表</span>
              <el-button
                  type="primary"
                  size="small"
                  @click="refreshDocumentList"
                  :loading="loadingDocuments"
              >
                刷新列表
              </el-button>
            </div>
          </template>

          <el-table
              :data="documents"
              style="width: 100%"
              v-loading="loadingDocuments"
              @selection-change="handleSelectionChange"
          >
            <el-table-column
                type="selection"
                width="55"
            />
            <el-table-column
                prop="id"
                label="ID"
                width="80"
            />
            <el-table-column
                prop="fileName"
                label="文件名"
                min-width="200"
            />
            <el-table-column
                prop="fileType"
                label="类型"
                width="80"
            />
            <el-table-column
                prop="fileSize"
                label="大小"
                width="100"
                :formatter="formatFileSize"
            />
            <el-table-column
                prop="status"
                label="状态"
                width="120"
            >
              <template #default="scope">
                <el-tag
                    v-if="scope.row.status === 0"
                    type="info"
                >
                  待处理
                </el-tag>
                <el-tag
                    v-else-if="scope.row.status === 1"
                    type="success"
                >
                  处理成功
                </el-tag>
                <el-tag
                    v-else-if="scope.row.status === 2"
                    type="danger"
                >
                  处理失败
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
                prop="createTime"
                label="上传时间"
                width="180"
                :formatter="formatDate"
            />
            <el-table-column
                label="操作"
                width="180"
            >
              <template #default="scope">
                <el-button
                    v-if="scope.row.status === 2"
                    type="warning"
                    size="small"
                    @click="retryDocument(scope.row.id)"
                >
                  重试
                </el-button>
                <el-button
                    type="danger"
                    size="small"
                    @click="deleteDocument(scope.row.id)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <!-- 知识库检索区域 -->
        <el-card class="search-card">
          <template #header>
            <span>知识库检索</span>
          </template>

          <el-form :model="searchForm" label-width="100px">
            <el-form-item label="检索问题">
              <el-input
                  v-model="searchForm.query"
                  placeholder="请输入要检索的问题..."
                  type="textarea"
                  :rows="3"
              />
            </el-form-item>

            <el-form-item label="返回结果数">
              <el-input-number
                  v-model="searchForm.topN"
                  :min="1"
                  :max="20"
              />
            </el-form-item>

            <el-form-item label="相似度阈值">
              <el-slider
                  v-model="searchForm.minScore"
                  :min="0"
                  :max="1"
                  :step="0.1"
                  show-input
              />
            </el-form-item>

            <el-form-item>
              <el-button
                  type="primary"
                  @click="handleSearch"
                  :loading="searching"
                  :disabled="!searchForm.query || selectedDocuments.length === 0"
              >
                {{ searching ? '检索中...' : '开始检索' }}
              </el-button>
              <el-button @click="resetSearchForm">
                重置
              </el-button>
            </el-form-item>
          </el-form>

          <el-alert
              v-if="selectedDocuments.length === 0"
              title="请至少选择一个文档进行检索"
              type="warning"
              :closable="false"
              show-icon
          />
        </el-card>

        <!-- 检索结果区域 -->
        <el-card v-if="searchResults.length > 0" class="search-results-card">
          <template #header>
            <span>检索结果 ({{ searchResults.length }} 条)</span>
          </template>

          <div
              v-for="(result, index) in searchResults"
              :key="index"
              class="search-result-item"
          >
            <div class="result-header">
              <h3>{{ index + 1 }}. {{ result.fileName }}</h3>
              <div class="result-meta">
                <el-tag size="small" type="success">相似度: {{ (result.score * 100).toFixed(1) }}%</el-tag>
                <el-tag size="small" type="info">块索引: {{ result.chunkIndex }}</el-tag>
              </div>
            </div>
            <div class="result-content">
              {{ result.text }}
            </div>
          </div>
        </el-card>
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import axios from 'axios'

// 响应式数据
const documents = ref([])
const selectedDocuments = ref([])
const loadingDocuments = ref(false)
const fileList = ref([])
const searching = ref(false)
const searchResults = ref([])

// 搜索表单
const searchForm = reactive({
  query: '',
  topN: 5,
  minScore: 0.6
})

// 获取文档列表
const fetchDocuments = async () => {
  loadingDocuments.value = true
  try {
    const response = await axios.get('http://localhost:8080/api/kb/document/list')
    if (response.data.code === 200) {
      documents.value = response.data.data
      // 默认选中所有文档
      selectedDocuments.value = [...documents.value]
    } else {
      ElMessage.error('获取文档列表失败: ' + response.data.msg)
    }
  } catch (error) {
    console.error('获取文档列表失败:', error)
    ElMessage.error('获取文档列表失败，请检查网络连接')
  } finally {
    loadingDocuments.value = false
  }
}

// 刷新文档列表
const refreshDocumentList = () => {
  fetchDocuments()
}

// 文件上传前的验证
const beforeUpload = (file) => {
  const allowedTypes = ['pdf', 'docx', 'txt', 'md']
  const fileExtension = file.name.split('.').pop().toLowerCase()

  if (!allowedTypes.includes(fileExtension)) {
    ElMessage.error('只支持 PDF、DOCX、TXT、MD 格式的文件')
    return false
  }

  const isLt50M = file.size / 1024 / 1024 < 50
  if (!isLt50M) {
    ElMessage.error('上传文件大小不能超过 50MB')
    return false
  }

  return true
}

// 文件上传成功
const handleUploadSuccess = (response) => {
  if (response.code === 200) {
    ElMessage.success('文档上传成功，正在后台处理...')
    fileList.value = []
    // 延迟刷新列表，给后端一些时间处理
    setTimeout(() => {
      fetchDocuments()
    }, 1000)
  } else {
    ElMessage.error('上传失败: ' + response.msg)
  }
}

// 文件上传失败
const handleUploadError = (error) => {
  console.error('上传失败:', error)
  ElMessage.error('文件上传失败，请检查网络连接')
}

// 处理选择变化
const handleSelectionChange = (selection) => {
  selectedDocuments.value = selection
}

// 删除文档
const deleteDocument = async (documentId) => {
  try {
    await ElMessageBox.confirm(
        '确定要删除这个文档吗？删除后将无法恢复。',
        '警告',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }
    )

    const response = await axios.delete(`http://localhost:8080/api/kb/document/${documentId}`)
    if (response.data.code === 200) {
      ElMessage.success('文档删除成功')
      await fetchDocuments()
    } else {
      ElMessage.error('删除失败: ' + response.data.msg)
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除文档失败:', error)
      ElMessage.error('删除文档失败')
    }
  }
}

// 重新处理失败的文档
const retryDocument = async (documentId) => {
  try {
    const response = await axios.post(`http://localhost:8080/api/kb/document/${documentId}/retry`)
    if (response.data.code === 200) {
      ElMessage.success('开始重新处理文档...')
      setTimeout(() => {
        fetchDocuments()
      }, 2000)
    } else {
      ElMessage.error('重试失败: ' + response.data.msg)
    }
  } catch (error) {
    console.error('重试处理失败:', error)
    ElMessage.error('重试处理失败')
  }
}

// 执行搜索
const handleSearch = async () => {
  if (!searchForm.query || searchForm.query.trim() === '') {
    ElMessage.warning('请输入检索问题')
    return
  }

  if (selectedDocuments.value.length === 0) {
    ElMessage.warning('请至少选择一个文档进行检索')
    return
  }

  searching.value = true
  try {
    const documentIds = selectedDocuments.value.map(doc => doc.id)
    const response = await axios.post('http://localhost:8080/api/kb/search', {
      query: searchForm.query,
      documentIds: documentIds,
      topN: searchForm.topN,
      minScore: searchForm.minScore
    })

    if (response.data.code === 200) {
      searchResults.value = response.data.data
      if (searchResults.value.length === 0) {
        ElMessage.info('未找到相关结果')
      } else {
        ElMessage.success(`找到 ${searchResults.value.length} 条相关结果`)
      }
    } else {
      ElMessage.error('检索失败: ' + response.data.msg)
    }
  } catch (error) {
    console.error('检索失败:', error)
    ElMessage.error('检索失败，请检查网络连接')
  } finally {
    searching.value = false
  }
}

// 重置搜索表单
const resetSearchForm = () => {
  searchForm.query = ''
  searchForm.topN = 5
  searchForm.minScore = 0.6
  searchResults.value = []
}

// 格式化文件大小
const formatFileSize = (row, column, cellValue) => {
  if (cellValue < 1024) {
    return cellValue + ' B'
  } else if (cellValue < 1024 * 1024) {
    return (cellValue / 1024).toFixed(2) + ' KB'
  } else {
    return (cellValue / 1024 / 1024).toFixed(2) + ' MB'
  }
}

// 格式化日期
const formatDate = (row, column, cellValue) => {
  if (!cellValue) return ''
  const date = new Date(cellValue)
  return date.toLocaleString('zh-CN')
}

// 组件挂载时获取文档列表
onMounted(() => {
  fetchDocuments()
})
</script>

<style scoped>
.knowledge-base {
  padding: 20px;
  background-color: #f5f7fa;
  min-height: 100vh;
  height: auto;
  overflow-y: auto;
}

.el-header {
  background-color: #409eff;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  margin-bottom: 20px;
  padding: 20px;
}

.el-header h1 {
  margin: 0;
  font-size: 24px;
}

.el-main {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
  height: auto;
  overflow: visible;
}

.upload-card,
.document-list-card,
.search-card,
.search-results-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.upload-demo {
  width: 100%;
}

.search-result-item {
  padding: 15px;
  margin-bottom: 15px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background-color: #fff;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.result-header h3 {
  margin: 0;
  font-size: 16px;
  color: #303133;
}

.result-meta {
  display: flex;
  gap: 10px;
}

.result-content {
  padding: 10px;
  background-color: #f5f7fa;
  border-radius: 4px;
  line-height: 1.6;
  color: #606266;
}

:deep(.el-upload-dragger) {
  width: 100%;
  min-height: 200px;
}

:deep(.el-table) {
  border-radius: 4px;
}

:deep(.el-card__header) {
  background-color: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
}
</style>