import { ElNotification } from 'element-plus'
import { imStore } from './store'
import { send as wsSend } from './ws'

/**
 * WebRTC视频通话引擎（1v1好友通话）
 * 流程：主叫 getUserMedia+INVITE -> 被叫响铃ACCEPT -> OFFER/ANSWER(SDP) + ICE双向交换 -> P2P连接
 * 信令走IM WebSocket（type:"CALL"），媒体流浏览器直连不经服务器
 */

let pc = null                    // RTCPeerConnection
let localStream = null
let remoteStream = null          // MediaStream（渲染用）
let pendingCandidates = []       // setRemoteDescription前到达的对端ICE候选
let remoteDescReady = false
let durationTimer = null
let ringTimer = null
let ringAudioCtx = null
let callConnectedOnce = false    // 是否接通过（决定聊天记录文案）

const ICE_SERVERS = [
  { urls: ['stun:stun.l.google.com:19302', 'stun:stun1.l.google.com:19302'] }
]

// store -> 本模块的信令事件入口（store.handleEvent 注册）
export function onCallEvent(data) {
  if (!data) return
  const call = imStore.call
  // 只处理当前通话的信令（callId匹配），避免串线
  if (call && String(data.callId) !== String(call.callId)) return
  switch (data.action) {
    case 'ACCEPT': return onAccepted()
    case 'REJECT': return endCall('对方已拒绝', 'rejected')
    case 'CANCEL': return endCall('对方已取消', 'cancelled')
    // 服务端原样转发action，挂断信号就是HANGUP
    case 'HANGUP': return endCall('通话已结束', 'hungup')
    case 'OFFER': return onOffer(data)
    case 'ANSWER': return onAnswer(data)
    case 'ICE': return onIce(data)
    default: break
  }
}

// ==================== 主叫 ====================

export async function startOutgoingCall(peer) {
  if (imStore.call) {
    ElNotification({ title: '通话', message: '当前已在通话中', type: 'warning' })
    return
  }
  if (!navigator.mediaDevices?.getUserMedia) {
    ElNotification({ title: '无法发起通话', message: '当前环境不支持摄像头/麦克风访问（需 HTTPS 或 localhost）', type: 'error', duration: 6000 })
    return
  }
  const callId = 'call' + Date.now() + Math.floor(Math.random() * 1000)
  try {
    localStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
  } catch (e) {
    ElNotification({ title: '无法访问摄像头/麦克风', message: '请检查浏览器权限设置（' + (e.name || e.message) + '）', type: 'error', duration: 6000 })
    return
  }
  createPeer(callId)
  callConnectedOnce = false
  imStore.call = { status: 'outgoing', callId, peer, role: 'caller' }
  imStore.callLocalStream = localStream
  imStore.callRemoteStream = null
  send({ action: 'INVITE', callId })
  startRing('outgoing')
  // 45秒无人接听自动取消
  window.__callInviteTimer = setTimeout(() => {
    if (imStore.call && imStore.call.status === 'outgoing') {
      send({ action: 'CANCEL', callId })
      endCall('对方无应答', 'timeout')
    }
  }, 45000)
}

async function onAccepted() {
  if (!pc) return
  clearTimeout(window.__callInviteTimer)
  stopRing()
  imStore.call.status = 'connecting'
  const offer = await pc.createOffer()
  await pc.setLocalDescription(offer)
  send({ action: 'OFFER', sdp: pc.localDescription })
}

// ==================== 被叫 ====================

export function onIncomingInvite(data) {
  if (imStore.call) {
    // 忙线 -> 自动回拒
    wsSend({ type: 'CALL', action: 'REJECT', peerId: data.fromUserId, callId: data.callId })
    return
  }
  callConnectedOnce = false
  imStore.call = { status: 'incoming', callId: data.callId, peer: data.from, role: 'callee' }
  imStore.callLocalStream = null
  imStore.callRemoteStream = null
  startRing('incoming')
}

export async function acceptIncomingCall() {
  const call = imStore.call
  if (!call || call.status !== 'incoming') return
  stopRing()
  try {
    localStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
  } catch (e) {
    ElNotification({ title: '无法访问摄像头/麦克风', message: '请检查浏览器权限设置（' + (e.name || e.message) + '）', type: 'error', duration: 6000 })
    send({ action: 'REJECT', callId: call.callId })
    endCall('设备不可用', 'device-error')
    return
  }
  createPeer(call.callId)
  imStore.call.status = 'connecting'
  imStore.callLocalStream = localStream
  send({ action: 'ACCEPT', callId: call.callId })
}

export function rejectIncomingCall() {
  const call = imStore.call
  if (!call) return
  stopRing()
  send({ action: 'REJECT', callId: call.callId })
  cleanup()
  imStore.call = null
}

async function onOffer(data) {
  if (!pc) return
  await pc.setRemoteDescription(new RTCSessionDescription(data.sdp))
  remoteDescReady = true
  await flushCandidates()
  const answer = await pc.createAnswer()
  await pc.setLocalDescription(answer)
  send({ action: 'ANSWER', sdp: pc.localDescription })
}

// ==================== 双向 ====================

async function onAnswer(data) {
  if (!pc) return
  await pc.setRemoteDescription(new RTCSessionDescription(data.sdp))
  remoteDescReady = true
  await flushCandidates()
}

