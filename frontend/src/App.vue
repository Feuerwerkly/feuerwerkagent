<template>
  <div id="app">
    <!-- 导航栏 -->
    <nav class="navbar">
      <div class="nav-container">
        <h1>🤖 FeuerwerkAgent</h1>
        <div class="nav-buttons">
          <button
              @click="currentView = 'chat'"
              :class="{ active: currentView === 'chat' }"
              class="nav-btn"
          >
            💬 AI对话
          </button>
          <button
              @click="currentView = 'shop'"
              :class="{ active: currentView === 'shop' }"
              class="nav-btn"
          >
            🛒 数码商城
          </button>
          <button
              @click="goIm()"
              :class="{ active: currentView === 'im' }"
              class="nav-btn"
          >
            💬 即时通讯
            <span v-if="imUnread > 0" class="nav-badge">{{ imUnread > 99 ? '99+' : imUnread }}</span>
          </button>
          <button
              @click="currentView = 'knowledge'"
              :class="{ active: currentView === 'knowledge' }"
              class="nav-btn"
          >
            📚 知识库
          </button>
        </div>
      </div>
    </nav>

    <!-- 主内容区域（商城/IM视图全宽，其余视图保持容器布局） -->
    <main class="main-content" :class="{ 'shop-full': currentView === 'shop' || currentView === 'im' }">
      <!-- AI对话界面 -->
      <div v-if="currentView === 'chat'" class="chat-view">
        <div class="chat-section">
          <div class="chat-container">
            <div class="messages" ref="messagesContainer">
              <div
                  v-for="(msg, index) in messages"
                  :key="index"
                  :class="['message', msg.role]"
              >
                <div class="message-content">
                  <div class="message-header">
                    <span class="role">{{ msg.role === 'user' ? '👤 用户' : '🤖 DeepSeek' }}</span>
                    <span class="time">{{ msg.time }}</span>
                  </div>
                  <div class="message-text">
                    <!-- 思考过程（仅助手消息） -->
                    <div v-if="msg.role === 'assistant' && msg.reasoning" class="reasoning-section">
                      <div class="reasoning-header" @click="toggleReasoning(index)">
                        <span class="reasoning-title">
                          🧠 思考过程
                          <span class="reasoning-toggle">
                            {{ reasoningCollapsed[index] ? '▶ 展开' : '▼ 收起' }}
                          </span>
                        </span>
                      </div>
                      <div v-show="!reasoningCollapsed[index]" class="reasoning-content">
                        {{ msg.reasoning }}
                      </div>
                    </div>

                    <!-- 正式回答内容 -->
                    <div v-if="msg.role === 'assistant'" class="markdown-content" v-html="renderMarkdown(msg.content)"></div>
                    <div v-else class="user-content">{{ msg.content }}</div>
                  </div>
                  <div v-if="msg.streaming" class="streaming-indicator">✨ 正在输入...</div>
                </div>
              </div>
            </div>

            <div class="input-section">
              <textarea
                  v-model="userMessage"
                  @keydown.enter.prevent="sendMessage"
                  placeholder="输入你的消息... (Enter发送)"
                  rows="3"
                  :disabled="isLoading"
              ></textarea>
              <div class="button-group">
                <button
                    @click="sendMessage"
                    :disabled="isLoading || !userMessage.trim()"
                    class="send-button"
                >
                  {{ isLoading ? '🤔 思考中...' : '🚀 发送' }}
                </button>
                <button
                    @click="clearMessages"
                    :disabled="isLoading"
                    class="clear-button"
                >
                  🗑️ 清空
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 知识库界面 -->
      <div v-if="currentView === 'knowledge'" class="knowledge-view">
        <KnowledgeBase />
      </div>

      <!-- 网上商店界面 -->
      <div v-if="currentView === 'shop'" class="shop-view">
        <ShopApp />
      </div>

      <!-- 即时通讯界面 -->
      <div v-if="currentView === 'im'" class="im-view">
        <ImApp />
      </div>
    </main>

    <!-- 全局登录/注册弹窗（商城与IM共用） -->
    <LoginDialog v-model="shopStore.loginVisible" />

    <!-- 视频通话弹窗（全局：任意视图下来电/通话不中断） -->
    <ImCallDialog />
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'
import KnowledgeBase from './components/KnowledgeBase.vue'
import ShopApp from './components/shop/ShopApp.vue'
import LoginDialog from './components/shop/LoginDialog.vue'
import ImApp from './components/im/ImApp.vue'
import ImCallDialog from './components/im/ImCallDialog.vue'
import { shopStore } from './shop/store'
import { imStore } from './im/store'
import { connect as imConnect, disconnect as imDisconnect } from './im/ws'

