<template>
  <el-dialog :model-value="!!call" :show-close="false" :close-on-click-modal="false"
             :close-on-press-escape="false" width="640px" align-center class="call-dialog"
             :modal="true" top="8vh">
    <div v-if="call" class="call-box">
      <!-- ============ 来电 / 去电 / 连接中（无视频） ============ -->
      <div v-if="call.status !== 'active'" class="call-stage">
        <div class="avatar-ring" :class="{ ringing: call.status === 'incoming' }">
          <ImAvatar :name="call.peer?.nickname" :id="call.peer?.id" />
        </div>
        <div class="peer-name">{{ call.peer?.nickname || '对方' }}</div>
        <div class="call-state">
          {{ stateText }}
        </div>

        <!-- 本端预览（主叫已拿到媒体流） -->
        <video v-if="localVideoOn" ref="localPreviewRef" class="local-preview" autoplay muted playsinline></video>

        <div class="call-btns">
          <!-- 来电：接听/拒绝 -->
          <template v-if="call.status === 'incoming'">
            <button class="call-btn accept" title="接听" @click="accept">📞</button>
            <button class="call-btn reject" title="拒绝" @click="reject">📵</button>
          </template>
          <!-- 去电/连接中：取消 -->
          <button v-else class="call-btn reject" title="取消" @click="cancel">✕</button>
        </div>
      </div>

      <!-- ============ 通话中（视频区） ============ -->
      <div v-else class="video-stage">
        <video ref="remoteVideoRef" class="remote-video" autoplay playsinline></video>
        <div class="remote-mic-tip" v-if="remoteNoAudio">对方未开麦克风</div>
        <video ref="localVideoRef" class="local-video" autoplay muted playsinline></video>
        <div class="video-info">
          <span>{{ call.peer?.nickname }}</span>
          <span class="duration">{{ durationText }}</span>
        </div>
        <div class="video-btns">
          <button class="call-btn reject big" title="挂断" @click="hangup">📵</button>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { imStore as im } from '../../im/store'
import { acceptIncomingCall, rejectIncomingCall, hangup, forceCleanupCall } from '../../im/webrtc'
import ImAvatar from './ImAvatar.vue'

const localPreviewRef = ref(null)
const remoteVideoRef = ref(null)
const localVideoRef = ref(null)

const call = computed(() => im.call)

const stateText = computed(() => {
  if (!im.call) return ''
  switch (im.call.status) {
    case 'incoming': return '邀请你视频通话...'
    case 'outgoing': return '等待对方接听...'
    case 'connecting': return '正在建立连接...'
    default: return ''
  }
})

const localVideoOn = computed(() => !!im.callLocalStream)

const remoteNoAudio = computed(() => {
  const s = im.callRemoteStream
  return s && s.getAudioTracks().length === 0
})

const durationText = computed(() => {
  const sec = im.callDuration
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

function accept() { acceptIncomingCall() }
function reject() { rejectIncomingCall() }
function cancel() { hangup() }

// 流变化时绑到video元素（srcObject必须JS赋值，不能模板绑定）
watch(() => im.callLocalStream, stream => {
  nextTick(() => {
    const targets = [localPreviewRef.value, localVideoRef.value]
    targets.forEach(v => { if (v) v.srcObject = stream })
  })
})

watch(() => im.callRemoteStream, stream => {
  nextTick(() => {
    if (remoteVideoRef.value) {
      remoteVideoRef.value.srcObject = stream
      remoteVideoRef.value.play().catch(() => {})
    }
  })
})

// WS断开 -> 通话强制收尾
watch(() => im.connected, connected => {
  if (!connected && im.call) {
    forceCleanupCall()
  }
})
</script>

<style scoped>
.call-box { min-height: 320px; }

/* ============ 呼叫阶段 ============ */
.call-stage {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 26px 0 10px;
  position: relative;
}

.avatar-ring {
  padding: 6px;
  border-radius: 14px;
  border: 3px solid #e6e9f5;
}

.avatar-ring.ringing {
  animation: ring 1s infinite;
}

@keyframes ring {
  0%, 100% { box-shadow: 0 0 0 0 rgba(103, 194, 58, 0.5); border-color: #67c23a; }
  50% { box-shadow: 0 0 0 14px rgba(103, 194, 58, 0); border-color: #95d475; }
}

.peer-name { font-size: 19px; font-weight: 600; color: #333; margin-top: 14px; }

.call-state { font-size: 13px; color: #999; margin: 8px 0 26px; }

.local-preview {
  position: absolute;
  right: 16px;
  top: 16px;
  width: 130px;
  aspect-ratio: 4/3;
  object-fit: cover;
  border-radius: 10px;
  background: #000;
  transform: scaleX(-1);
}

.call-btns { display: flex; gap: 46px; }

.call-btn {
  width: 62px;
  height: 62px;
  border-radius: 50%;
  border: none;
  font-size: 26px;
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}

.call-btn:hover { transform: scale(1.1); }

.call-btn.accept {
  background: linear-gradient(135deg, #67c23a, #529b2e);
  color: #fff;
  box-shadow: 0 6px 18px rgba(103, 194, 58, 0.4);
}

.call-btn.reject {
  background: linear-gradient(135deg, #f56c6c, #c45656);
  color: #fff;
  box-shadow: 0 6px 18px rgba(245, 108, 108, 0.4);
}

.call-btn.big { width: 58px; height: 58px; font-size: 24px; }

/* ============ 通话阶段 ============ */
.video-stage {
  position: relative;
  border-radius: 12px;
  overflow: hidden;
  background: #1a1d29;
  aspect-ratio: 16/10;
}

.remote-video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.local-video {
  position: absolute;
  right: 14px;
  top: 14px;
  width: 150px;
  aspect-ratio: 4/3;
  object-fit: cover;
  border-radius: 10px;
  border: 2px solid rgba(255, 255, 255, 0.7);
  background: #000;
  transform: scaleX(-1);
}

.remote-mic-tip {
  position: absolute;
  left: 14px;
  top: 14px;
  background: rgba(0, 0, 0, 0.55);
  color: #ffd04b;
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 12px;
}

.video-info {
  position: absolute;
  left: 14px;
  bottom: 14px;
  color: #fff;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 10px;
  background: rgba(0, 0, 0, 0.45);
  padding: 6px 12px;
  border-radius: 8px;
}

.duration { font-variant-numeric: tabular-nums; opacity: 0.9; }

.video-btns {
  position: absolute;
  right: 14px;
  bottom: 14px;
}
</style>
