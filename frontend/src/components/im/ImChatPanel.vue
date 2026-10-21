<template>
  <div class="chat-panel">
    <!-- 头部 -->
    <div class="chat-header">
      <div class="title">
        <span class="name">{{ im.active.name }}</span>
        <span v-if="im.active.peerType === 'SINGLE'" class="sub" :class="{ on: im.active.online }">
          {{ im.active.online ? '在线' : '离线' }}
        </span>
        <span v-else class="sub">{{ im.active.memberCount || '' }}人</span>
      </div>
      <div class="actions">
        <el-tooltip v-if="im.active.peerType === 'SINGLE'" content="视频通话" placement="bottom">
          <el-button text type="success" size="small" @click="startCall">📹 通话</el-button>
        </el-tooltip>
        <el-button v-if="im.active.peerType === 'GROUP'" text type="primary" size="small"
                   @click="$emit('open-group-info')">⋯ 群信息</el-button>
        <el-button text size="small" @click="im.closeConversation()">✕</el-button>
      </div>
    </div>

    <!-- 消息区 -->
    <div ref="msgBox" class="msg-box" @scroll="onScroll">
      <div v-if="!im.historyDone" class="load-more">
        <el-button v-if="!im.loadingHistory" text size="small" @click="im.loadHistory()">查看更早消息</el-button>
        <span v-else class="loading-tip">加载中...</span>
      </div>
      <div v-if="im.activeMessages.length === 0 && !im.loadingHistory" class="no-msg">
        暂无消息，发条消息打个招呼吧 👋
      </div>

      <div v-for="(m, i) in im.activeMessages" :key="m.id || m.clientId"
           class="msg-row" :class="{ mine: isMine(m) }">
        <!-- 日期分隔 -->
        <div v-if="showDateDivider(i)" class="date-divider">{{ dateDividerText(m) }}</div>
        <div class="row-body">
          <ImAvatar :name="m.fromNickname" :id="m.fromUserId" :emoji="isMine(m) ? '' : ''" />
          <div class="bubble-col">
            <div v-if="im.active.peerType === 'GROUP' && !isMine(m)" class="sender-name">{{ m.fromNickname }}</div>
            <div class="bubble" :class="{ mine: isMine(m), failed: m.failed }" :title="formatFullTime(m.createTime)">
              <el-image v-if="m.contentType === 'IMAGE'" :src="m.content" :preview-src-list="[m.content]"
                        fit="cover" class="msg-img" preview-teleported />
              <span v-else class="text">{{ m.content }}</span>
            </div>
            <div v-if="m.pending" class="state-tip">发送中..</div>
            <div v-else-if="m.failed" class="state-tip fail">发送失败</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="input-area">
      <div class="tools">
        <el-upload :show-file-list="false" :http-request="sendImage" accept="image/*">
          <el-button text size="small">🖼️ 图片</el-button>
        </el-upload>
        <span class="hint">Enter 发送 / Shift+Enter 换行</span>
      </div>
      <textarea ref="inputRef" v-model="draft" rows="3" placeholder="输入消息..."
                @keydown.enter.exact.prevent="sendText"></textarea>
      <div class="send-row">
        <el-button type="primary" :disabled="!draft.trim() && !sendingImage" @click="sendText">
          {{ sendingImage ? '上传中...' : '发送' }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { imStore as im } from '../../im/store'
import { shopStore } from '../../shop/store'
import imApi from '../../im/api'
import { formatFullTime } from '../../im/util'
import { startOutgoingCall } from '../../im/webrtc'
import ImAvatar from './ImAvatar.vue'

defineEmits(['open-group-info'])

const msgBox = ref(null)
const inputRef = ref(null)
const draft = ref('')
const sendingImage = ref(false)
const myId = () => shopStore.user?.id

// 发起视频通话（好友单聊）
function startCall() {
  if (!im.active) return
  startOutgoingCall({
    id: im.active.peerId,
    nickname: im.active.name
  })
}

function isMine(m) {
  return String(m.fromUserId) === String(myId())
}

function sendText() {
  const content = draft.value.trim()
  if (!content) return
  im.sendMessage({ peerType: im.active.peerType, peerId: im.active.peerId, contentType: 'TEXT', content })
  draft.value = ''
  scrollToBottom()
}

async function sendImage({ file }) {
  if (!file) return
  sendingImage.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    const data = await imApi.post('/api/shop/upload/image', fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    im.sendMessage({ peerType: im.active.peerType, peerId: im.active.peerId, contentType: 'IMAGE', content: data.url })
    scrollToBottom()
  } catch (e) { /* 拦截器已提示 */ } finally {
    sendingImage.value = false
  }
}

async function onScroll() {
  const box = msgBox.value
  if (!box || im.loadingHistory || im.historyDone) return
  if (box.scrollTop < 50) {
    const prevHeight = box.scrollHeight
    const added = await im.loadHistory()
    if (added) {
      await nextTick()
      box.scrollTop = box.scrollHeight - prevHeight
    }
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (msgBox.value) {
      msgBox.value.scrollTop = msgBox.value.scrollHeight
    }
  })
}