async function onIce(data) {
  if (!pc) return
  if (!remoteDescReady) {
    pendingCandidates.push(data.candidate)
    return
  }
  try {
    await pc.addIceCandidate(new RTCIceCandidate(data.candidate))
  } catch (e) {
    console.warn('ICE候选添加失败', e)
  }
}

export function hangup() {
  const call = imStore.call
  if (!call) return
  send({ action: 'HANGUP', callId: call.callId })
  endCall(null, 'local-hangup')
}

/**
 * 结束/异常收尾：清理资源并写一条通话记录到聊天
 */
function endCall(toastMsg, reason) {
  const call = imStore.call
  clearTimeout(window.__callInviteTimer)
  stopRing()
  if (call && call.peer && call.role === 'caller') {
    // 聊天记录：主叫方写入一条通话摘要
    const connected = callConnectedOnce
    const dur = connected ? formatDuration(imStore.callDuration) : ''
    imStore.sendCallRecord(call.peer.id, connected ? `[视频通话] 通话时长 ${dur}` : '[视频通话] 未接通')
  }
  cleanup()
  imStore.call = null
  if (toastMsg) {
    ElNotification({ title: '视频通话', message: toastMsg, type: 'info', duration: 3000 })
  }
}

function cleanup() {
  if (durationTimer) { clearInterval(durationTimer); durationTimer = null }
  if (pc) {
    try { pc.close() } catch (e) { /* 忽略 */ }
    pc = null
  }
  if (localStream) {
    localStream.getTracks().forEach(t => t.stop())
    localStream = null
  }
  remoteStream = null
  remoteDescReady = false
  pendingCandidates = []
  imStore.callDuration = 0
  imStore.callLocalStream = null
  imStore.callRemoteStream = null
}

function createPeer(callId) {
  pc = new RTCPeerConnection({ iceServers: ICE_SERVERS })
  pc.onicecandidate = e => {
    if (e.candidate && imStore.call) {
      send({ action: 'ICE', callId, candidate: e.candidate.toJSON() })
    }
  }
  pc.ontrack = e => {
    remoteStream = e.streams[0]
    imStore.callRemoteStream = remoteStream
  }
  pc.onconnectionstatechange = () => {
    if (!pc || !imStore.call) return
    const state = pc.connectionState
    if (state === 'connected') {
      callConnectedOnce = true
      imStore.call.status = 'active'
      imStore.callDuration = 0
      const start = Date.now()
      if (durationTimer) clearInterval(durationTimer)
      durationTimer = setInterval(() => { imStore.callDuration = Math.floor((Date.now() - start) / 1000) }, 1000)
    } else if (state === 'failed') {
      send({ action: 'HANGUP', callId })
      endCall('连接失败（网络穿透不成功）', 'failed')
    } else if (state === 'disconnected') {
      // 短暂失联等ICE重启或最终failed/connected
    }
  }
  if (localStream) {
    localStream.getTracks().forEach(t => pc.addTrack(t, localStream))
  }
}

function send(payload) {
  const call = imStore.call
  if (call && call.peer) {
    wsSend({ type: 'CALL', peerId: call.peer.id, ...payload })
  }
}

async function flushCandidates() {
  const list = pendingCandidates
  pendingCandidates = []
  for (const c of list) {
    try {
      await pc.addIceCandidate(new RTCIceCandidate(c))
    } catch (e) { /* 忽略 */ }
  }
}

// ==================== 响铃（Web Audio合成，无需音频文件） ====================

function beep(freq, when, duration) {
  const ctx = ensureAudio()
  if (!ctx) return
  const osc = ctx.createOscillator()
  const gain = ctx.createGain()
  osc.frequency.value = freq
  osc.type = 'sine'
  gain.gain.setValueAtTime(0.0001, ctx.currentTime + when)
  gain.gain.exponentialRampToValueAtTime(0.12, ctx.currentTime + when + 0.03)
  gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + when + duration)
  osc.connect(gain).connect(ctx.destination)
  osc.start(ctx.currentTime + when)
  osc.stop(ctx.currentTime + when + duration + 0.05)
}

function ensureAudio() {
  try {
    if (!ringAudioCtx) {
      const AC = window.AudioContext || window.webkitAudioContext
      ringAudioCtx = new AC()
    }
    if (ringAudioCtx.state === 'suspended') ringAudioCtx.resume()
    return ringAudioCtx
  } catch (e) {
    return null
  }
}

function startRing(kind) {
  stopRing()
  ringTimer = setInterval(() => {
    if (kind === 'incoming') {
      beep(660, 0, 0.4)
      beep(550, 0.5, 0.4)
    } else {
      beep(440, 0, 0.3)
    }
  }, kind === 'incoming' ? 2000 : 3000)
  // 立即响一声
  if (kind === 'incoming') { beep(660, 0, 0.4); beep(550, 0.5, 0.4) }
  else beep(440, 0, 0.3)
}

function stopRing() {
  if (ringTimer) { clearInterval(ringTimer); ringTimer = null }
}

function formatDuration(sec) {
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return m > 0 ? `${m}分${String(s).padStart(2, '0')}秒` : `${s}秒`
}

/**
 * WS断开时强制收尾（ImCallDialog watch兜底调用）
 */
export function forceCleanupCall() {
  if (imStore.call) {
    cleanup()
    imStore.call = null
    stopRing()
    ElNotification({ title: '视频通话', message: '连接断开，通话已结束', type: 'warning', duration: 3000 })
  }
}