// 配置marked以支持代码高亮
marked.setOptions({
  highlight: function(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(code, { language: lang }).value
      } catch (err) {}
    }
    return hljs.highlightAuto(code).value
  },
  breaks: true,
  gfm: true
})

const API_BASE = '/api/ai'

// 当前视图
const currentView = ref('chat')

// 响应式数据
const messages = ref([])
const userMessage = ref('')
const isLoading = ref(false)
const messagesContainer = ref(null)

// 思考过程折叠状态
const reasoningCollapsed = ref({})

// 发送消息
const sendMessage = async () => {
  if (!userMessage.value.trim() || isLoading.value) return

  const message = userMessage.value.trim()
  userMessage.value = ''

  // 添加用户消息
  messages.value.push({
    role: 'user',
    content: message,
    time: getCurrentTime(),
    streaming: false
  })

  // 添加AI消息占位符
  const aiMessageIndex = messages.value.length
  messages.value.push({
    role: 'assistant',
    reasoning: '',
    content: '',
    time: getCurrentTime(),
    streaming: true
  })

  isLoading.value = true
  await scrollToBottom()

  try {
    console.log('🚀 开始DeepSeek流式对话:', message)

    const response = await fetch(`${API_BASE}/stream/chat?message=${encodeURIComponent(message)}`)

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.trim() && line.startsWith('data:')) {
          try {
            const jsonStr = line.substring(5).trim()
            const data = JSON.parse(jsonStr)

            if (data.event === 'reasoning' || data.reasoning) {
              messages.value[aiMessageIndex].reasoning += data.content || data.reasoning
              await scrollToBottom()
            }
            else if (data.event === 'content' || data.content) {
              messages.value[aiMessageIndex].content += data.content
              await scrollToBottom()
            }

            if (data.status === 'done') {
              messages.value[aiMessageIndex].streaming = false
            }

            if (data.error) {
              messages.value[aiMessageIndex].content += '\n❌ 错误: ' + data.error
              messages.value[aiMessageIndex].streaming = false
            }
          } catch (e) {
            console.error('解析数据失败:', e, '原始数据:', line)
          }
        }
      }
    }

    console.log('✅ 流式对话完成')

  } catch (error) {
    console.error('❌ 对话失败:', error)
    messages.value[aiMessageIndex].content = '❌ 错误: ' + error.message
    messages.value[aiMessageIndex].streaming = false
  } finally {
    isLoading.value = false
  }
}

// 清空消息
const clearMessages = async () => {
  if (isLoading.value) return

  try {
    await fetch(`${API_BASE}/stream/clear-history`, {
      method: 'POST'
    })
  } catch (error) {
    console.error('清空历史失败:', error)
  }

  messages.value = []
}

// 滚动到底部
const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

// 切换思考过程折叠状态
const toggleReasoning = (index) => {
  if (reasoningCollapsed.value[index] === undefined) {
    reasoningCollapsed.value[index] = true
  }
  reasoningCollapsed.value[index] = !reasoningCollapsed.value[index]
}

