import { reactive } from 'vue'
import { ElNotification } from 'element-plus'
import imApi from './api'
import { send as wsSend } from './ws'
import { onCallEvent, onIncomingInvite } from './webrtc'
import { shopStore } from '../shop/store'

/**
 * IM全局状态：会话/好友/申请/群 + WS事件处理 + 消息收发 + 视频通话
 */
export const imStore = reactive({
  connected: false,
  // IM界面是否可见（控制消息通知）
  imVisible: false,
  sidebarTab: 'conversations', // conversations|friends|groups|requests
  conversations: [],
  friends: [],
  requests: [],
  groups: [],

  // ==================== 视频通话状态 ====================
  // call: {status: outgoing|incoming|connecting|active, callId, peer, role: caller|callee}
  call: null,
  callLocalStream: null,
  callRemoteStream: null,
  callDuration: 0,

  // 当前打开的会话
  active: null, // {peerType, peerId, name, ...}
  activeMessages: [],
  loadingHistory: false,
  historyDone: false,

  // ==================== 数据加载 ====================

  async refreshAll() {
    if (!shopStore.isLoggedIn()) return
    await Promise.all([this.refreshConversations(), this.refreshFriends(),
      this.refreshRequests(), this.refreshGroups()])
  },

  async refreshConversations() {
    try {
      this.conversations = await imApi.get('/chat/conversations') || []
    } catch (e) { /* 拦截器已提示 */ }
  },

  async refreshFriends() {
    try {
      this.friends = await imApi.get('/contacts/friends') || []
    } catch (e) { /* 拦截器已提示 */ }
  },

  async refreshRequests() {
    try {
      const all = await imApi.get('/contacts/requests') || []
      this.requests = all.filter(r => r.status === 0)
    } catch (e) { /* 拦截器已提示 */ }
  },

  async refreshGroups() {
    try {
      this.groups = await imApi.get('/groups') || []
    } catch (e) { /* 拦截器已提示 */ }
  },

  unreadTotal() {
    return this.conversations.reduce((s, c) => s + (Number(c.unread) || 0), 0)
  },

  // ==================== 会话与消息 ====================

  findConversation(peerType, peerId) {
    return this.conversations.find(c => c.peerType === peerType && String(c.peerId) === String(peerId))
  },

  /**
   * 打开会话：加载历史 + 清未读
   */
  async openConversation(peerType, peerId, extraInfo) {
    let conv = this.findConversation(peerType, peerId)
    if (!conv) {
      // 可能是刚加的好友/新群，会话列表还没刷新
      conv = { peerType, peerId, unread: 0, lastMessage: {}, ...extraInfo }
      this.conversations.unshift(conv)
      this.refreshConversations()
    }
    this.active = { ...conv }
    this.activeMessages = []
    this.historyDone = false
    await this.loadHistory()
    if (conv.unread > 0) {
      conv.unread = 0
      imApi.post('/chat/read', { peerType, peerId }).catch(() => {})
    }
  },

  async loadHistory() {
    if (!this.active || this.loadingHistory) return
    this.loadingHistory = true
    try {
      const beforeId = this.activeMessages.length ? this.activeMessages[0].id : null
      const list = await imApi.get('/chat/messages', {
        params: {
          peerType: this.active.peerType,
          peerId: this.active.peerId,
          beforeId: beforeId || undefined
        }
      }) || []
      if (list.length < 50) this.historyDone = true
      if (list.length) {
        this.activeMessages = [...list, ...this.activeMessages]
      }
      return list.length
    } catch (e) {
      return 0
    } finally {
      this.loadingHistory = false
    }
  },

  closeConversation() {
    this.active = null
    this.activeMessages = []
  },

  /**
   * 发送消息（乐观UI：先上屏pending，ACK到达后确认，ERROR时标失败）
   */
  sendMessage({ peerType, peerId, contentType = 'TEXT', content }) {
    if (!this.active) return
    const clientId = 'c' + Date.now() + Math.floor(Math.random() * 1000)
    const optimistic = {
      clientId,
      pending: true,
      failed: false,
      peerType,
      peerId,
      fromUserId: shopStore.user.id,
      fromNickname: shopStore.user.nickname || shopStore.user.username,
      contentType,
      content,
      createTime: new Date().toISOString().slice(0, 19)
    }
    this.activeMessages.push(optimistic)
    // 本地会话列表同步预览
    const conv = this.findConversation(peerType, peerId)
    if (conv) {
      conv.lastMessage = { content: contentType === 'IMAGE' ? '[图片]' : content, contentType, createTime: optimistic.createTime }
    }
    const ok = wsSend({ type: 'SEND', peerType, peerId, contentType, content, clientId })
    if (!ok) {
      optimistic.pending = false
      optimistic.failed = true
    }
  },

  // ==================== WS事件处理 ====================

  handleEvent(event) {
    const { type, data } = event || {}
    switch (type) {
      case 'MESSAGE': return this.onMessage(data)
      case 'ACK': return this.onAck(data)
      case 'ERROR': return this.onError(data)
      case 'PRESENCE': return this.onPresence(data)
      case 'FRIEND_REQUEST': return this.onFriendRequest(data)
      case 'FRIEND_ACCEPTED': return this.onFriendAccepted(data)
      case 'GROUP_EVENT': return this.onGroupEvent(data)
      case 'CALL': return this.onCallSignal(data)
      default: break
    }
  },

  /**
   * 视频通话信令：INVITE需要无call也能进入（响铃），其余走webrtc状态机
   */
  onCallSignal(data) {
    if (!data) return
    if (data.action === 'INVITE') {
      onIncomingInvite(data)
      ElNotification({
        title: '视频通话来电',
        message: `${data.from?.nickname || '好友'} 邀请你视频通话`,
        type: 'success',
        duration: 0,
        onClick: () => { window.dispatchEvent(new CustomEvent('im-open-chat')) }
      })
    } else {
      onCallEvent(data)
    }
  },

  /**
   * 通话结束后向聊天写入一条摘要记录（静默发送）
   */
  sendCallRecord(peerId, content) {
    wsSend({ type: 'SEND', peerType: 'SINGLE', peerId, contentType: 'TEXT', content })
    const conv = this.findConversation('SINGLE', peerId)
    if (conv) {
      conv.lastMessage = { content, contentType: 'TEXT', createTime: new Date().toISOString().slice(0, 19) }
    }
  },

  onMessage(msg) {
    if (!msg) return
    const conv = this.findConversation(msg.peerType, msg.peerId)
    if (conv) {
      conv.lastMessage = {
        content: msg.contentType === 'IMAGE' ? '[图片]' : msg.content,
        contentType: msg.contentType,
        fromUserId: msg.fromUserId,
        createTime: msg.createTime,
        fromNickname: msg.fromNickname
      }
      // 置顶
      const idx = this.conversations.indexOf(conv)
      if (idx > 0) {
        this.conversations.splice(idx, 1)
        this.conversations.unshift(conv)
      }
    }
    // 打开中的会话：直接上屏并自动已读
    const isActive = this.active
      && this.active.peerType === msg.peerType
      && String(this.active.peerId) === String(msg.peerId)
    if (isActive) {
      this.activeMessages.push(msg)
      imApi.post('/chat/read', { peerType: msg.peerType, peerId: msg.peerId }).catch(() => {})
    } else {
      if (conv) conv.unread = (Number(conv.unread) || 0) + 1
      // 群消息在预览里带上发送者
      if (conv && msg.peerType === 'GROUP') {
        conv.lastMessage.fromNickname = msg.fromNickname
      }
    }
    // 非IM视图或非当前会话 -> 桌面通知
    if (!(this.imVisible && isActive)) {
      const title = msg.peerType === 'GROUP' ? `${msg.fromNickname}（${this.conversationName(msg)}）` : (msg.fromNickname || '新消息')
      ElNotification({
        title,
        message: msg.contentType === 'IMAGE' ? '[图片]' : String(msg.content).slice(0, 50),
        type: 'info',
        duration: 4000,
        onClick: () => {
          this.openConversation(msg.peerType, msg.peerId).then(() => {
            window.dispatchEvent(new CustomEvent('im-open-chat'))
          })
        }
      })
    }
  },

  conversationName(msg) {
    const conv = this.findConversation(msg.peerType, msg.peerId)
    return conv ? conv.name : '群聊'
  },

  onAck(data) {
    if (!data) return
    const idx = this.activeMessages.findIndex(m => m.clientId && m.clientId === data.clientId)
    if (idx >= 0) {
      this.activeMessages.splice(idx, 1, { ...data, pending: false, failed: false })
    }
  },

  onError(data) {
    if (data && data.clientId) {
      const msg = this.activeMessages.find(m => m.clientId === data.clientId)
      if (msg) {
        msg.pending = false
        msg.failed = true
      }
    }
  },

  onPresence(data) {
    if (!data) return
    const friend = this.friends.find(f => String(f.id) === String(data.userId))
    if (friend) friend.online = data.online
    const conv = this.findConversation('SINGLE', data.userId)
    if (conv) conv.online = data.online
  },

  onFriendRequest(data) {
    if (!data) return
    this.refreshRequests()
    ElNotification({
      title: '好友申请',
      message: `${data.from?.nickname || data.from?.username || '有人'} 请求添加你为好友：${data.message || '无留言'}`,
      type: 'info',
      duration: 5000
    })
  },

  onFriendAccepted(data) {
    if (!data) return
    this.refreshFriends()
    this.refreshConversations()
    ElNotification({
      title: '好友申请已通过',
      message: `${data.friend?.nickname || data.friend?.username || '对方'} 已通过你的好友申请，可以开始聊天啦`,
      type: 'success',
      duration: 5000
    })
  },

  onGroupEvent(data) {
    if (!data) return
    const group = data.group || {}
    const actionText = {
      created: `群「${group.name}」已创建`,
      invited: `你被邀请加入群「${group.name}」`,
      removed: `你被移出群「${group.name}」`,
      disbanded: `群「${group.name}」已解散`,
      updated: `群「${group.name}」信息已更新`
    }[data.action] || '群聊有更新'
    this.refreshGroups()
    this.refreshConversations()
    ElNotification({
      title: '群聊通知',
      message: actionText,
      type: data.action === 'removed' || data.action === 'disbanded' ? 'warning' : 'info',
      duration: 5000
    })
  }
})