function sameDay(a, b) {
  const d1 = new Date(a), d2 = new Date(b)
  return d1.getFullYear() === d2.getFullYear() && d1.getMonth() === d2.getMonth() && d1.getDate() === d2.getDate()
}

function showDateDivider(i) {
  if (i === 0) return true
  const cur = im.activeMessages[i], prev = im.activeMessages[i - 1]
  return !sameDay(cur.createTime, prev.createTime)
}

function dateDividerText(m) {
  const d = new Date(m.createTime)
  const pad = n => String(n).padStart(2, '0')
  const today = new Date()
  if (sameDay(d, today)) return `${pad(d.getHours())}:${pad(d.getMinutes())}`
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

// 切换会话时滚到底部
watch(() => im.active && im.active.peerType + im.active.peerId, () => scrollToBottom())
// 新消息追加时若接近底部则自动滚
watch(() => im.activeMessages.length, (n, o) => {
  const box = msgBox.value
  if (!box) return
  const nearBottom = box.scrollHeight - box.scrollTop - box.clientHeight < 150
  if (nearBottom || n === o + 1 && im.activeMessages[n - 1] && isMine(im.activeMessages[n - 1])) {
    scrollToBottom()
  }
})

onMounted(scrollToBottom)
</script>

<style scoped>
.chat-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 18px;
  border-bottom: 1px solid #ececec;
}

.title { display: flex; align-items: baseline; gap: 10px; }

.name { font-size: 16px; font-weight: 600; color: #333; }

.sub { font-size: 12px; color: #bbb; }
.sub.on { color: #67c23a; }

.msg-box {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
  background: #f6f7fb;
}

.load-more { text-align: center; margin-bottom: 8px; }
.loading-tip { font-size: 12px; color: #bbb; }

.no-msg { text-align: center; color: #bbb; padding: 60px 0; font-size: 13px; }

.date-divider {
  text-align: center;
  color: #aab;
  font-size: 11px;
  margin: 10px 0;
}

.msg-row { display: flex; flex-direction: column; }

.row-body {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}

.msg-row.mine .row-body { flex-direction: row-reverse; }

.bubble-col { max-width: 65%; display: flex; flex-direction: column; }

.msg-row.mine .bubble-col { align-items: flex-end; }

.sender-name { font-size: 12px; color: #999; margin-bottom: 3px; }

.bubble {
  background: #fff;
  border-radius: 4px 12px 12px 12px;
  padding: 9px 13px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

.bubble.mine {
  background: linear-gradient(135deg, #667eea, #764ba2);
  border-radius: 12px 4px 12px 12px;
}

.bubble.failed { background: #fef0f0; border: 1px dashed #f56c6c; }

.text {
  font-size: 14px;
  color: #333;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
}

.bubble.mine .text { color: #fff; }

.msg-img {
  width: 180px;
  height: 180px;
  border-radius: 8px;
  cursor: pointer;
  display: block;
}

.state-tip { font-size: 11px; color: #bbb; margin-top: 3px; }
.state-tip.fail { color: #f56c6c; }

.input-area {
  border-top: 1px solid #ececec;
  padding: 8px 14px 10px;
}

.tools {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.hint { font-size: 11px; color: #ccc; }

textarea {
  width: 100%;
  border: none;
  outline: none;
  resize: none;
  font-size: 14px;
  font-family: inherit;
  padding: 6px 2px;
  box-sizing: border-box;
  background: transparent;
}

.send-row {
  display: flex;
  justify-content: flex-end;
}
</style>
