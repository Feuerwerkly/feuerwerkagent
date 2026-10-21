<template>
  <el-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)"
             title="发起群聊" width="440px" align-center :close-on-click-modal="false">
    <el-form label-width="70px">
      <el-form-item label="群名称" required>
        <el-input v-model="name" maxlength="30" placeholder="给群聊起个名字" />
      </el-form-item>
      <el-form-item :label="`成员(${selected.length})`">
        <div class="member-pick">
          <div v-if="friends.length === 0" class="no-friend">还没有好友，先去添加好友吧</div>
          <el-checkbox-group v-model="selected">
            <el-checkbox v-for="f in friends" :key="f.id" :value="f.id">
              <span class="f-name">{{ f.nickname }}</span>
              <span class="f-state">{{ f.online ? '在线' : '离线' }}</span>
            </el-checkbox>
          </el-checkbox-group>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="creating" @click="create">创建群聊</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { imStore as im } from '../../im/store'
import imApi from '../../im/api'

const props = defineProps({ modelValue: Boolean })
const emit = defineEmits(['update:modelValue', 'created'])

const name = ref('')
const selected = ref([])
const creating = ref(false)

const friends = computed(() => im.friends)

watch(() => props.modelValue, v => {
  if (v) {
    name.value = ''
    selected.value = []
    im.refreshFriends()
  }
})

async function create() {
  if (!name.value.trim()) {
    ElMessage.warning('请填写群名称')
    return
  }
  if (selected.value.length === 0) {
    ElMessage.warning('请至少选择一位好友')
    return
  }
  creating.value = true
  try {
    const group = await imApi.post('/groups', { name: name.value.trim(), memberIds: selected.value })
    ElMessage.success('群聊创建成功')
    emit('update:modelValue', false)
    emit('created', group)
  } catch (e) { /* 拦截器已提示 */ } finally {
    creating.value = false
  }
}
</script>

<style scoped>
.member-pick {
  max-height: 240px;
  overflow-y: auto;
  width: 100%;
  border: 1px solid #eee;
  border-radius: 8px;
  padding: 6px 12px;
}

.no-friend { color: #bbb; font-size: 13px; padding: 8px 0; }

:deep(.el-checkbox) {
  display: flex;
  width: 100%;
  margin-right: 0;
}

.f-name { font-size: 14px; }
.f-state { font-size: 12px; color: #bbb; margin-left: 8px; }
</style>