// 获取当前时间
const getCurrentTime = () => {
  const now = new Date()
  return now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

// 渲染markdown内容
const renderMarkdown = (content) => {
  if (!content) return ''
  try {
    return marked.parse(content)
  } catch (e) {
    console.error('Markdown渲染失败:', e)
    return content
  }
}

// 检查服务器状态
onMounted(async () => {
  // 支付宝支付完成同步回跳：?payReturn=1 -> 自动切到商店订单页
  if (new URLSearchParams(window.location.search).get('payReturn') === '1') {
    currentView.value = 'shop'
  }
  // 已登录则建立IM长连接
  if (shopStore.isLoggedIn()) {
    imConnect()
  }
  // 消息通知点击 -> 跳转IM聊天
  window.addEventListener('im-open-chat', onImOpenChat)
  try {
    const response = await fetch(`${API_BASE}/health`)
    if (response.ok) {
      console.log('✅ 服务器连接正常')
    } else {
      console.warn('⚠️ 服务器响应异常')
    }
  } catch (error) {
    console.error('❌ 无法连接到服务器，请确保后端已启动')
  }
})

onUnmounted(() => {
  window.removeEventListener('im-open-chat', onImOpenChat)
})

// 登录/退出 -> 连接/断开IM长连接
watch(() => shopStore.token, token => {
  if (token) {
    imConnect()
  } else {
    imDisconnect()
  }
})

function onImOpenChat() {
  currentView.value = 'im'
}

// 进入IM视图：未登录则弹登录框
function goIm() {
  currentView.value = 'im'
  if (!shopStore.isLoggedIn()) {
    shopStore.loginVisible = true
  }
}

// IM未读总数（导航角标）
const imUnread = computed(() => (shopStore.isLoggedIn() ? imStore.unreadTotal() : 0))
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

#app {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  flex-direction: column;
}

/* 导航栏样式 */
.navbar {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  box-shadow: 0 2px 20px rgba(0, 0, 0, 0.1);
  position: sticky;
  top: 0;
  z-index: 100;
}

.nav-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 15px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.navbar h1 {
  font-size: 1.5rem;
  color: #667eea;
  margin: 0;
}

.nav-buttons {
  display: flex;
  gap: 15px;
}

.nav-btn {
  padding: 10px 20px;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s;
  background: #f5f5f5;
  color: #333;
  position: relative;
}

/* 导航按钮未读角标（IM消息） */
.nav-badge {
  position: absolute;
  top: -6px;
  right: -6px;
  background: #f56c6c;
  color: #fff;
  border-radius: 10px;
  font-size: 11px;
  line-height: 16px;
  padding: 0 6px;
  min-width: 16px;
  font-weight: 600;
}

.nav-btn:hover {
  background: #e9ecef;
  transform: translateY(-1px);
}

.nav-btn.active {
  background: #667eea;
  color: white;
}

.main-content {
  flex: 1;
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
}

/* 商城视图：全宽铺满（小米式整页布局），滚动交给页面本身 */
.main-content.shop-full {
  max-width: none;
  padding: 0;
  width: 100%;
}

.chat-view {
  height: calc(100vh - 80px);
}

.chat-section {
  background: white;
  border-radius: 15px;
  padding: 20px;
  box-shadow: 0 10px 30px rgba(0,0,0,0.2);
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.messages {
  flex: 1;
  overflow-y: auto;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 15px;
  background: #f8f9fa;
  margin-bottom: 15px;
  min-height: 0;
}

.message {
  margin-bottom: 15px;
  display: flex;
  width: 100%;
}

.message.user {
  justify-content: flex-end;
}

.message.assistant {
  justify-content: flex-start;
}

.message-content {
  max-width: 75%;
  padding: 12px 16px;
  border-radius: 12px;
  position: relative;
  text-align: left;
}

.message.user .message-content {
  background: #667eea;
  color: white;
}

.message.assistant .message-content {
  background: white;
  border: 1px solid #e0e0e0;
  color: #333;
}

.message-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 0.85rem;
  opacity: 0.8;
}

.message-text {
  line-height: 1.6;
  text-align: left;
  word-wrap: break-word;
}

.message.user .message-text {
  text-align: right;
}

/* Markdown内容样式 */
.markdown-content {
  text-align: left;
  color: #333;
  line-height: 1.8;
}

.markdown-content h1,
.markdown-content h2,
.markdown-content h3,
.markdown-content h4,
.markdown-content h5,
.markdown-content h6 {
  margin-top: 1em;
  margin-bottom: 0.5em;
  font-weight: 600;
  color: #2c3e50;
}

