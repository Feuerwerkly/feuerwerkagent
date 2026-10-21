import { ElMessage } from 'element-plus'
import { shopStore } from '../shop/store'
import { imStore } from './store'

/**
 * IM WebSocket 单例：连接 /ws/im?token=，断线指数退避重连，25s心跳
 */
let socket = null
let heartbeatTimer = null
let retryTimer = null
let failures = 0
let manualClosed = false

function wsUrl() {
  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${proto}://${window.location.host}/ws/im?token=${encodeURIComponent(shopStore.token)}`
}

export function connect() {
  if (!shopStore.token) return
  manualClosed = false
  openSocket()
}

function openSocket() {
  if (!shopStore.token || (socket && socket.readyState === WebSocket.OPEN)) {
    return
  }
  try {
    socket = new WebSocket(wsUrl())
  } catch (e) {
    scheduleRetry()
    return
  }

  socket.onopen = () => {
    failures = 0
    imStore.connected = true
    startHeartbeat()
    // (重)连后刷新全部数据（断线期间的消息/事件通过REST兜底）
    imStore.refreshAll()
  }

  socket.onmessage = event => {
    try {
      const data = JSON.parse(event.data)
      imStore.handleEvent(data)
    } catch (e) {
      console.warn('IM消息解析失败', event.data)
    }
  }

  socket.onclose = () => {
    imStore.connected = false
    stopHeartbeat()
    socket = null
    if (!manualClosed && shopStore.token) {
      scheduleRetry()
    }
  }

  socket.onerror = () => {
    // onclose 会随之触发，统一在 onclose 处理重连
  }
}

function scheduleRetry() {
  if (retryTimer) return
  failures++
  if (failures > 5) {
    ElMessage.error('IM连接失败，登录可能已过期，请重新登录')
    shopStore.clearAuth()
    shopStore.loginVisible = true
    failures = 0
    return
  }
  const delay = Math.min(1000 * Math.pow(2, failures - 1), 15000)
  retryTimer = setTimeout(() => {
    retryTimer = null
    openSocket()
  }, delay)
}

export function disconnect() {
  manualClosed = true
  stopHeartbeat()
  if (retryTimer) {
    clearTimeout(retryTimer)
    retryTimer = null
  }
  if (socket) {
    socket.close()
    socket = null
  }
  imStore.connected = false
}

export function send(payload) {
  if (socket && socket.readyState === WebSocket.OPEN) {
    socket.send(JSON.stringify(payload))
    return true
  }
  return false
}

function startHeartbeat() {
  stopHeartbeat()
  heartbeatTimer = setInterval(() => {
    send({ type: 'PING' })
  }, 25000)
}

function stopHeartbeat() {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
}
