<template>
  <el-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)"
             :title="`群信息 - ${info?.name || ''}`" width="500px" align-center>
    <div v-if="info" class="group-info">
      <!-- 群主可编辑 -->
      <div class="edit-row">
        <template v-if="isOwner">
          <el-input v-model="editName" size="small" style="width: 200px" maxlength="30" placeholder="群名称" />
          <el-input v-model="editNotice" size="small" style="flex: 1" maxlength="100" placeholder="群公告（选填）" />
          <el-button size="small" type="primary" :loading="saving" @click="saveInfo">保存</el-button>
        </template>
        <template v-else>
          <div class="notice" v-if="info.notice">📢 {{ info.notice }}</div>
        </template>
      </div>

      <div class="section-title">群成员（{{ members.length }}）</div>
      <div class="member-list">
        <div v-for="m in members" :key="m.id" class="member">
          <div class="avatar-wrap">
            <ImAvatar :name="m.nickname" :id="m.id" />
            <span v-if="m.online" class="dot"></span>
          </div>
          <div class="m-info">
            <div class="m-name">{{ m.nickname }} <el-tag v-if="m.role === 'OWNER'" size="small" type="warning">群主</el-tag></div>
            <div class="m-sub">@{{ m.username }} · {{ m.online ? '在线' : '离线' }}</div>
          </div>
          <el-button v-if="isOwner && m.role !== 'OWNER'" size="small" text type="danger" @click="kick(m)">
            移出
          </el-button>
        </div>
      </div>

      <!-- 邀请好友 -->
      <div class="section-title">邀请好友</div>
      <div v-if="invitableFriends.length === 0" class="no-invite">好友都已在群里啦</div>
      <div v-else class="invite-row">
        <el-select v-model="inviteIds" multiple placeholder="选择要邀请的好友" size="small" style="flex: 1">
          <el-option v-for="f in invitableFriends" :key="f.id" :label="f.nickname" :value="f.id" />
        </el-select>
        <el-button size="small" type="primary" :disabled="inviteIds.length === 0" :loading="inviting" @click="invite">
          邀请
        </el-button>
      </div>

      <div class="danger-row">
        <el-button v-if="!isOwner" type="warning" plain @click="quit">退出群聊</el-button>
        <el-button v-if="isOwner" type="danger" plain @click="disband">解散群聊</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { imStore as im } from '../../im/store'
import { shopStore } from '../../shop/store'
import imApi from '../../im/api'
import ImAvatar from './ImAvatar.vue'

const props = defineProps({ modelValue: Boolean })
const emit = defineEmits(['update:modelValue'])

const info = ref(null)
const members = ref([])
const editName = ref('')
const editNotice = ref('')
const inviteIds = ref([])
const saving = ref(false)
const inviting = ref(false)

const isOwner = computed(() => info.value && String(info.value.ownerId) === String(shopStore.user?.id))

const invitableFriends = computed(() => {
  if (!info.value) return []
  const inGroup = new Set(members.value.map(m => String(m.id)))
  return im.friends.filter(f => !inGroup.has(String(f.id)))
})

watch(() => props.modelValue, async v => {
  if (v && im.active && im.active.peerType === 'GROUP') {
    await load()
  }
})

async function load() {
  const groupId = im.active.peerId
  try {
    info.value = await imApi.get(`/groups/${groupId}`)
    members.value = await imApi.get(`/groups/${groupId}/members`) || []
    editName.value = info.value.name || ''
    editNotice.value = info.value.notice || ''
    inviteIds.value = []
    im.refreshFriends()
  } catch (e) { /* 拦截器已提示 */ }
}

async function saveInfo() {
  saving.value = true
  try {
    await imApi.put(`/groups/${info.value.id}`, { name: editName.value, notice: editNotice.value })
    ElMessage.success('群信息已更新')
    if (im.active) im.active.name = editName.value
    load()
  } catch (e) { /* 拦截器已提示 */ } finally {
    saving.value = false
  }
}

async function invite() {
  inviting.value = true
  try {
    await imApi.post(`/groups/${info.value.id}/members`, { userIds: inviteIds.value })
    ElMessage.success('邀请成功')
    load()
  } catch (e) { /* 拦截器已提示 */ } finally {
    inviting.value = false
  }
}

async function kick(m) {
  try {
    await ElMessageBox.confirm(`确定将「${m.nickname}」移出群聊吗？`, '移出成员', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await imApi.delete(`/groups/${info.value.id}/members/${m.id}`)
    ElMessage.success('已移出')
    load()
  } catch (e) { /* 拦截器已提示 */ }
}

async function quit() {
  try {
    await ElMessageBox.confirm('确定退出该群聊吗？', '退出群聊', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await imApi.post(`/groups/${info.value.id}/quit`)
    ElMessage.success('已退出群聊')
    emit('update:modelValue', false)
    im.closeConversation()
    im.refreshGroups()
    im.refreshConversations()
  } catch (e) { /* 拦截器已提示 */ }
}

async function disband() {
  try {
    await ElMessageBox.confirm('解散后所有成员将被移出且不可恢复，确定解散吗？', '解散群聊', { type: 'error' })
  } catch (e) {
    return
  }
  try {
    await imApi.post(`/groups/${info.value.id}/disband`)
    ElMessage.success('群聊已解散')
    emit('update:modelValue', false)
    im.closeConversation()
    im.refreshGroups()
    im.refreshConversations()
  } catch (e) { /* 拦截器已提示 */ }
}
</script>

<style scoped>
.group-info { display: flex; flex-direction: column; gap: 6px; }

.edit-row { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }

.notice {
  background: #fdf6ec;
  color: #b88230;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
  width: 100%;
}

.section-title { font-size: 13px; color: #999; margin-top: 8px; }

.member-list {
  max-height: 220px;
  overflow-y: auto;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 4px 10px;
}

.member {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 0;
  border-bottom: 1px dashed #f5f5f5;
}

.member:last-child { border-bottom: none; }

.avatar-wrap { position: relative; }

.dot {
  position: absolute;
  right: -1px;
  bottom: -1px;
  width: 10px;
  height: 10px;
  background: #67c23a;
  border: 2px solid #fff;
  border-radius: 50%;
}

.m-info { flex: 1; min-width: 0; }

.m-name { font-size: 14px; color: #333; display: flex; align-items: center; gap: 6px; }

.m-sub { font-size: 12px; color: #bbb; margin-top: 2px; }

.no-invite { color: #bbb; font-size: 13px; }

.invite-row { display: flex; gap: 8px; }

.danger-row {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 12px;
}
</style>
