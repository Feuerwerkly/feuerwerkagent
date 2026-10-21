<template>
  <div class="im-app">
    <!-- 未登录提示 -->
    <div v-if="!store.isLoggedIn()" class="need-login">
      <div class="login-card">
        <div class="big">💬</div>
        <h3>即时通讯</h3>
        <p>登录后可以添加好友、发起群聊、实时收发消息</p>
        <el-button type="primary" size="large" @click="store.loginVisible = true">立即登录</el-button>
      </div>
    </div>

    <template v-else>
      <!-- 左侧栏 -->
      <aside class="im-sidebar">
        <div class="my-profile">
          <ImAvatar :name="meName" :id="store.user.id" />
          <div class="me-info">
            <div class="me-name">{{ meName }}</div>
            <div class="conn-state" :class="im.connected ? 'on' : 'off'">
              {{ im.connected ? '● 已连接' : '○ 连接中...' }}
            </div>
          </div>
        </div>

        <div class="side-tabs">
          <button v-for="t in tabs" :key="t.key" class="side-tab"
                  :class="{ active: im.sidebarTab === t.key }" @click="im.sidebarTab = t.key">
            {{ t.label }}
            <span v-if="t.key === 'requests' && im.requests.length" class="badge">{{ im.requests.length }}</span>
          </button>
        </div>

        <!-- 会话列表 -->
        <div v-show="im.sidebarTab === 'conversations'" class="list">
          <div v-if="im.conversations.length === 0" class="empty">暂无会话，去「好友」里发起聊天吧</div>
          <div v-for="c in im.conversations" :key="c.peerType + '-' + c.peerId"
               class="list-item" :class="{ active: isActive(c) }" @click="openConv(c)">
            <div class="avatar-wrap">
              <ImAvatar :name="c.name" :id="c.peerId" :emoji="c.peerType === 'GROUP' ? '👥' : ''" />
              <span v-if="c.peerType === 'SINGLE' && c.online" class="online-dot"></span>
            </div>
            <div class="item-main">
              <div class="row1">
                <span class="name">{{ c.name }}</span>
                <span class="time">{{ formatTime(c.lastMessage?.createTime) }}</span>
              </div>
              <div class="row2">
                <span class="preview">{{ previewOf(c) }}</span>
                <span v-if="c.unread > 0" class="unread">{{ c.unread > 99 ? '99+' : c.unread }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 好友 -->
        <div v-show="im.sidebarTab === 'friends'" class="list">
          <div class="search-box">
            <el-input v-model="searchKw" placeholder="搜索用户名/昵称加好友" size="small" clearable
                      @keyup.enter="doSearch" @clear="searchResults = []">
              <template #append>
                <el-button :icon="Search" @click="doSearch" />
              </template>
            </el-input>
          </div>
          <!-- 搜索结果 -->
          <template v-if="searchResults.length">
            <div class="section-title">搜索结果</div>
            <div v-for="u in searchResults" :key="u.id" class="list-item">
              <ImAvatar :name="u.nickname" :id="u.id" />
              <div class="item-main">
                <div class="row1"><span class="name">{{ u.nickname }}</span></div>
                <div class="row2"><span class="preview">@{{ u.username }} {{ u.online ? '· 在线' : '' }}</span></div>
              </div>
              <el-button v-if="u.relation === 'NONE'" size="small" type="primary" plain @click="addFriend(u)">加好友</el-button>
              <el-button v-else-if="u.relation === 'FRIEND'" size="small" type="success" plain @click="chatWith(u)">聊天</el-button>
              <span v-else-if="u.relation === 'PENDING_OUT'" class="relation-tag">待验证</span>
              <el-button v-else-if="u.relation === 'PENDING_IN'" size="small" type="warning" plain
                         @click="im.sidebarTab = 'requests'">去处理</el-button>
            </div>
          </template>
          <div class="section-title">我的好友（{{ im.friends.length }}）</div>
          <div v-if="im.friends.length === 0" class="empty">还没有好友，上方搜索添加</div>
          <div v-for="f in im.friends" :key="f.id" class="list-item" @click="chatWith(f)">
            <div class="avatar-wrap">
              <ImAvatar :name="f.nickname" :id="f.id" />
              <span v-if="f.online" class="online-dot"></span>
            </div>
            <div class="item-main">
              <div class="row1"><span class="name">{{ f.nickname }}</span></div>
              <div class="row2"><span class="preview">{{ f.online ? '在线' : '离线' }}</span></div>
            </div>
            <el-button size="small" text type="danger" @click.stop="removeFriend(f)">删除</el-button>
          </div>
        </div>

        <!-- 群聊 -->
        <div v-show="im.sidebarTab === 'groups'" class="list">
          <div class="search-box">
            <el-button type="primary" size="small" style="width:100%" @click="createGroupVisible = true">
              ＋ 发起群聊
            </el-button>
          </div>
          <div v-if="im.groups.length === 0" class="empty">还没有群聊，点击上方发起</div>
          <div v-for="g in im.groups" :key="g.id" class="list-item" @click="openConv({ peerType: 'GROUP', peerId: g.id, name: g.name })">
            <ImAvatar name="" :id="g.id" emoji="👥" />
            <div class="item-main">
              <div class="row1">
                <span class="name">{{ g.name }}</span>
                <span class="time">{{ g.memberCount }}人</span>
              </div>
              <div class="row2"><span class="preview">{{ g.myRole === 'OWNER' ? '我是群主' : '我是成员' }}</span></div>
            </div>
          </div>
        </div>

        <!-- 好友申请 -->
        <div v-show="im.sidebarTab === 'requests'" class="list">
          <div v-if="im.requests.length === 0" class="empty">暂无待处理的好友申请</div>
          <div v-for="r in im.requests" :key="r.id" class="list-item">
            <ImAvatar :name="r.from?.nickname" :id="r.from?.id" />
            <div class="item-main">
              <div class="row1"><span class="name">{{ r.from?.nickname }}</span></div>
              <div class="row2"><span class="preview">{{ r.message || '请求添加你为好友' }}</span></div>
            </div>
            <div class="req-btns">
              <el-button size="small" type="success" @click="acceptReq(r)">同意</el-button>
              <el-button size="small" plain @click="rejectReq(r)">拒绝</el-button>
            </div>
          </div>
        </div>
      </aside>

      <!-- 聊天区 -->
      <section class="im-main">
        <ImChatPanel v-if="im.active" @open-group-info="groupInfoVisible = true" />
        <div v-else class="no-chat">
          <div class="big">💬</div>
          <p>选择一个会话开始聊天</p>
        </div>
      </section>
    </template>

    <!-- 建群弹窗 -->
    <ImCreateGroupDialog v-model="createGroupVisible" @created="onGroupCreated" />

    <!-- 群信息弹窗 -->
    <ImGroupInfoDialog v-model="groupInfoVisible" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { shopStore as store } from '../../shop/store'
import { imStore as im } from '../../im/store'
import imApi from '../../im/api'
import { formatTime } from '../../im/util'
import ImAvatar from './ImAvatar.vue'
import ImChatPanel from './ImChatPanel.vue'
import ImCreateGroupDialog from './ImCreateGroupDialog.vue'
import ImGroupInfoDialog from './ImGroupInfoDialog.vue'

const tabs = [
  { key: 'conversations', label: '💬 会话' },
  { key: 'friends', label: '👥 好友' },
  { key: 'groups', label: '👨‍👩‍👧 群聊' },
  { key: 'requests', label: '🔔 申请' }
]

const searchKw = ref('')
const searchResults = ref([])
const createGroupVisible = ref(false)
const groupInfoVisible = ref(false)

const meName = computed(() => store.user?.nickname || store.user?.username || '我')

function isActive(c) {
  return im.active && im.active.peerType === c.peerType && String(im.active.peerId) === String(c.peerId)
}

function previewOf(c) {
  const last = c.lastMessage
  if (!last || !last.createTime) return '暂无消息'
  const prefix = c.peerType === 'GROUP' && last.fromNickname ? `${last.fromNickname}: ` : ''
  return prefix + (last.content || '')
}

async function openConv(c) {
  await im.openConversation(c.peerType, c.peerId, { name: c.name })
}

async function chatWith(f) {
  await im.openConversation('SINGLE', f.id, { name: f.nickname, online: f.online })
}

async function doSearch() {
  if (!searchKw.value.trim()) {
    searchResults.value = []
    return
  }
  try {
    searchResults.value = await imApi.get('/contacts/search', { params: { keyword: searchKw.value.trim() } }) || []
  } catch (e) { /* 拦截器已提示 */ }
}

async function addFriend(u) {
  try {
    const { value } = await ElMessageBox.prompt(
      `向 ${u.nickname} 发送好友申请`, '添加好友',
      { confirmButtonText: '发送', cancelButtonText: '取消', inputValue: `我是${meName.value}，加个好友吧~` }
    )
    await imApi.post('/contacts/requests', { toUserId: u.id, message: value })
    ElMessage.success('申请已发送，等待对方验证')
    u.relation = 'PENDING_OUT'
  } catch (e) { /* 取消或拦截器已提示 */ }
}

async function acceptReq(r) {
  try {
    await imApi.post(`/contacts/requests/${r.id}/accept`)
    ElMessage.success(`已和 ${r.from?.nickname} 成为好友`)
    im.refreshFriends()
    im.refreshConversations()
    im.refreshRequests()
  } catch (e) { /* 拦截器已提示 */ }
}

async function rejectReq(r) {
  try {
    await imApi.post(`/contacts/requests/${r.id}/reject`)
    im.refreshRequests()
  } catch (e) { /* 拦截器已提示 */ }
}

async function removeFriend(f) {
  try {
    await ElMessageBox.confirm(`确定删除好友「${f.nickname}」吗？聊天记录将无法继续`, '删除好友', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await imApi.delete(`/contacts/friends/${f.id}`)
    ElMessage.success('已删除好友')
    im.refreshFriends()
    im.refreshConversations()
  } catch (e) { /* 拦截器已提示 */ }
}

async function onGroupCreated(group) {
  await im.refreshGroups()
  await im.refreshConversations()
  await im.openConversation('GROUP', group.id, { name: group.name })
  ElNotification({ title: '群聊已创建', message: `「${group.name}」创建成功，可以开始群聊啦`, type: 'success' })
}

onMounted(() => {
  im.imVisible = true
  im.refreshAll()
  // 通知栏点击后跳转聊天
  window.addEventListener('im-open-chat', () => {})
})

onUnmounted(() => {
  im.imVisible = false
})
</script>

<style scoped>
.im-app {
  display: flex;
  height: calc(100vh - 110px);
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
}

.need-login {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-card {
  text-align: center;
  padding: 40px;
}

.big { font-size: 64px; }
.login-card h3 { margin: 12px 0 8px; color: #333; }
.login-card p { color: #999; margin-bottom: 20px; font-size: 14px; }

/* ============ 左侧栏 ============ */
.im-sidebar {
  width: 300px;
  border-right: 1px solid #ececec;
  display: flex;
  flex-direction: column;
  background: #fafbfc;
}

.my-profile {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px;
  border-bottom: 1px solid #ececec;
}

.me-name { font-size: 15px; font-weight: 600; color: #333; }

.conn-state { font-size: 12px; margin-top: 2px; }
.conn-state.on { color: #67c23a; }
.conn-state.off { color: #e6a23c; }

.side-tabs {
  display: flex;
  border-bottom: 1px solid #ececec;
}

.side-tab {
  flex: 1;
  padding: 10px 2px;
  border: none;
  background: none;
  font-size: 13px;
  color: #666;
  cursor: pointer;
  position: relative;
  transition: color 0.2s;
}

.side-tab.active { color: #667eea; font-weight: 600; }
.side-tab.active::after {
  content: '';
  position: absolute;
  left: 20%;
  right: 20%;
  bottom: 0;
  height: 2px;
  background: #667eea;
  border-radius: 2px;
}

.badge {
  position: absolute;
  top: 4px;
  right: 10px;
  background: #f56c6c;
  color: #fff;
  font-size: 10px;
  border-radius: 8px;
  padding: 0 5px;
  min-width: 14px;
  line-height: 14px;
}

.list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.empty {
  color: #bbb;
  font-size: 13px;
  text-align: center;
  padding: 30px 10px;
}

.search-box { padding: 8px; }

.section-title {
  font-size: 12px;
  color: #999;
  padding: 8px 10px 4px;
}

.list-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s;
}

.list-item:hover { background: #eef0f5; }
.list-item.active { background: #e4e9ff; }

.avatar-wrap { position: relative; }

.online-dot {
  position: absolute;
  right: -2px;
  bottom: -2px;
  width: 11px;
  height: 11px;
  background: #67c23a;
  border: 2px solid #fafbfc;
  border-radius: 50%;
}

.item-main {
  flex: 1;
  min-width: 0;
}

.row1 {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.name {
  font-size: 14px;
  color: #333;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.time { font-size: 11px; color: #bbb; flex-shrink: 0; }

.row2 {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 3px;
  gap: 6px;
}

.preview {
  font-size: 12px;
  color: #999;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.unread {
  background: #f56c6c;
  color: #fff;
  font-size: 11px;
  border-radius: 9px;
  padding: 0 6px;
  line-height: 16px;
  flex-shrink: 0;
}

.relation-tag { font-size: 12px; color: #bbb; }

.req-btns { display: flex; flex-direction: column; gap: 4px; }

/* ============ 聊天区 ============ */
.im-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.no-chat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #bbb;
}

.no-chat .big { font-size: 72px; margin-bottom: 10px; }
</style>
