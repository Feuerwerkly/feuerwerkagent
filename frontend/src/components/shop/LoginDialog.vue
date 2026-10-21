<template>
  <el-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)"
             :title="mode === 'login' ? '登录 Feuerwerk 商城' : '注册新账号'" width="400px" align-center>
    <el-tabs v-model="mode" stretch>
      <el-tab-pane label="登录" name="login">
        <el-form :model="loginForm" label-width="70px" @submit.prevent>
          <el-form-item label="用户名">
            <el-input v-model="loginForm.username" placeholder="用户名" @keydown.enter="doLogin" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="loginForm.password" type="password" show-password placeholder="密码" @keydown.enter="doLogin" />
          </el-form-item>
          <el-button type="primary" style="width: 100%" :loading="loading" @click="doLogin">登 录</el-button>
          <div class="tip">演示买家：demo / 123456　管理员：admin / admin123</div>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="注册" name="register">
        <el-form :model="regForm" label-width="70px" @submit.prevent>
          <el-form-item label="用户名">
            <el-input v-model="regForm.username" placeholder="2-20位字母数字下划线或中文" />
          </el-form-item>
          <el-form-item label="昵称">
            <el-input v-model="regForm.nickname" placeholder="选填" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="regForm.password" type="password" show-password placeholder="至少6位" />
          </el-form-item>
          <el-button type="primary" style="width: 100%" :loading="loading" @click="doRegister">注 册</el-button>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../../shop/api'
import { shopStore } from '../../shop/store'

defineProps({ modelValue: Boolean })
const emit = defineEmits(['update:modelValue', 'logged-in'])

const mode = ref('login')
const loading = ref(false)
const loginForm = reactive({ username: '', password: '' })
const regForm = reactive({ username: '', nickname: '', password: '' })

async function doLogin() {
  if (!loginForm.username || !loginForm.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const data = await api.post('/auth/login', { ...loginForm })
    shopStore.setAuth(data.user, data.token)
    ElMessage.success(`欢迎回来，${data.user.nickname || data.user.username}！`)
    emit('update:modelValue', false)
    emit('logged-in')
  } catch (e) { /* 拦截器已提示 */ } finally {
    loading.value = false
  }
}

async function doRegister() {
  if (!regForm.username || !regForm.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const data = await api.post('/auth/register', { ...regForm })
    shopStore.setAuth(data.user, data.token)
    ElMessage.success('注册成功，已自动登录！')
    emit('update:modelValue', false)
    emit('logged-in')
  } catch (e) { /* 拦截器已提示 */ } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.tip {
  margin-top: 10px;
  font-size: 12px;
  color: #999;
  text-align: center;
}
</style>