.markdown-content h1 { font-size: 1.8em; border-bottom: 2px solid #eee; padding-bottom: 0.3em; }
.markdown-content h2 { font-size: 1.5em; border-bottom: 1px solid #eee; padding-bottom: 0.3em; }
.markdown-content h3 { font-size: 1.3em; }
.markdown-content h4 { font-size: 1.1em; }

.markdown-content p {
  margin: 0.8em 0;
  line-height: 1.8;
}

.markdown-content ul,
.markdown-content ol {
  margin: 0.8em 0;
  padding-left: 2em;
}

.markdown-content li {
  margin: 0.4em 0;
  line-height: 1.6;
}

.markdown-content code {
  background: #f4f3ec;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 0.9em;
  color: #e83e8c;
}

.markdown-content pre {
  background: #1e1e1e;
  padding: 1em;
  border-radius: 8px;
  overflow-x: auto;
  margin: 1em 0;
}

.markdown-content pre code {
  background: transparent;
  padding: 0;
  color: #d4d4d4;
}

.markdown-content blockquote {
  border-left: 4px solid #667eea;
  padding-left: 1em;
  margin: 1em 0;
  color: #666;
  font-style: italic;
  background: #f8f9fa;
  padding: 1em;
  border-radius: 0 8px 8px 0;
}

.markdown-content strong {
  font-weight: 600;
  color: #2c3e50;
}

.markdown-content em {
  font-style: italic;
}

.markdown-content a {
  color: #667eea;
  text-decoration: none;
  border-bottom: 1px solid transparent;
  transition: border-bottom-color 0.2s;
}

.markdown-content a:hover {
  border-bottom-color: #667eea;
}

.markdown-content table {
  border-collapse: collapse;
  width: 100%;
  margin: 1em 0;
}

.markdown-content table th,
.markdown-content table td {
  border: 1px solid #ddd;
  padding: 8px 12px;
  text-align: left;
}

.markdown-content table th {
  background-color: #f2f2f2;
  font-weight: 600;
}

.markdown-content table tr:nth-child(even) {
  background-color: #f9f9f9;
}

.markdown-content img {
  max-width: 100%;
  height: auto;
  border-radius: 8px;
  margin: 1em 0;
}

.markdown-content hr {
  border: none;
  border-top: 2px solid #eee;
  margin: 2em 0;
}

/* 用户消息样式 */
.user-content {
  white-space: pre-wrap;
  word-wrap: break-word;
  line-height: 1.6;
}

/* 思考过程样式 */
.reasoning-section {
  margin-bottom: 12px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  overflow: hidden;
  background: #f8f9fa;
}

.reasoning-header {
  padding: 8px 12px;
  background: #e9ecef;
  cursor: pointer;
  user-select: none;
  display: flex;
  justify-content: space-between;
  align-items: center;
  transition: background 0.2s;
}

.reasoning-header:hover {
  background: #dee2e6;
}

.reasoning-title {
  font-size: 0.85rem;
  color: #495057;
  font-weight: 500;
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.reasoning-toggle {
  font-size: 0.75rem;
  color: #6c757d;
  margin-left: 8px;
}

.reasoning-content {
  padding: 12px;
  font-size: 0.9rem;
  color: #495057;
  line-height: 1.6;
  white-space: pre-wrap;
  word-wrap: break-word;
  max-height: 300px;
  overflow-y: auto;
  background: #ffffff;
  border-top: 1px solid #e0e0e0;
}

.streaming-indicator {
  margin-top: 8px;
  font-size: 0.85rem;
  opacity: 0.8;
  animation: pulse 1s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 0.5; }
  50% { opacity: 1; }
}

.input-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

textarea {
  width: 100%;
  padding: 12px;
  border: 2px solid #e0e0e0;
  border-radius: 8px;
  font-size: 14px;
  font-family: inherit;
  resize: none;
}

textarea:focus {
  outline: none;
  border-color: #667eea;
}

.button-group {
  display: flex;
  gap: 10px;
}

button {
  flex: 1;
  padding: 12px 24px;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
}

.send-button {
  background: #667eea;
  color: white;
}

.send-button:hover:not(:disabled) {
  background: #5568d3;
}

.clear-button {
  background: #f0f0f0;
  color: #333;
}

.clear-button:hover:not(:disabled) {
  background: #e0e0e0;
}

button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

@media (max-width: 768px) {
  .nav-container {
    padding: 10px 15px;
    flex-direction: column;
    gap: 10px;
  }

  .navbar h1 {
    font-size: 1.2rem;
  }

  .main-content {
    padding: 10px;
  }

  .message-content {
    max-width: 85%;
  }

  textarea {
    font-size: 16px;
  }
}
</style>